# BL-R06c — Content publication coordinator, phase outbox và canonical commit

[Registry](../../../delivery-backlog.md) · [F08](../README.md) · [Publication protocol](../publication/protocol.md) · [Service boundaries](../../../architecture/service-boundaries.md) · [Quality process](../../../quality-process.md) · [DoD](../../../delivery/contract.md)

## Goal, trạng thái và quyền

**Goal:** Content điều phối bền một publication operation toàn catalog từ candidate đã chuẩn bị qua `STAGE` → `FENCE` → canonical commit + `RELEASE` → `COMPLETE`, hoặc `ABORT` trước canonical commit. Mọi phase command/outbox và receipt phải exact, retry/restart hội tụ; Content chỉ đổi canonical head sau đúng durable `FENCED` receipt của Learning và phải ghi canonical P+1 cùng `RELEASE` outbox trong một Content transaction. `COMPLETE` chỉ được ghi sau đúng durable `RELEASED` receipt.

Ngày 25/10/2026 người dùng yêu cầu tiếp tục task kế tiếp sau khi BL-R06b `DONE`. Control Center hoàn thiện brief này và mở một implementation chat riêng theo Mode B; riêng BL-R06c chuyển `IN_PROGRESS`. Executor được tự triển khai, sửa lỗi và chạy validation trong write scope dưới đây. Executor chỉ được đưa task tối đa tới `REVIEW`; Control Center giữ quyền nghiệm thu `DONE`.

**Trạng thái hiện hành 28/10/2026: `DONE`.** Control Center đã nghiệm thu candidate, đối chiếu AC01–07/DONE-01–05 và ghi final evidence pointer vào release manifest. BL-R06 parent và downstream vẫn giữ trạng thái riêng trong registry.

Đầu ra chính là `content-service/publication` coordinator: forward Content schema cho operation/outbox/receipt/readiness, strict INT-04 client, dispatcher/recovery và packaged maintenance CLI. R1/Content/Learning/MD01, REQ-10/16; Content sở hữu canonical revision/coordinator, Learning tiếp tục sở hữu verified replica, publication gate và applied policy.

## Dependency và đầu vào đã đạt

| Dependency | Phần được tái sử dụng | Không được suy rộng |
|---|---|---|
| [BL-R06a](BL-R06a.md) | Immutable candidate/bundle/manifest, active reservation, canonical-head oracle, validate/prepare CLI, private INT-03 provider và FX-05 | Candidate hợp lệ chưa active; R06a không có phase, outbox, canonical writer hoặc Learning readiness |
| [BL-R06b](BL-R06b.md) | Learning INT-04 STAGE/FENCE/RELEASE/ABORT + status, strict `content-global` cursor, durable receipts, verified replica và local shared/exclusive gate | `RELEASED` ở Learning chưa chứng minh Content `COMPLETE`; local probe chưa là T1/T2/progress thật |
| [BL-R02c](../../../platform/delivery/tasks/BL-R02c.md) | Own-DB atomic outbox/state pattern, immutable exact bytes, lease/token, retry/lost-ACK/restart, wrong ACK và network ngoài transaction | Generic delivery probe/recovery không phải publication authority, canonical state hoặc production restore |

Không thêm dependency mới. R05/private-case và impact matrix được dùng qua candidate/manifest đã validate của R06a; leaf này không tự đánh giá lại semantic content hoặc sửa authority của Learning.

## Execution role, mode và write scope

- Role: Backend/Platform executor cho Content coordinator; Control Center là integrator/reviewer.
- Mode: chat implementation riêng theo Mode B, một writer. Không tạo subagent hoặc chat khác nếu người dùng không yêu cầu.
- Được sửa: `backend/services/content-service/src/main/java/lab/backend/content/publication/**`, test tương ứng, forward migration Content V5, composition/profile/POM tối thiểu của Content cho publication; fixture mới dưới `contracts/fixtures/publication/**` nếu cần oracle R06c nhưng không rewrite fixture đã qualify; `ops/content/**`; explicit R06c branch/report gate và review helper trong `scripts/**`; Content README, task/checkpoint/registry R06c và một release-manifest field riêng cho coordinator candidate.
- Được đọc nhưng mặc định không sửa: R06a/R06b/R02c implementation, `contracts/http/content/internal/publication/**`, `contracts/http/learning/internal/publication/**`, Learning migrations/source và qualified evidence. Nếu executable INT-04 contract thật sự sai hoặc thiếu, dừng và ghi finding; không âm thầm đổi provider contract đã `DONE`.
- Integrator sở hữu final `DONE` và qualified manifest transition. Executor không đổi parent BL-R06, sibling hoặc downstream task.
- Ngoài phạm vi: Learning/Identity/FE source, learner work T1/T2/job/progress/projection, public browser publish API, CMS/admin UI, global/cross-DB transaction, broker/service/framework/dependency mới, production secret/deploy/restore và sửa migration V1–V4.

Nếu cần file ngoài write scope, ghi lý do và ảnh hưởng vào checkpoint trước. Không dùng tên task để tự tạo service mới hoặc refactor delivery foundation đã qualify.

## Context tối thiểu cho implementation

1. File này: goal, scope, AC, bảng DoD và checkpoint mới nhất.
2. [Publication protocol §1–4](../publication/protocol.md), riêng PUB-01–07, linearization PUB-04, retry/abort/recovery và lock order.
3. [INT-04 contract](../../../../contracts/http/learning/internal/publication/README.md) cùng phase/receipt/status schemas; [INT-03/candidate contract](../../../../contracts/http/content/internal/publication/README.md) chỉ để bind exact candidate/bundle/manifest.
4. Content V4/`PublicationStore` của R06a và Learning INT-04 behavior/checkpoint R06b; R02c `DeliveryStore`/dispatcher chỉ là transaction/retry pattern, không phải publication domain API.
5. [DATA-02–07/14 owners](../../../delivery/test-release-plan.md), Content service section trong [quality process](../../../quality-process.md), `scripts/services.ps1` và helper/report tests bị ảnh hưởng.

Không đọc lại toàn repo hoặc checkpoint lịch sử nếu checkpoint hiện hành không dẫn tới. Khi review source, dùng symbol/range/search và chỉ mở file cần sửa.

## Invariant và quyết định đã chốt

