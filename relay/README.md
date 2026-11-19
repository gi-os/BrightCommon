# light-report-relay

The Cloudflare Worker every app's shake-to-report posts to. It holds the only key that can file
issues in `gi-os/light-reports`; apps send `{title, body, labels}` with no credential.

Set up once (nothing in CI deploys it):

1. Create a fine-grained PAT: repository access `gi-os/light-reports` only, permission
   Issues: read and write, nothing else.
2. `cd relay && npx wrangler secret put REPORT_PAT` and paste it, then `npx wrangler deploy`.
3. Set the repo variable `REPORT_RELAY_URL` to `https://<worker host>/report` here and in each
   app that has its own report code, so the next build points at it.

`npm test` runs the tests with plain Node; no install needed.
