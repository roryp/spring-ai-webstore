// Canvas panels and what each one shows. Errors are TraceErrors; extension.mjs turns them into CanvasErrors.
// A panel is keyed by its instanceId and lives until onClose. Its data is keyed by trace ID.
import { DISPLAY_NAME } from "./schemas.mjs";
import { startPageServer } from "./server.mjs";
import {
    EXAMPLE_TRACE,
    EXAMPLE_TRACE_ID,
    TraceError,
    assertTraceId,
    buildView,
    defaultSpanId,
    describeForAgent,
    describeSpanForAgent,
    parseTrace,
} from "./trace.mjs";

function invalidInput(message) {
    return new TraceError("invalid_input", message);
}

export function createController({ store, log = () => {} }) {
    const panels = new Map();
    const opening = new Map();

    async function loadTrace(traceId) {
        if (traceId === EXAMPLE_TRACE_ID) return { trace: EXAMPLE_TRACE, source: "example", savedAt: null };
        const saved = await store.load(traceId);
        if (!saved) {
            throw new TraceError(
                "trace_not_found",
                `There's no saved trace with ID ${traceId}. Save it with save_trace first, or show ${EXAMPLE_TRACE_ID}.`,
            );
        }
        return { trace: saved.trace, source: "saved", savedAt: saved.savedAt };
    }

    function viewOf(panel) {
        return buildView(panel.trace, {
            source: panel.source,
            savedAt: panel.savedAt,
            selectedSpanId: panel.selectedSpanId,
            revision: panel.revision,
            traceVersion: panel.traceVersion,
        });
    }

    function publish(panel) {
        panel.revision += 1;
        panel.server?.broadcast("state", viewOf(panel));
    }

    function display(panel, loaded, { force = false } = {}) {
        const sameTrace = panel.trace?.traceId === loaded.trace.traceId;
        const changed = force || !sameTrace || panel.source !== loaded.source || panel.savedAt !== loaded.savedAt;
        const keepSelection = sameTrace && loaded.trace.spans.some((span) => span.id === panel.selectedSpanId);
        panel.trace = loaded.trace;
        panel.source = loaded.source;
        panel.savedAt = loaded.savedAt;
        panel.selectedSpanId = keepSelection ? panel.selectedSpanId : defaultSpanId(loaded.trace);
        if (changed) panel.traceVersion += 1;
        publish(panel);
    }

    function select(panel, spanId) {
        if (!panel.trace.spans.some((span) => span.id === spanId)) {
            throw new TraceError(
                "unknown_span",
                `There's no span with ID "${spanId}" in trace ${panel.trace.traceId}. Use get_view to list the span IDs.`,
            );
        }
        panel.selectedSpanId = spanId;
        publish(panel);
    }

    function requirePanel(instanceId) {
        const panel = panels.get(instanceId);
        if (!panel) throw new TraceError("not_open", "This canvas panel isn't open. Open it with open_canvas first.");
        return panel;
    }

    function opened(panel) {
        const view = viewOf(panel);
        const what = view.source === "example" ? "Real data from step 4" : `Trace ${view.traceId}`;
        return { title: DISPLAY_NAME, status: `${what} · ${view.spans.length} spans · ${view.durationLabel}`, url: panel.server.url };
    }

    function readOpenInput(input) {
        if (input === undefined || input === null) return undefined;
        if (typeof input !== "object" || Array.isArray(input)) throw invalidInput("Open the canvas with an object, such as {}.");
        for (const key of Object.keys(input)) {
            if (key !== "traceId") throw invalidInput(`Unknown open input "${key}". The only input is traceId.`);
        }
        return input.traceId === undefined ? undefined : assertTraceId(input.traceId);
    }

    /** Opens a panel, or reuses its live server and reloads its trace by trace ID. */
    async function open(instanceId, input) {
        const requested = readOpenInput(input);
        const pending = opening.get(instanceId);
        if (pending) {
            await pending.catch(() => {});
            return open(instanceId, input);
        }
        const existing = panels.get(instanceId);
        if (existing) {
            display(existing, await loadTrace(requested ?? existing.trace.traceId));
            return opened(existing);
        }
        const starting = (async () => {
            const panel = { trace: null, source: null, savedAt: null, selectedSpanId: null, revision: 0, traceVersion: 0, server: null };
            display(panel, await loadTrace(requested ?? EXAMPLE_TRACE_ID));
            panel.server = await startPageServer({ getView: () => viewOf(panel), select: (spanId) => select(panel, spanId), log });
            panels.set(instanceId, panel);
            return panel;
        })();
        opening.set(instanceId, starting);
        try {
            return opened(await starting);
        } finally {
            opening.delete(instanceId);
        }
    }

    async function close(instanceId) {
        const pending = opening.get(instanceId);
        if (pending) await pending.catch(() => {});
        const panel = panels.get(instanceId);
        if (!panel) return;
        panels.delete(instanceId);
        await panel.server.close();
    }

    return {
        open,
        close,

        async closeAll() {
            await Promise.all([...panels.keys()].map(close));
        },

        getView(instanceId) {
            return describeForAgent(viewOf(requirePanel(instanceId)));
        },

        selectSpan(instanceId, input) {
            const panel = requirePanel(instanceId);
            if (typeof input?.spanId !== "string") throw invalidInput("Give the spanId to select. Use get_view to list the span IDs.");
            select(panel, input.spanId);
            return { selectedSpanId: input.spanId, span: describeSpanForAgent(viewOf(panel), input.spanId) };
        },

        replay(instanceId) {
            const panel = requirePanel(instanceId);
            const pages = panel.server.broadcast("replay", { revision: panel.revision });
            const duration = viewOf(panel).durationLabel;
            if (pages === 0) {
                return { replayed: false, pages, reason: "No canvas page is showing right now. Show the canvas, then replay again." };
            }
            return { replayed: true, pages, duration };
        },

        async showTrace(instanceId, input) {
            const panel = requirePanel(instanceId);
            display(panel, await loadTrace(assertTraceId(input?.traceId)));
            return describeForAgent(viewOf(panel));
        },

        async saveTrace(instanceId, input) {
            const panel = requirePanel(instanceId);
            const trace = parseTrace(input);
            if (trace.traceId === EXAMPLE_TRACE_ID) {
                throw new TraceError("reserved_trace_id", `${EXAMPLE_TRACE_ID} is the built-in example. Save your trace under its own ID.`);
            }
            const saved = await store.save(trace);
            display(panel, { trace: saved.trace, source: "saved", savedAt: saved.savedAt }, { force: true });
            return {
                saved: { file: saved.file, savedAt: saved.savedAt, replaced: saved.replaced },
                view: describeForAgent(viewOf(panel)),
            };
        },
    };
}
