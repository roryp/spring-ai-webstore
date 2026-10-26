// Saved traces live outside the repository, in the Copilot home that every Copilot host shares:
// <COPILOT_HOME or ~/.copilot>/extension-data/trail-store-trace-explorer/traces/trace-<traceId>.json
// Each file is keyed by its trace ID, written atomically, and checked on every read.
import { randomBytes } from "node:crypto";
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import { homedir } from "node:os";
import path from "node:path";
import { CANVAS_ID } from "./schemas.mjs";
import { TraceError, assertTraceId, parseTrace } from "./trace.mjs";

export const STORE_FORMAT = "trail-store-trace-explorer/saved-trace";
export const STORE_VERSION = 1;

export function defaultDataDir(env = process.env) {
    const copilotHome = env.COPILOT_HOME ? env.COPILOT_HOME : path.join(homedir(), ".copilot");
    return path.join(copilotHome, "extension-data", CANVAS_ID);
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// On Windows, a reader or a virus scanner can hold the target file open for a moment.
async function replaceFile(from, to) {
    for (let attempt = 0; ; attempt++) {
        try {
            return await rename(from, to);
        } catch (err) {
            if (attempt >= 5 || !["EPERM", "EACCES", "EBUSY"].includes(err.code)) throw err;
            await sleep(25 * 2 ** attempt);
        }
    }
}

export function createTraceStore({ dataDir = defaultDataDir() } = {}) {
    const dir = path.join(dataDir, "traces");
    const fileFor = (traceId) => path.join(dir, `trace-${assertTraceId(traceId)}.json`);

    function decode(text, traceId, file) {
        const damaged = (why) =>
            new TraceError("storage_corrupt", `The saved trace ${file} ${why} It was left as it is: move or delete it to save this trace again.`);
        let record;
        try {
            record = JSON.parse(text);
        } catch {
            throw damaged("isn't valid JSON.");
        }
        if (record === null || typeof record !== "object" || record.format !== STORE_FORMAT) {
            throw new TraceError("storage_unrecognized", `The file ${file} isn't a trace saved by this canvas. It was left as it is.`);
        }
        if (record.version !== STORE_VERSION) {
            throw new TraceError(
                "storage_unrecognized",
                `The saved trace ${file} has format version ${JSON.stringify(record.version)}, but this canvas reads version ` +
                    `${STORE_VERSION}. It was left as it is.`,
            );
        }
        if (typeof record.savedAt !== "string" || Number.isNaN(Date.parse(record.savedAt))) throw damaged("has no valid savedAt time.");
        let trace;
        try {
            trace = parseTrace(record.trace);
        } catch (err) {
            if (err instanceof TraceError) throw damaged(`doesn't hold a valid trace. ${err.message}`);
            throw err;
        }
        if (trace.traceId !== traceId) throw damaged(`holds trace ${trace.traceId}, not ${traceId}.`);
        return { trace, savedAt: record.savedAt, file };
    }

    /** Returns { trace, savedAt, file }, or null if no trace is saved under this ID. */
    async function load(traceId) {
        const file = fileFor(traceId);
        let text;
        try {
            text = await readFile(file, "utf8");
        } catch (err) {
            if (err.code === "ENOENT") return null;
            throw new TraceError("storage_failed", `Couldn't read the saved trace ${file}: ${err.message}`);
        }
        return decode(text, traceId, file);
    }

    /** Saves a trace that parseTrace has already checked. Never overwrites a damaged or unknown file. */
    async function save(trace) {
        const file = fileFor(trace.traceId);
        try {
            await mkdir(dir, { recursive: true });
        } catch (err) {
            throw new TraceError("storage_failed", `Couldn't create the folder ${dir}: ${err.message}`);
        }
        const existing = await load(trace.traceId);
        const record = { format: STORE_FORMAT, version: STORE_VERSION, savedAt: new Date().toISOString(), trace };
        const temp = path.join(dir, `.trace-${trace.traceId}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`);
        try {
            await writeFile(temp, `${JSON.stringify(record, null, 2)}\n`, { encoding: "utf8", flag: "wx" });
            await replaceFile(temp, file);
        } catch (err) {
            await rm(temp, { force: true }).catch(() => {});
            throw new TraceError("storage_failed", `Couldn't save the trace to ${file}: ${err.message}`);
        }
        return { trace, savedAt: record.savedAt, file, replaced: existing !== null };
    }

    return { dir, fileFor, load, save };
}
