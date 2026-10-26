// One small web server per canvas panel, on 127.0.0.1 and a free port. It serves the page,
// the panel's view as JSON, server-sent events when something changes, and the user's span selection.
import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { TraceError } from "./trace.mjs";

const PAGE_DIR = path.join(path.dirname(fileURLToPath(import.meta.url)), "page");
const ASSETS = new Map([
    ["/", { file: "index.html", type: "text/html; charset=utf-8" }],
    ["/app.js", { file: "app.js", type: "text/javascript; charset=utf-8" }],
    ["/style.css", { file: "style.css", type: "text/css; charset=utf-8" }],
]);
const BASE_HEADERS = {
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
};
const PAGE_POLICY =
    "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'";
const MAX_BODY_BYTES = 2048;
const HEARTBEAT_MS = 20_000;

class HttpError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

function send(res, status, type, body, headers = {}) {
    res.writeHead(status, { ...BASE_HEADERS, "Content-Type": type, ...headers });
    res.end(body);
}

function sendJson(res, status, value) {
    send(res, status, "application/json; charset=utf-8", JSON.stringify(value));
}

function requireMethod(req, ...methods) {
    if (!methods.includes(req.method)) throw new HttpError(405, `Use ${methods.join(" or ")}.`);
}

async function readJson(req) {
    // Requiring JSON also stops other web pages from posting here without a CORS preflight.
    if (!/^application\/json\b/i.test(req.headers["content-type"] ?? "")) throw new HttpError(415, "Send JSON.");
    const chunks = [];
    let size = 0;
    for await (const chunk of req) {
        size += chunk.length;
        if (size > MAX_BODY_BYTES) throw new HttpError(413, "The request is too large.");
        chunks.push(chunk);
    }
    try {
        return JSON.parse(Buffer.concat(chunks).toString("utf8"));
    } catch {
        throw new HttpError(400, "The request isn't valid JSON.");
    }
}

/**
 * Starts the page server for one panel.
 * getView() returns the panel's current view; select(spanId) records the user's choice or throws TraceError.
 */
export async function startPageServer({ getView, select, log = () => {} }) {
    const clients = new Set();

    async function route(req, res) {
        const { pathname } = new URL(req.url ?? "/", "http://127.0.0.1");
        const asset = ASSETS.get(pathname);
        if (asset) {
            requireMethod(req, "GET", "HEAD");
            const body = await readFile(path.join(PAGE_DIR, asset.file));
            const headers = asset.file === "index.html" ? { "Content-Security-Policy": PAGE_POLICY } : {};
            return send(res, 200, asset.type, req.method === "HEAD" ? undefined : body, headers);
        }
        if (pathname === "/favicon.ico") {
            res.writeHead(204, BASE_HEADERS);
            return res.end();
        }
        if (pathname === "/state") {
            requireMethod(req, "GET");
            return sendJson(res, 200, getView());
        }
        if (pathname === "/events") {
            requireMethod(req, "GET");
            res.writeHead(200, { ...BASE_HEADERS, "Content-Type": "text/event-stream; charset=utf-8", Connection: "keep-alive" });
            res.write(`retry: 3000\n\nevent: state\ndata: ${JSON.stringify(getView())}\n\n`);
            clients.add(res);
            req.on("close", () => clients.delete(res));
            return;
        }
        if (pathname === "/select") {
            requireMethod(req, "POST");
            const body = await readJson(req);
            if (body === null || typeof body !== "object" || typeof body.spanId !== "string" || body.spanId.length > 64) {
                throw new HttpError(400, 'Send {"spanId": "<span ID>"}.');
            }
            select(body.spanId);
            return sendJson(res, 200, { ok: true });
        }
        throw new HttpError(404, "Not found.");
    }

    const server = createServer((req, res) => {
        route(req, res).catch((err) => {
            if (res.headersSent) return res.end();
            if (err instanceof HttpError) return sendJson(res, err.status, { error: err.message });
            if (err instanceof TraceError) return sendJson(res, 400, { error: err.message, code: err.code });
            log(`The trace canvas page server failed on ${req.method} ${req.url}: ${err?.message ?? err}`);
            sendJson(res, 500, { error: "The canvas server hit an unexpected error." });
        });
    });

    await new Promise((resolve, reject) => {
        server.once("error", reject);
        server.listen(0, "127.0.0.1", () => {
            server.off("error", reject);
            resolve();
        });
    });
    server.on("error", (err) => log(`The trace canvas page server reported an error: ${err.message}`));

    // Comments keep idle event streams open through proxies.
    const heartbeat = setInterval(() => {
        for (const client of clients) client.write(": keep-alive\n\n");
    }, HEARTBEAT_MS);
    heartbeat.unref();

    return {
        url: `http://127.0.0.1:${server.address().port}/`,

        /** Sends an event to every open page and returns how many received it. */
        broadcast(event, data) {
            const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
            let delivered = 0;
            for (const client of clients) {
                if (client.destroyed || client.writableEnded) {
                    clients.delete(client);
                    continue;
                }
                client.write(payload);
                delivered += 1;
            }
            return delivered;
        },

        async close() {
            clearInterval(heartbeat);
            for (const client of clients) client.end();
            clients.clear();
            await new Promise((resolve) => {
                server.close(() => resolve());
                server.closeAllConnections();
            });
        },
    };
}
