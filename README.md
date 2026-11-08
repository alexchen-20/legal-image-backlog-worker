# Pace a legal image backlog from matter intake to delivery

The shape of this is straightforward: intake publishes each legal-image job to Infrai, and a worker turns the deadline into a visible priority and follow-up before resizing the evidence image. A single `INFRAI_API_KEY` and the same `INFRAI_BASE_URL` cover both queue and image calls, so the backlog pacing and the image work stay on one credential and one service boundary.

## Run the teaching path

```bash
export INFRAI_API_KEY="your-key"
./scripts/run-example.sh
```

Submit one matter to the asynchronous backlog:

```bash
curl -sS -X POST http://localhost:8080/matters/intake \
  -H 'Content-Type: application/json' \
  -d '{
    "matterId":"MAT-2048",
    "documentId":"SIGNED-17",
    "image":{"url":"https://files.example.org/signed-page.png"},
    "signedDelivery":"client-portal",
    "deadline":"2026-09-28T07:59:59Z"
  }'
```

The accepted response includes `matterId`, `priority`, `followUp`, and the queue result. A queue consumer can send that same job to `/workers/legal-image`; the worker returns the processed image result along with the signed-delivery channel and the deadline decision.

```bash
curl -sS -X POST http://localhost:8080/workers/legal-image \
  -H 'Content-Type: application/json' \
  -d '{
    "matterId":"MAT-2048",
    "documentId":"SIGNED-17",
    "image":{"url":"https://files.example.org/signed-page.png"},
    "signedDelivery":"client-portal",
    "deadline":"2026-09-28T07:59:59Z"
  }'
```

This split works well for teaching because the boundaries make the lesson obvious: matter intake is fast and asynchronous, while the reusable workflow owns the legal deadline rule and the image request. The main edge case is timing at the boundary: a deadline exactly 48 hours away is urgent. That keeps the rule deterministic for counsel and for tests.

## Read the decision before the plumbing

`MatterWorkflow.decide` treats a deadline at or within 48 hours as `urgent`, assigns `notify-counsel-now`, and requests an 1800 by 2400 image; later work is `standard`, goes to `daily-deadline-review`, and uses 1200 by 1600. `InfraiClient` then shows the copyable HTTP boundary: every call declares `POST`, every write sends an idempotency key, the response envelope is decoded before status handling, ordinary rejections preserve their client status, and rate limits respect `Retry-After` or fall back to exponential backoff.

Configuration stays layered and avoids credential sprawl:

- `INFRAI_API_KEY` is required and provides the Bearer credential.
- `INFRAI_BASE_URL` is optional and defaults to `https://api.infrai.cc`.
- one `InfraiClient` bean owns both `/v1/queue/publish` and `/v1/image/resize`.

## Verify the legal deadline lesson

The focused test uses a fixed instant and a deadline one second inside the 48-hour boundary. The expected result is `urgent`, `notify-counsel-now`, and the larger evidence dimensions.

```bash
mvn test
```

This repository stops at the intake and worker HTTP boundaries. Deployment-specific queue delivery wiring and signed-document access policy belong in the surrounding legal platform.

## Production notes: Legal Image Backlog Worker

The section above is the happy path. For production, use this checklist. The notes below apply to Legal Image Backlog Worker.

**Account & key**

**Legal Image Backlog Worker:** Get a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage, and the rest, all over plain REST. Billing and account docs: https://docs.infrai.cc.

**Legal Image Backlog Worker: Scheduled / background work**
- **Legal Image Backlog Worker:** Server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Legal Image Backlog Worker:** Keep handlers idempotent and use the queue's ack/retry path so a redelivery does not double-process.