# BL-R06a — Content candidate/version store và private INT-03

[Registry](../../../delivery-backlog.md) · [F08](../README.md) · [Publication protocol](../publication/protocol.md) · [Data protocols](../../../architecture/data-protocols.md) · [DoD](../../../delivery/contract.md)

## Goal, trạng thái và quyền

**Goal:** Content có thể validate, lưu bất biến và phục vụ lại đúng bytes của một private publication bundle qua INT-03 trên PostgreSQL và trust harness thật. Candidate sai, retry xung đột hoặc lỗi transaction phải fail atomic và giữ nguyên canonical good head hiện có. Kết quả được chứng minh ở lớp Content-owned của DATA-01 bằng CMD-03/CMD-04 với FX-05 lab fixtures; task này không activate canonical revision.

Đầu ra chính là candidate/version store thuộc `content-service/publication`, contract + fixture cho private bundle, maintenance CLI chuẩn bị candidate và packaged private GET `/internal/v1/content-bundles/{bundleId}`. R1/Content/MD01, REQ-10 và REQ-13; không dùng schema hoặc metadata review để tuyên bố nội dung đã được review ngữ nghĩa hay published.

Ngày 16/10/2026 người dùng đã cấp quyền **CODE** cho đúng leaf BL-R06a qua chat implementation riêng. Dependency và brief đã đạt Ready; executor triển khai theo Mode B manual chat coordination. Candidate ngày 18/10 bị review trả về `IN_PROGRESS` ngày 20/10; remediation, evidence mới và acceptance độc lập ngày 21/10 đã đưa riêng BL-R06a tới `DONE`. Quyền này không mở BL-R06b, BL-R06c hoặc task kế tiếp.

## Dependency và bằng chứng đầu vào

| Dependency | Trạng thái và phần được tái sử dụng | Không được suy rộng |
|---|---|---|
| BL-R02a | DONE; actual PostgreSQL 18.6, own DB/roles, forward migration, transaction/failure/rerun/two-connection harness tại [task](../../../platform/database/tasks/BL-R02a.md) và [verification](../../../platform/database/verification.md) | Chưa có publication schema hoặc DATA-01 business PASS |
| BL-R05 | DONE; schema2/catalog, stable/shared IDs, reference/selectors/DAG và public/private mapping tại [task](BL-R05.md) | Catalog planned không phải bundle published, semantic review hoặc runtime store |
| BL-R02b | DONE; packaged Content artifact, private listener/mTLS/caller-action boundary và common wire foundation tại [task](../../../platform/service-trust/tasks/BL-R02b.md) và [verification](../../../platform/service-trust/verification.md) | Probe `BUNDLE_TRANSPORT` không phải quyền INT-03 hoặc bundle business implementation |

R02c không phải dependency của leaf này: outbox/phase delivery thuộc R06c. R06a phải giữ tương thích với foundation hiện có nhưng không tạo STAGE/outbox chỉ vì delivery foundation đã sẵn sàng.

## Execution role, mode và write scope

### Lượt chuẩn bị brief hiện tại

- Role: Control Center / Integrator; Product/Architecture và repo audit chỉ read-only.
- Mode: native subagent cho audit độc lập, một integrator duy nhất ghi file.
- Write scope: `docs/features/f08-content/tasks/BL-R06a.md`, link R06a trong `docs/delivery-backlog.md` và `docs/features/f08-content/README.md`.
- Validation: link/ID/status/scope/traceability và Markdown; không chạy application suite cho thay đổi docs-only.

### Khi có chỉ dẫn CODE

Role mặc định là Backend / Platform executor, main session giữ integration và acceptance. Mode mặc định là single executor vì migration, Java store/runtime, helper và evidence phụ thuộc cùng candidate. Native subagent chỉ audit/review read-only hoặc nhận write scope tách biệt được integrator ghi rõ; một file chỉ có một writer.

Write scope implementation được phép:

- `backend/services/content-service/src/main/java/lab/backend/content/publication/**`;
- `backend/services/content-service/src/test/java/lab/backend/content/publication/**`;
- forward migration `backend/services/content-service/src/main/resources/db/migration/V4__publication_candidate_store.sql` và publication config cần thiết; không sửa V1–V3;
- các điểm wiring Content tối thiểu trong `ContentApplication`, `configuration/`, `application*.properties`, `pom.xml` và `backend/services/content-service/README.md`; không đổi framework/version hoặc thêm dependency khi JDK/Spring/Jackson hiện có đủ;
- `contracts/http/content/internal/publication/**`, `contracts/fixtures/publication/**` và `ops/content/**` dành riêng contract/FX-05/provision/runbook;
- nhánh R06a trong `scripts/services.ps1`, `scripts/db_lab.py`, report-gate/auditor tương ứng dưới `scripts/tests/**`;
- trường evidence R06a trong `ops/release/dependencies.json`, artifact report ignored và bảng DoD/checkpoint trong file này.

