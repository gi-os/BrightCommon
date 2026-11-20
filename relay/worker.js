// The phone posts here without credentials; the Worker only writes to a queue.
// A private tracker workflow reads the queue and files issues.
//
//   POST /report   {"title": "...", "body": "...", "labels": ["notebook", "crash", "field"]}
//
// Status codes are chosen for the queue on the phone, which deletes a report on 400, 413 or 422
// (it can never post) and keeps it for a later launch on anything else.

// Cap the incoming JSON separately from the normalized queue message.
const MAX_REQUEST_BYTES = 400_000;
// Leave room below Cloudflare's 128 KB message limit for internal metadata.
const MAX_MESSAGE_BYTES = 125_000;
const MAX_TITLE = 256;
// GitHub refuses an issue body over 65,536 characters; light-common trims to 60,000 first.
const MAX_BODY = 65_536;
const MAX_LABELS = 6;
const LABEL = /^[a-z0-9][a-z0-9._-]{0,39}$/;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname !== "/report") return text(404, "not found");
    if (request.method !== "POST") return text(405, "POST only", { Allow: "POST" });

    if (!env.REPORTS || !env.LIMITER) return text(503, "relay not configured");

    const key = request.headers.get("CF-Connecting-IP") || "unknown";
    const { success } = await env.LIMITER.limit({ key });
    if (!success) return text(429, "slow down");

    const declared = Number(request.headers.get("Content-Length") || "0");
    if (declared > MAX_REQUEST_BYTES) return text(413, "too large");
    const raw = await readCapped(request, MAX_REQUEST_BYTES);
    if (raw === null) return text(413, "too large");

    const report = parse(raw);
    if (typeof report === "string") return text(400, report);

    const message = { ...report, received_at: new Date().toISOString() };
    if (new TextEncoder().encode(JSON.stringify(message)).byteLength > MAX_MESSAGE_BYTES) {
      return text(413, "too large");
    }
    try {
      await env.REPORTS.send(message, { contentType: "json" });
    } catch {
      return text(502, "queue unavailable");
    }

    return text(201, "queued");
  },
};

/** The validated report, or a string saying why it was rejected. */
export function parse(raw) {
  let o;
  try {
    o = JSON.parse(raw);
  } catch {
    return "not json";
  }
  if (!o || typeof o !== "object" || Array.isArray(o)) return "not an object";
  const { title, body, labels = [] } = o;
  if (typeof title !== "string" || !title.trim()) return "title missing";
  if (title.length > MAX_TITLE) return "title too long";
  if (typeof body !== "string") return "body missing";
  if (body.length > MAX_BODY) return "body too long";
  if (!Array.isArray(labels) || labels.length > MAX_LABELS) return "bad labels";
  if (!labels.every((l) => typeof l === "string" && LABEL.test(l))) return "bad labels";
  // Only these three fields go on. Anything else a caller sends (assignees, milestone, a
  // different repo) is dropped rather than forwarded.
  return { title, body, labels };
}

/** The body as text, or null once it passes `cap` bytes; never buffers more than that. */
async function readCapped(request, cap) {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > cap) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const all = new Uint8Array(size);
  let at = 0;
  for (const c of chunks) {
    all.set(c, at);
    at += c.byteLength;
  }
  return new TextDecoder().decode(all);
}

function text(status, message, headers = {}) {
  return new Response(message, {
    status,
    headers: { "Content-Type": "text/plain; charset=utf-8", ...headers },
  });
}