1. R1 chỉ có một publication operation active toàn catalog. Operation mới chỉ được mở sau operation trước `COMPLETE` hoặc `ABORTED`; process lease không thay durable serialization.
2. Candidate, parent/target, bundle/manifest digest, realm, Content/Learning generation và sequence được bind bất biến vào operation và từng phase command. Same phase retry gửi byte-identical command; same identity khác bytes/context là conflict.
3. Producer state, exact phase command và publication outbox phải commit atomically trong Content DB. Network, sleep/backoff và status poll luôn nằm ngoài Content transaction/row lock.
4. STAGE/FENCE ACK chỉ được nhận khi receipt/status đúng contract, operation, phase/state, parent/target, bundle/manifest digest, realm, cả generation, sequence và command digest. Wrong/malformed/stale/future receipt không advance coordinator hoặc head.
5. Content không suy ACK từ timeout. Lost response dùng byte-identical retry hoặc private status để lấy durable fact; status không được dùng để bỏ qua prefix/phase hay nhận operation khác.
6. PUB-04 là linearization point: đúng `FENCED` receipt là điều kiện bắt buộc; update canonical head P→P+1 và insert exact `RELEASE` outbox phải cùng một transaction. Fault ở bất kỳ điểm nào phải rollback cả hai hoặc giữ cả hai.
7. Sau PUB-04 không còn `ABORT`, rollback P hoặc thay command dưới ID cũ. Coordinator giữ operation active và retry `RELEASE` đến đúng `RELEASED` receipt; Learning vẫn fenced/pending trong khoảng này.
8. `learningReadyRevision` chỉ advance cùng durable Content `COMPLETE` sau receipt `RELEASED` đúng binding. Canonical revision và learning-ready revision là hai giá trị riêng; status phải biểu diễn được `P+1 / learning pending` mà không báo thành công giả.
9. `ABORT` chỉ được yêu cầu trước PUB-04. Nếu STAGE/FENCE outcome chưa rõ, coordinator phải resolve/retry đúng prefix rồi phát durable ABORT; không skip sequence, timeout-unfence hoặc mở operation mới trước đúng `ABORTED` receipt. Abort giữ canonical P và history/bundle còn tham chiếu.
10. Candidate reservation/generation chỉ được rotate/clear qua trusted coordinator transition và không cho stale operation chiếm lại slot. App role không DDL, không tự provision/generation/readiness, không arbitrary update canonical/outbox/receipt/binding.
11. CLI là maintenance-only, packaged, fail closed và không nhận arbitrary DSN/peer URL/generation để bypass topology. Tối thiểu phải quan sát/điều khiển start/dispatch-or-resume/status/abort theo state hợp lệ; stdout/stderr không chứa private bundle, expected, secret, SQL/stack detail.
12. Impact/withdrawal dùng đúng immutable manifest đã qualify. R06c chỉ chứng minh exact manifest đi cùng STAGE/RELEASE và coordinator không complete sai; không claim actual T1/T2/history/current-evidence behavior của R13b/R22a.
13. Handwritten FE/BE/SQL/scripts/tests phải dễ đọc: không minify nhiều statement/declaration trên một dòng; ưu tiên một statement/declaration mỗi dòng, format toàn symbol chạm tới, khoảng 100 ký tự/dòng khi hợp lý và comment tiếng Việt cạnh transaction/lock/authorization/retry/race/invariant không hiển nhiên.

Wire/client encoding là quyết định cục bộ nhưng phải mirror executable INT-04 v1, strict UTF-8/duplicate/unknown field/unsafe bigint/path/version/bounds và verify exact response bytes trước effect. Không silent fallback state/version và không sửa Learning contract để hợp thức hóa client lỏng.

## Acceptance và Definition of Done

| AC | Điều kiện quan sát được | Bằng chứng bắt buộc |
|---|---|---|
| R06c-AC01 — Durable coordinator model | Forward Content V5 lưu immutable operation binding, state, phase command/outbox, receipt và canonical/learning-ready revisions; one-active-operation + strict sequence/prefix. Fresh/rerun/rollback/least privilege đúng; exact retry hội tụ, changed/stale/gap/race conflict không partial effect | Actual PostgreSQL CMD-03, failure injection và two-connection race; V1–V4 hash không đổi; app role/constraint/trigger negatives |
| R06c-AC02 — INT-04 client và phase dispatch | Content serialize exact STAGE/FENCE/RELEASE/ABORT, gọi đúng private Learning route qua mTLS, bounded response, strict receipt/status verify và durable lease/retry. Send/poll ngoài DB lock; lost request/ACK, restart, 4xx/5xx/timeout/truncated/oversize/malformed/wrong cert không đổi state sai | Java wire/consumer tests + packaged Content/Learning CMD-04/05; byte-identical resend và no private/error leak |
| R06c-AC03 — STAGE/FENCE gate | Start atomically bind active R06a candidate + STAGE outbox; chỉ đúng `STAGED` receipt mới tạo FENCE; chỉ đúng `FENCED` receipt mới cho PUB-04. Learning down trước FENCE ACK giữ canonical P; stale status/ACK/op/generation/digest/sequence không mở commit | Actual cross-service mTLS + PG crash/restart cases; DATA-02 coordinator/local-gate layer, DATA-03 và DATA-14 |
| R06c-AC04 — Canonical commit, RELEASE và COMPLETE | FENCE ACK → canonical P+1 + exact RELEASE outbox atomic; fault trước commit giữ P/no RELEASE, fault sau commit luôn có durable RELEASE. RELEASE retry/restart/lost ACK một effect; only correct `RELEASED` advances learning-ready P+1 and COMPLETE | Actual PG transaction/fault barriers + packaged Learning; DATA-04, status shows canonical-ahead/pending, final head/readiness/receipt exact |
| R06c-AC05 — Abort và recovery boundary | Before PUB-04 abort intent/outbox durable, resolves uncertain prior prefix, Learning ABORTED ACK then Content ABORTED/rotation; P unchanged. After PUB-04 abort is rejected and only RELEASE recovery remains. Old ABORT/RELEASE cannot clear/complete newer operation | Crash at each state, exact retry/status reconciliation, restart and stale-operation races; DATA-05 plus negative CLI/state tests |
| R06c-AC06 — Impact, security và managed evidence | Cosmetic/reverify/withdraw/invalid-rubric candidate keeps exact manifest/digests through coordinator; no false COMPLETE/readiness or payload leak. Private Learning only, fixed lab topology, no public trigger/bypass. Explicit R06c CMD-03/04/05 branches reject missing/zero/skip/duplicate/hidden failure/unknown selector | FX-05/current oracle, DATA-06/07 producer-layer cases, strict helper/report gates, artifact/input/dependency/secret/process/listener/cluster audit; affected R06a/R06b/R02c regressions |
| R06c-AC07 — Review và handoff | Code/comment/runbook/contract/task/manifest thống nhất state, lock, retry, operator actions và giới hạn; touched handwritten code đạt rule dễ đọc; không P0/P1 trong supported scope. Executor chỉ chuyển `REVIEW`, không claim parent/downstream/production | DONE-01–05, scoped final source/evidence review và checkpoint có exact runs/hashes/failures/limits |

DoD là R06c-AC01–07 cộng DONE-01–05 trong `docs/delivery/contract.md`. DATA-02/06/07 tại leaf này chỉ là lớp coordinator, exact manifest và actual Learning gate đã có; không thay full owner T1/T2/current progress. Missing suite, zero test, required skip hoặc controlled fixture không được báo PASS cho lớp actual PostgreSQL/mTLS khác.

