// Shake-to-report relay: the one place the light-reports key lives.
//
// The apps used to post straight to the GitHub issues API with a PAT compiled into
// BuildConfig. A sideloaded APK is a zip anyone can open, so that key was public, and an
// Issues key on light-reports can read every report as well as write one: phone numbers,
// install ids, screenshots. Now the phone posts here with no credential at all and this
// worker files the issue with REPORT_PAT, a Cloudflare secret that never leaves Cloudflare.
//
// The worst anyone can do with the URL is what they could do with a report sheet: file an
// issue into one private tracker, at the rate limit below. Nothing can be read back through it.
//
//   POST /report   {"title": "...", "body": "...", "labels": ["notebook", "crash", "field"]}
//
// Status codes are chosen for the queue on the phone, which deletes a report on 400, 413 or 422
// (it can never post) and keeps it for a later launch on anything else.

// Room for a full 65,536-character body even when every character is multi-byte and
// JSON-escaped; the character limit below is the one that matters.
const MAX_REQUEST_BYTES = 400_000;
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

    if (!env.REPORT_PAT || !env.REPORT_REPO) return text(503, "relay not configured");

    if (env.LIMITER) {
      const key = request.headers.get("CF-Connecting-IP") || "unknown";
      const { success } = await env.LIMITER.limit({ key });
      if (!success) return text(429, "slow down");
    }

    const declared = Number(request.headers.get("Content-Length") || "0");
    if (declared > MAX_REQUEST_BYTES) return text(413, "too large");
    const raw = await readCapped(request, MAX_REQUEST_BYTES);
    if (raw === null) return text(413, "too large");

    const report = parse(raw);
    if (typeof report === "string") return text(400, report);

    let upstream;
    try {
      upstream = await fetch(`https://api.github.com/repos/${env.REPORT_REPO}/issues`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${env.REPORT_PAT}`,
          Accept: "application/vnd.github+json",
          "Content-Type": "application/json; charset=utf-8",
          "User-Agent": "light-report-relay",
          "X-GitHub-Api-Version": "2022-11-28",
        },
        body: JSON.stringify(report),
      });
    } catch {
      return text(502, "github unreachable");
    }

    if (upstream.ok) return text(201, "filed");
    // A body GitHub will never take: the phone should drop it, not retry it forever.
    if (upstream.status === 422) return text(422, "rejected by github");
    if (upstream.status === 403 || upstream.status === 429) return text(429, "github rate limit");
    // 401 or 404 mean the relay's own key or repo is wrong. That is fixed here, not on the
    // phone, so the report must stay queued: never pass a 4xx through for it.
    return text(502, `github ${upstream.status}`);
  },
};

/** The report as GitHub wants it, or a string saying why it is not one. */
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
