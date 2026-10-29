# BL-R06b — Learning verified bundle replica và publication gate

[Registry](../../../delivery-backlog.md) · [F08](../README.md) · [Publication protocol](../publication/protocol.md) · [Data protocols](../../../architecture/data-protocols.md) · [DoD](../../../delivery/contract.md)

## Goal, trạng thái và quyền

**Goal:** Learning có thể kéo private bundle bất biến từ Content qua INT-03, tự kiểm exact bytes/checksum/manifest/reference/grading input, lưu replica staged trong own PostgreSQL và xử lý bền STAGE/FENCE/RELEASE/ABORT qua INT-04. Shared publication gate phải sắp thứ tự local content-sensitive transaction với FENCE/RELEASE mà không giữ DB lock qua network. Kết quả được chứng minh bằng actual PostgreSQL, packaged Content/Learning mTLS và fault harness; task không triển khai Content coordinator hoặc canonical commit.

Ngày 21/10/2026 người dùng yêu cầu triển khai task kế tiếp sau khi R06a `DONE`. Control Center đã hoàn thiện brief và mở một implementation chat riêng theo Mode B; riêng BL-R06b chuyển `IN_PROGRESS`. Executor được tự triển khai, sửa lỗi và chạy validation trong write scope dưới đây; chỉ Control Center quyết định `DONE` sau independent acceptance.

Đầu ra chính là `learning-service/contentreplica`: strict INT-03 consumer, immutable verified replica store, INT-04 phase/status provider, durable receipts và publication gate. R1/Learning/MD01, REQ-05/10/16; Learning giữ replica/policy, Content vẫn sở hữu authoring, candidate và canonical publication revision.

## Dependency và đầu vào đã đạt

| Dependency | Phần được tái sử dụng | Không được suy rộng |
|---|---|---|
| [BL-R02b](../../../platform/service-trust/tasks/BL-R02b.md) | Packaged Learning/Content listeners, mTLS caller/action/environment, strict wire và trust harness | Chưa là INT-03 consumer hoặc INT-04 business authorization |
| [BL-R02c](../../../platform/delivery/tasks/BL-R02c.md) | Own DB durable inbox/receipt, exact retry/digest/generation, network ngoài transaction và fault patterns | Delivery probe không phải publication state/effect hoặc coordinator |
| [BL-R06a](BL-R06a.md) | Immutable private bundle/manifest contract, Content INT-03 provider, FX-05 và final Content evidence | Không có Learning replica, phase receiver, fence/release hoặc canonical activation |

R05 catalog/private-case contract được dùng gián tiếp qua R06a candidate; không thêm dependency mới hoặc đánh giá lại semantic content trong leaf này.

## Execution role, mode và write scope

- Role: Backend/Platform executor cho Learning; Control Center là integrator/reviewer.
- Mode: manual chat riêng, một executor; không tạo thêm subagent trừ khi người dùng yêu cầu. Executor đọc file task này thay vì nhận toàn bộ lịch sử Control Center.
- Được sửa: `backend/services/learning-service/src/main/java/lab/backend/learning/contentreplica/**`, test tương ứng, forward migration Learning V4, composition/config publication cần thiết, `contracts/http/learning/internal/publication/**`, fixture publication nếu consumer oracle thật sự cần, `ops/learning/**`, helper/report gate trong `scripts/**`, Learning README và checkpoint/task/registry của R06b.
- Integrator sở hữu quyết định `DONE` và final release-manifest transition. Executor được đưa task tối đa tới `REVIEW`, không đổi sibling.
- Ngoài phạm vi: Content implementation/R06a, coordinator/outbox/canonical commit R06c, learner work thật T1/T2/job/progress, browser API/FE, Identity, production secret/deploy/restore, framework/dependency mới và sửa V1–V3.

Nếu implementation cần file ngoài write scope, ghi lý do và ảnh hưởng vào checkpoint trước; không âm thầm mở R06c hoặc task learner-work.

## Context tối thiểu cho implementation

