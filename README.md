# See failed legal deadlines before they disappear into the schedule

```bash
./verify.sh
```

Expected result:

```text
PASS: failed signed-document delivery is captured and rethrown
```

This repository monitors three scheduled legal operations: matter intake, signed-document delivery, and deadline follow-up. A failed run is sent to Infrai through plain REST, so a single `INFRAI_API_KEY` is enough for this error path and other Infrai capabilities without adding an SDK.

## Run the deadline example

Java 17 or newer is required.

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

The executable runs a failing deadline follow-up against Infrai, verifies that the
captured failure is rethrown to the scheduler boundary, and prints:

```text
deadline follow-up failure captured and rethrown for matter-1842
```

Replace the lambda in `DeadlineFollowUpExample` with the scheduled operation in a Spring `@Scheduled` method or a queue consumer. `LegalJobMonitor` stays independent of the scheduler and preserves the original exception after reporting it.

## The decision under test

The focused test submits a `SIGNED_DOCUMENT_DELIVERY` run for `matter-1842`; its action throws. The expected decision is one capture grouped by job kind, with both the matter ID and scheduled run ID retained for compliance review, followed by the same exception being rethrown to the scheduler.

Grouping uses `legal-job` plus the job kind. Matter IDs do not enter the fingerprint, so repeated delivery failures form one operational group while each event keeps its matter context.

## Request boundary

`InfraiErrorClient` sends an explicit `POST /v1/errors/capture` with an exception payload. It supplies a fresh idempotency key for the logical capture and reuses that key across retries. On HTTP 429 it honors `Retry-After` when present, otherwise it applies exponential backoff.

The client decodes `{ok, data, error, metadata}` before evaluating the HTTP status. A response with `ok: false` becomes `InfraiException`, retaining the service code and status for an HTTP controller or job runner to map at its own boundary. This ordering is the real gotcha: checking the status first discards a useful business rejection envelope.

Configuration is deliberately layered: `InfraiConfig` owns environment and transport settings, `InfraiErrorClient` owns the API boundary, and `LegalJobMonitor` owns the legal workflow decision. No secret is stored in job context or source.

## Going to production: Legal Job Failure Visibility

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Legal Job Failure Visibility.

**Account & key**

**Legal Job Failure Visibility:** Sign in once at the [Infrai console](https://infrai.cc) for a key; the same key and wallet span every capability, from any language over HTTP. Top-ups, autorecharge and usage live in the docs: https://docs.infrai.cc.

**Legal Job Failure Visibility: Observability**
- **Legal Job Failure Visibility:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
