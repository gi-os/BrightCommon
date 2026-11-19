import { test, beforeEach } from "node:test";
import assert from "node:assert/strict";
import worker, { parse } from "../worker.js";

const env = { REPORT_PAT: "server-side-key", REPORT_REPO: "gi-os/light-reports" };
let calls;

function reply(status) {
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    return new Response("{}", { status });
  };
}

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
  reply(201);
});

test("files the issue with the server-side key", async () => {
  const res = await worker.fetch(post(good), env);
  assert.equal(res.status, 201);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, "https://api.github.com/repos/gi-os/light-reports/issues");
  assert.equal(calls[0].init.headers.Authorization, "Bearer server-side-key");
  assert.deepEqual(JSON.parse(calls[0].init.body), good);
});

test("only title, body and labels are forwarded", async () => {
  await worker.fetch(post({ ...good, assignees: ["someone"], repo: "gi-os/other" }), env);
  assert.deepEqual(Object.keys(JSON.parse(calls[0].init.body)).sort(), ["body", "labels", "title"]);
  assert.match(calls[0].url, /gi-os\/light-reports/);
});

test("a malformed report is a 400 and never reaches GitHub", async () => {
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

test("wrong path and method are refused", async () => {
  assert.equal((await worker.fetch(new Request("https://relay.example/"), env)).status, 404);
  assert.equal((await worker.fetch(new Request("https://relay.example/report"), env)).status, 405);
});

test("an unconfigured relay keeps reports queued", async () => {
  const res = await worker.fetch(post(good), {});
  assert.equal(res.status, 503);
});

test("a bad relay key is a 502, so the phone keeps the report", async () => {
  reply(401);
  assert.equal((await worker.fetch(post(good), env)).status, 502);
  reply(404);
  assert.equal((await worker.fetch(post(good), env)).status, 502);
});

test("GitHub's own refusals map to what the phone queue expects", async () => {
  reply(422);
  assert.equal((await worker.fetch(post(good), env)).status, 422);
  reply(403);
  assert.equal((await worker.fetch(post(good), env)).status, 429);
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