1. File này: goal, AC, bảng DoD và checkpoint mới nhất.
2. [Publication protocol §1–4](../publication/protocol.md), [DATA-01/02 và INT-03/04](../../../architecture/data-protocols.md), [service boundary §5–6](../../../architecture/service-boundaries.md), [SEC-16](../../../delivery/test-release-plan.md).
3. [INT-03 contract](../../../../contracts/http/content/internal/publication/README.md) và FX-05 trong `contracts/fixtures/publication/`.
4. Learning V2 admission gate/[R04c](../../f10-identity-access/sessions/tasks/BL-R04c.md) cho transaction/gate/least-privilege pattern; Learning V3 delivery/[R02c](../../../platform/delivery/tasks/BL-R02c.md) cho durable receipt/generation/fault pattern. Không copy authority hoặc import runtime xuyên service.
5. Chỉ các Learning transport/config/database tests và `scripts/services.ps1` branches bị ảnh hưởng.

## Invariant và quyết định đã chốt

1. Content sở hữu canonical revision; Learning chỉ giữ verified replica, staged operation, applied head/policy và local gate. `RELEASED` ở Learning không tự chứng minh Content `COMPLETE`.
2. INT-03 download và validation diễn ra trước transaction giữ publication gate/row lock. Không HTTP, sleep hoặc backoff trong transaction.
3. STAGE chỉ ACK sau khi exact bundle + manifest đã được rehash, validate và commit atomically. Invalid/oversize/truncated/wrong-version/wrong-header/reference/private-case input không tạo partial replica/receipt và không làm hỏng last-good replica.
4. Phase envelope/receipt immutable và bind operation, phase, parent/target, bundle/manifest digest, realm, generation, sequence và contract version. Exact retry trả cùng durable receipt; same ID/phase khác bytes hoặc context conflict.
5. State order là STAGE → FENCE → RELEASE, hoặc ABORT trước RELEASE. Không timeout tự mở fence; stale RELEASE/ABORT không được clear fence của operation khác. RELEASE atomically áp head/policy P+1, ghi receipt và clear đúng fence; ABORT giữ P và không xóa historical version còn tham chiếu.
6. Content-sensitive callback/snapshot giữ shared publication gate tới commit/snapshot; FENCE/RELEASE/ABORT cần exclusive gate. Lock order kế thừa protocol; không dùng learner/account gate để publisher giữ network.
7. Trong fence, local probe content-sensitive phải trả/persist trạng thái `publication_pending`, không pass/fail hoặc current mastery. Leaf chỉ chứng minh gate port/probe, không claim T1/T2 thật.
8. INT-04/status chỉ ở private listener cho Content đúng caller/action/environment. Status/receipt/error/log không chứa bundle bytes, private expected, key/password hoặc internal exception detail; browser/edge/Identity/forged header không có quyền.
9. Runtime không auto-migrate/provision/reset. Own app role không DDL/cross-DB/update-delete immutable replica/receipt hoặc tự đổi generation/readiness.

Wire encoding chi tiết là quyết định cục bộ nhưng phải chốt contract + fixture trước runtime path. JSON strict: bad UTF-8, duplicate/unknown/privileged field, unsafe bigint, unsupported major, ambiguous path/query và body quá giới hạn đều fail closed. INT-03 consumer chấp nhận additive response metadata của major hiện hành nhưng không silent-fallback version/state lạ.

## Acceptance và Definition of Done

