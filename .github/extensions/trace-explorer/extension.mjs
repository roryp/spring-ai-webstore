// Trail Store trace canvas: one step 4 question as an OpenTelemetry trace.
// This file only wires the Copilot SDK. The behavior lives in controller.mjs, trace.mjs and storage.mjs.
import { CanvasError, createCanvas, joinSession } from "@github/copilot-sdk/extension";
import { createController } from "./controller.mjs";
import { ACTIONS, CANVAS_ID, DESCRIPTION, DISPLAY_NAME, openInputSchema } from "./schemas.mjs";
import { createTraceStore } from "./storage.mjs";
import { TraceError } from "./trace.mjs";

let session;

function warn(message) {
    session?.log(message, { level: "warning", ephemeral: true }).catch(() => {});
}

const controller = createController({ store: createTraceStore(), log: warn });

function toCanvasError(err) {
    if (err instanceof CanvasError) return err;
    if (err instanceof TraceError) return new CanvasError(err.code, err.message);
    return new CanvasError("internal_error", `${DISPLAY_NAME} hit an unexpected error: ${err?.message ?? err}`);
}

const guarded = (handler) => async (ctx) => {
    try {
        return await handler(ctx);
    } catch (err) {
        throw toCanvasError(err);
    }
};

const handlers = {
    get_view: (ctx) => controller.getView(ctx.instanceId),
    select_span: (ctx) => controller.selectSpan(ctx.instanceId, ctx.input),
    replay: (ctx) => controller.replay(ctx.instanceId),
    show_trace: (ctx) => controller.showTrace(ctx.instanceId, ctx.input),
    save_trace: (ctx) => controller.saveTrace(ctx.instanceId, ctx.input),
};

session = await joinSession({
    canvases: [
        createCanvas({
            id: CANVAS_ID,
            displayName: DISPLAY_NAME,
            description: DESCRIPTION,
            inputSchema: openInputSchema,
            actions: ACTIONS.map((action) => ({ ...action, handler: guarded(handlers[action.name]) })),
            open: guarded((ctx) => controller.open(ctx.instanceId, ctx.input)),
            onClose: async (ctx) => {
                try {
                    await controller.close(ctx.instanceId);
                } catch (err) {
                    warn(`${DISPLAY_NAME} couldn't stop its page server cleanly: ${err?.message ?? err}`);
                }
            },
        }),
    ],
});
