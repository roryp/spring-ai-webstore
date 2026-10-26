import assert from "node:assert/strict";
import { mkdtemp, rm } from "node:fs/promises";
import { request } from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { afterEach, beforeEach, test } from "node:test";
import { createController } from "../controller.mjs";
import { createTraceStore } from "../storage.mjs";
import { TraceError } from "../trace.mjs";

let dataDir;
let store;
let controller;

beforeEach(async () => {
    dataDir = await mkdtemp(path.join(tmpdir(), "trace-canvas-"));
    store = createTraceStore({ dataDir });
    controller = createController({ store });
});

afterEach(async () => {
    await controller.closeAll();
    await rm(dataDir, { recursive: true, force: true });
});

// "Do you sell kayaks?" from the step 4 README: 2 model calls, 527 tokens in and 44 out.
const kayaks = {
    traceId: "0af7651916cd43dd8448eb211c80319c",
    label: "Do you sell kayaks?",
    spans: [
        { id: "req", name: "http post /ask", kind: "server", startMs: 0, durationMs: 3100 },
        { id: "m1", parentId: "req", name: "chat gpt-6-luna", startMs: 5, durationMs: 1400, attributes: { "gen_ai.usage.input_tokens": "190", "gen_ai.usage.output_tokens": "12" } },
        { id: "t1", parentId: "req", name: "execute_tool listProducts", startMs: 1410, durationMs: 1 },
        { id: "m2", parentId: "req", name: "chat gpt-6-luna", startMs: 1415, durationMs: 1650, attributes: { "gen_ai.usage.input_tokens": "337", "gen_ai.usage.output_tokens": "32" } },
    ],
};

const failsWith = (code) => (err) => err instanceof TraceError && err.code === code;

function post(url, body, type = "application/json") {
    return fetch(new URL("select", url), { method: "POST", headers: { "Content-Type": type }, body: JSON.stringify(body) });
}

// A tiny server-sent events reader: next(name) resolves with the data of the next event of that name.
function subscribe(url) {
    const queued = [];
    const waiting = [];
    let buffer = "";
    const req = request(new URL("events", url), (res) => {
        res.setEncoding("utf8");
        res.on("data", (chunk) => {
            buffer += chunk;
            for (let end = buffer.indexOf("\n\n"); end !== -1; end = buffer.indexOf("\n\n")) {
                const block = buffer.slice(0, end);
                buffer = buffer.slice(end + 2);
                const name = /^event: (.+)$/m.exec(block)?.[1];
                if (!name) continue;
                const data = JSON.parse(/^data: (.+)$/m.exec(block)[1]);
                const index = waiting.findIndex((waiter) => waiter.name === name);
                if (index === -1) queued.push({ name, data });
                else waiting.splice(index, 1)[0].resolve(data);
            }
        });
    });
    req.on("error", () => {});
    req.end();
    return {
        next(name) {
            const index = queued.findIndex((event) => event.name === name);
            if (index !== -1) return Promise.resolve(queued.splice(index, 1)[0].data);
            return new Promise((resolve) => waiting.push({ name, resolve }));
        },
        close: () => req.destroy(),
    };
}

test("opens a page on 127.0.0.1 that shows the step 4 example", async () => {
    const opened = await controller.open("panel", {});
    assert.match(opened.url, /^http:\/\/127\.0\.0\.1:\d+\/$/);
    assert.match(opened.status, /Real data from step 4 · 4 spans · 3\.5 s/);

    const page = await fetch(opened.url);
    assert.equal(page.status, 200);
    assert.match(page.headers.get("content-type"), /^text\/html/);
    assert.match(page.headers.get("content-security-policy"), /default-src 'none'/);
    assert.match(await page.text(), /One question, as a trace/);
    for (const asset of ["app.js", "style.css"]) assert.equal((await fetch(new URL(asset, opened.url))).status, 200, asset);
    assert.equal((await fetch(new URL("favicon.ico", opened.url))).status, 204, "no icon, and no console error");
    assert.equal((await fetch(new URL("nothing-here", opened.url))).status, 404);

    const state = await (await fetch(new URL("state", opened.url))).json();
    assert.equal(state.traceId, "step-4-example");
    assert.equal(state.selectedSpanId, "request");
    assert.equal(state.summary.cost.label, "$0.000081");
});