| AC | Điều kiện quan sát được | Bằng chứng bắt buộc |
|---|---|---|
| R06b-AC01 — INT-04 contract | Strict STAGE/FENCE/RELEASE/ABORT envelope, receipt và status bind đủ operation/phase/parent/target/digests/realm/generation/sequence/version; exact duplicate hội tụ, changed retry/out-of-order/stale/unknown field/version bị từ chối; status không lộ private payload | Executable schemas/fixtures + Java wire tests và negative corpus; input không mutate |
| R06b-AC02 — INT-03 verify/pull | Learning gọi đúng Content private resource, kiểm contract/revision/length/digest/link, lấy manifest riêng, rehash exact bytes và validate schema/reference/private-case/pinned versions trước STAGE. Sai/truncated/oversize/redirect/public/wrong CA/SAN/EKU/caller/header hoặc Content unavailable không ghi partial state | Packaged Content + Learning actual mTLS consumer tests, controlled corrupt/fault cases, no DB lock held across network |
| R06b-AC03 — Immutable replica/store | Forward Learning V4 lưu bundle/manifest/operation/phase receipt exact và atomic; fresh/rerun/rollback đúng. Same operation/bundle/version khác bytes conflict; two-connection race một effect; app role không DDL/cross-DB/mutate immutable rows hoặc generation | Actual PostgreSQL CMD-03, failure injection, role/constraint/race tests; V1–V3 hashes không đổi |
| R06b-AC04 — Gate và phase effects | STAGE chưa active; FENCE chờ shared callback/snapshot đã mở và chặn callback mới; RELEASE atomically áp policy/head P+1 + receipt + clear đúng fence; pre-release ABORT giữ P và clear đúng fence. Lost ACK/restart/exact retry hội tụ; stale phase không mở fence mới | Actual PG two-connection barriers + packaged phase/status runtime/fault tests. DATA-02 chỉ local gate layer, chưa T1/T2/progress end-to-end |
| R06b-AC05 — INT-04 security/privacy | Private Learning provider chỉ nhận Content đúng action/resource/environment và method/path/body; public listener, browser/edge/Identity/cookie/Origin/forged caller, query/ambiguous path và wrong cert bị deny trước effect. Receipt/status bounded, no-store và không lộ grading input | Actual mTLS CMD-04/05 SEC-16 positive/negative, artifact/log/error privacy scan |
| R06b-AC06 — Managed evidence | Explicit R06b CMD-03/04/05 branches; exact named suites/cases >0, không failure/error/required skip. Missing config/suite/report, zero/duplicate/hidden failure hoặc unknown selector fail nonzero. Affected R02b/R02c/R04c/R06a contracts còn đạt theo impact | Strict helper/report-gate tests, exact source/artifact/input hashes, dependency/secret/package audit, owned process/listener/cluster cleanup |
| R06b-AC07 — Review/handoff | Comment Việt cho transaction/lock/state/retry; contract/runbook/task/manifest thống nhất behavior và giới hạn; không P0/P1 trong supported scope; không đổi FE/Content/Identity/dependency/framework/old migrations ngoài quyết định đã ghi | DONE-01–05, scoped final review và checkpoint; không claim R06/R06c hoặc cross-service publication DONE |

## Validation đích

| Gate | Nội dung tối thiểu | Evidence |
|---|---|---|
| CMD-03 Learning/R06b | V4 fresh/rerun, immutable store/receipt/head, rollback, exact retry/conflict, two-connection shared/exclusive gate, role/generation negatives | `artifacts/quality/R06b/<runId>/` + current DoD table |
| CMD-04 Learning/R06b | Wire/schema, actual packaged Content INT-03 consumer và Learning INT-04 provider mTLS, exact bytes/version/privacy/path/caller negatives | Run/report contract với exact named tests và retained artifact |
| CMD-05 `DATA-01`, local `DATA-02`, `SEC-16` | Invalid bundle no stage/good replica giữ; fence race/lost ACK/restart/stale phase; no bypass/private leak | Actual fault selectors; local DATA-02 không thay owner R06c/R13b |
| Affected regressions | Learning bootstrap/DB/trust/admission/delivery và R06a provider contract theo input impact | Giữ evidence cũ khi hashes còn khớp; chỉ rerun gate bị ảnh hưởng |
| Final review | Source/input/artifact/package/secret/private-payload/process cleanup, docs/status/links và no P0/P1 | Durable report hoặc checkpoint có exact run/hash/limit |

## Plan thực thi

1. Freeze INT-04 v1 schemas/fixtures và consumer oracle từ INT-03; thêm strict wire/unit negatives trước runtime.
2. Thêm Learning V4 least-privilege replica/operation/receipt/gate schema và store; kiểm atomicity, retry, race, lock order và last-good safety trên PostgreSQL thật.
3. Thêm INT-03 mTLS client + validator/download ngoài transaction, sau đó INT-04 phase/status handler và explicit publication lab profile.
4. Nối shared gate port/probe và STAGE/FENCE/RELEASE/ABORT effects; kiểm lost ACK/restart/fault/privacy bằng packaged Content/Learning artifacts.
5. Thêm strict R06b helper/report gates, chạy targeted tests rồi CMD-03/04/05 và affected regressions. Không chạy lại suite không bị ảnh hưởng nếu input hash còn hợp lệ.
6. Freeze candidate, audit AC01–07/DONE-01–05 và cập nhật bảng evidence/checkpoint. Executor chỉ chuyển `REVIEW`; Control Center nghiệm thu `DONE`.

