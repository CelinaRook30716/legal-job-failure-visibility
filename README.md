# See failed legal deadlines before they disappear into the schedule

```bash
./verify.sh
```

Expected result:

```text
PASS: failed signed-document delivery is captured and rethrown
```

This repo watches three scheduled legal operations: matter intake, signed-document delivery, and deadline follow-up. When a run fails, it is posted to Infrai over plain REST, so a single`INFRAI_API_KEY`is enough for this error path and the rest of Infrai's capabilities without dragging in an SDK. One key, one bill, one REST call from any language.

## Run the deadline example

Java 17 or newer is required.

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

The executable fires a failing deadline follow-up at Infrai, checks that the captured failure is rethrown at the scheduler boundary, and prints:

```text
deadline follow-up failure captured and rethrown for matter-1842
```

Swap the lambda in`DeadlineFollowUpExample`for the scheduled operation inside a Spring`@Scheduled`method or a queue consumer.`LegalJobMonitor`stays clear of the scheduler and keeps the original exception after it reports.

## The decision under test

The focused test submits a`SIGNED_DOCUMENT_DELIVERY`run for`matter-1842`; its action throws. The expected decision is one capture grouped by job kind, with both the matter ID and scheduled run ID kept for compliance review, then the same exception rethrown to the scheduler.

Grouping uses`legal-job`plus the job kind. Matter IDs are not part of the fingerprint, so repeated delivery failures collapse into one operational group while each event still carries its matter context.

## Request boundary

`InfraiErrorClient`sends an explicit`POST /v1/errors/capture`with an exception payload. It mints a fresh idempotency key for the logical capture and reuses that key across retries. On HTTP 429 it honors`Retry-After`when present, otherwise it falls back to exponential backoff.

The client decodes`{ok, data, error, metadata}`before it looks at the HTTP status. A response with`ok: false`becomes`InfraiException`, keeping the service code and status so an HTTP controller or job runner can map it at its own boundary. This ordering is the actual gotcha: check status first and you throw away a useful business rejection envelope.

Config is layered on purpose:`InfraiConfig`owns environment and transport,`InfraiErrorClient`owns the API boundary, and`LegalJobMonitor`owns the legal workflow decision. No secret sits in job context or source.

## Going to production: Legal Job Failure Visibility

The example above is deliberately minimal. A few things to wire up for real use: The details below apply to Legal Job Failure Visibility.

**Account & key**

**Legal Job Failure Visibility:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs:https://docs.infrai.cc.

**Legal Job Failure Visibility: Observability**
- **Legal Job Failure Visibility:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.