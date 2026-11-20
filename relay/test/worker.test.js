import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import worker, { parse } from "../worker.js";

let calls;
const env = {
  REPORTS: { send: async (message, options) => calls.push({ message, options }) },
  LIMITER: { limit: async () => ({ success: true }) },
};

function post(body, extra = {}) {
  return new Request("https://relay.example/report", {
    method: "POST",
    body: typeof body === "string" ? body : JSON.stringify(body),
    headers: { "CF-Connecting-IP": "203.0.113.9" },
    ...extra,
  });
}

const good = { title: "Notebook v1.62.0 — froze", body: "### What happened", labels: ["notebook", "freeze", "field"] };

beforeEach(() => {
  calls = [];
});

test("accepts the report only after enqueueing it", async () => {
  const res = await worker.fetch(post(good), env);
  assert.equal(res.status, 201);
  assert.equal(await res.text(), "queued");
  assert.equal(calls.length, 1);
  const { received_at, ...report } = calls[0].message;
  assert.equal(new Date(received_at).toISOString(), received_at);
  assert.deepEqual(report, good);
  assert.deepEqual(calls[0].options, { contentType: "json" });
});

test("only validated fields and the relay timestamp are queued", async () => {
  await worker.fetch(post({ ...good, assignees: ["someone"], repo: "gi-os/other", received_at: "fake" }), env);
  assert.deepEqual(Object.keys(calls[0].message).sort(), ["body", "labels", "received_at", "title"]);
  assert.notEqual(calls[0].message.received_at, "fake");
});

test("a malformed report is a 400 and never reaches the queue", async () => {
  for (const bad of ["nope", { body: "x" }, { title: "t", body: 3 }, { title: "t", body: "b", labels: ["Has Space"] }]) {
    const res = await worker.fetch(post(bad), env);
    assert.equal(res.status, 400, JSON.stringify(bad));
  }
  assert.equal(calls.length, 0);
});

test("an oversized request is a 413", async () => {
  const res = await worker.fetch(post({ ...good, body: "x".repeat(500_000) }), env);
  assert.equal(res.status, 413);
  assert.equal(calls.length, 0);
});

test("a streamed body with no Content-Length is still capped", async () => {
  const chunk = new Uint8Array(64 * 1024).fill(120);
  let sent = 0;
  const body = new ReadableStream({
    pull(c) {
      if (sent++ > 20) c.close();
      else c.enqueue(chunk);
    },
  });
  const req = new Request("https://relay.example/report", { method: "POST", body, duplex: "half" });
  assert.equal(req.headers.get("Content-Length"), null);
  assert.equal((await worker.fetch(req, env)).status, 413);
  assert.ok(sent < 10, `read ${sent} chunks`);
});

test("a full-size multi-byte report fits", async () => {
  const res = await worker.fetch(post({ ...good, body: "é".repeat(60_000) }), env);
  assert.equal(res.status, 201);
});

test("a report that fits the character cap but exceeds the queue byte cap is a 413", async () => {
  for (const body of ["界".repeat(60_000), "\u0000".repeat(22_000)]) {
    const res = await worker.fetch(post({ ...good, body }), env);
    assert.equal(res.status, 413);
  }
  assert.equal(calls.length, 0);
});

test("wrong path and method are refused", async () => {
  assert.equal((await worker.fetch(new Request("https://relay.example/"), env)).status, 404);
  assert.equal((await worker.fetch(new Request("https://relay.example/report"), env)).status, 405);
});

test("an unconfigured relay keeps reports queued", async () => {
  for (const bindings of [{ REPORTS: env.REPORTS }, { LIMITER: env.LIMITER }]) {
    const res = await worker.fetch(post(good), bindings);
    assert.equal(res.status, 503);
  }
  assert.equal(calls.length, 0);
});

test("enqueue failure is a 502, so the phone keeps the report", async () => {
  const unavailable = { ...env, REPORTS: { send: async () => { throw new Error("unavailable"); } } };
  assert.equal((await worker.fetch(post(good), unavailable)).status, 502);
});

test("the rate limiter is keyed on the caller's IP", async () => {
  const keys = [];
  const limited = { ...env, LIMITER: { limit: async ({ key }) => (keys.push(key), { success: false }) } };
  const res = await worker.fetch(post(good), limited);
  assert.equal(res.status, 429);
  assert.deepEqual(keys, ["203.0.113.9"]);
  assert.equal(calls.length, 0);
});

test("parse accepts a report with no labels", () => {
  assert.deepEqual(parse(JSON.stringify({ title: "t", body: "" })), { title: "t", body: "", labels: [] });
});