## Bảng DoD/evidence hiện hành

| Gate | Trạng thái 25/10/2026 | Evidence / việc còn thiếu |
|---|---|---|
| R06b-AC01 | PASS | V4 có durable cursor duy nhất `content-global`; receipt/effect/cursor commit cùng transaction và cursor chỉ tiến đúng một sequence đã có receipt. CMD-03 `886033e9-7055-4fb5-ae7d-4bb34001f7d4` cùng CMD-04 `c16a06a4-fc03-4790-8080-aee2d71959a7` chứng minh exact old receipt hội tụ, future gap/stale/same-sequence changed command bị conflict và không advance effect/head/cursor |
| R06b-AC02 | PASS | `ReplicaValidator` buộc private-case v2 integral, exact field, ID/target bound và manifest row/type/pattern/bound; chỉ nhận đúng bốn policy tuple. Bốn unit cases gồm negative corpus cho version/boolean/reason/schemaVersion/duplicate/bounds; packaged CMD-04 và DATA-01 `4c1ff853-6ea9-4edf-bebd-64df6e5d4d01` giữ last-good khi bundle sai hoặc Content unavailable |
| R06b-AC03 | PASS | CMD-03 `886033e9-7055-4fb5-ae7d-4bb34001f7d4`: V4 fresh/rerun/rollback, 5 `PublicationStoreIT` cases, actual two-connection race và role negatives đều 0 fail/error/skip. App chỉ được đổi operation `state` và cursor một bước có receipt; replica/receipt/context/generation bất biến, V1–V3 không đổi |
| R06b-AC04 | PASS | CMD-03 chứng minh actual callback transaction giữ shared gate, FENCE chờ commit, durable fence vẫn trả `publication_pending` sau restart và ABORT giữ P. CMD-04 cùng DATA-02 `86764821-2f33-40a3-964b-58e33ba3ba62` chạy packaged STAGE/FENCE/restart/exact retry/ABORT/RELEASE/status; giới hạn vẫn là local gate, chưa claim T1/T2/progress |
| R06b-AC05 | PASS | CMD-04 + SEC-16 `1c8a4c20-436e-42c4-8b28-a65370ee9568`: public/wrong caller/header/cookie/origin/method/body/path/query và wrong CA/EKU/SAN/environment/ambiguous cert bị deny trước effect; response/log/evidence scan không lộ private sentinel/exception/password, no-store |
| R06b-AC06 | PASS | Trước metadata-only acceptance transition, CMD-03/04, DATA-01/02 và SEC-16 đều `PASS`, `clusterStopped=true`, required report có 0 failure/error/skip và 91/250 input mỗi run khớp hash. Helper report gate `PASS` 36/36 với ba script hash current; final JAR/hash/package, privacy và owned process/listener cleanup đều đạt. Registry/task/F08/release-pointer chuyển `DONE` sau nghiệm thu không đổi code/contract/runtime |
| R06b-AC07 / DONE-01–05 | PASS — `DONE` | Control Center nghiệm thu độc lập ngày 25/10/2026: hai P1 đã được sửa và có negative evidence; scoped source/evidence review không còn P0/P1. Contract/runbook/checkpoint/release pointer đồng nhất, không mở hoặc claim R06c |
| Ready gate | PASS | Goal/output/scope/dependency/invariant/AC/validation và quyền CODE đã chốt |

## Checkpoint khởi tạo — 21/10/2026