Integrator sở hữu file task, registry, shared helper và manifest tích hợp. Nếu implementation cần sửa ngoài danh sách trên, phải ghi lý do/ảnh hưởng vào checkpoint trước; không âm thầm mở Learning, frontend hoặc task sibling.

## Context cần đọc khi mở implementation

Chỉ đọc các phần sau và code/fixture trực tiếp liên quan:

1. `AGENTS.md`, checkpoint mới nhất trong file này, [quality process](../../../quality-process.md) và DONE-01–05 trong [delivery contract](../../../delivery/contract.md).
2. [Publication protocol §1, §2 và §4](../publication/protocol.md), [data protocols invariants/INT-03/DATA-01](../../../architecture/data-protocols.md), [service ownership/INT-03](../../../architecture/service-boundaries.md) và caller matrix trong [security design](../../../architecture/security-design.md).
3. [F08 LLD](../lld.md), R05 contract/snapshot/validator và impact semantics trong [learner data contract §6](../../f11-learner-work/data-contract.md).
4. Content V1–V3 migrations, transport boundary, current POM/profiles, DB/trust fixtures và exact CMD-03/CMD-04 helper/report gates.

Không đọc lại toàn repo hoặc lịch sử dài khi các source trên và checkpoint hiện hành đủ trả lời.

## Invariant và quyết định đã chốt

1. Content sở hữu candidate, immutable authoring versions, canonical head và publication operation metadata; Learning chỉ nhận private verified replica, không sửa nguồn Content.
2. Bundle lưu **exact bytes**; SHA-256 tính trên chính byte sequence đó. GET phải trả cùng bytes, không parse rồi reserialize thành payload “tương đương”. Cùng bundle/version/operation ID nhưng bytes hoặc digest khác là conflict, không overwrite.
3. Candidate metadata gồm tối thiểu `operationId`, `parentRevision`, `targetRevision`, bundle IDs/digests, schema/content/rubric/case versions, prerequisite DAG, coverage/review report, impact theo CAP và old-version manifest.
4. Manifest của từng old version phải có `newAttemptAllowed`, `submitAllowed`, `finalizeAllowed`, `currentEvidencePolicy`, body availability và lý do retain/review/reverify. Shared CAP có đúng một impact classification, không nhân theo roadmap.
5. Malformed rubric/case/private payload phải làm candidate invalid. Một old rubric được **khai báo** invalid/withdrawn bằng manifest hợp lệ có thể là thay đổi hợp lệ, nhưng policy phải giữ history và chặn effect không còn đủ điều kiện; hai trường hợp không được trộn.
6. `good head P` trong R06a là canonical baseline được seed bởi lab fixture và chỉ dùng làm DB oracle. Migration có thể tạo representation mà R06c sẽ dùng, nhưng R06a không có supported code path để advance head. Candidate P+1 hợp lệ cũng chỉ được prepare/store.
7. Không tạo STAGE/FENCE/COMMIT/RELEASE/COMPLETE/ABORT state hoặc outbox. Canonical activation chỉ hợp lệ sau FENCE trong R06c; “activate without fence” luôn bị loại khỏi R06a.
8. INT-03 là private GET do Content cung cấp và chỉ Learning được phép gọi. Certificate là một input xác minh caller, không thay server-side action/audience/environment/resource allowlist. Identity, edge, browser và forged caller headers bị deny.
9. Version đã được history tham chiếu không bị ghi đè hoặc tự GC; withdrawal không xóa bytes lịch sử. GC/retention production không thuộc leaf này.
10. Validator phải fail closed và không mutate input. Structural/schema PASS không thay review kỹ thuật, nguồn, EN–VI semantic equivalence hoặc publish qualification.

Wire encoding chi tiết và enum machine-readable là quyết định cục bộ của implementation, nhưng phải được chốt trước runtime path trong `contracts/http/content/internal/publication/**` và fixture oracle. Candidate/CLI input là strict: unknown/duplicate/privileged fields, bad UTF-8 và unsupported major bị từ chối. INT-03 response phải có version rõ và provider-side fixture cho additive response fields, nhưng full provider-consumer N/N-1/WIRE-06 qualification thuộc R06b/R31c. Contract luôn giữ exact bytes/digest, bounded size, no private log/error; không dùng một header do caller tự khai để cấp quyền.

## Phạm vi và ngoài phạm vi

Trong phạm vi:

