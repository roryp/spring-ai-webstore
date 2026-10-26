import assert from "node:assert/strict";
import { test } from "node:test";
import { ACTIONS, openInputSchema } from "../schemas.mjs";
import { EXAMPLE_TRACE, TraceError, buildView, describeForAgent, formatDuration, parseTrace } from "../trace.mjs";

const span = { id: "a", name: "http post /ask", startMs: 0, durationMs: 1 };

test("the example is the real step 4 trace from the README", () => {
    const view = buildView(EXAMPLE_TRACE, { source: "example" });

    assert.equal(view.durationLabel, "3.5 s");
    assert.deepEqual(
        view.spans.map((s) => [s.name, s.depth, s.category, s.barLabel]),
        [
            ["http post /ask", 0, "request", "3.5 s"],
            ["chat gpt-6-luna", 1, "model", "1.5 s, 197 in / 13 out"],
            ["execute_tool listProducts", 1, "tool", "1 ms"],
            ["chat gpt-6-luna", 1, "model", "1.9 s, 352 in / 39 out"],
        ],
    );
    assert.equal(view.summary.tokensLabel, "549 in + 52 out");
    assert.equal(view.summary.cost.label, "$0.000081");
    assert.equal(view.summary.modelCalls, 2);
    assert.equal(view.summary.toolCalls, 1);
    assert.deepEqual(view.ticks.map((tick) => tick.label), ["0 s", "1 s", "2 s", "3 s"]);
    assert.match(view.note, /traces don't record it/);
});

test("a trace from Application Insights, with tokens as text, gets totals and a cost", () => {
    const trace = parseTrace({
        traceId: "4bf92f3577b34da6a3ce929d0e0e4736",
        spans: [
            { id: "b", parentId: "a", name: "chat gpt-6-luna", startMs: 1010, durationMs: 1000, attributes: { "gen_ai.usage.input_tokens": "188", "gen_ai.usage.output_tokens": "22" } },
            { id: "a", name: "POST /ask", startMs: 1000, durationMs: 1500 },
        ],
    });
    const view = buildView(trace, { source: "saved", savedAt: "2026-10-22T10:00:00.000Z" });

    assert.deepEqual(view.spans.map((s) => s.id), ["a", "b"], "parents come before their children");
    assert.equal(view.spans[0].category, "request");
    assert.equal(view.spans[1].startMs, 10, "times are relative to the first span");
    assert.equal(view.spans[1].model, "gpt-6-luna");
    assert.equal(view.summary.tokensLabel, "188 in + 22 out");
    assert.equal(view.summary.cost.label, "$0.000030", "the README's poem question");
    assert.equal(view.note, null);
});

test("cost is only shown for models with known prices", () => {
    const priced = (attributes) =>
        buildView(parseTrace({ traceId: "t1", spans: [{ ...span, name: "chat other-model", attributes }] }), { source: "saved" }).summary;

    const unknown = priced({ "gen_ai.request.model": "other-model", "gen_ai.usage.input_tokens": 10 });
    assert.equal(unknown.cost, null);
    assert.match(unknown.costNote, /not other-model/);
    assert.equal(unknown.tokensLabel, "10 in + 0 out");

    const none = priced(undefined);
    assert.equal(none.cost, null);
    assert.match(none.costNote, /no token counts/);
});

test("bad traces are refused with a reason", () => {
    const cases = [
        [{ traceId: "ABC", spans: [span] }, /lowercase/],
        [{ traceId: "t1", spans: [] }, /1 to 500 spans/],
        [{ traceId: "t1", extra: 1, spans: [span] }, /unknown field "extra"/],
        [{ traceId: "t1", spans: [{ ...span, start_ms: 0 }] }, /unknown field "start_ms"/],
        [{ traceId: "t1", spans: [span, span] }, /Two spans have the ID "a"/],
        [{ traceId: "t1", spans: [{ ...span, parentId: "zz" }] }, /no span has that ID/],
        [{ traceId: "t1", spans: [{ ...span, parentId: "b" }, { ...span, id: "b", parentId: "a" }] }, /loop/],
        [{ traceId: "t1", spans: [{ ...span, durationMs: Number.NaN }] }, /durationMs/],
        [{ traceId: "t1", spans: [{ ...span, name: " " }] }, /name/],
        [{ traceId: "t1", spans: [{ ...span, kind: "browser" }] }, /kind/],
        [{ traceId: "t1", spans: [{ ...span, attributes: { x: { nested: true } } }] }, /attributes\.x/],
    ];
    for (const [input, reason] of cases) {
        assert.throws(() => parseTrace(input), (err) => err instanceof TraceError && reason.test(err.message), reason.source);
    }
});

test("durations read the way the README writes them", () => {
    const cases = [
        [0, "0 ms"],
        [0.4, "under 1 ms"],
        [1, "1 ms"],
        [999.4, "999 ms"],
        [999.6, "1.0 s"],
        [3460, "3.5 s"],
        [59_940, "59.9 s"],
        [59_960, "1 min 0 s"],
        [125_000, "2 min 5 s"],
    ];
    for (const [ms, label] of cases) assert.equal(formatDuration(ms), label);
});

test("the agent's view has no layout numbers", () => {
    const view = describeForAgent(buildView(EXAMPLE_TRACE, { source: "example", selectedSpanId: "tool" }));

    assert.equal(view.selectedSpanId, "tool");
    assert.deepEqual(Object.keys(view.spans[1]).sort(), ["description", "duration", "durationMs", "id", "inputTokens", "model", "name", "outputTokens", "parentId", "startMs", "type"]);
    assert.equal(view.spans[2].tool, "listProducts");
    assert.match(view.summary.cost, /^\$0\.000081 at \$0\.10 per 1M tokens in/);
});

test("actions have unique names and strict input schemas", () => {
    const names = ACTIONS.map((action) => action.name);
    assert.equal(new Set(names).size, names.length);
    for (const action of ACTIONS) {
        assert.ok(!action.name.startsWith("canvas."), action.name);
        assert.ok(action.description.length > 0, action.name);
        if (action.inputSchema) {
            assert.equal(action.inputSchema.type, "object", action.name);
            assert.equal(action.inputSchema.additionalProperties, false, action.name);
        }
    }
    assert.equal(openInputSchema.type, "object");
});