- Session goal: chuẩn bị và khởi động implementation riêng cho BL-R06b; không triển khai R06c.
- Task / trạng thái: `BL-R06b · IN_PROGRESS`; dependencies R02b/R02c/R06a `DONE`. R06c giữ `PLANNED`.
- Execution role / mode / write scope: Control Center soạn brief và mở manual implementation chat; executor Learning là writer duy nhất trong scope task cho tới handoff. Không native subagent mặc định.
- Đã thay đổi: tạo brief R06b, registry/F08 link và trạng thái; chưa sửa application source, migration, contract, fixture, helper hoặc release manifest.
- Acceptance: Ready gate đạt; AC01–07 chưa bắt đầu, không có runtime PASS.
- Kiểm tra: docs/config validation của Control Center phải hoàn tất trước handoff; application suites chưa chạy vì implementation chưa bắt đầu.
- Bước tiếp theo: executor đọc context tối thiểu ở trên, triển khai theo plan, ghi evidence thật và giữ `IN_PROGRESS` nếu thiếu bất kỳ required gate; chỉ đưa `REVIEW` khi đủ, không tự mở R06c.

## Checkpoint implementation — 23/10/2026

- Thay đổi: thêm INT-04 executable request/receipt/status schemas và corpus; Learning V4 immutable replica/operation/receipt/head; strict INT-03 client/validator; INT-04 private handler/status; publication profile/topology/provisioning; durable shared/exclusive gate port; managed R06b CMD-03/04/05 branches. Không sửa Content/Identity source, FE, dependency/framework hoặc V1–V3.
- Sửa invariant trong vòng review: bỏ `FOR SHARE` thừa làm app SELECT cần UPDATE privilege; durable fence không còn chỉ là in-memory write lock mà được đọc dưới shared gate nên callback sau FENCE/restart trả `publication_pending`; receipt/status bind thêm bundle/security/delivery/command fields; ABORT giữ applied head và chỉ clear own fence.
- Validation hiện hành: CMD-03 `ec4b5737-23fa-417c-841f-dcb84420bcbb`; CMD-04 `7d6e602b-1f73-46d7-87d8-e24f70e246ef`; DATA-02 `a3e47eff-bc91-427d-b52a-0ddd42fe8fb0`; DATA-01 `8d826f11-4c60-4c7f-84e6-032056dfb4aa`; SEC-16 `3d91a46b-40b5-46f8-a58b-c078818c7874`. Tất cả report `PASS`, không required skip/failure/error và `clusterStopped=true`; artifacts giữ trong `artifacts/quality/R06b/<runId>/`.
- Failure history giữ để chẩn đoán, không tính PASS: các initdb/socket/helper/runtime lỗi trước; run `71f3a3a2-…` phát hiện fixture ABORT đụng immutable target revision rồi đã sửa bằng cách dùng operation staged từ race, không nới constraint.
- Trạng thái: giữ `BL-R06b · IN_PROGRESS`, chưa chuyển `REVIEW`. AC02 còn consumer fault matrix; AC04 còn packaged ABORT + actual learner callback barrier; AC06 còn negative helper-gate/final audit. Không mở BL-R06c.
- Bước tiếp theo: bổ sung controlled INT-03 response fault provider/tests, packaged ABORT và concurrent local callback probe, thêm helper negative audit; sau mỗi source/tool change chạy lại đúng affected current-input gate rồi mới final acceptance.

## Checkpoint freeze và handoff REVIEW — 23/10/2026

