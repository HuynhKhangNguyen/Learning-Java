# BL-R08 — Learning allocation và exposure trước disclosure

[Registry](../../../delivery-backlog.md) · [Work contract](../data-contract.md) ·
[Work LLD](../lld.md) · [API boundaries](../../../architecture/service-boundaries.md) ·
[Lifecycle/exposure protocol](../../../architecture/lifecycle-recovery-protocol.md)

## Brief thực thi — 03/11/2026

- Trạng thái hiện hành: `BLOCKED` theo review candidate `07372a1`; chưa READY vì thiếu admission
  API-L01/L02 và allocation metadata contract trong verified replica. Xem checkpoint cuối để gỡ.
- Quyền CODE: người dùng yêu cầu task kế tiếp sau nghiệm thu R28a. Chỉ giao leaf BL-R08.
- Dependencies: BL-R04d, BL-R06c, BL-R28a đều DONE. Không cần chờ production provider R28b để
  implement/qualify local lab; production assessment chưa được qualification từ evidence này.
- Goal: Learning cấp attempt do server chọn case/mode/version, giữ owner/epoch và exposure floor;
  API-L01/L02 chỉ tiết lộ assigned prompt khi JRN-01 có verified receipt và auth/publication recheck
  đạt. Cùng request retry không tạo attempt hoặc exposure operation mới.
- Mode B: một chat Backend/Platform implementation riêng; Control Center giữ brief/review/nghiệm thu.
  Không dùng subagent, không tự tạo chat. Không mở R09/R10/R11 hoặc sibling khác.
- Git: nhánh `codex/bl-r08-learning-allocation`, base `41aff92`. Đây là stacked branch trên R28a/R07
  chưa merge integration branch. Theo Git workflow thường trực, mỗi lượt implement/fix validation xong
  tự tạo commit mới rồi bàn giao; không amend candidate đã review. Commit không tự đổi task thành DONE.

## Context phải đọc và source liên quan

Đọc registry → file này → checkpoint cuối. Chỉ mở phần được dẫn và symbol liên quan:

- Work contract: mục2–5, LD-01/02 và retry/owner/pending trong mục9; work LLD phần API/lock order.
- API boundaries: API-L01/L02 và API-L08 chỉ cho lookup receipt allocation khi cần; không triển khai
  toàn bộ API-L03–16.
- Lifecycle/recovery protocol mục1–2: JRN-01, pending/outbox trước external append và recheck trước
  disclosure; data protocols phần lock order/publication cùng session protocol admission/fence.
- [Collections cases](../../f04-assessment/collections/cases.md) mục7 finite clean pool;
  [publication protocol](../../f08-content/publication/protocol.md) phần current policy/fence.
- Learning `identityaccess/{LearningAccess,AdmissionGate,AdmissionRuntime,AdmissionWire}.java`,
  `contentreplica/{PublicationStore,PublicationGate,ReplicaValidator,PublicationConfiguration}.java`,
  `journal/{JournalStore,FilesystemJournalStore}.java`, các tests tương ứng và migration V1–V4.
- [Quality process](../../../quality-process.md), [command/case mapping](../../../delivery/test-release-plan.md)
  phần CMD-02–06, DATA-22 và owner isolation; helpers DB/trust/fault/journal hiện có khi cần nối command.

Không import Java giữa services hoặc tạo shared runtime/domain JAR. Không sửa topology ba services.
Chỉ dùng verified released Learning replica làm nguồn private case; không gọi Content qua network
trong transaction và không lấy fixture hardcoded thay canonical/pinned replica trong runtime.

## Scope và write scope

Một đầu ra chính là allocation backend API/store/coordinator với evidence thực:

- Learning `src/main/java/lab/backend/learning/work/**`, `exposure/**` và tests cùng owner packages.
- Migration mới dưới Learning `src/main/resources/db/migration/`; không sửa checksum V1–V4.
  Tạo learner guard/epoch, attempt, draft revision0, exposure/reservation, allocation receipt và
  pending journal operation/outbox tối thiểu; không tạo submission/grading/progress implementation.
- Minimal integration trong Learning `identityaccess/**`, `contentreplica/**`, `journal/**`,
  `transport/**`, `LearningApplication.java`, service config/POM và tests nếu symbol thực sự cần nối
  API/admission/transaction; không redesign dependency đã nghiệm thu.
- `contracts/http/learning/public/**`, `contracts/fixtures/exposure/**`, `ops/learning/**`.
- `scripts/services.ps1`, helper DB/trust/fault hiện có và helper/test R08 mới cần cho CMD-03/04/05;
  journal helper chỉ đổi tối thiểu khi cần binding R08, giữ R28a qualification nghĩa cũ.