test("reopening a panel keeps its server and what it shows", async () => {
    const first = await controller.open("panel", {});
    controller.selectSpan("panel", { spanId: "chat-2" });

    const again = await controller.open("panel", {});
    assert.equal(again.url, first.url);
    assert.equal(controller.getView("panel").selectedSpanId, "chat-2");

    const [a, b] = await Promise.all([controller.open("other", {}), controller.open("other", {})]);
    assert.equal(a.url, b.url, "two opens at once share one server");
});

test("the page shares the user's selection and checks it", async () => {
    const { url } = await controller.open("panel", {});

    assert.equal((await post(url, { spanId: "tool" })).status, 200);
    assert.equal(controller.getView("panel").selectedSpanId, "tool");

    const unknown = await post(url, { spanId: "nope" });
    assert.equal(unknown.status, 400);
    assert.match((await unknown.json()).error, /no span with ID "nope"/);
    assert.equal((await post(url, { spanId: "tool" }, "text/plain")).status, 415);
    assert.equal((await post(url, { span: "tool" })).status, 400);
    assert.equal((await fetch(new URL("select", url))).status, 405);
    assert.equal(controller.getView("panel").selectedSpanId, "tool");
});

test("agent actions reach the open page as events", async () => {
    const { url } = await controller.open("panel", {});
    const events = subscribe(url);
    try {
        const initial = await events.next("state");
        assert.equal(initial.selectedSpanId, "request");

        const selected = controller.selectSpan("panel", { spanId: "chat-1" });
        assert.equal(selected.span.inputTokens, 197);
        const update = await events.next("state");
        assert.equal(update.selectedSpanId, "chat-1");
        assert.ok(update.revision > initial.revision);

        assert.deepEqual(controller.replay("panel"), { replayed: true, pages: 1, duration: "3.5 s" });
        await events.next("replay");
    } finally {
        events.close();
    }

    let result = controller.replay("panel");
    for (let tries = 0; result.replayed && tries < 50; tries++) {
        await new Promise((resolve) => setTimeout(resolve, 20));
        result = controller.replay("panel");
    }
    assert.equal(result.replayed, false, "with no page showing, replay says so");
    assert.match(result.reason, /No canvas page is showing/);
});

test("a saved trace is kept by its ID, so any panel can show it later", async () => {
    await controller.open("panel", {});
    const result = await controller.saveTrace("panel", kayaks);
    assert.equal(result.saved.file, path.join(dataDir, "traces", `trace-${kayaks.traceId}.json`));
    assert.equal(result.saved.replaced, false);
    assert.equal(result.view.source, "saved");
    assert.deepEqual(
        [result.view.summary.inputTokens, result.view.summary.outputTokens, result.view.summary.cost],
        [527, 44, "$0.000075 at $0.10 per 1M tokens in and $0.50 per 1M out (GPT-6 Luna, Global Standard)"],
    );

    // A new controller is what the extension gets after a reload.
    const reloaded = createController({ store });
    try {
        await reloaded.open("later", { traceId: kayaks.traceId });
        const view = reloaded.getView("later");
        assert.equal(view.label, "Do you sell kayaks?");
        assert.equal(view.spans.length, 4);

        const example = await reloaded.showTrace("later", { traceId: "step-4-example" });
        assert.equal(example.source, "example");
        assert.equal(example.selectedSpanId, "request");
    } finally {
        await reloaded.closeAll();
    }
});

test("refuses what it can't show, with a reason", async () => {
    await controller.open("panel", {});

    await assert.rejects(controller.showTrace("panel", { traceId: "feedfacefeedface" }), failsWith("trace_not_found"));
    await assert.rejects(controller.saveTrace("panel", { ...kayaks, traceId: "step-4-example" }), failsWith("reserved_trace_id"));
    await assert.rejects(controller.saveTrace("panel", { ...kayaks, spans: [] }), failsWith("invalid_trace"));
    assert.throws(() => controller.selectSpan("panel", { spanId: "nope" }), failsWith("unknown_span"));
    assert.throws(() => controller.getView("closed-panel"), failsWith("not_open"));
    await assert.rejects(controller.open("bad", { traceId: "Not An ID" }), failsWith("invalid_trace_id"));
    await assert.rejects(controller.open("bad", { colour: "red" }), failsWith("invalid_input"));
    assert.equal(controller.getView("panel").traceId, "step-4-example", "a failed action leaves the panel as it was");
});

test("closing a panel stops its server", async () => {
    const { url } = await controller.open("panel", {});
    await controller.close("panel");
    await assert.rejects(fetch(new URL("state", url)));
    assert.throws(() => controller.getView("panel"), failsWith("not_open"));
});