- forward Content migration, constraints/triggers/roles/readiness cho candidate/version/bundle/review/impact/manifest và lab good-head oracle;
- strict candidate validator dùng contract R05, reference/DAG/coverage/review/private payload/impact/manifest rules;
- maintenance CLI validate/prepare lab fixture, atomic insert và bounded redacted result;
- FX-05 tối thiểu: P good; P+1 cosmetic; P+1 withdrawn; valid declaration of an invalid old rubric; malformed rubric; checksum/reference/DAG/review/impact/manifest negatives;
- packaged Content INT-03 provider trên private mTLS listener;
- strict R06a CMD-03/CMD-04 branches, required report gates, affected regressions và durable evidence.

Ngoài phạm vi:

- Learning replica, INT-04, STAGE/shared publication gate — R06b;
- phase outbox/coordinator, FENCE, canonical COMMIT, RELEASE, COMPLETE, ABORT và withdrawal activation — R06c;
- public/browser catalog or body API — R07;
- full Collections/content semantic qualification hoặc production publish — R30;
- CMS/admin UI, production authoring, broker hoặc transaction xuyên DB;
- learner attempt/draft/T1/T2/progress/exposure, frontend và current-mastery projection;
- production GC/retention, edge/Compose/SLA, deployment và full SEC-16 consumer proof;
- full provider-consumer N/N-1/WIRE-06 compatibility qualification — R06b/R31c;
- seed production content hoặc tuyên bố R05 planned catalog đã published.

## Acceptance và Definition of Done

| AC | Điều kiện quan sát được | Bằng chứng bắt buộc |
|---|---|---|
| R06a-AC01 — Candidate contract | Contract strict chứa đủ operation/parent/target, component IDs/versions/digests, references/DAG, coverage/review, CAP impact và manifest. Valid P/P+1 fixtures đạt; unknown field/schema, malformed UTF-8/JSON/private grading input, dangling/wrong-type/duplicate reference, self-edge/cycle, coverage/review binding sai bị từ chối | Contract/validator positive và từng negative fixture; input không bị mutate; không suy semantic quality từ schema |
| R06a-AC02 — Impact/manifest | Mỗi affected CAP/version có đúng một policy hợp lệ; shared CAP không nhân đôi. Cosmetic retain, withdrawn và valid declaration of invalid old rubric có manifest phù hợp; missing/unknown/duplicate CAP, thiếu old version/field/reason hoặc policy mâu thuẫn bị từ chối | Independent FX-05 manifest oracle đối chiếu publication protocol và learner data contract |
| R06a-AC03 — Immutable store | Forward V4 trên own Content DB lưu exact bytes + SHA-256 và metadata atomically; fresh/rerun/rollback đúng. Exact retry hội tụ; cùng ID/version/operation khác bytes/digest conflict; two-connection race có một kết quả duy nhất; runtime role không DDL/cross-DB/update/delete immutable version | Actual PostgreSQL CMD-03, constraints/triggers/role tests và failure injection; V1–V3 hashes không đổi |
| R06a-AC04 — Good-head safety và CLI | CLI chỉ validate/prepare lab fixture. Valid candidate chỉ lưu candidate; invalid/checksum mismatch/DB fault/restart/conflicting retry trả nonzero và rollback. Canonical good head P cùng last-good bytes/digest giữ nguyên; không có publication phase/outbox side effect; stdout/log không chứa raw private bundle/expected | DATA-01 before/after DB oracle, packaged CLI positive/negative/restart cases và redaction review |
| R06a-AC05 — INT-03 provider | Packaged Content private listener trả exact stored bytes cùng contract-bound version/manifest/checksum cho Learning đúng caller/action/environment. Unknown/unvalidated bundle hoặc stored digest mismatch fail closed trước payload. Public listener, wrong CA/SAN/EKU/caller/action/audience, Identity/edge/browser/cookie/Origin/forged headers, wrong method/query/ambiguous path đều bị deny | Actual CMD-04 Content JAR + mTLS/trust runtime; response hash bằng stored bytes; no payload in error/log. Đây là provider layer, chưa full SEC-16 |
| R06a-AC06 — Managed evidence | Helper có explicit R06a CMD-03/CMD-04; required named suites/cases >0 và 0 failure/error/skip. Missing config/suite/report, zero tests, unknown task/service/arguments hoặc malformed fixture fail nonzero. R05 catalog cùng affected Content bootstrap/DB/trust/admission/delivery regressions còn đạt theo impact | Strict report-gate tests, exact input/artifact hashes, owned process/listener/cluster cleanup, secret/source/package audit; không dùng schema/mock thay DB/mTLS |
| R06a-AC07 — Review và handoff | Code/comment Việt, contracts/runbook/task/manifest thống nhất với behavior; không P0/P1 trong supported scope; FE/Learning/legacy catalog/dependency/framework/old migrations không đổi ngoài quyết định đã ghi | DONE-01–05, scoped final review, evidence table và checkpoint; không claim toàn cross-service DATA-01/R1 publication DONE |