- Thay đổi từ checkpoint trước: INT-03 client dùng bounded streaming và controlled fault matrix cho redirect/truncated/oversize/duplicate/wrong metadata; packaged runtime thêm ABORT exact retry, Learning restart và lost-ACK convergence; actual PostgreSQL test giữ callback transaction trên connection riêng để chứng minh FENCE chờ shared gate; helper thêm 36 negative/positive parser/report cases. Final review còn phát hiện app role được `UPDATE` toàn bộ operation context, đã thu hẹp V4 xuống `UPDATE(state)` và thêm role regression.
- Current-input evidence sau bản sửa cuối: CMD-03 `5801a239-b3ce-42ec-9efe-ba645f879816`; CMD-04 `935a13af-317d-4530-af3a-66cf527140fa`; DATA-02 `e569ce2e-96ae-4b15-9aac-efb1b51c7bc7`; DATA-01 `33265b3e-9533-4efb-96a7-9eee0a380292`; SEC-16 `7b74f8f1-4204-4d64-8fa1-38c520cb1837`. Tất cả `PASS`, 0 failure/error/skip trong required reports, input mismatch bằng 0 và `clusterStopped=true`.
- Audit: helper `artifacts/quality/R06b/helper-publication-tests.json` PASS 36/36 và ba input hash còn current; final Learning JAR SHA-256 `a2f531116de4329f4c60b4d18d25badc17a43005bbd66224375f705d33071f62` khớp SEC-16 report, 246 entry không có test/private fixture/key. Recursive scan 110 evidence text file không thấy private sentinel/private key/password/internal exception; không còn Java/PostgreSQL/psql process hoặc listener 8080/8443/18083/18443/lab ports.
- Failure history không tính PASS: hai diagnostic CMD-03 `f021fd50-…` và `1e524ed6-…` bị Windows loopback socket `10013` trước provision; cả hai vẫn cleanup cluster. Cùng lệnh chạy ngoài sandbox sau đó PASS, không đổi timeout/gate để che lỗi.
- Trạng thái: executor chuyển riêng `BL-R06b · REVIEW`; R06b-AC01–07 đạt cho supported lab scope, không claim R06/R06c, canonical Content commit, learner T1/T2/progress hoặc production deployment. Chỉ Control Center có quyền independent acceptance và chuyển `DONE`.
- Bước tiếp theo: Control Center đối chiếu source, exact reports và giới hạn ở checkpoint này; nếu acceptance đạt thì đóng riêng R06b. Không tự mở BL-R06c.

## Checkpoint Control Center review — 24/10/2026

- Kết quả: **không nhận `DONE`**, trả riêng BL-R06b từ `REVIEW` về `IN_PROGRESS`; BL-R06c giữ `PLANNED` và chưa được mở.
- Evidence đã xác minh: đúng năm report cuối ở checkpoint freeze đều `PASS`, `clusterStopped=true`, 0 failure/error/skip; toàn bộ 91/250 input hash và ba helper input hash vẫn khớp source hiện tại. Evidence này chỉ chứng minh các case đã chạy, không phủ hai finding bên dưới.
- P1 / AC01: V4 chỉ đặt `UNIQUE(delivery_sequence)` trên receipt, còn `PublicationStore` không có durable receiver cursor hoặc phép kiểm `sequence == expected`. Vì vậy một phase đúng state với sequence tương lai/nhảy/lùi vẫn commit nếu chưa đụng giá trị unique; trái strict `content-global` stream và out-of-order/stale acceptance. Test hiện tại chỉ dùng chuỗi tăng đều 1→7 và không có gap/old-prefix case.
- P1 / AC02: `ReplicaValidator` kiểm exact field/digest/link nhưng không bắt kiểu/bound cho manifest `version`, `newAttemptAllowed`, `submitAllowed`, `finalizeAllowed`, `reason`; `schemaVersion` cũng không yêu cầu integral. Learning vì thế chưa tự reject đầy đủ malformed policy/private bundle theo AC02.
- Sửa tối thiểu cần có: thêm cursor/prefix bền và atomic với receipt/effect; reject future gap, stale không có exact receipt và same-sequence changed command mà không advance state. Siết validator theo INT-03 manifest/private-case v2 contract, gồm type, pattern/bound và negative corpus cho từng field.
- Validation cần làm lại sau sửa: targeted unit/actual-PG negatives trước; sau đó CMD-03, CMD-04, DATA-01, DATA-02 và SEC-16 theo input ảnh hưởng, giữ exact report/cleanup. Helper 36/36 chỉ cần chạy lại nếu helper/script input đổi.

## Checkpoint remediation sau Control Center review — 24/10/2026

