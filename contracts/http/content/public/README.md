# Content browser public contract

API-C01–03 are available only on the verified public edge listener and preserve
the `/api/v1` prefix. `GET /catalog` and `GET /topics/{topicId}` are anonymous.
`GET /topics/{topicId}/units/{unitId}` additionally requires the exact
`__Host-backend-lab-session` cookie and one canonical `X-Session-Context` UUID;
Content obtains a fresh Identity admission and checks its durable local fence
before reading the unit.

All success responses carry decimal-string `canonicalRevision` and
`appliedRevision`, UTC-millisecond `asOf`, and `learningSyncPending`. Catalog and
outline responses are revision-bound with an ETag. Unit responses are always
`Cache-Control: private, no-store`, `X-Content-Type-Options: nosniff`, and never
emit `Set-Cookie`.

DTOs are allowlists. Catalog and outline contain only roadmap, TOP/CAP,
prerequisite, placement, publication status, and unit outline metadata. Unit
body contains safe typed blocks. Expected values, cases, rubric, hints,
solutions, credentials, admission data, and principals are not fields in these
schemas. Planned topics have explicit `PLANNED` status and no synthetic body.
Aliases resolve only when the published snapshot maps exactly one alias.
Each catalog roadmap embeds its owned TOP summaries with stable TOP/CAP IDs,
title/label, and publication status; `placementTopicIds` must be a subset of that
roadmap's `topicIds`.

Unknown query, encoded/ambiguous path, duplicate privileged header/cookie,
non-GET method, request content type/body, unsupported snapshot schema, missing
or corrupt canonical snapshot, and canonical/readiness mismatch fail closed.
Errors use the common bounded envelope and do not reveal private-resource
existence.
