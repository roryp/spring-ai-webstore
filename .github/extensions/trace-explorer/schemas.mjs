// The names, descriptions and JSON Schemas the agent sees. Plain data, so tests can load it without the SDK.
import { EXAMPLE_TRACE_ID, LIMITS, SPAN_ID_PATTERN, SPAN_KINDS, TRACE_ID_PATTERN } from "./trace.mjs";

export const CANVAS_ID = "trail-store-trace-explorer";
export const DISPLAY_NAME = "Trail Store trace";
export const DESCRIPTION =
    "Shows one Trail Store question as an OpenTelemetry trace: a timeline of spans that Container Apps sends to Application Insights.";

const traceIdSchema = {
    type: "string",
    pattern: TRACE_ID_PATTERN,
    description: `${EXAMPLE_TRACE_ID} for the built-in example, or a saved trace's ID, such as an Application Insights operation_Id.`,
};

const spanIdSchema = { type: "string", pattern: SPAN_ID_PATTERN };

const spanSchema = {
    type: "object",
    properties: {
        id: { ...spanIdSchema, description: "Span ID, unique in this trace. Application Insights calls it id." },
        parentId: {
            ...spanIdSchema,
            description: "ID of the span this one runs inside. Omit it for the root span, such as the web request.",
        },
        name: {
            type: "string",
            minLength: 1,
            maxLength: LIMITS.name,
            description: "Span name, such as http post /ask, chat gpt-6-luna or execute_tool listProducts.",
        },
        kind: { type: "string", enum: [...SPAN_KINDS], description: "OpenTelemetry span kind, if known." },
        startMs: {
            type: "number",
            minimum: 0,
            maximum: LIMITS.ms,
            description: "When the span starts, in milliseconds from the start of the trace.",
        },
        durationMs: { type: "number", minimum: 0, maximum: LIMITS.ms, description: "How long the span took, in milliseconds." },
        description: {
            type: "string",
            maxLength: LIMITS.description,
            description: "Optional explanation of the step in plain words, for the canvas.",
        },
        attributes: {
            type: "object",
            maxProperties: LIMITS.attributes,
            additionalProperties: { type: ["string", "number", "boolean"] },
            description:
                "Span attributes, as in Application Insights customDimensions. The canvas reads gen_ai.operation.name, " +
                "gen_ai.request.model, gen_ai.usage.input_tokens, gen_ai.usage.output_tokens and gen_ai.tool.name.",
        },
    },
    required: ["id", "name", "startMs", "durationMs"],
    additionalProperties: false,
};

export const openInputSchema = {
    type: "object",
    properties: {
        traceId: {
            ...traceIdSchema,
            description:
                `Trace to show. Leave it out to keep what the panel shows, or to start with ${EXAMPLE_TRACE_ID}: ` +
                "a real question to step 4. Use a trace ID saved with save_trace to show that trace again.",
        },
    },
    additionalProperties: false,
};

export const ACTIONS = [
    {
        name: "get_view",
        description:
            "Return what the canvas shows: the trace, its spans with IDs, timings, models and tokens, the totals, " +
            "and the span the user has selected.",
    },
    {
        name: "select_span",
        description:
            "Select one span and show its details, for example to walk someone through the trace one step at a time. " +
            "Get the span IDs from get_view.",
        inputSchema: {
            type: "object",
            properties: { spanId: { ...spanIdSchema, description: "ID of the span to select." } },
            required: ["spanId"],
            additionalProperties: false,
        },
    },
    {
        name: "replay",
        description:
            "Replay the request on the canvas: the spans grow along the timeline at real speed, then the trace travels " +
            "through the Container Apps environment to Application Insights. Reports replayed: false if no page is showing.",
    },
    {
        name: "show_trace",
        description: `Show the built-in example (${EXAMPLE_TRACE_ID}) or a trace saved earlier with save_trace.`,
        inputSchema: {
            type: "object",
            properties: { traceId: traceIdSchema },
            required: ["traceId"],
            additionalProperties: false,
        },
    },
    {
        name: "save_trace",
        description:
            "Save a trace, for example one read from Application Insights, and show it. Use its operation_Id as traceId. " +
            "Times are milliseconds from the start of the trace. The trace is stored privately on this machine, " +
            "outside the repository, and replaces any trace saved with the same ID.",
        inputSchema: {
            type: "object",
            properties: {
                traceId: traceIdSchema,
                label: {
                    type: "string",
                    maxLength: LIMITS.label,
                    description: "Optional title, such as the question that was asked. Traces don't record questions.",
                },
                spans: { type: "array", minItems: 1, maxItems: LIMITS.spans, items: spanSchema },
            },
            required: ["traceId", "spans"],
            additionalProperties: false,
        },
    },
];