- Task này, registry dòng/link R08 và F11 README/owner docs cần đồng bộ. Release dependency metadata
  chỉ cập nhật hashes/evidence thực khi existing packaging thay đổi, không upgrade pin/dependency.

Ngoài scope: save draft CAS (R09), hint/solution support (R10), submit/T1 (R11), grader/job/T2,
abandon workflow, frontend, import/reset/delete/restore, S3/provider/cloud/deploy, case authoring hoặc
tự sinh family mới. Không viết handler submit giả để báo đã chứng minh T1: R08 chỉ cung cấp invariant/
guard cho task R11 consume và test guard từ actual allocation state.

## Invariants và quyết định thực thi

1. Owner lấy từ authenticated admission. Mutation kiểm context/CSRF/Origin theo existing boundary;
   lookup/retry vẫn phải auth/owner. Client không được chọn case sạch, owner, epoch, grader/rubric
   version hay passed. Client có thể yêu cầu exercise/mode hợp lệ; server kiểm và quyết định mode
   effective từ content/policy. Practice không dùng private assessment fixtures để tạo clean bypass.
2. Lock order: auth_account_gate → learner_guard → shared publication gate → resource/receipt.
   Chốt migration/SQL lock cùng Connection; process-local PublicationGate không đủ chứng minh
   nhiều replica. Shared/exclusive DB coordination phải tương thích FENCE/RELEASE hiện có.
3. Reserve case dưới learner guard, pin bundle/case/rubric/input versions và eligibility-at-assignment.
   Cùng scope owner/assessment group không cấp hai clean assignments tranh nhau; retry/resume trả
   assignment đã có hoặc conflict đúng owner, không tiêu thụ family clean khác để lách guard.
4. Exposure monotonic theo owner/family/exposureGroup/equivalence từ trusted content, không theo
   tên biến hoặc client. Encounter lần allocation đầu ảnh hưởng attempt tương lai, không tự làm mất
   eligibility độc lập của chính assignment vừa cấp. Không có mutation xóa floor khi reset/logout.
5. Local tx ghi preparing + pending exposure/outbox/receipt trước; journal append/read-back ngoài
   mọi DB/domain locks. Worker/pump chỉ chuẩn bị durable state, không tự gửi prompt cho browser.
   Bound retry dùng same operation/record bytes, timeout không là ACK; restart tiếp tục từ durable DB.
6. Journal receipt bind đúng operation/owner/family/version/digest và covered prefix; receipt sai,
   thiếu hoặc corrupt giữ pending/unavailable. Final local tx/retrieval dùng admission còn valid,
   current context/epoch/account status và current publication policy. Auth refresh ở ngoài locks.
7. Preparing/status/error không có prompt, input case private, answer/expected/rubric/hints/solution.
   Ready DTO chỉ allowlist learner-visible assigned prompt/input/schema/metadata. Authenticated
   response no-store; sentinel scan HTTP/log/artifact chống lộ private expected.
8. Idempotency key scope owner+operation, fingerprint canonical bind exercise/requested mode và
   business inputs, không gồm cookie/CSRF. Same key/same payload hội tụ; different payload409;
   concurrent keys vẫn qua assignment guard. CASE_EXHAUSTED không sinh case/shuffle giả clean,
   không cấp pass và không overwrite good work.

## Acceptance

| ID | Điều kiện đạt và lớp bằng chứng |
|---|---|
| R08-AC01 | Migration fresh/rerun/checksum/rollback và owner roles PostgreSQL thật; constraints owner/epoch, một assignment trong guard scope, immutable pinned refs và unique allocation receipt. Seed đúng published replica, không hardcode runtime case. |
| R08-AC02 | API-L01 create và L02 resume/read chạy packaged Learning với admission thật; attempt+draft revision0 đúng owner/version; anonymous, forged owner/mode/case/version/context, B đọc ID A bị từ chối. DTO/schema/error bigint conventions, CSRF/Origin và no-store đúng contract. |
| R08-AC03 | Hai DB connections/replicas có deterministic barrier chứng minh một clean assignment; same-key retries và different-key race không tạo duplicate/tiêu thụ thêm family. Same key khác payload409; finite pool exhausted/withdrawn/fenced không fake clean. First assignment không bị self-encounter downgrade; future attempt đã known bị chặn độc lập. |
| R08-AC04 | Actual local filesystem journal JRN-01 và own PG chứng minh preparing→append/verified receipt→ready; fault trước append, lost ACK, crash sau ACK trước DB final, DB rollback và restart retry hội tụ. Journal I/O không giữ DB locks; preparing/missing/wrong receipt không disclosure và guard không cho future T1 consume. |
| R08-AC05 | Revoke/context expiry/credential floor, epoch/pending/deleted guard và FENCE/RELEASE/withdraw tranh journal/final/read có controlled ordering. Changed gate trước final snapshot thì deny/pending, không leak prompt; snapshot đã commit trước gate tuân protocol linearization. Không dùng fixture thay whole reset/delete/coordinator production claim. |
| R08-AC06 | Required suites/cases có tên, nonzero/no failure/error/skip, current inputs/JAR/raw XML/report hashes và cleanup thật. CMD-03 Learning, CMD-04 packaged API, CMD-05 DATA-22 R08 subset và CMD-06 lab đạt. Missing case/suite/stale input/unknown selector phải nonzero. |
| R08-AC07 | Affected CMD-02/package/regressions cho admission/publication/journal còn đạt; review privacy/artifact/format/comment/transaction paths không P0/P1 mở. Registry/checkpoint chỉ ghi evidence thật; mỗi lượt bàn giao có candidate commit. |

