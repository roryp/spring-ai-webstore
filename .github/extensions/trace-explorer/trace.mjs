// The trace this canvas draws: validation for traces the agent saves, the built-in
// step 4 example, and the view model the page renders. No network or disk access here.

export class TraceError extends Error {
    constructor(code, message) {
        super(message);
        this.name = "TraceError";
        this.code = code;
    }
}

export const EXAMPLE_TRACE_ID = "step-4-example";
export const TRACE_ID_PATTERN = "^[a-z0-9][a-z0-9_-]{0,63}$";
export const SPAN_ID_PATTERN = "^[A-Za-z0-9_-]{1,64}$";
export const SPAN_KINDS = Object.freeze(["server", "client", "internal", "producer", "consumer"]);
export const LIMITS = Object.freeze({
    spans: 500,
    name: 200,
    label: 200,
    description: 500,
    attributes: 64,
    attributeKey: 128,
    attributeValue: 1000,
    ms: 86_400_000,
});

const TRACE_ID = new RegExp(TRACE_ID_PATTERN);
const SPAN_ID = new RegExp(SPAN_ID_PATTERN);
const TRACE_KEYS = new Set(["traceId", "label", "spans"]);
const SPAN_KEYS = new Set(["id", "parentId", "name", "kind", "startMs", "durationMs", "description", "attributes"]);

// From the step 4 README: GPT-6 Luna, Global Standard, in US dollars per 1M tokens.
const PRICES = new Map([
    ["gpt-6-luna", { input: 0.1, output: 0.5, text: "$0.10 per 1M tokens in and $0.50 per 1M out (GPT-6 Luna, Global Standard)" }],
]);

const CATEGORY_LABELS = Object.freeze({
    request: "web request",
    model: "model call",
    tool: "tool call",
    other: "other step",
});

const DEFAULT_DESCRIPTIONS = Object.freeze({
    request: "The web request. Everything the app did to answer it happens inside this span.",
    model: "A call to the AI model.",
    tool: "A tool call: Spring AI runs one of your Java methods.",
    other: "A timed step inside the trace.",
});

const MODEL_OPERATIONS = new Set(["chat", "text_completion", "generate_content", "embeddings"]);
const TICK_STEPS_MS = [
    1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10_000, 15_000, 30_000, 60_000, 120_000, 300_000,
    600_000, 900_000, 1_800_000, 3_600_000, 7_200_000, 14_400_000, 21_600_000, 43_200_000, 86_400_000,
];

const usd = new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    minimumSignificantDigits: 2,
    maximumSignificantDigits: 2,
});

function fail(message) {
    throw new TraceError("invalid_trace", message);
}

function isPlainObject(value) {
    if (value === null || typeof value !== "object" || Array.isArray(value)) return false;
    const proto = Object.getPrototypeOf(value);
    return proto === Object.prototype || proto === null;
}

function rejectUnknownKeys(value, allowed, where) {
    for (const key of Object.keys(value)) {
        if (!allowed.has(key)) fail(`${where} has an unknown field "${key}". Use: ${[...allowed].join(", ")}.`);
    }
}

function requiredText(value, where, max) {
    if (typeof value !== "string" || value.trim().length === 0 || value.length > max) {
        fail(`${where} must be text of 1 to ${max} characters.`);
    }
    return value.trim();
}

function optionalText(value, where, max) {
    if (value === undefined || value === null) return undefined;
    if (typeof value !== "string" || value.length > max) fail(`${where} must be text of up to ${max} characters.`);
    return value.trim() || undefined;
}

function milliseconds(value, where) {
    if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > LIMITS.ms) {
        fail(`${where} must be a number of milliseconds from 0 to ${LIMITS.ms}.`);
    }
    return value;
}

export function assertTraceId(value) {
    if (typeof value !== "string" || !TRACE_ID.test(value)) {
        throw new TraceError(
            "invalid_trace_id",
            "A trace ID must be 1 to 64 lowercase letters, digits, - or _, starting with a letter or digit. " +
                "Application Insights operation IDs already look like this.",
        );
    }
    return value;
}

function parseAttributes(value, where) {
    if (value === undefined || value === null) return undefined;
    if (!isPlainObject(value)) fail(`${where} must be an object of names and values.`);
    const entries = Object.entries(value);
    if (entries.length > LIMITS.attributes) fail(`${where} can have at most ${LIMITS.attributes} attributes.`);
    for (const [key, item] of entries) {
        if (key.length === 0 || key.length > LIMITS.attributeKey) {
            fail(`${where} has a name that isn't 1 to ${LIMITS.attributeKey} characters long.`);
        }
        const ok =
            typeof item === "boolean" ||
            (typeof item === "number" && Number.isFinite(item)) ||
            (typeof item === "string" && item.length <= LIMITS.attributeValue);
        if (!ok) fail(`${where}.${key} must be text (up to ${LIMITS.attributeValue} characters), a number or true/false.`);
    }
    return entries.length === 0 ? undefined : Object.fromEntries(entries);
}

