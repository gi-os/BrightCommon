# light-report-relay

The Cloudflare Worker every app's shake-to-report posts to. Apps send `{title, body, labels}`
with no credential. The Worker enqueues reports without any GitHub credential.
`201 queued` means Cloudflare accepted the report, not that an issue has been filed yet.

Set up once (nothing in CI deploys it):

1. Create the queue with `npx wrangler queues create light-reports`. Enable its HTTP pull
   consumer with `npx wrangler queues consumer http add light-reports`.
2. Configure the ingestion workflow in `gi-os/light-reports` with its queue credentials.
3. From `relay/`, run `npx wrangler deploy`. The config binds `REPORTS` to `light-reports` and
   `LIMITER` to the per-IP rate limiter. Remove any old `REPORT_PAT` secret from an existing deployment.

BrightCommon defaults to `https://light-report-relay.glupo.workers.dev/report`. For a different
deployment, set the optional `REPORT_RELAY_URL` repository Actions variable before publishing.

`POST /report` allows up to 400,000 request bytes and 125,000 serialized queue-message bytes
(including the relay timestamp). Either size limit returns `413`, which the phone does not retry.

`npm test` runs the tests with plain Node; no install needed.