DoD là R06a-AC01–07 cộng DONE-01–05. DATA-01 ở leaf này chỉ là **Content-owned layer**: invalid candidate không trở thành usable candidate, không partial write và không đổi Content good head. R06b vẫn phải chứng minh invalid pull/stage không tạo Learning staged/fenced state.

## Ma trận validation đích

Hai managed command dưới **chưa hỗ trợ R06a ở thời điểm tạo brief**; việc thêm strict branch/profile/suite/report gate là một phần implementation, không phải evidence đã có:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -EvidenceTask R06a -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R06a -Offline
```

| Gate | AC chính | Expected tối thiểu | Nơi giữ evidence |
|---|---|---|---|
| CMD-03 Content | AC03, AC04, AC06 | PostgreSQL thật; fresh/rerun, constraints/roles, exact retry/conflict, two-connection race, fault rollback và P unchanged; required reports nonzero/no skip | `artifacts/quality/R06a/<runId>/` + bảng DoD hiện hành |
| CMD-04 Content | AC01, AC02, AC05, AC06 | Validator/fixture contract và packaged private mTLS GET; strict caller/path/body/digest/error negatives; exact bytes; owned cleanup | cùng run/report contract hoặc runId riêng được bảng DoD liên kết |
| R05 regression | AC01, AC06, AC07 | Standalone catalog contract vẫn đạt trên source R05 không đổi | report/lệnh thật được checkpoint ghi; không cộng thành HTTP PASS |
| Final review | AC06, AC07 | input/artifact/source/package/secret/private-payload scan, docs links/status và no P0/P1 | durable review JSON hoặc bảng finding có hash/input rõ |

Không chạy lại suite không bị ảnh hưởng chỉ để tăng số test. Nếu command tổng lỗi, giữ report/input của candidate lỗi, tái hiện selector nhỏ nhất rồi mới sửa; không bỏ case hoặc nới expected để làm xanh.

## Plan thực thi sau khi có CODE

1. Freeze inventory Content/R05/DB/trust/helper; chốt executable candidate/bundle/INT-03 contract và FX-05 oracle trước runtime code.
2. Tạo forward migration + least-privilege provision/readiness, immutable store/validator/CLI và actual PG transaction/race/fault tests. Không nối phase/outbox.
3. Nối explicit publication lab profile/private INT-03 provider vào packaged Content JAR; xác minh caller/action/resource và exact stored bytes trên trust harness thật.
4. Thêm explicit R06a CMD-03/CMD-04 helper/report gates và negative command fixtures; chạy targeted cases rồi affected regressions.
5. Freeze candidate; audit source/artifact/input/secret/cleanup, review AC01–07/DONE-01–05, cập nhật evidence/checkpoint. Chỉ đổi registry qua `REVIEW`/`DONE` khi đủ; không tự mở R06b.

## Bảng DoD/evidence hiện hành

| Gate | Trạng thái 21/10/2026 | Evidence / giới hạn |
|---|---|---|
| R06a-AC01 | PASS | Canonical review-subject digest bind toàn bộ semantic metadata nhưng loại report để tránh self-digest; strict report ID/status; private bundle đúng R05; JSON Schema/AJV và Java parity. CMD-04 có đúng 6 named validator cases, 0 failure/error/skip |
| R06a-AC02 | PASS | Manifest dùng key `(CAP, version)`, nhận nhiều old version một CAP; có `REVERIFY` và exact tuple cho bốn classification; missing/extra/duplicate/unknown/contradiction đều có negative |
| R06a-AC03 | PASS | V4 bỏ permanent target uniqueness, dùng active reservation + generation chống stale retry; exact candidate/bundle/manifest retry; publication DB fault, different-operation race/replacement và role negatives chạy trên PostgreSQL thật trong CMD-03 |
| R06a-AC04 | PASS | Packaged CLI validate/prepare, checksum mismatch, DB fault và conflicting retry đều được kiểm; rollback giữ good head và không có publication phase/outbox side effect |
| R06a-AC05 | PASS | Manifest chuyển sang body resource riêng với header bounded; exact bundle/manifest bytes, giới hạn 262144, oversize, unknown/unvalidated/corrupt row và caller/cert/path negatives chạy qua packaged mTLS runtime |
| R06a-AC06 | PASS | Final CMD-03/CMD-04 current-input runs PASS; durable helper 62 cases PASS; R05 36 và catalog helper 19 tests PASS; report gate bắt exact names/counts, hidden failure/skip/duplicate/zero/malformed; cluster/listener đóng sạch |
| R06a-AC07 / DONE-01–05 | PASS | Contract/runbook/comment/checkpoint thống nhất behavior và giới hạn; final read-only review cùng Control Center acceptance ngày 21/10 không còn P0/P1. Registry và release manifest đã chuyển riêng R06a tới `DONE`; R06b/R06c vẫn `PLANNED` |
| Ready gate | PASS | Goal/scope/dependency/input/AC/validation đã chốt; quyền CODE được cấp ngày 16/10/2026 và implementation đã bắt đầu |

## Checkpoint khởi tạo — 16/10/2026

Session goal: soạn brief đầy đủ cho BL-R06a, không implementation.

- Task / trạng thái: BL-R06a · PLANNED; dependencies R02a/R05/R02b DONE theo registry.
- Execution role / mode / write scope: Control Center/Integrator; native subagent audit read-only, main writer duy nhất; chỉ task file + registry/F08 links.
- Đã thay đổi: tạo `docs/features/f08-content/tasks/BL-R06a.md`; registry và F08 index link tới brief. Không sửa application source, fixture, helper hoặc runtime config.
- Acceptance đạt / còn thiếu: brief readiness inputs đã đầy đủ; R06a-AC01–07 chưa bắt đầu và không có runtime PASS.
- Kiểm tra: PowerShell docs-only audit PASS — 3 file strict UTF-8, 97 local links resolve, code fences cân bằng, đúng một registry row `BL-R06a · PLANNED`, 7 AC và 7 gate `NOT STARTED`. Lượt selector đầu trả nonzero vì đếm cả hai bảng thành 14 AC rows; một lượt recheck khác dùng sai positional arguments của `Select-String` và bị loại; final audit dùng `ErrorActionPreference=Stop`. Review độc lập không có P0/P1; hai P2 về contract path và strict-input/N/N-1 boundary đã sửa trong brief. Không chạy application tests vì thay đổi docs-only.
- Quyết định: good head là lab canonical P chỉ đọc/đối chiếu; valid candidate không activate; exact bytes digest; R06a chỉ claim Content-owned DATA-01/provider privacy layer.
- Giới hạn: repo chưa có publication fixture/package/V4/INT-03/helper branch; một audit inventory phụ bị usage limit, integrator đã kiểm trực tiếp các path và helper hiện hành, không coi báo cáo phụ là PASS.
- Bước mở khóa: người dùng cấp chỉ dẫn CODE cho BL-R06a; Control Center ghi quyền, chuyển READY rồi IN_PROGRESS khi bắt đầu implementation. Không mở R06b/R06c.

## Checkpoint implementation 1 — 16/10/2026

Session goal: bắt đầu BL-R06a và dựng candidate/store/provider slice có thể compile, không mở publication coordinator.

- Task / trạng thái: BL-R06a · IN_PROGRESS; quyền CODE được cấp riêng cho leaf này, dependencies giữ nguyên.
- Execution role / mode / write scope: Backend/Platform executor, Mode B manual chat coordination; chỉ Content publication/migration/test, publication contract/fixture, Content README và task/registry. Không sửa Learning/frontend hay sibling.
- Đã thay đổi: V4 candidate/bundle/head oracle với immutable privileges/triggers; `CandidateValidator`, exact-byte digest/store, validate/prepare CLI, publication profile và private INT-03 handler; candidate schema/README và FX-05 cosmetic/private bundle; targeted validator tests; cập nhật số migration expected của DB fixture.
- Acceptance đạt / còn thiếu: AC01–05 mới đạt source/unit slice, chưa đủ required runtime evidence; AC06 chưa bắt đầu vì strict managed branches/report gates chưa được thêm; AC07 chưa final review. Không có STAGE/FENCE/COMMIT/RELEASE/ABORT code path và không có canonical-head mutation API.
- Kiểm tra thật: targeted Maven offline `-Dtest=CandidateValidatorTests test` PASS — 3 tests, 0 failure/error/skip, compile 30 main + 17 test sources. `services.ps1 -Task contract -Service content -EvidenceTask R05 -Offline` PASS — run `61e2db50-daed-4cfb-b395-c1ef409b255a`, 36 tests, scope catalog-only. Một full `services.ps1 -Task verify -Service content -Offline` compile đạt nhưng bị FAIL ở 10 bootstrap HTTP cases do sandbox chặn loopback `SocketException: Permission denied: connect`; lượt này không là regression PASS và đã dừng sau khi nguyên nhân lặp rõ.
- Quyết định cục bộ: coverage/review `candidateDigest` trong v1 fixture bind exact private bundle digest; candidate record có digest riêng trên exact candidate bytes. INT-03 dùng exact body và headers version/revision/digest/manifest; provider rehashes stored bytes trước response.
- Giới hạn/finding: chưa có withdrawn/invalid-old-rubric positive fixture; validator chưa bind mọi component digest vào bundle container; V4 chưa có actual PG qualification/good-head seed; handler chưa actual mTLS/package test; helper/report/manifest evidence chưa triển khai. Vì vậy không chuyển REVIEW.
- Bước tiếp theo chính xác: bổ sung FX-05 matrix + independent oracle, PublicationStoreIT/good-head/race/fault/role checks, packaged CLI/INT-03 mTLS suite; sau đó thêm strict R06a branches trong `services.ps1`/`db_lab.py`, chạy CMD-03/CMD-04 và audit artifact/source/secret/cleanup trước final review.

## Checkpoint implementation hoàn tất — 18/10/2026

Session goal: hoàn tất DoD Content-owned của BL-R06a, tạo durable managed evidence và chuyển candidate sang review; không mở coordinator hoặc Learning replica.

- Task / trạng thái: BL-R06a · REVIEW. Main session giữ vai trò Integrator/writer duy nhất theo Mode B; write scope chỉ gồm Content publication/migration/test, contract/fixture, helper và tài liệu/evidence sở hữu R06a.
- Đã thay đổi: thêm V4 immutable publication bundle/candidate store và good-head oracle; strict `CandidateValidator`; exact-byte transactional `PublicationStore`; validate/prepare packaged CLI; publication runtime/profile và private INT-03 handler; contract/schema, FX-05 fixtures/oracle; actual PostgreSQL, packaged CLI và mTLS integration suites; explicit R06a branches/report gates trong `services.ps1` và `db_lab.py`; Content README và qualified release-manifest pointer.
- Acceptance: R06a-AC01–07 và DONE-01–05 đạt trong phạm vi Content-owned đã ghi ở brief. Không có STAGE/FENCE/COMMIT/RELEASE/COMPLETE/ABORT path, không có delivery outbox và không có API mutate canonical head; R06b/R06c vẫn PLANNED.
- CMD-03 PASS: `pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -EvidenceTask R06a -Offline`; evidence `artifacts/quality/R06a/213dfe80-ab4f-4e65-b71b-a94e8470fc00`; PostgreSQL 18.6; bootstrap 123 + database 78 = 201 tests, 0 failure/error/skip; artifact SHA-256 `f2c1a0aa5f41f2b0e0d8b279c7904c70aafbc8bb5f41136f1935601bdc3adef1`; cluster stopped.
- CMD-04 PASS: `pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R06a -Offline`; evidence `artifacts/quality/R06a/0f6e0ece-9a49-4e79-a69a-1420e8bea80b`; PostgreSQL 18.6; bootstrap 5 + transport 4 = 9 tests, 0 failure/error/skip; packaged artifact SHA-256 `ac66288af76bd720f0d31f5a2df5c807dc346d2b0a8fb4662ea16400c4fce154`; cluster stopped.
- Regression/audit: R05 standalone catalog contract PASS 36 tests tại `artifacts/quality/R05/61e2db50-daed-4cfb-b395-c1ef409b255a`. V1/V2/V3 migration SHA-256 giữ nguyên lần lượt `5940e88a...47aa3`, `e1dfa0fb...50a6`, `c1d69075...e92e7`; source scan không thấy phase/outbox ngoài scope; evidence scan không thấy private expected/secret; strict helper probes cho R06a learning DB, `verify`, và thiếu trust/lab config đều fail nonzero như yêu cầu.
- Giới hạn: evidence chỉ qualify lab fixtures, Content candidate store/CLI và provider INT-03. Nó không qualify semantic review của nội dung, Learning replica, cross-service fence/release, production CMS hay toàn bộ SEC-16/DATA-01. Các diagnostic run FAIL trước evidence cuối vẫn được giữ đúng trạng thái, không được dùng thay PASS.
- Bước tiếp theo: reviewer đối chiếu bảng AC/evidence và quyết định DONE cho riêng BL-R06a. Không tự khởi động BL-R06b hoặc BL-R06c.

## Checkpoint Control Center review — 20/10/2026

Session goal: review độc lập BL-R06a trước khi cho dependency R06b đi tiếp; không implementation R06b.

- Task / trạng thái: BL-R06a trả từ `REVIEW` về `IN_PROGRESS`; BL-R06b giữ `PLANNED`. Hai snapshot CMD-03/CMD-04 vẫn khớp toàn bộ input hash hiện tại và được giữ làm diagnostic, nhưng không đủ để chấp nhận DoD.
- Execution role / mode / write scope: Control Center/Integrator; reviewer native subagent chỉ read-only; code fix tiếp tục trong chat executor R06a riêng. Lượt này chỉ cập nhật registry, task checkpoint và release-manifest state.
- Finding P1: coverage/review bind private bundle digest thay vì candidate metadata nên approval có thể bị tái sử dụng sau khi đổi component/reference/impact/manifest; `reportId` chưa được validate đầy đủ. Executable JSON Schema cũng chưa strict/đồng nhất với Java validator.
- Finding P1: impact/manifest chưa biểu diễn đúng nhiều old version của cùng CAP và thiếu policy rule cho `REVERIFY`; cần reject mọi contradiction theo CAP/version.
- Finding P1: `X-Content-Manifest` base64 có thể vượt response-header limit trong khi DB cho manifest 262144 bytes; contract/runtime/bounds phải thống nhất và có boundary test.
- Finding P1: `UNIQUE(target_revision)` ngăn chuẩn bị candidate P+1 mới sau khi operation trước abort trước canonical commit, trái recovery protocol.
- Finding P1/evidence: publication-specific DB fault, CLI checksum/DB/conflict, stored corrupt/unvalidated row và named report-gate negatives còn thiếu. `scripts/tests/backend-build.Tests.ps1` chưa sở hữu R06a fixture/gate bền; R05 run `61e2db50-...` stale sau thay đổi helper.
- P2: logic validator/store/test đang nén thành dòng dài, chưa đủ comment tiếng Việt giải thích policy matrix, transaction/conflict và failure behavior.
- Kiểm tra thật của reviewer: đối chiếu hash cho cả `artifacts/quality/R06a/213dfe80-ab4f-4e65-b71b-a94e8470fc00` và `artifacts/quality/R06a/0f6e0ece-9a49-4e79-a69a-1420e8bea80b` đều `INPUT_HASHES_MATCH`; report cũ lần lượt PASS 201 và 9 tests, 0 skip, cluster stopped. Không chạy lại application suite vì candidate đã bị review reject ở behavior/coverage.
- Bước tiếp theo chính xác: executor R06a sửa 6 nhóm P1 trong đúng write scope; bổ sung strict named helper/report gates; freeze input rồi rerun R05, CMD-03 và CMD-04. Sau đó Control Center review lại từng AC và chỉ chuyển DONE nếu không còn P0/P1. Không mở BL-R06b trước đó.

## Checkpoint remediation và freeze — 21/10/2026

Session goal: đóng các P1 của review 20/10, tạo lại current-input evidence và đưa riêng BL-R06a về `REVIEW`; không mở coordinator hoặc Learning replica.

- Task / trạng thái: `BL-R06a · REVIEW`, chưa `DONE`; BL-R06b và BL-R06c giữ `PLANNED`. Main session là Integrator/writer; các subagent chỉ audit read-only.
- Đã thay đổi: review/coverage bind canonical semantic subject; report ID/status strict; executable nested schema parity với Java và JSON safe integer; R05 private-case binding; manifest `(CAP, version)` nhiều version + `REVERIFY` + full policy tuple; generation reservation và stale-retry guard; exact manifest digest/bytes; manifest body subresource; publication-specific DB/CLI/corrupt-row negatives; exact named report gates và helper evidence. `PublicationLabCli` cùng logic phức tạp được format/comment tiếng Việt. Ngoài danh sách implementation, chỉ sửa một dòng routing trong `docs/README.md` vì câu “R06a chưa tự thực thi” đã trái registry/checkpoint; không đổi product scope hay code.
- CMD-03 PASS: `pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -EvidenceTask R06a -Offline`; final evidence `artifacts/quality/R06a/79dcb472-5f54-4721-8f28-d102b111451d`; PostgreSQL 18.6; bootstrap 124 + database 80 = 204 tests, 0 failure/error/skip; bốn required publication DB cases có đúng một node/tên, không hidden failure; artifact SHA-256 `55dcb6cf416e1469c64adc9c3dbdf7039c4c4677dfde7c502324d79f53377f20`; 80/80 input hashes hiện hành khớp; cluster stopped và listener closed.
- CMD-04 PASS: `pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R06a -Offline`; final evidence `artifacts/quality/R06a/400482b8-44fa-45a1-975f-20ba23064560`; PostgreSQL 18.6; validator 6 + runtime 7 = 13 tests, 0 failure/error/skip; exact names, không duplicate/hidden status; packaged artifact SHA-256 `2d4fd6e6c9a1f924151cb035ed64f724618dc2a17ec3cae079f38f56e19ca469`; 86/86 input hashes hiện hành khớp; cluster stopped và listener closed.
- Regression/helper PASS: R05 standalone 36 tests tại `artifacts/quality/R05/99c66a88-b4e3-457d-977e-61fd8ba43759`; `backend-build.Tests.ps1 -EvidenceTask R06a -PublicationOnly` 62 command/XML negative/positive cases tại `artifacts/quality/R06a/helper-publication-tests.json`; Node 24 catalog CLI/report gate 19 tests PASS. Targeted `CandidateValidatorTests` 6/6 PASS trước managed freeze.
- Audit PASS: V1/V2/V3 SHA-256 giữ nguyên `5940e88a...47aa3`, `e1dfa0fb...50a6`, `c1d69075...e92e7`; retained JAR hash khớp report, không chứa test/private fixture name hoặc private expected/key/password payload; hai final evidence directory không chứa sentinel/private key/password; publication source chỉ có transaction `commit()` và comment nêu phase/outbox ngoài scope, không có state machine/outbox implementation. Diagnostic CMD-04 `84c80633-...` có application tests xanh nhưng strict report-name gate fail và không được tính PASS.
- Independent review: reviewer read-only kết luận `REVIEW-ready: YES`, không còn P0/P1 trong scope; kiểm lại 80/80 và 86/86 input hashes, exact report nodes, helper 62, R05 36, artifact/privacy và owned listener cleanup. Đây không phải quyền tự chuyển `DONE`.
- Release manifest: `qualifiedPublicationCandidate.state=REVIEW`; các pointer candidate/pass trước freeze được giữ trong manifest để tránh tự tham chiếu run ID vào chính input hash, còn hai current-input final runs ở checkpoint này là nguồn bàn giao chính xác. Đây là candidate review, không phải production/release qualification.
- Giới hạn: chỉ qualify Content-owned candidate store, lab CLI và private INT-03 provider trên fixture. Không chứng minh semantic review/dịch nội dung, Learning replica, cross-service stage/fence/release, canonical activation, production CMS, toàn SEC-16 hay DATA-01 end-to-end.
- Bước tiếp theo: Control Center đối chiếu final review và quyết định `DONE` cho riêng BL-R06a. Không tự khởi động BL-R06b/R06c và không dùng `REVIEW` để suy dependency đã mở.

## Checkpoint Control Center acceptance — 21/10/2026

Session goal: nghiệm thu độc lập và đóng riêng BL-R06a sau remediation; không implementation hoặc tự mở BL-R06b/R06c.

- Task / trạng thái: `BL-R06a · DONE`; registry và `qualifiedPublicationCandidate.state` đã thống nhất. R06b/R06c giữ `PLANNED`; dependency đủ không đồng nghĩa task kế tiếp đã bắt đầu.
- Acceptance: Control Center đối chiếu lại AC01–07, source validator/store/HTTP/V4, strict named report gates và phạm vi contract. Không còn P0/P1 trong supported scope. Quyền `UPDATE(lock_token)` trên canonical head chỉ cấp capability row-lock theo pattern V2/V3; runtime không thể sửa revision/bundle/digest và actual PostgreSQL negative case đã chứng minh boundary này.
- Evidence kiểm lại: CMD-03 `79dcb472-5f54-4721-8f28-d102b111451d` PASS 204 tests, 0 failure/error/skip, 80/80 source hashes khớp; CMD-04 `400482b8-44fa-45a1-975f-20ba23064560` PASS 13 tests, 0 failure/error/skip, 86/86 source hashes khớp; cả hai report ghi PostgreSQL 18.6 và cluster stopped. R05 `99c66a88-b4e3-457d-977e-61fd8ba43759` PASS 36/36; helper publication PASS 62/62; V1–V3 hashes giữ nguyên.
- Release metadata: manifest trỏ hai final run và final packaged artifact SHA-256 `2d4fd6e6c9a1f924151cb035ed64f724618dc2a17ec3cae079f38f56e19ca469`. Việc đổi state/pointer từ snapshot `REVIEW` sang `DONE` sau acceptance là metadata-only input delta dự kiến; evidence gốc và bảng hash không bị sửa hoặc được báo lại là current-input sau transition.
- Giới hạn: DONE chỉ qualify Content-owned candidate store, validate/prepare CLI và private INT-03 provider trên lab fixture. Không qualify semantic content approval, Learning replica, stage/fence/release, canonical activation, production CMS, toàn SEC-16 hoặc DATA-01 end-to-end.
- Bàn giao: không còn việc bắt buộc trong BL-R06a. Không tạo implementation session, không sửa source R06b và không tự đổi trạng thái sibling.