- Sửa hai P1 trong đúng scope Learning: thêm `publication_delivery_cursor` cho stream `content-global`, trigger/privilege/store update buộc receipt + effect + cursor atomic và sequence nối prefix; exact receipt cũ vẫn ACK, còn gap/stale/same-sequence changed command bị từ chối không effect. `ReplicaValidator` nay mirror INT-03 private-case v2 và manifest bounds/types/patterns, gồm integral conversion guard và đúng bốn policy tuple.
- Current-input evidence sau bản sửa cuối: CMD-03 `886033e9-7055-4fb5-ae7d-4bb34001f7d4`; CMD-04 `c16a06a4-fc03-4790-8080-aee2d71959a7`; DATA-02 `86764821-2f33-40a3-964b-58e33ba3ba62`; DATA-01 `4c1ff853-6ea9-4edf-bebd-64df6e5d4d01`; SEC-16 `1c8a4c20-436e-42c4-8b28-a65370ee9568`. Cả năm `PASS`, `clusterStopped=true`, required reports có 0 failure/error/skip và toàn bộ 91/250 captured input mỗi run khớp SHA-256 hiện tại.
- Exact coverage: CMD-03 có 5 `PublicationStoreIT` cases và 4 `ReplicaValidatorTests`; CMD-04 có đúng 3 `PublicationRuntimeIT` cases. Helper `helper-publication-tests.json` vẫn `PASS` 36/36 và ba script hash current vì helper input không đổi. Bộ rerun đầu tiên trước khi bổ sung integral overflow negative chỉ giữ làm lịch sử chẩn đoán, không tính current evidence.
- Final audit: SEC-16 Learning JAR SHA-256 `0b20da2aede323f84f9022a434436964b529e471f2b1737462bde84983dad177` khớp report; 247 entry không có test/key/certificate/private marker. Recursive scan năm evidence directory không thấy private sentinel/private key/password/SQL exception; không còn Java/PostgreSQL/psql process hoặc listener 8080/8443/18083/18443/lab/transient ports.
- Trạng thái: executor chuyển riêng `BL-R06b · REVIEW`; R06c giữ `PLANNED`. Candidate không claim canonical Content commit, learner T1/T2/progress hay production deployment; chỉ Control Center được nghiệm thu và chuyển `DONE`.

## Checkpoint Control Center acceptance — 25/10/2026

- Kết quả: **AC01–AC07 và DONE-01–05 đạt; BL-R06b chuyển `DONE`**. BL-R06c giữ `PLANNED`, không được mở trong phiên nghiệm thu này.
- Review source: durable cursor `content-global` được lock và buộc `last+1`; receipt, phase effect và cursor cùng transaction. Exact receipt cũ trả trước cursor check, còn gap/stale/changed command không advance effect/head/cursor. Trigger và column privilege chặn app skip/rewind trực tiếp.
- Review validator: private-case v2 buộc integral schema version, bounded ID/target, target duy nhất; manifest buộc exact fields, typed/bounded version/boolean/policy/reason và chỉ chấp nhận bốn policy tuple đã chốt. Negative corpus phủ type, bound, duplicate, empty và policy contradiction.
- Evidence được nhận: CMD-03 `886033e9-7055-4fb5-ae7d-4bb34001f7d4`; CMD-04 `c16a06a4-fc03-4790-8080-aee2d71959a7`; DATA-02 `86764821-2f33-40a3-964b-58e33ba3ba62`; DATA-01 `4c1ff853-6ea9-4edf-bebd-64df6e5d4d01`; SEC-16 `1c8a4c20-436e-42c4-8b28-a65370ee9568`. Trước acceptance metadata update, cả năm có input mismatch 0, `PASS`, 0 failure/error/skip và `clusterStopped=true`; helper 36/36 còn current.
- Artifact: final Learning JAR SHA-256 `0b20da2aede323f84f9022a434436964b529e471f2b1737462bde84983dad177` khớp SEC-16 report. Control Center chỉ đổi registry/task/F08 và `ops/release/dependencies.json` sau khi nhận candidate; đây là expected metadata-only delta, không làm thay đổi runtime đã qualify.
- Giới hạn giữ nguyên: `DONE` chỉ cho Learning verified replica, INT-04 participant và local publication gate trong owned lab. Không chứng minh Content canonical coordinator/commit, toàn BL-R06, learner T1/T2/progress, production deploy hoặc semantic content approval.
- Bước tiếp theo: không tự mở task khác. BL-R06c chỉ được brief/triển khai khi người dùng giao riêng.
