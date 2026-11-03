# R08 owned allocation lab

Run `scripts/services.ps1 -Task db -Service learning -EvidenceTask R08 -Offline` for actual own-PG
migration/role/rollback and allocation/filesystem-journal core. `-Task contract` runs the separate
three-packaged-service canonical admission/publication/allocation layer; `-Task fault -Case DATA-22`
runs the currently named allocation runtime subset. Required names live in r08-required-cases.json
and r08_report.py. Unknown/missing selectors/suites, zero cases, failure/error/skip are nonzero.

Each managed run creates its own PG cluster and PKI with ownership markers, checks full affected
input hashes/inventory before/after, retains JAR/XML/log evidence under artifacts/quality/R08/runId,
and closes owned listeners. The existing process-local JVM IPC directory workaround is inherited.
Do not pass production DB/trust paths. Lab filesystem evidence does not qualify an off-host provider.

Runtime config is an exact properties file adjacent to validated trust config: schemaVersion1,
enabled=true, matching realm/generation/dbRunId and journalDirectory equal to that owned directory's
allocation-journal child. Startup verifies existing own DB state and never migrates/provisions it.

R08 has no reset/delete/submit/grading/browser journey implementation. Lifecycle/epoch mutations in
controlled tests are operator fixtures and must not be described as production lifecycle completion.