## Validation đích

Các command dưới là interface đích; explicit R06c selector/report gate chưa tồn tại khi tạo brief và là một phần implementation, không phải evidence đã có:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -EvidenceTask R06c -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R06c -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -EvidenceTask R06c -Case DATA-02 -Offline
```

`-Task fault` phải hỗ trợ riêng `DATA-02`, `DATA-03`, `DATA-04`, `DATA-05`, `DATA-06`, `DATA-07`, `DATA-14`; mỗi selector chỉ nhận đúng named cases và native argument vector, không chạy suite khác rồi gắn nhãn. Nếu thiết kế helper cần một tên task khác để đúng existing command taxonomy, cập nhật brief/checkpoint trước khi freeze evidence và giữ mapping CMD-05 rõ.

| Gate | Nội dung tối thiểu | Evidence |
|---|---|---|
| CMD-03 Content/R06c | V5 fresh/rerun, state/outbox/receipt/head/readiness atomicity, one-active/race/rollback, exact retry/sequence, pre/post-commit abort và least privilege | `artifacts/quality/R06c/<runId>/` + bảng DoD hiện hành |
| CMD-04 Content/R06c | Strict INT-04 command/receipt/status consumer, actual packaged Content→Learning mTLS, CLI/package/config/path/cert/body/version/privacy negatives | Exact named unit/integration reports, retained artifacts và current input hashes |
| CMD-05 DATA-02–07/14 | Gate-before-commit, participant down, every commit/ACK crash point, abort/stale op, impact/withdraw binding và wrong receipt realm/generation/digest/sequence | Actual Content + Learning + own PostgreSQL fault runs; ghi rõ lớp nào chỉ coordinator/oracle |
| Affected regressions | R06a candidate/INT-03, R06b participant/local gate và R02c delivery/trust behavior theo input impact | Giữ evidence cũ chỉ khi captured inputs vẫn khớp; rerun đúng gate bị ảnh hưởng |
| Final review | Source/input/artifact/package/dependency/secret/private-payload, old migration hashes, process/listener/cluster cleanup, docs/status/links và no P0/P1 | Durable review JSON hoặc checkpoint có exact hash/run/limit |

Không chạy full repo chỉ để tăng số test. Failed/diagnostic run phải giữ nguyên trạng thái và không cộng vào PASS; sửa source/helper nào thì rerun đúng gate có input bị đổi trước freeze.

## Plan thực thi

1. Freeze current R06a candidate/V4 và R06b INT-04 schemas/fixtures; viết strict Content command/receipt/status codec + negative corpus trước coordinator runtime.
2. Thêm Content V5 least-privilege environment/operation/outbox/receipt/head-readiness model và store; chứng minh one-active, exact retry, sequence, atomic start/advance/abort/canonical commit trên PostgreSQL thật.
3. Thêm fixed-topology mTLS INT-04 client, leased dispatcher/status reconciliation và packaged maintenance CLI. Mọi network ở ngoài transaction; resume từ DB sau process restart.
4. Nối STAGE/FENCE, PUB-04 canonical+RELEASE atomic, RELEASE→COMPLETE và precommit ABORT; thêm fault hooks/barriers ở trước/sau từng commit/send/ACK để kiểm hội tụ.
5. Thêm FX-05 impact/withdraw producer-layer cases và explicit R06c CMD-03/04/05 branches/report gates/helper negatives; chạy targeted rồi affected regressions.
6. Freeze candidate, audit AC01–07/DONE-01–05 và cập nhật checkpoint/manifest riêng. Giữ `IN_PROGRESS` khi thiếu bất kỳ required gate; chỉ đưa `REVIEW` khi đủ, không mở BL-R07 hoặc task khác.

## Bảng DoD/evidence hiện hành

| Gate | Trạng thái 28/10/2026 | Evidence / giới hạn |
|---|---|---|
| R06c-AC01 | PASS | CMD-03 `c4962b42-3ed9-435f-ac03-e0cb9cc042ef`: actual PostgreSQL 18.6, 126 unit/bootstrap + 82 database tests; V5 fresh/rerun/rollback, immutable global sequence, exact retry/conflict, least privilege và coordinator two-connection start/lease race đều đạt |
| R06c-AC02 | PASS | CMD-04 `d9c0562c-490b-4dcd-9761-05993a3d9a4b` chạy packaged Content CLI/Learning mTLS, args/config/privacy negatives, participant-down/lease/restart; DATA-14 phủ wrong binding cùng bounded HTTP/TLS faults |
| R06c-AC03 | PASS | DATA-02 `0fc6ba82-49c7-49b8-a3df-a4bbfba3175a`, DATA-03 `07880568-40e8-45cd-a361-e08b5c750536` và DATA-14 `11cb1a3a-5a0f-4a4c-90e7-384899977ad0` chứng minh STAGE/FENCE gate, canonical P khi chưa đúng ACK và stale/wrong receipt không effect |
| R06c-AC04 | PASS | DATA-04 `17edc633-4905-49da-94cb-1d89a24c9ea9` cùng DB release-outbox fault chứng minh canonical P+1 + RELEASE atomic, pending sống qua restart, COMPLETE/readiness chỉ sau RELEASED |
| R06c-AC05 | PASS | DATA-05 `5708adc1-5995-4ebc-aafb-dabb925bed8a` và CMD-03 chứng minh precommit ABORT, postcommit deny, exact immutable-bundle reuse và operation mới tiếp tục global sequence thay vì reset |
| R06c-AC06 | PASS | DATA-06 `6b052dd5-ed8d-49ea-ab63-2085ed34bfc8`, DATA-07 `a2972266-6aee-4f24-bd00-0a0d7b355508`, helper 29/29, strict selectors/reports, artifact/input/dependency/privacy/package/process/listener/cluster audit đều đạt; các case này không thay learner T1/T2/current-overlay owner |
| R06c-AC07 / DONE-01–05 | PASS | Source/contract/runbook/task/registry thống nhất; Control Center review không còn P0/P1 trong supported scope, exact failures/limits/run IDs đã được nhận. Riêng BL-R06c lên `DONE`; BL-R06 parent và downstream giữ `PLANNED` |
| Ready gate | PASS | Goal/output/dependency/scope/invariant/AC/validation, separate-chat mode và quyền CODE đã chốt |

## Checkpoint implementation hiện hành — 25/10/2026

- Đã thêm Content V5 với immutable binding, operation/outbox/receipt/readiness,
  one-active index và trusted `start_publication`, `accept_publication_receipt`,
  `request_publication_abort`; app role chỉ SELECT/EXECUTE, không arbitrary update.
- `PublicationStore` đã nối exact STAGE→FENCE→RELEASE, PUB-04 canonical+RELEASE atomic,
  RELEASED→COMPLETE/readiness và precommit ABORT. Strict INT-04 codec kiểm exact bytes,
  state, operation, revision, digest, realm, cả generation, sequence và command digest.
- Đã thêm fixed-topology mTLS `PublicationPeerClient`, one-step restart-safe dispatcher,
  maintenance CLI start/dispatch/status/abort và owned-lab provision script. Network
  diễn ra sau khi đóng DB read và trước transaction accept receipt.
- Validation PASS: targeted `PublicationCoordinatorWireTests` 2 tests, zero skip;
  actual PostgreSQL CMD-03 qua helper hiện hữu tại
  `artifacts/quality/R06a/51c70db4-337a-4914-9e5a-d13d109c2c96` với 81 database tests,
  126 unit/bootstrap tests, zero failure/skip và owned listener closed. Đây chỉ là
  evidence transaction/coordinator thêm vào R06a runner, chưa phải qualified R06c report.
- Actual PG case chứng minh V5 fresh/rerun, exact phase bytes, precommit ABORT giữ P,
  FENCE fault rollback receipt/head/RELEASE, successful P+1 + RELEASE, readiness chỉ
  advance sau RELEASED, postcommit abort deny và direct operation/receipt write denied.
- Failed diagnostic được giữ: sandbox loopback deny tại run
  `cd01e083-adb4-4923-97eb-9adaed3fbb07`; test-order contamination đã sửa tại run
  `34ab533c-6aeb-488f-b301-d200d122882a`. Không tính hai run này là PASS.
- Còn thiếu: explicit R06c CMD-03/04/05 selectors/report gate; lease/token và status
  reconciliation cho uncertain ACK; actual packaged Content→Learning mTLS/restart/cert/
  timeout/oversize matrix; DATA-02–07/14 impact/withdraw selectors; final artifact/privacy/
  migration-hash review. Vì vậy task giữ `IN_PROGRESS`, chưa freeze và chưa `REVIEW`.
- Bước tiếp theo: thêm lease/status recovery rồi packaged cross-service runtime IT và
  helper selectors; không mở BL-R07, parent hoặc sibling.

## Checkpoint khởi tạo — 25/10/2026

- Session goal: chuẩn bị và khởi động implementation riêng cho BL-R06c; không triển khai BL-R07 hoặc roll up parent BL-R06.
- Task / trạng thái: `BL-R06c · IN_PROGRESS`; dependencies BL-R06a, BL-R06b và BL-R02c đều `DONE` theo registry/current checkpoint.
- Execution role / mode / write scope: Control Center soạn brief và mở implementation chat riêng; executor Content coordinator là writer duy nhất trong task scope cho tới handoff. Control Center chat không viết application code R06c.
- Đã thay đổi ở checkpoint này: tạo brief R06c, registry/F08 link và trạng thái. Chưa sửa application source, migration, contract, fixture, helper, ops runtime hoặc release manifest.
- Acceptance: ready gate đạt; R06c-AC01–07 chưa bắt đầu, không có runtime PASS. R06a/R06b/R02c evidence chỉ là dependency input, không được gắn nhãn thành R06c evidence.
- Validation Control Center: docs-only link/UTF-8/selector audit cần hoàn tất trước handoff; application suite chưa chạy vì implementation chưa bắt đầu.
- Bước tiếp theo: executor triển khai theo plan, ghi evidence thật và giữ `IN_PROGRESS` nếu thiếu DoD; chỉ chuyển `REVIEW` để Control Center nghiệm thu. Không tự mở task kế tiếp.

## Checkpoint Control Center review — 26/10/2026

- Kết quả: **không nhận `REVIEW` hoặc `DONE`**; BL-R06c giữ
  `IN_PROGRESS`, BL-R06 parent và BL-R07 giữ `PLANNED`. Executor cũng đã
  bàn giao trạng thái `IN_PROGRESS`, không có claim hoàn tất bền trong task.
- Evidence đã xác minh: run
  `artifacts/quality/R06a/51c70db4-337a-4914-9e5a-d13d109c2c96` là
  `PASS`, PostgreSQL 18.6, 81 database + 126 unit/bootstrap tests, zero
  failure/error/skip, `clusterStopped=true`; 86/86 captured input hashes còn
  khớp source hiện tại. Report tự ghi scope R06a và loại trừ
  STAGE/FENCE/canonical activation, nên chỉ là diagnostic cho code đã chạy,
  không phải qualified R06c CMD-03. Checkpoint trước ghi 42 integration tests
  không khớp report 81 database tests và phải được sửa ở lần handoff sau.
- P1 / AC01, AC03, AC04 và least privilege: V5 cấp `content_app` quyền
  `EXECUTE` trực tiếp `accept_publication_receipt`, nhưng function chỉ kiểm
  kích thước/regex digest rồi tin `receipt_bytes`, `receipt_sha256` và next
  command do caller truyền. Nó không chứng minh digest khớp bytes hoặc receipt/
  next command bind operation/phase/revision/generation/sequence. Một app
  connection có thể bỏ qua Java codec, gọi STAGE→FENCE→RELEASE bằng payload giả
  và làm canonical head/readiness advance không có durable Learning ACK. Direct
  table-write negatives hiện tại không phủ privileged-function bypass này.
- P1 / AC05: `request_publication_abort` cho phép đổi từ
  `STAGE_PENDING`/`FENCE_PENDING` sang `ABORT_PENDING` và tạo sequence kế
  tiếp ngay cả khi phase trước chưa ACK. `PublicationStore.pending` lại chọn
  undelivered sequence lớn nhất, nên gửi ABORT trước STAGE/FENCE; Learning có
  thể reject gap và operation bị kẹt vĩnh viễn ở `ABORT_PENDING`. Cần resolve
  uncertain prefix bằng exact retry/status rồi mới tạo/gửi ABORT, không skip.
- P1 / AC02–06: lease fields chưa có claim/token/expiry path; peer status chỉ có
  HTTP method nhưng không được verify/reconcile; CLI status gộp
  COMPLETE/ABORTED/missing thành `TERMINAL_OR_IDLE`. Chưa có packaged
  Content→Learning runtime, restart/lost-ACK/cert/timeout/bounds, impact/
  withdrawal và DATA-02–07/14. `scripts/services.ps1` cùng helper chưa có
  R06c selector/named report gate, vì vậy missing/zero/skip vẫn có thể lọt qua
  run đang gắn nhãn R06a.
- P2 / AC07: `PublicationRuntime` đã được R06c sửa nhưng constructor/accessor
  vẫn dồn nhiều declaration/statement trên một dòng, trái rule code dễ đọc.
  Format toàn symbol bị chạm; không reformat file ngoài scope.
- Bước sửa tối thiểu: đóng privileged-function bypass bằng transition/role
  design có binding được DB bảo vệ và negative test gọi trực tiếp bằng app
  role; sửa abort để hội tụ prefix trước khi ABORT; triển khai durable lease và
  strict status reconciliation; tách terminal status thật; thêm explicit R06c
  CMD-03/04/05 + DATA selectors/report-gate negatives, packaged cross-service
  evidence và final artifact/privacy/migration/cleanup review. Chỉ đưa lại
  `REVIEW` sau current-input evidence đạt đủ AC01–07; không mở task kế tiếp.

## Checkpoint sửa finding Control Center — 26/10/2026

- Đã xử lý P1 privileged-function bypass: `accept_publication_receipt` tự parse
  receipt/next-command JSON trong DB và bind đủ operation, phase/state, revision,
  bundle/manifest digest, realm, hai generation, sequence và stored command digest
  trước effect. Actual app-role negative gọi function trực tiếp với receipt giả bị
  từ chối, không advance state/head.
- Đã xử lý nhánh skip-prefix của P1 AC05: abort tại `STAGE_PENDING` chỉ ghi durable
  `abort_requested`; STAGE exact command vẫn phải hội tụ trước, rồi ACK transaction
  mới tạo exact ABORT sequence 2. Abort khi FENCE đã được tạo hoặc sau PUB-04 bị từ
  chối bằng `PUBLICATION_ABORT_PREFIX_OR_COMMIT_DENIED`; không còn chọn ABORT vượt
  STAGE/FENCE prefix.
- Đã format toàn `PublicationRuntime` symbol theo finding P2; không reformat ngoài
  scope. Checkpoint cũ đã sửa số đếm từ 42 integration thành 81 database tests theo
  report Control Center xác minh.
- Validation PASS sau sửa: targeted strict codec 2 tests, zero failure/skip; actual
  PostgreSQL CMD-03 diagnostic qua helper hiện hữu tại
  `artifacts/quality/R06a/a80bedd9-046e-4792-9649-f0a927d6460f`, helper PASS và
  owned listener closed. Failed syntax diagnostic
  `artifacts/quality/R06a/0d4965bd-28f7-40f3-a588-fc0bd42ff365` được giữ, không
  tính PASS.
- Chưa đóng các finding P1 AC02–06 còn lại: durable lease/token/expiry, strict
  status reconciliation, terminal status riêng, packaged Content→Learning runtime,
  explicit R06c CMD-03/04/05 + DATA selectors/report gates và final review. Task
  tiếp tục `IN_PROGRESS`, chưa gửi lại `REVIEW`.

## Checkpoint Control Center re-review — 26/10/2026

- Kết quả: BL-R06c tiếp tục `IN_PROGRESS`; chưa nhận `REVIEW`/`DONE`.
  Formatting của `PublicationRuntime` và trường hợp abort ngay từ
  `STAGE_PENDING` đã được cải thiện, nhưng hai P1 cốt lõi chưa đóng.
- P1 / AC01, AC03, AC04 vẫn mở: DB-side parser chỉ kiểm số lượng 14/12 key,
  không kiểm exact key set và dùng toán tử `<>` với field có thể thiếu. Một
  object đủ 14 key lạ làm các phép so sánh required field trả `NULL`; PL/pgSQL
  `IF NULL` không reject. Test direct-app hiện chỉ thử receipt `{}`, không
  phủ wrong-key-set/null semantics, duplicate key hoặc forged fully-bound
  receipt.
- P1 / least privilege vẫn mở ở tầng trust: kể cả sửa exact key set,
  `content_app` còn quyền gọi SECURITY DEFINER transition và có thể đọc toàn
  bộ binding/command digest để tự dựng receipt đúng cấu trúc. Parse/bind JSON
  không chứng minh receipt đến từ Learning qua mTLS. Cần role/capability riêng
  chỉ coordinator giữ, hoặc authenticated receipt/proof mà DB transition tự
  verify; direct general-app role phải không thể advance canonical/readiness.
- P1 / AC05 vẫn mở: sau STAGE ACK operation là `FENCE_PENDING` nhưng
  `request_publication_abort` từ chối state này. Protocol PUB-07 và AC05 cho
  phép ABORT ở mọi thời điểm trước PUB-04, gồm FENCE chưa gửi hoặc FENCE ACK
  chưa được Content commit. Cần durable abort intent, resolve exact FENCE
  prefix nếu đã tạo/sent, rồi FENCE receipt phải tạo ABORT thay vì canonical
  commit khi intent đã tồn tại.
- Evidence mới
  `artifacts/quality/R06a/a80bedd9-046e-4792-9649-f0a927d6460f` có report
  PASS 81 database + 126 unit/bootstrap, zero failure/error/skip và cluster
  stopped, nhưng captured input không còn current: `PublicationRuntime.java`
  hash khác snapshot. Report vẫn mang scope R06a loại trừ canonical activation
  và không có R06c named gate, nên không qualify bất kỳ AC R06c nào.
- Các finding lease/token/expiry, status reconciliation/terminal state,
  packaged cross-service runtime, DATA-02–07/14, explicit CMD-03/04/05 gates
  và final audit vẫn giữ nguyên. Bước tiếp theo là sửa ba P1 trên bằng negative
  cases đúng threat/boundary, rồi hoàn thiện các lớp còn thiếu và rerun
  current-input R06c evidence; không mở task kế tiếp.

## Checkpoint Control Center review 3 — 26/10/2026

- Kết quả: BL-R06c vẫn `IN_PROGRESS`; không nhận `REVIEW`/`DONE`.
- Finding AC05 về phạm vi precommit abort đã được sửa **ở mức source**:
  `FENCE_PENDING` nay ghi durable `abort_requested`; exact FENCE receipt
  tạo ABORT sequence tiếp theo, không advance canonical head, rồi ABORTED giữ P.
  Test source có case STAGE→FENCE_PENDING→abort intent→FENCED receipt→ABORT và
  kiểm canonical vẫn P. Finding này chỉ được đóng hoàn toàn sau actual
  current-input PG + packaged lost-ACK/restart/status evidence.
- P1 AC01/03/04 vẫn mở: receipt/next-command parser còn dùng key-count thay vì
  exact key set và phép so sánh nullable, nên wrong-key object có thể lọt qua
  PL/pgSQL `IF NULL`. Không có negative cho wrong-key/null/duplicate semantics.
- P1 least privilege vẫn mở: general `content_app` còn `EXECUTE`
  `accept_publication_receipt` và đọc được toàn bộ binding/digest để tự dựng
  fully-bound receipt. DB-side structural validation không tạo provenance từ
  Learning. Chưa có coordinator-only role/capability hoặc authenticated proof
  và direct-general-app deny.
- Không có evidence mới sau sửa. Run gần nhất
  `a80bedd9-046e-4792-9649-f0a927d6460f` hiện lệch 4/86 captured inputs:
  `PublicationRuntime.java`, `PublicationStore.java`, V5 coordinator SQL và
  `DatabaseIsolationIT.java`. Report vẫn là scope R06a, không phải R06c gate.
- Durable lease/token/expiry, strict status reconciliation/terminal status,
  packaged Content→Learning mTLS/restart/fault matrix, DATA-02–07/14, explicit
  R06c CMD-03/04/05 report gates và final artifact/privacy/migration/cleanup
  review vẫn thiếu. Không mở task kế tiếp.

## Checkpoint sửa review 3 — 26/10/2026

- Đã thay parser key-count-only bằng exact required-key set + count và
  `COALESCE(..., true)` fail-closed cho nullable comparison ở receipt và next
  command. Actual negative dùng object 14 wrong keys với capability đúng bị từ
  chối trước effect; general-app capability sai cũng bị từ chối.
- Đã thêm coordinator capability boundary: V5 environment chỉ lưu SHA-256;
  transition tự verify raw capability bằng PostgreSQL `sha256(bytea)`. General
  `content_app` có thể đọc binding/digest và có EXECUTE grant nhưng không có raw
  capability thì không advance state/head/readiness. `PublicationStore` transition
  fail closed khi thiếu capability; runtime chỉ đọc bounded capability file trong
  owned trust directory, không nhận qua CLI argv.
- AC05 source + actual PG hiện phủ cả hai precommit vị trí: abort intent ở
  `STAGE_PENDING` resolve STAGE rồi ABORT; ở `FENCE_PENDING` resolve exact FENCE
  rồi ABORT, không PUB-04 và canonical vẫn P. Sau PUB-04 abort tiếp tục bị deny.
- Validation current-input: targeted strict codec 2 tests PASS, zero skip; actual
  PostgreSQL helper PASS tại
  `artifacts/quality/R06a/d778b6d0-8f31-4e26-b124-7fde46cb2c8c`, owned listener
  closed. Run vẫn mang scope R06a nên chỉ là diagnostic, chưa qualify R06c AC.
- Còn thiếu duplicate-key DB bypass negative (Java strict codec đã reject nhưng
  capability boundary là authority chính), capability file/package/privacy audit,
  durable lease/status reconciliation, packaged cross-service runtime, explicit
  R06c CMD-03/04/05 + DATA selectors và final review. Task giữ `IN_PROGRESS`.

### Bổ sung capability null-semantics — 26/10/2026

- Capability checks của cả ba trusted transitions dùng
  `NOT COALESCE(length-and-sha256-match, false)`, nên SQL `NULL`, missing
  environment, capability ngắn/dài hoặc hash sai đều fail closed; không còn
  lặp lại lỗi three-valued logic mà receipt parser vừa được sửa.
- Actual app-role negatives hiện phủ capability sai, capability `NULL` và object
  đủ 14 wrong keys với capability đúng. Current-input PostgreSQL diagnostic PASS
  tại `artifacts/quality/R06a/9e7b7a12-f9b8-4a5d-b91a-a68fa2ef8565`;
  helper xác nhận zero failure/error/skip và owned listener closed. Scope report
  vẫn là R06a, chưa thay explicit R06c gate.

## Checkpoint Control Center review 4 — 26/10/2026

- Kết quả: BL-R06c tiếp tục `IN_PROGRESS`; không nhận `REVIEW`/`DONE`. Exact
  required-key set, nullable capability fail-closed và hai vị trí precommit abort
  đã có source + actual PostgreSQL diagnostic phù hợp, nên các finding tương ứng
  của review 3 được đóng trong phạm vi diagnostic hiện tại.
- Evidence `artifacts/quality/R06a/9e7b7a12-f9b8-4a5d-b91a-a68fa2ef8565`
  là PASS với 81 database + 126 unit/bootstrap tests, zero failure/error/skip,
  `clusterStopped=true`; Control Center đối chiếu 86/86 captured input hashes còn
  khớp source hiện tại. Report tự ghi scope R06a và loại trừ STAGE/FENCE/canonical
  activation, nên không qualify R06c CMD-03/04/05 hoặc AC03–06.
- P1 / AC01, AC03, AC04 mới: ba trusted SQL transition chỉ kiểm digest có 64 ký
  tự hex, không tự kiểm digest bằng SHA-256 của bytes nhận vào. `start_publication`
  có thể lưu STAGE `command_bytes` và `command_sha256` không cùng identity;
  `accept_publication_receipt` có thể lưu receipt digest sai và tạo next outbox
  với digest không khớp command bytes. Java path hiện tính đúng nhưng database
  đang sở hữu atomic state/outbox/receipt invariant, còn capability chỉ authorize
  caller chứ không chứng minh payload integrity. Cần fail closed bằng
  `encode(sha256(bytes), 'hex')` cho start command, receipt và next command, cộng
  direct-transition negatives chứng minh mismatch rollback không effect.
- P2 / maintainability: `request_publication_abort` nhận `p_command_bytes` và
  `p_command_sha256`, Java còn tạo exact ABORT command để truyền vào, nhưng SQL
  không đọc hai tham số này và chỉ ghi `abort_requested`. Nếu ABORT command chỉ
  được materialize sau khi prefix receipt hội tụ thì bỏ tham số/dead computation;
  nếu chúng là identity của durable intent thì phải validate và lưu rõ, tránh API
  tạo cảm giác đã bind command trong khi thực tế chưa bind.
- Các blocker DoD đã biết vẫn giữ nguyên: chưa có durable lease/token/expiry,
  strict status reconciliation và terminal status riêng; chưa có packaged
  Content→Learning mTLS/restart/fault matrix, DATA-02–07/14, explicit R06c
  CMD-03/04/05 selector/report gates, artifact/privacy/package audit hoặc final
  manifest/review. Không mở BL-R07, parent hoặc task kế tiếp.

## Checkpoint sửa review 4 — 26/10/2026

- V5 trusted transitions giờ tự kiểm
  `encode(sha256(bytes), 'hex')` bằng PostgreSQL cho initial STAGE command,
  receipt và next phase command trước insert/effect. Regex/length không còn được
  dùng thay payload-integrity invariant.
- Actual direct-transition negatives truyền capability đúng nhưng digest sai ở
  từng vị trí STAGE/receipt/next-command; mỗi case bị từ chối và transaction
  rollback trước coordinator/head/readiness effect. Happy path vẫn dùng exact
  Java-generated bytes/digest.
- Đã bỏ dead `p_command_bytes`/`p_command_sha256` khỏi
  `request_publication_abort`; Java không materialize ABORT sớm. Function chỉ
  ghi durable intent bằng `operationId + coordinator capability`; exact ABORT
  command được tạo sau khi current prefix receipt hội tụ.
- Validation current-input PASS: targeted strict codec 2 tests, zero skip; actual
  PostgreSQL diagnostic tại
  `artifacts/quality/R06a/450d92b1-56ba-4ecb-8481-c28489b73501`, helper PASS và
  owned listener closed. Report vẫn mang scope R06a, chưa qualify R06c gate.
- Blocker còn lại không đổi: durable lease/token/expiry, strict status reconcile +
  terminal state, packaged Content→Learning mTLS/restart/fault matrix, explicit
  R06c CMD-03/04/05 + DATA selectors/report gates và final audit. Task giữ
  `IN_PROGRESS`.

### Bổ sung lease và status recovery — 26/10/2026

- V5 có trusted `claim_publication_delivery`: DB ghi lease token + DB-clock expiry
  trên exact undelivered phase; token khác không takeover trước expiry. Receipt
  transition yêu cầu đúng token còn hạn, còn exact receipt retry sau terminal
  transition vẫn hội tụ từ stored receipt.
- Dispatcher claim lease trước network, giữ exact outbox bytes, và nếu PUT có
  uncertain outcome thì GET private INT-04 status; status bytes chỉ được accept
  qua cùng strict receipt binding với exact pending command. Network/status poll
  vẫn ngoài Content transaction/row lock.
- CLI status không còn gộp COMPLETE/ABORTED/missing: missing trả
  `PUBLICATION_NOT_FOUND`; operation tồn tại trả exact durable state, phase,
  parent/target và abort intent, không in payload/private expected.
- Fault test reuse cùng durable lease token qua injected PUB-04 rollback; claim
  token mới bị conflict thay vì chiếm sớm. Diagnostic đầu
  `artifacts/quality/R06a/a7caf33a-190f-4ed4-b4c0-5073b822972f` giữ failure do
  test thử claim token mới; sau sửa, current-input PostgreSQL helper PASS tại
  `artifacts/quality/R06a/913fa881-97c3-4336-a073-bf80c5359ef8`, owned listener
  closed. Scope vẫn R06a diagnostic, chưa phải R06c qualification.
- Còn thiếu packaged cross-service proof cho lost-ACK/status/restart/expiry,
  explicit R06c selectors/report gates, DATA-02–07/14 và final audit; task giữ
  `IN_PROGRESS`.

## Checkpoint Control Center review 5 — 26/10/2026

- Kết quả: BL-R06c tiếp tục `IN_PROGRESS`; không nhận `REVIEW`/`DONE`. Finding
  digest/bytes và dead ABORT parameters của review 4 đã được sửa đúng ở source;
  direct-transition negatives hiện phủ mismatch ở initial command, receipt và
  next command.
- Evidence `artifacts/quality/R06a/913fa881-97c3-4336-a073-bf80c5359ef8`
  là PASS với 81 database + 126 unit/bootstrap tests, zero failure/error/skip,
  `clusterStopped=true`; Control Center đối chiếu 86/86 captured input hashes còn
  khớp source hiện tại. Report vẫn tự giới hạn scope R06a và loại trừ
  STAGE/FENCE/canonical activation, nên chỉ là current-input diagnostic.
- P1 / AC01, AC02, AC04, AC05: lease transition chưa fail closed với token
  `NULL`. `claim_publication_delivery` không yêu cầu non-null token, nên có thể
  ghi `lease_token=NULL` cùng `lease_until` tương lai. Tệ hơn, active receipt
  check dùng `lease_token IS DISTINCT FROM p_lease_token OR lease_until < now`;
  khi stored/provided token đều `NULL` và `lease_until` cũng `NULL`, biểu thức là
  `false OR NULL`, làm PL/pgSQL `IF NULL` không reject. Vì vậy caller có đúng
  coordinator capability có thể đưa valid receipt qua transition mà chưa claim
  lease. Cần yêu cầu token và expiry non-null cho active transition, kiểm expiry
  fail closed, giữ exact terminal retry riêng; thêm direct negatives cho no claim,
  null/wrong/expired token và chứng minh không có state/head/outbox effect.
- Status recovery source dùng GET result qua cùng strict 14-field binding với
  exact pending command; CLI đã tách missing/terminal/pending state. Phần này hợp
  lý ở mức source nhưng chưa có packaged Content→Learning lost-ACK/status/restart/
  expiry evidence, nên chưa đóng AC02/04/05.
- Các blocker qualification còn nguyên: chưa có explicit R06c CMD-03/04/05
  selectors/report gates, DATA-02–07/14, packaged cross-service fault matrix,
  capability/privacy/package/migration/cleanup audit hoặc final manifest/review.
  Không mở BL-R07, parent hoặc task kế tiếp.

## Checkpoint sửa review 5 và mở CMD-03 R06c — 27/10/2026

- Finding P1 lease `NULL` đã sửa fail closed trong V5: claim yêu cầu token khác
  `NULL`; active receipt yêu cầu provided/stored token và expiry khác `NULL`, token
  khớp và expiry còn hiệu lực theo DB clock. Exact terminal receipt retry vẫn đi
  qua nhánh stored-receipt riêng, không phụ thuộc lease đã clear.
- Actual PostgreSQL test bổ sung direct negatives cho receipt chưa claim, claim
  token `NULL`, token sai và token hết hạn; mỗi case chứng minh operation vẫn
  `STAGE_PENDING`, chưa có receipt/FENCE outbox và canonical head vẫn P. Lease mới
  sau expiry tiếp tục exact prefix thành công.
- Đã thêm explicit `R06c` CMD-03 branch trong `scripts/services.ps1` và
  `scripts/db_lab.py`. CMD-04/05 cố ý fail closed cho tới khi có packaged
  coordinator suite; không cho nhãn R06c chạy qua generic trust/fault suite.
  Named report gate yêu cầu exact coordinator case, reject missing/zero/skip/
  duplicate/hidden failure/unexpected invocation. Helper XML/command doubles PASS
  13 cases tại `artifacts/quality/R06c/helper-coordinator-tests.json`; đây chỉ là
  helper evidence, không thay actual DB/mTLS.
- Current-input R06c CMD-03 PASS tại
  `artifacts/quality/R06c/967beaf8-787e-4fac-8f45-894d78b0109b`: 126
  unit/bootstrap + 81 database tests, zero failure/error/skip,
  `clusterStopped=true`. Report capture 94 inputs, recheck 0 mismatch; retained
  Content artifact SHA-256
  `0e215f12950e202b82cb59c64958e79feb561d602050d0cba73b2013073ce350`.
  V1–V4 hashes bằng qualified R06a input; retained JAR không chứa capability file,
  private key hoặc secret entry.
- Diagnostic fail trước PASS được giữ tại
  `artifacts/quality/R06a/0a7935b1-fd2f-4345-93e0-f6a93e4ef2c6`: fixture
  next-command digest trước đây dùng lease `NULL`, nay bị lease gate chặn đúng;
  fixture đã đổi sang valid lease rồi PASS. Không tính run fail là evidence.
- Còn thiếu packaged Content→Learning CMD-04/05, lost request/ACK/status/restart/
  expiry/cert/timeout/truncated/oversize/malformed matrix, DATA-02–07/14 và
  impact/withdraw producer layer. Chưa cập nhật final release manifest, chưa đủ
  AC02–07; task giữ `IN_PROGRESS`, chưa chuyển `REVIEW` và không mở BL-R07.

## Checkpoint executor freeze và bàn giao REVIEW — 28/10/2026

- Task / trạng thái: `BL-R06c · REVIEW`. Executor đã hoàn tất AC01–07 và
  DONE-01–05 trong supported lab scope; không chuyển `DONE`, không roll up BL-R06
  và không mở BL-R07/downstream. `publicationCoordinatorCandidate` trong release
  manifest cố ý vẫn `IN_PROGRESS`: field đó giao Control Center quyền nhận REVIEW,
  ghi final evidence pointers và chuyển trạng thái sau nghiệm thu.
- Source thay đổi cuối: Content V5/store/wire dùng delivery sequence global bền;
  start/next command tăng cursor atomically và exact retry giữ immutable prefix.
  DB test thêm two-connection exact-start/one-lease race. Packaged coordinator CLI
  thêm negative args/config/privacy; test driver chỉ retry một lần sau uncertain
  redacted error và test barrier expire durable lease, còn repeated fault vẫn fail.
- Final-current CMD-03: `c4962b42-3ed9-435f-ac03-e0cb9cc042ef`, PostgreSQL
  18.6, 126 unit/bootstrap + 82 database = 208 tests, 0 failure/error/skip,
  `clusterStopped=true`, 95/95 captured input hashes current.
- Final-current CMD-04: `d9c0562c-490b-4dcd-9761-05993a3d9a4b`, exact
  `packagedCoordinatorRecoversParticipantDownLeaseAndCompletesExactRelease`, 2
  coordinator wire + 1 runtime test và Learning peer 153 tests, tất cả 0
  failure/error/skip; `clusterStopped=true`, 261/261 input hashes current.
- Final-current CMD-05: DATA-02 `0fc6ba82-49c7-49b8-a3df-a4bbfba3175a`,
  DATA-03 `07880568-40e8-45cd-a361-e08b5c750536`, DATA-04
  `17edc633-4905-49da-94cb-1d89a24c9ea9`, DATA-05
  `5708adc1-5995-4ebc-aafb-dabb925bed8a`, DATA-06
  `6b052dd5-ed8d-49ea-ab63-2085ed34bfc8`, DATA-07
  `a2972266-6aee-4f24-bd00-0a0d7b355508` và DATA-14
  `11cb1a3a-5a0f-4a4c-90e7-384899977ad0`. Mỗi run có đúng 2 wire + 1 named
  runtime test, Learning peer 153 tests, 0 failure/error/skip,
  `clusterStopped=true` và 261/261 input hashes current.
- Helper `artifacts/quality/R06c/helper-coordinator-tests.json` PASS 29/29,
  3/3 script hashes current. Retained Content/Learning JAR hashes khớp report ở
  cả chín run; package scan 506 entries không có test/fixture/key/capability.
  Evidence privacy scan không thấy private sentinel/capability/private key/
  password/`SQLException`. V1–V4 SHA-256 khớp qualified R06a; offline runtime
  dependency tree chỉ có stack đã pin, không dependency mới. Sau audit không còn
  Java/PostgreSQL/psql process hoặc owned listener 8080/8443/18083/18443.
- Diagnostic failures được giữ, không tính PASS: `9fef1b74-...` bắt lỗi assertion
  so identity của `byte[]`; `38437c63-...` bắt JVM NOTE trước fixed CLI error;
  `af730901-...`/`87cbe2c7-...` dẫn tới recovery-aware exact retry; DATA-03
  `1f2a513c-...` gặp bounded startup timeout rồi run final-current đạt. Không bỏ
  selector, required case hoặc nới product timeout để làm xanh.
- Giới hạn: DATA-02/06/07 ở leaf này chỉ chứng minh coordinator/exact manifest và
  actual Learning publication gate; không chứng minh learner T1/T2, current
  progress overlay, browser/CMS/admin, production deploy/secret/restore hoặc
  transaction xuyên DB.

## Checkpoint Control Center nghiệm thu DONE — 28/10/2026

- Control Center đã đối chiếu lại source trọng yếu của coordinator/store/V5 và
  DATA-14. Lease/token fail closed; sai realm/generation/digest/sequence và các
  response not-found/unavailable/malformed/oversize/truncated/slow không advance
  state hoặc canonical head. Không còn finding P0/P1 trong supported scope.
- Đã nhận AC01–07 và DONE-01–05 từ candidate freeze: CMD-03
  `c4962b42-3ed9-435f-ac03-e0cb9cc042ef`, CMD-04
  `d9c0562c-490b-4dcd-9761-05993a3d9a4b`, cùng DATA-02
  `0fc6ba82-49c7-49b8-a3df-a4bbfba3175a`, DATA-03
  `07880568-40e8-45cd-a361-e08b5c750536`, DATA-04
  `17edc633-4905-49da-94cb-1d89a24c9ea9`, DATA-05
  `5708adc1-5995-4ebc-aafb-dabb925bed8a`, DATA-06
  `6b052dd5-ed8d-49ea-ab63-2085ed34bfc8`, DATA-07
  `a2972266-6aee-4f24-bd00-0a0d7b355508` và DATA-14
  `11cb1a3a-5a0f-4a4c-90e7-384899977ad0`.
- Chín report đều `PASS`, zero failure/error/required skip,
  `clusterStopped=true`; captured inputs tại freeze là 95/95 cho CMD-03 và
  261/261 cho từng CMD-04/05. Helper report `PASS` 29/29 với 3/3 script hashes
  current. Retained artifact/package, dependency, privacy, migration và cleanup
  audit đã được đối chiếu theo checkpoint executor.
- Release manifest đã nhận exact DB/contract/DATA/helper pointers và
  `requiredSkips: 0`. Phạm vi qualification vẫn chỉ là lab coordinator và exact
  manifest/publication gate đã ghi; không suy thành learner T1/T2/current progress,
  browser/CMS/admin, production deploy/restore hoặc transaction xuyên database.
- Hậu kiểm sau nghiệm thu cho thấy cả chín retained input manifests chỉ lệch đúng
  `ops/release/dependencies.json`, do Control Center đổi field riêng của R06c từ
  executor candidate sang qualified `DONE` và thêm evidence pointers. Mọi input
  source, test, script, contract và migration còn lại vẫn khớp; đây là delta
  metadata hậu nghiệm thu đã được ghi rõ trong `qualifiedBaseline`, không phải
  application change và không cần chạy lại suite.
- Registry chuyển riêng `BL-R06c` sang `DONE`. `BL-R06` và `BL-R07` vẫn
  `PLANNED`; Control Center không mở task kế tiếp.
