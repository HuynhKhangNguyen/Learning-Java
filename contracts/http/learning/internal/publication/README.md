# INT-04 Learning publication participant v1

`phase-v1.schema.json`, `receipt-v1.schema.json`, and `status-v1.schema.json`
are the executable request, phase receipt, and status contracts. Status is
`GET /internal/v1/publication-operations/{operationId}`. The phase resource is
`PUT /internal/v1/publication-operations/{operationId}/phases/{phase}`. Phases are `STAGE`, `FENCE`,
`RELEASE`, and `ABORT`. Every command binds the exact operation, parent/target
revision, bundle and manifest digests, realm, Content generation, Learning
generation, delivery sequence, and contract version.

STAGE pulls both INT-03 resources and validates exact bytes before opening the
database transaction. Exact retries return the stored receipt; the same
operation/phase with different bytes or security context is a conflict.
Status returns the latest durable phase binding, including bundle ID, digests,
realm, both generations, delivery sequence, and command digest. Status and
receipts never contain bundle bytes or private grading input. All responses are
`Cache-Control: no-store`.

`examples/` is an executable positive/negative corpus. The Learning unit suite
runs the schemas with the repository's locked AJV and compares phase verdicts
with the strict Java decoder; unknown or private fields are rejected.