function parseSpan(value, where) {
    if (!isPlainObject(value)) fail(`${where} must be an object.`);
    rejectUnknownKeys(value, SPAN_KEYS, where);
    if (typeof value.id !== "string" || !SPAN_ID.test(value.id)) fail(`${where}.id must be 1 to 64 letters, digits, - or _.`);
    let parentId;
    if (value.parentId !== undefined && value.parentId !== null) {
        if (typeof value.parentId !== "string" || !SPAN_ID.test(value.parentId)) {
            fail(`${where}.parentId must be the ID of another span in this trace.`);
        }
        parentId = value.parentId;
    }
    if (value.kind !== undefined && !SPAN_KINDS.includes(value.kind)) fail(`${where}.kind must be one of: ${SPAN_KINDS.join(", ")}.`);
    const span = {
        id: value.id,
        parentId,
        name: requiredText(value.name, `${where}.name`, LIMITS.name),
        kind: value.kind,
        startMs: milliseconds(value.startMs, `${where}.startMs`),
        durationMs: milliseconds(value.durationMs, `${where}.durationMs`),
        description: optionalText(value.description, `${where}.description`, LIMITS.description),
        attributes: parseAttributes(value.attributes, `${where}.attributes`),
    };
    for (const key of Object.keys(span)) if (span[key] === undefined) delete span[key];
    return span;
}

/** Checks a trace from outside, such as an action input or a saved file, and returns a clean copy. */
export function parseTrace(value) {
    if (!isPlainObject(value)) fail("The trace must be an object.");
    rejectUnknownKeys(value, TRACE_KEYS, "The trace");
    const traceId = assertTraceId(value.traceId);
    const label = optionalText(value.label, "label", LIMITS.label);
    if (!Array.isArray(value.spans) || value.spans.length === 0 || value.spans.length > LIMITS.spans) {
        fail(`spans must be a list of 1 to ${LIMITS.spans} spans.`);
    }
    const spans = value.spans.map((span, index) => parseSpan(span, `spans[${index}]`));
    const byId = new Map();
    for (const span of spans) {
        if (byId.has(span.id)) fail(`Two spans have the ID "${span.id}".`);
        byId.set(span.id, span);
    }
    for (const span of spans) {
        if (span.parentId !== undefined && !byId.has(span.parentId)) {
            fail(`Span "${span.id}" has parentId "${span.parentId}", but no span has that ID.`);
        }
    }
    for (const span of spans) {
        const seen = new Set();
        for (let current = span; current.parentId !== undefined; current = byId.get(current.parentId)) {
            if (seen.has(current.id)) fail(`The parentId links from span "${span.id}" go round in a loop.`);
            seen.add(current.id);
        }
    }
    return label === undefined ? { traceId, spans } : { traceId, label, spans };
}

// One real question to step 4, as drawn in docs/diagrams/step-4-trace.png, rounded like the README.
export const EXAMPLE_TRACE = parseTrace({
    traceId: EXAMPLE_TRACE_ID,
    label: "Is the Summit Waterproof Jacket in stock?",
    spans: [
        {
            id: "request",
            name: "http post /ask",
            kind: "server",
            startMs: 0,
            durationMs: 3460,
            description: "The web request: the browser posts the question to /ask, and the answer page comes back.",
        },
        {
            id: "chat-1",
            parentId: "request",
            name: "chat gpt-6-luna",
            startMs: 0,
            durationMs: 1490,
            description: "First model call: the model asks for the tool.",
            attributes: {
                "gen_ai.operation.name": "chat",
                "gen_ai.request.model": "gpt-6-luna",
                "gen_ai.usage.input_tokens": 197,
                "gen_ai.usage.output_tokens": 13,
            },
        },
        {
            id: "tool",
            parentId: "request",
            name: "execute_tool listProducts",
            startMs: 1500,
            durationMs: 1,
            description: "Spring AI runs your Java method, InventoryTools.listProducts, which reads the catalog.",
        },
        {
            id: "chat-2",
            parentId: "request",
            name: "chat gpt-6-luna",
            startMs: 1510,
            durationMs: 1940,
            description: "Second model call: the model writes the answer.",
            attributes: {
                "gen_ai.operation.name": "chat",
                "gen_ai.request.model": "gpt-6-luna",
                "gen_ai.usage.input_tokens": 352,
                "gen_ai.usage.output_tokens": 39,
            },
        },
    ],
});

