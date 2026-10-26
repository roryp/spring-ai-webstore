// The canvas page: draws the trace, replays it, and tells the extension which span the user selects.
// Data arrives as JSON and goes into the page with textContent, never as HTML.

const $ = (id) => document.getElementById(id);
const els = {
    source: $("source"),
    title: $("trace-title"),
    meta: $("trace-meta"),
    note: $("trace-note"),
    clock: $("clock"),
    replay: $("replay"),
    timeline: $("timeline"),
    spans: $("spans"),
    axis: $("axis"),
    status: $("status"),
    detailTitle: $("detail-title"),
    detailDescription: $("detail-description"),
    detailFacts: $("detail-facts"),
    detailAttributes: $("detail-attributes"),
    attributes: $("attributes"),
    totals: $("totals"),
    hops: [...document.querySelectorAll(".hop")],
};
const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const HOP_MS = 800;

let view = null;
let drawnTraceKey = null;
let shownSelection = null;
let replayRun = 0;
let connectionLost = false;

function el(tag, { className, text, attrs } = {}, children = []) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    for (const [name, value] of Object.entries(attrs ?? {})) node.setAttribute(name, value);
    node.append(...children);
    return node;
}

function announce(message) {
    els.status.textContent = message;
}

function plural(count, word) {
    return `${count} ${word}${count === 1 ? "" : "s"}`;
}

function clockLabel(ms) {
    return ms < 1000 ? `${Math.round(ms)} ms` : `${(ms / 1000).toFixed(1)} s`;
}

function savedAtLabel(iso) {
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? "time unknown" : date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function fact(term, value, note) {
    const dd = el("dd", { text: value });
    if (note) dd.append(el("small", { text: note }));
    return el("div", {}, [el("dt", { text: term }), dd]);
}

function rows() {
    return [...els.spans.querySelectorAll(".row")];
}

function rowFor(spanId) {
    return rows().find((row) => row.dataset.id === spanId) ?? null;
}

// Wide bars hold their label; narrow ones put it beside the bar, on whichever side has room.
function labelPlacement(span) {
    if (span.widthPct >= 30) return "inside";
    return span.startPct + span.widthPct <= 72 ? "after" : "before";
}

function drawRow(span) {
    const track = el("span", { className: "track", attrs: { "data-category": span.category } }, [
        el("span", { className: "bar" }, [el("span", { className: "fill" })]),
        el("span", { className: `bar-label ${labelPlacement(span)}`, text: span.barLabel }),
    ]);
    track.style.setProperty("--start", `${span.startPct}%`);
    track.style.setProperty("--width", `${span.widthPct}%`);
    const nameCell = el("span", { className: "name-cell" }, [
        el("span", { className: "name", text: span.name }),
        el("span", { className: "kind", text: span.categoryLabel }),
    ]);
    nameCell.style.setProperty("--depth", String(span.depth));
    const label = `${span.name}, ${span.categoryLabel}, starts at ${span.startLabel}, takes ${span.barLabel}. ${span.description}`;
    const button = el("button", { className: "row", attrs: { type: "button", "aria-pressed": "false", "aria-label": label, "data-id": span.id } }, [
        nameCell,
        track,
        el("span", { className: "why", text: span.description }),
    ]);
    button.addEventListener("click", () => choose(span.id));
    return el("li", {}, [button]);
}

function drawTotals() {
    const { summary } = view;
    els.totals.replaceChildren(
        fact("Took", view.durationLabel),
        fact("Model calls", String(summary.modelCalls)),
        fact("Tool calls", String(summary.toolCalls)),
        fact("Tokens", summary.hasTokens ? summary.tokensLabel : "None recorded"),
        summary.cost ? fact("Cost", summary.cost.label, `at ${summary.cost.pricing}`) : fact("Cost", "Not shown", summary.costNote),
    );
}

function drawTrace() {
    stopReplay();
    const hadFocus = els.spans.contains(document.activeElement);
    drawnTraceKey = view.traceKey;
    shownSelection = null;
    els.source.textContent = view.source === "example" ? "Real data from step 4" : `Saved trace · ${savedAtLabel(view.savedAt)}`;
    els.title.textContent = view.label ?? `Trace ${view.traceId}`;
    els.meta.textContent = `${plural(view.spans.length, "span")} · ${view.durationLabel} · trace ID ${view.traceId}`;
    els.note.textContent = view.note ?? "";
    els.note.hidden = !view.note;
    els.spans.replaceChildren(...view.spans.map(drawRow));
    els.axis.replaceChildren(
        ...view.ticks.map((tick) => {
            const node = el("span", { className: "tick", text: tick.label });
            node.style.setProperty("--pct", `${tick.pct}%`);
            return node;
        }),
    );
    drawTotals();
    els.replay.disabled = false;
    if (hadFocus) rowFor(view.selectedSpanId ?? view.spans[0]?.id)?.focus();
}

function drawSelection() {
    for (const row of rows()) row.setAttribute("aria-pressed", String(row.dataset.id === view.selectedSpanId));
    const span = view.spans.find((candidate) => candidate.id === view.selectedSpanId);
    if (!span) {
        els.detailTitle.textContent = "None yet";
        els.detailDescription.textContent = "Select a span in the timeline to see what it records.";
        els.detailFacts.replaceChildren();
        els.detailAttributes.hidden = true;
        shownSelection = null;
        return;
    }
    els.detailTitle.replaceChildren(el("code", { text: span.name }));
    els.detailDescription.textContent = span.description;
    const facts = [
        fact("Type", span.categoryLabel),
        fact("Starts", span.startMs === 0 ? "At the start of the trace" : `${span.startLabel} into the trace`),
        fact("Takes", span.durationLabel),
        fact("Runs inside", span.parentName ?? "Nothing. It's the root span."),
    ];
    if (span.model) facts.push(fact("Model", span.model));
    if (span.inputTokens !== null || span.outputTokens !== null) {
        facts.push(fact("Tokens", `${span.inputTokens ?? "?"} in / ${span.outputTokens ?? "?"} out`));
    }
    if (span.tool) facts.push(fact("Tool", span.tool));
    if (span.kind) facts.push(fact("Span kind", span.kind));
    els.detailFacts.replaceChildren(...facts);
    els.attributes.replaceChildren(
        ...span.attributes.map(({ key, value }) =>
            el("tr", {}, [el("th", { text: key, attrs: { scope: "row" } }), el("td", { text: value })]),
        ),
    );
    els.detailAttributes.hidden = span.attributes.length === 0;
    if (shownSelection !== span.id) {
        const row = rowFor(span.id);
        if (row && row !== document.activeElement) row.scrollIntoView({ block: "nearest" });
        shownSelection = span.id;
    }
}

function apply(next) {
    if (view && next.revision < view.revision) return;
    view = next;
    if (view.traceKey !== drawnTraceKey) drawTrace();
    drawSelection();
}

async function choose(spanId) {
    if (!view) return;
    view = { ...view, selectedSpanId: spanId };
    drawSelection();
    try {
        const response = await fetch("select", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ spanId }),
        });
        if (!response.ok) {
            const body = await response.json().catch(() => ({}));
            throw new Error(body.error ?? `the canvas server answered ${response.status}`);
        }
    } catch (err) {
        announce(`Copilot can't see your selection: ${err.message}`);
    }
}

