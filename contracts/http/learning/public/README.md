# Learning allocation API-L01/L02 — R08

Explicit canonical owned-lab composition uses `backendlab.allocation.config`; this document describes
implemented ports and does not claim production journal qualification or browser journey evidence.

- `POST /api/v1/attempts`: exact [create schema](allocation-create-v1.schema.json), maximum8192 bytes.
  Cookie `__Host-backend-lab-session`, `X-Session-Context` canonical UUID, `Idempotency-Key` canonical
  UUID, `X-CSRF-TOKEN` and configured exact `Origin` are required. Identity binds exact validated body.
- `GET /api/v1/attempts/{attemptId}`: authenticated Cookie/context; empty body/query. No mutation fields
  are sent in INT-01. Re-admission happens before final snapshot after journal I/O.
- Responses use the exact [allowlist schema](allocation-response-v1.schema.json),200 ready or202
  preparing, JSON and `Cache-Control: no-store`, no auth Set-Cookie. Error envelope follows common
  wire. Bigint epoch/revision values are decimal strings. Preparing contains no prompt/input/schema.
- Same owner/key/business payload converges; changed payload409 `KEY_REUSED`. Different keys resume
  the current assignment in the same guard scope. Auth/owner/epoch remain mandatory on every retry.
- v2 replicas remain historical publication inputs but cannot allocate. V3 pool consists of released
  immutable replicas; current head manifest explicitly denies withdrawn old rubric versions. An absent
  old-version row is accepted only when current active v3 proves the same rubricVersion and target.
- No submission/grading/save-draft/support endpoint is implemented here. `requireReadyForSubmission`
  is a local R11 consume guard, not a T1 implementation. No receipt lookup route is enabled.