const EXAMPLE_NOTE =
    "We added the question ourselves: traces don't record it. " +
    "Spring AI's own framework spans, such as spring_ai chat_client, are left out.";

export function formatDuration(ms) {
    if (ms === 0) return "0 ms";
    if (ms < 1) return "under 1 ms";
    if (ms < 999.5) return `${Math.round(ms)} ms`;
    if (ms < 59_950) return `${(ms / 1000).toFixed(1)} s`;
    const seconds = Math.round(ms / 1000);
    return `${Math.floor(seconds / 60)} min ${seconds % 60} s`;
}

function axisTicks(durationMs) {
    const length = Math.max(durationMs, 1);
    const step = TICK_STEPS_MS.find((candidate) => length / candidate <= 5) ?? TICK_STEPS_MS.at(-1);
    const ticks = [];
    for (let ms = 0; ms <= length; ms += step) {
        let label = `${ms} ms`;
        if (step >= 60_000) label = `${ms / 60_000} min`;
        else if (step >= 1000) label = `${ms / 1000} s`;
        ticks.push({ pct: (ms / length) * 100, label });
    }
    return ticks;
}

function text(value) {
    return typeof value === "string" && value.trim() ? value.trim() : null;
}

function tokenCount(value) {
    const number = typeof value === "string" && value.trim() !== "" ? Number(value) : value;
    return Number.isInteger(number) && number >= 0 ? number : null;
}

function afterPrefix(name, prefix) {
    return name.toLowerCase().startsWith(prefix) ? text(name.slice(prefix.length)) : null;
}

function categorize(span, operation) {
    const name = span.name.toLowerCase();
    if (MODEL_OPERATIONS.has(operation) || name.startsWith("chat ")) return "model";
    if (operation === "execute_tool" || name.startsWith("execute_tool ") || name.startsWith("tool_call ")) return "tool";
    if (span.kind === "server" || (!span.parentId && /^(http )?(get|post|put|patch|delete|head|options) /.test(name))) {
        return "request";
    }
    return "other";
}

// Depth-first, so each span sits under the span it runs inside; siblings in start order.
function orderSpans(spans) {
    const children = new Map();
    spans.forEach((span, index) => {
        const key = span.parentId ?? null;
        if (!children.has(key)) children.set(key, []);
        children.get(key).push({ span, index });
    });
    for (const list of children.values()) list.sort((a, b) => a.span.startMs - b.span.startMs || a.index - b.index);
    const ordered = [];
    const stack = (children.get(null) ?? []).map((entry) => ({ ...entry, depth: 0 })).reverse();
    while (stack.length > 0) {
        const { span, depth } = stack.pop();
        ordered.push({ span, depth });
        const kids = children.get(span.id) ?? [];
        for (let i = kids.length - 1; i >= 0; i--) stack.push({ ...kids[i], depth: depth + 1 });
    }
    return ordered;
}

function describeSpan(span, depth, origin, scale, byId) {
    const attributes = span.attributes ?? {};
    const category = categorize(span, text(attributes["gen_ai.operation.name"]));
    const inputTokens = tokenCount(attributes["gen_ai.usage.input_tokens"]);
    const outputTokens = tokenCount(attributes["gen_ai.usage.output_tokens"]);
    const hasTokens = inputTokens !== null || outputTokens !== null;
    const model =
        text(attributes["gen_ai.request.model"]) ??
        text(attributes["gen_ai.response.model"]) ??
        (category === "model" ? afterPrefix(span.name, "chat ") : null);
    const tool =
        text(attributes["gen_ai.tool.name"]) ??
        (category === "tool" ? afterPrefix(span.name, "execute_tool ") ?? afterPrefix(span.name, "tool_call ") : null);
    const startMs = span.startMs - origin;
    const durationLabel = formatDuration(span.durationMs);
    return {
        id: span.id,
        parentId: span.parentId ?? null,
        parentName: span.parentId ? byId.get(span.parentId).name : null,
        name: span.name,
        kind: span.kind ?? null,
        depth,
        category,
        categoryLabel: CATEGORY_LABELS[category],
        startMs,
        durationMs: span.durationMs,
        startPct: (startMs / scale) * 100,
        widthPct: (span.durationMs / scale) * 100,
        startLabel: formatDuration(startMs),
        durationLabel,
        barLabel: hasTokens ? `${durationLabel}, ${inputTokens ?? "?"} in / ${outputTokens ?? "?"} out` : durationLabel,
        description: span.description ?? DEFAULT_DESCRIPTIONS[category],
        model,
        tool,
        inputTokens,
        outputTokens,
        attributes: Object.entries(attributes).map(([key, value]) => ({ key, value: String(value) })),
    };
}