## Validation và Definition of Done

Executor mở rộng helper theo selector explicit `-EvidenceTask R08` (hiện chưa có, phải implement):

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service learning -EvidenceTask R08 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service learning -EvidenceTask R08 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service learning -Case DATA-22 -EvidenceTask R08 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline
```

DATA-22 ở đây chỉ allocation R08; R10/R27 vẫn chưa nghiệm thu. CMD-06 R28a PASS riêng không chứng
minh R08 DB→journal→disclosure; CMD-05 phải chạy actual allocation end-to-end ở packaged service.
CMD-07 browser/edge chưa có R31a nên không claim user journey E2E; packaged HTTP/TLS evidence vẫn
bắt buộc. Chạy affected selected CMD-02 và helper tests theo chất lượng thực tế, không dùng skip.

Reports dưới `artifacts/quality/R08/<runId>/`: snapshot full affected input inventory trước/sau,
raw required XML/logs redact, runtime/commands/exit/cases, JAR hashes và dependency/cleanup bindings.
Test doubles chỉ kiểm runner/report gates; không thay actual PG, packaged auth hoặc journal proof.
Giữ failed diagnostics và không rewrite history. Dừng mở rộng validation khi required checks đủ và
không có concern mới; không chạy toàn repo vô điều kiện.

DONE-01: AC01–05 có implementation và evidence đúng lớp; DONE-02: strict command/current-candidate
bindings/cleanup AC06; DONE-03: AC07 review/regression/privacy đạt; DONE-04: owner docs/task/registry
đồng bộ và limits đúng; DONE-05: staged review và candidate commit đúng Git workflow. Chỉ Control
Center nghiệm thu DONE. Executor đã validation/commit và bàn giao chờ review thì đổi registry và
checkpoint sang NEED_REVIEW; còn implementation/validation/fix thì IN_PROGRESS, ghi AC thiếu và bước
tiếp theo chính xác. Review yêu cầu sửa thì về IN_PROGRESS, fix xong lại commit và NEED_REVIEW.

## Checkpoint mở task — 03/11/2026

- Control Center xác nhận dependencies DONE, BL-R08 READY. Registry vẫn PLANNED, chưa code/validation
  runtime của R08. Branch đã tạo từ reviewed base41aff92, stacked trên R28a/R07.
- Lượt này chỉ tạo brief và link registry/F11 README, kiểm docs-only link/traceability/diff; không có
  implementation claim. Executor cần kiểm HEAD thật trước sửa, chuyển IN_PROGRESS rồi thực hiện AC.
- Bàn giao phải ghi candidate/parent/branch, file đổi, AC còn thiếu, exact runs/hashes/limits/findings.
  Lượt fix sau review tạo commit mới trên cùng branch; không mở task tiếp theo.

```text
Thực hiện BL-R08 theo docs/features/f11-learner-work/tasks/BL-R08.md và registry/checkpoint mới nhất.
Mục tiêu: server allocation API-L01/L02, pinned owner/case/version và verified JRN-01 trước prompt
disclosure, đạt AC01–07 trong local lab. Chỉ sửa write scope trong brief trên branch
codex/bl-r08-learning-allocation. Triển khai, validation và checkpoint; tự commit mỗi lượt implement/
fix sau validation theo Git workflow. Bàn giao candidate chờ review thì NEED_REVIEW; còn việc thì
IN_PROGRESS. Chỉ Control Center nghiệm thu mới DONE. Không mở task khác.
```

## Checkpoint executor — 03/11/2026 — `IN_PROGRESS`, dependency contract blocker

- Git audit: branch `codex/bl-r08-learning-allocation`, reviewed stacked base `41aff92`, task-doc
  parent `5e29ac8`; working tree sạch trước lượt. Executor đã đọc registry, checkpoint,
  Git/quality mapping và chỉ source do brief dẫn. Chưa có implementation runtime R08.
- Blocker admission: canonical Identity provider hiện exact-allowlist caller Learning thành
  action `LEARNING_DRAFT_SAVE`, method `PUT`, route `/api/v1/drafts/{uuid}` trong
  `CanonicalAdmissionWire.parse`. API-L01 cần `POST /api/v1/attempts`, API-L02 cần authenticated
  `GET /api/v1/attempts/{attemptId}`; cả hai shape/action chưa được provider đăng ký. Write
  scope R08 chỉ cho minimal integration phía Learning, không cho sửa Identity owner. Dùng
  draft-save admission cho allocation sẽ sai action/route binding và không đạt AC02/AC05.
- Blocker content: verified Learning replica V4/private-case-v2 chỉ chấp nhận exact fields
  `schemaVersion,id,unitId,expected,rubricTargets`; publication manifest chỉ có policy theo
  capability/version. Replica không có exercise, mode policy, family/exposureGroup/equivalence,
  caseVersion, learner-visible prompt/input/schema hoặc rubric/grader version để chọn và ghim.
  Brief cấm hardcode runtime case và cấm gọi Content trong transaction, nên Learning không thể
  tự bổ sung dữ liệu thiếu mà vẫn đạt AC01–03.
- AC còn thiếu: AC01–AC07 đều chưa đạt. Chưa tạo migration/API/store/helper,
  chưa có selector `-EvidenceTask R08`, và không chạy bốn required command để tránh
  ghi PASS cho layer chưa tồn tại. Validation lượt audit chỉ gồm scoped source search,
  task/link review, `git diff --check` và staged diff review; không có runtime evidence/hash.
- Control Center cần chốt dependency trước khi executor code: (1) giao owner task/cho phép
  mở rộng Identity canonical admission contract cho API-L01/L02; (2) giao owner task/contract
  publication bundle mới mang allocation metadata + learner-visible prompt trong verified replica,
  kèm migration/compatibility cho replica V4 hiện hành. Không được coi hai thay đổi này là
  local implementation detail của Learning.
- Giới hạn: không mở R09/R10/sibling, không sửa Identity/Content source ngoài scope,
  không tạo fixture/hardcode thay canonical replica, không claim JRN-01 allocation hay packaged
  HTTP. Registry chuyển `IN_PROGRESS`; chỉ chuyển `NEED_REVIEW` sau implementation, required
  validation và candidate commit đủ AC01–07.

## Checkpoint Control Center review candidate 07372a1 — 03/11/2026

- **Kết luận:** đây là handoff discovery/blocker, không phải implementation candidate để nghiệm thu.
  Commit `07372a1`, parent `5e29ac8`, branch `codex/bl-r08-learning-allocation`; working tree sạch
  khi review bắt đầu. AC01–07 chưa đạt; không có code/runtime report R08 và không đổi NEED_REVIEW/DONE.
- **Blocker được xác minh:** Identity `session/CanonicalAdmissionWire.java` chỉ cho Learning action
  LEARNING_DRAFT_SAVE, PUT và route drafts; phân loại mọi Learning request là mutation. Không thể
  dùng contract này cho POST attempts hoặc owner GET attempt mà giữ correct action/CSRF binding.
- **Blocker được xác minh:** Learning `contentreplica/ReplicaValidator.java` exact-allowlist private
  bundle v2 gồm schemaVersion/id/unitId/expected/rubricTargets; không có allocation metadata và
  learner-visible prompt/input. `contracts/catalog/private-case-v2.schema.json` cùng shape. Cần
  producer/consumer contract transition có compatibility và trusted published fixtures; không lấy
  expected/private grader bytes làm prompt hoặc tự hardcode runtime metadata.
- **Đính chính readiness:** dependencies DONE chỉ chứng minh foundation đã giao, không chứng minh
  chúng có đủ domain contract cho R08. Quyết định READY trong checkpoint mở task là quá sớm; không
  thu hồi nghiệm thu R04d/R06c/R28a trong scope của các task đó.
- **Điều kiện gỡ BLOCKED:** Control Center chốt owner/write scope cho hai contract changes: Identity
  admission create/read attempt; Content producer + shared schema + Learning verified replica mang
  allocation metadata/prompt. Có thể bổ sung scope leaf R08 hoặc tách prerequisite được duyệt, nhưng
  lượt review này chưa tự mở task hay cấp scope cross-service mới. Sau khi có contract/fixtures đủ,
  cập nhật brief/dependency rồi executor về IN_PROGRESS để implement/validate; handoff code candidate
  mới dùng NEED_REVIEW.
- **Validation:** scoped source/contract inspection và `git show --check 07372a1`; chỉ docs changed,
  không chạy app suites cho code chưa có. Các command R08 chưa được thực hiện/không được báo PASS.
