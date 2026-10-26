import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { homedir, tmpdir } from "node:os";
import path from "node:path";
import { afterEach, beforeEach, test } from "node:test";
import { STORE_FORMAT, STORE_VERSION, createTraceStore, defaultDataDir } from "../storage.mjs";
import { TraceError, parseTrace } from "../trace.mjs";

let dataDir;
let store;

beforeEach(async () => {
    dataDir = await mkdtemp(path.join(tmpdir(), "trace-store-"));
    store = createTraceStore({ dataDir });
});

afterEach(async () => {
    await rm(dataDir, { recursive: true, force: true });
});

const trace = parseTrace({
    traceId: "abc123",
    label: "Do you sell kayaks?",
    spans: [{ id: "root", name: "http post /ask", startMs: 0, durationMs: 2500 }],
});

test("saves a trace under its ID and reads it back", async () => {
    assert.equal(await store.load("abc123"), null);

    const saved = await store.save(trace);
    assert.equal(saved.replaced, false);
    assert.equal(saved.file, path.join(dataDir, "traces", "trace-abc123.json"));

    const record = JSON.parse(await readFile(saved.file, "utf8"));
    assert.equal(record.format, STORE_FORMAT);
    assert.equal(record.version, STORE_VERSION);

    const loaded = await store.load("abc123");
    assert.deepEqual(loaded.trace, trace);
    assert.equal(loaded.savedAt, saved.savedAt);

    assert.equal((await store.save(trace)).replaced, true);
    assert.deepEqual(await readdir(store.dir), ["trace-abc123.json"], "no temporary files are left behind");
});

test("reports damaged or unknown files and never overwrites them", async () => {
    await mkdir(store.dir, { recursive: true });
    const file = store.fileFor("abc123");
    const savedAt = "2026-10-22T10:00:00.000Z";
    const cases = [
        ["{ not json", "storage_corrupt"],
        [JSON.stringify({ format: "someone-else" }), "storage_unrecognized"],
        [JSON.stringify({ format: STORE_FORMAT, version: 99, savedAt, trace }), "storage_unrecognized"],
        [JSON.stringify({ format: STORE_FORMAT, version: STORE_VERSION, trace }), "storage_corrupt"],
        [JSON.stringify({ format: STORE_FORMAT, version: STORE_VERSION, savedAt, trace: { ...trace, spans: [] } }), "storage_corrupt"],
        [JSON.stringify({ format: STORE_FORMAT, version: STORE_VERSION, savedAt, trace: { ...trace, traceId: "other" } }), "storage_corrupt"],
    ];
    for (const [text, code] of cases) {
        await writeFile(file, text);
        const refused = (err) => err instanceof TraceError && err.code === code && err.message.includes(file);
        await assert.rejects(store.load("abc123"), refused, text);
        await assert.rejects(store.save(trace), refused, text);
        assert.equal(await readFile(file, "utf8"), text, "the file is left as it was");
    }
});

test("refuses trace IDs that aren't safe file names", () => {
    for (const traceId of ["../escape", "a.b", "Upper", ""]) {
        assert.throws(() => store.fileFor(traceId), (err) => err instanceof TraceError && err.code === "invalid_trace_id");
    }
    assert.equal(path.basename(store.fileFor("con")), "trace-con.json", "the prefix keeps Windows device names safe");
});

test("keeps its data in the Copilot home, outside the repository", () => {
    const home = path.join("somewhere", "copilot-home");
    assert.equal(defaultDataDir({ COPILOT_HOME: home }), path.join(home, "extension-data", "trail-store-trace-explorer"));
    const fallback = path.join(homedir(), ".copilot", "extension-data", "trail-store-trace-explorer");
    assert.equal(defaultDataDir({ COPILOT_HOME: "" }), fallback);
    assert.equal(defaultDataDir({}), fallback);
});