function summarize(spans) {
    const withTokens = spans.filter((span) => span.inputTokens !== null || span.outputTokens !== null);
    const inputTokens = withTokens.reduce((total, span) => total + (span.inputTokens ?? 0), 0);
    const outputTokens = withTokens.reduce((total, span) => total + (span.outputTokens ?? 0), 0);
    let cost = null;
    let costNote = null;
    if (withTokens.length === 0) {
        costNote = "This trace has no token counts, so there's no cost to show.";
    } else {
        const unknown = [...new Set(withTokens.filter((span) => !PRICES.has(span.model)).map((span) => span.model ?? "an unnamed model"))];
        if (unknown.length > 0) {
            costNote = `Prices are known only for ${[...PRICES.keys()].join(", ")}, not ${unknown.join(", ")}.`;
        } else {
            const total = withTokens.reduce((sum, span) => {
                const price = PRICES.get(span.model);
                return sum + ((span.inputTokens ?? 0) * price.input + (span.outputTokens ?? 0) * price.output) / 1_000_000;
            }, 0);
            const models = [...new Set(withTokens.map((span) => span.model))];
            cost = { usd: total, label: usd.format(total), pricing: models.map((model) => PRICES.get(model).text).join("; ") };
        }
    }
    return {
        modelCalls: spans.filter((span) => span.category === "model").length,
        toolCalls: spans.filter((span) => span.category === "tool").length,
        hasTokens: withTokens.length > 0,
        inputTokens,
        outputTokens,
        tokensLabel: `${inputTokens} in + ${outputTokens} out`,
        cost,
        costNote,
    };
}

/** The span a panel selects first: the root that starts first. */
export function defaultSpanId(trace) {
    return orderSpans(trace.spans)[0].span.id;
}

/**
 * Everything the page needs to draw one trace, with times relative to the trace's first span.
 * traceVersion changes whenever the panel swaps its trace, so the page knows to redraw the timeline.
 */
export function buildView(trace, { source, savedAt = null, selectedSpanId = null, revision = 0, traceVersion = 0 }) {
    const origin = Math.min(...trace.spans.map((span) => span.startMs));
    const end = Math.max(...trace.spans.map((span) => span.startMs + span.durationMs));
    const durationMs = end - origin;
    const byId = new Map(trace.spans.map((span) => [span.id, span]));
    const spans = orderSpans(trace.spans).map(({ span, depth }) =>
        describeSpan(span, depth, origin, durationMs > 0 ? durationMs : 1, byId),
    );
    return {
        traceKey: `${trace.traceId}#${traceVersion}`,
        traceId: trace.traceId,
        label: trace.label ?? null,
        source,
        savedAt,
        note: source === "example" ? EXAMPLE_NOTE : null,
        durationMs,
        durationLabel: formatDuration(durationMs),
        ticks: axisTicks(durationMs),
        spans,
        summary: summarize(spans),
        selectedSpanId: byId.has(selectedSpanId) ? selectedSpanId : null,
        revision,
    };
}

function compactSpan(span) {
    const compact = {
        id: span.id,
        parentId: span.parentId,
        name: span.name,
        type: span.categoryLabel,
        startMs: span.startMs,
        durationMs: span.durationMs,
        duration: span.durationLabel,
        description: span.description,
    };
    if (span.model) compact.model = span.model;
    if (span.tool) compact.tool = span.tool;
    if (span.inputTokens !== null) compact.inputTokens = span.inputTokens;
    if (span.outputTokens !== null) compact.outputTokens = span.outputTokens;
    return compact;
}

/** A smaller view for the agent: no layout numbers, just what the canvas shows. */
export function describeForAgent(view) {
    const { summary } = view;
    return {
        traceId: view.traceId,
        label: view.label,
        source: view.source,
        savedAt: view.savedAt,
        duration: view.durationLabel,
        durationMs: view.durationMs,
        selectedSpanId: view.selectedSpanId,
        spans: view.spans.map(compactSpan),
        summary: {
            modelCalls: summary.modelCalls,
            toolCalls: summary.toolCalls,
            inputTokens: summary.inputTokens,
            outputTokens: summary.outputTokens,
            cost: summary.cost ? `${summary.cost.label} at ${summary.cost.pricing}` : null,
            costNote: summary.costNote,
        },
    };
}

export function describeSpanForAgent(view, spanId) {
    const span = view.spans.find((candidate) => candidate.id === spanId);
    return span ? compactSpan(span) : null;
}