function moveFocus(event) {
    const all = rows();
    const index = all.indexOf(document.activeElement);
    if (index === -1) return;
    let target = null;
    switch (event.key) {
        case "ArrowDown":
            target = all[index + 1];
            break;
        case "ArrowUp":
            target = all[index - 1];
            break;
        case "Home":
            target = all[0];
            break;
        case "End":
            target = all.at(-1);
            break;
        default:
            return;
    }
    event.preventDefault();
    target?.focus();
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function stopReplay() {
    replayRun += 1;
    els.timeline.classList.remove("replaying");
    for (const row of rows()) {
        row.style.removeProperty("--progress");
        row.classList.remove("pending");
    }
    els.clock.textContent = "";
    for (const hop of els.hops) hop.classList.remove("active", "done");
}

function showTime(entries, t, total) {
    els.timeline.style.setProperty("--t", String(t / total));
    els.clock.textContent = clockLabel(t);
    for (const { row, span } of entries) {
        const progress =
            span.durationMs === 0 ? Number(t >= span.startMs) : Math.min(Math.max((t - span.startMs) / span.durationMs, 0), 1);
        row.style.setProperty("--progress", String(progress));
        row.classList.toggle("pending", t < span.startMs);
    }
}

async function sendTraceOn(run) {
    els.clock.textContent = view.durationLabel;
    announce(`The request took ${view.durationLabel}. OpenTelemetry now sends its trace on.`);
    for (const hop of els.hops) {
        hop.classList.add("active");
        if (!reducedMotion.matches) await wait(HOP_MS);
        if (run !== replayRun) return;
        hop.classList.replace("active", "done");
    }
    els.timeline.classList.remove("replaying");
    announce("The trace is in Application Insights. On Azure, it shows up there one to three minutes after the request.");
}

// Replays at real speed, but squeezes long traces into 8 seconds and stretches tiny ones to 1.5.
function replay() {
    if (!view) return;
    stopReplay();
    const run = replayRun;
    const total = Math.max(view.durationMs, 1);
    const speed = total / Math.min(Math.max(total, 1500), 8000);
    const entries = rows().map((row) => ({ row, span: view.spans.find((span) => span.id === row.dataset.id) }));
    let pace = "at real speed";
    if (speed > 1.05) pace = `${speed.toFixed(1)} times faster than real time`;
    else if (speed < 0.95) pace = "in slow motion";
    announce(`Replaying the request ${pace}.`);
    if (reducedMotion.matches) {
        showTime(entries, total, total);
        sendTraceOn(run);
        return;
    }
    els.timeline.classList.add("replaying");
    const startedAt = performance.now();
    const frame = (now) => {
        if (run !== replayRun) return;
        const t = Math.min((now - startedAt) * speed, total);
        showTime(entries, t, total);
        if (t < total) requestAnimationFrame(frame);
        else sendTraceOn(run);
    };
    requestAnimationFrame(frame);
}

async function loadState() {
    const response = await fetch("state", { cache: "no-store" });
    if (!response.ok) throw new Error(`the canvas server answered ${response.status}`);
    apply(await response.json());
}

function listen() {
    const events = new EventSource("events");
    events.addEventListener("state", (event) => apply(JSON.parse(event.data)));
    events.addEventListener("replay", () => replay());
    events.addEventListener("open", () => {
        if (!connectionLost) return;
        connectionLost = false;
        announce("Connected to the canvas server again.");
    });
    events.addEventListener("error", () => {
        if (connectionLost) return;
        connectionLost = true;
        announce("Lost the connection to the canvas server. Trying again…");
    });
}

els.replay.addEventListener("click", replay);
els.spans.addEventListener("keydown", moveFocus);
loadState().catch((err) => announce(`Couldn't load the trace: ${err.message}`));
listen();
