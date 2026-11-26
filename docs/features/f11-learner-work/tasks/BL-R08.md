# BL-R08 — Learning allocation và exposure trước disclosure

[Registry](../../../delivery-backlog.md) · [Work contract](../data-contract.md) ·
[Work LLD](../lld.md) · [API boundaries](../../../architecture/service-boundaries.md) ·
[Lifecycle/exposure protocol](../../../architecture/lifecycle-recovery-protocol.md)

## Brief thực thi — 03/11/2026

- Trạng thái hiện hành: `IN_PROGRESS`; prerequisites và allocation đã có implementation candidate,
  còn validation tích hợp và review trước nghiệm thu. Owner/write scope đã được cấp trong R08.
- Quyền CODE: người dùng yêu cầu task kế tiếp sau nghiệm thu R28a. Chỉ giao leaf BL-R08.
- Dependencies: BL-R04d, BL-R06c, BL-R28a đều DONE. Không cần chờ production provider R28b để
  implement/qualify local lab; production assessment chưa được qualification từ evidence này.
- Goal: Learning cấp attempt do server chọn case/mode/version, giữ owner/epoch và exposure floor;
  API-L01/L02 chỉ tiết lộ assigned prompt khi JRN-01 có verified receipt và auth/publication recheck
  đạt. Cùng request retry không tạo attempt hoặc exposure operation mới.
- Mode A theo chỉ dẫn người dùng mới nhất: Control Center tự điều phối native executor trong cùng
  workspace, giữ brief/registry/review/nghiệm thu. Một executor writer cho implementation, không tạo
  chat người dùng hoặc mở R09/R10/R11/sibling khác. Checkpoint lịch sử Mode B không còn hiện hành.
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

### Scope bổ sung được cấp — admission và allocation contract

Người dùng cho phép mở rộng chính leaf R08, không tạo task prerequisite mới. Đọc và implement
[Learning admission](../../../../contracts/http/admission/learning-allocation.md) và
[allocation v3](../../../../contracts/catalog/allocation-v3.md), cùng schema được link.
Chúng thay giả định v2 đủ cho allocation trong brief cũ; v2 compatibility vẫn phải giữ.

- Identity `session/CanonicalAdmissionWire.java` và canonical admission authority/handler/verifier
  liên quan trong `src/main/java/lab/backend/identity/session/**`, tests tương ứng. Transport
  allowlist/config chỉ khi cần đăng ký actions mới; POM chỉ cho required suites. Không redesign
  credential/login/revoke protocol ngoài read/mutation classification và exact action binding.
- Content `src/main/java/lab/backend/content/publication/**` và tests chỉ phần private bundle
  validation, storage/transport/discriminator v2/v3, normal lab publish; không đổi coordinator
  ordering/ACK protocol. Learning `contentreplica/**`/tests hỗ trợ discriminator và pinned metadata.
- Additive migration mới của Content/Learning nếu storage thật sự cần; không sửa applied migrations
  hoặc immutable bundle/receipt/history. Không tạo DB hoặc service mới.
- `contracts/catalog/private-case-v3.schema.json`, `allocation-v3.md`, relevant fixtures;
  `contracts/http/admission/**`, private publication docs/schema/fixtures của Content và Learning.
- `scripts/catalog/{validate,contract,catalog.test}.mjs` chỉ private case validation/version routing;
  không đổi catalog outline v2, v2 fixtures, framework hoặc thêm dependency.
- Identity/Content POM test profiles và ops publication/admission lab runbooks/fixtures; helpers
  DB/trust/fault/tests được mở rộng để qualify affected contracts trên cả ba services.

Một writer, làm tuần tự: Identity action + producer/consumer schema → compatibility tests → normal
Content publish sang Learning → allocation/journal/API → full affected AC. Không coi dependency
foundation DONE là bằng chứng đã hỗ trợ domain contract mới.

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

AC02 bổ sung: actual canonical Identity kiểm registered shapes trong admission extension. GET read
không bị caller-based mutation/CSRF classification; action/method/path/audience/body/context mismatch
deny. Optional receipt chỉ enable khi implemented. Draft-save/Content-read regression bắt buộc.

AC01/03 bổ sung: Content producer/Learning replica nhận exact v2/v3; v2 historical stage/fence/release
vẫn đạt nhưng v2 allocation deny/unavailable. V3 đầy đủ authored metadata/learner prompt/input/schema
và refs/digests đúng. Missing/unknown/conflicting metadata/version reject trước đổi good head. Actual
packaged publication→replica→allocation dùng v3 lab fixture có hai clean alternatives, practice tách
assessment family; expected không lọt learner DTO.

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

Scope mở rộng cần thêm targeted CMD-04 Identity canonical admission, CMD-04 Content producer/catalog
và Learning replica; actual publication regression CMD-05 R06c cho v2 và v3. Executor đăng ký selectors
strict ở helper theo suite thật, ghi exact commands trong checkpoint; không gọi selector chưa tạo
hoặc mock là PASS. CMD-02 affected cả ba service và catalog schema tests theo touched symbols.

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

## DoD/evidence hiện hành — 06/11/2026

HEAD implementation `de35532`, parent `1bd1716`, branch `codex/bl-r08-learning-allocation`.
Working tree có fix harness và format chưa commit. Các run dưới là evidence từng lớp.
Report ở `artifacts/quality/<selector>/<runId>/report.json`; giữ nguyên các failed diagnostics.

| AC/cổng | Evidence hiện có | Hiệu lực và việc còn thiếu |
|---|---|---|
| AC01/03, producer v2/v3 và immutable bytes | R06a CMD03 `3fd30b5c-563e-4234-a36f-79919ceac730`: PASS, Content unit140/DB83, cleanup true | Main đã đọc report; chưa thay publication end-to-end hoặc full R08 constraints/races |
| AC01/03, replica v2/v3 và immutable bytes | R06b CMD03 `3d773cbd-7a79-4b2e-94d3-82ca5f499ad2`: PASS, Learning unit158/DB83, cleanup true | Main đã đọc report; store fixture head99 chỉ chứng minh store boundary, không canonical publication |
| AC07, Content transport subset | R06a CMD04 `df7feb4e-3e95-4d5b-b31a-1f7a85dbc8e2`: PASS, bootstrap6/transport7, cleanup true | Main đã đọc report; single-service subset không tái hiện topology canonical ba service |
| AC02/04/05, packaged allocation | R08 CMD04 Learning `b2a7f549-90a3-4efb-b12e-55427a630c67`: 22/23 PASS, overall FAIL, cleanup true | `aiMissingCurrentTarget...` nhận 503 tại create dương tính. Chưa có mã lỗi để phân biệt gate. Cần chẩn đoán trước lượt chạy lại |
| AC02/05/07, canonical Identity | R08 CMD04 Identity `8cc5a9af-ea0e-42c0-8e0b-029d170cf4b9`: PASS 11 runtime, cleanup true | Main đã khớp 357 input hashes và 45 artifact hashes trên snapshot của run. Có regression draft-save/Content-read. Sửa harness `AllocationRuntimeIT` làm binding cần kiểm lại |
| AC01/03/04/06, Learning DB | R08 CMD03 `c3b0afb0-752c-4dd3-b359-02e544b253e4`: PASS 86 DB và 158 unit, cleanup true | Gồm 9 AllocationStoreIT. Sửa harness `AllocationRuntimeIT` làm binding cần kiểm lại |
| AC05/06/07, Content publication | R08 CMD04 Content `4407c492-6621-4545-8f0c-02a22cf3af12`: PASS 4 runtime, cleanup true | Main khớp 357 input hashes và 45 artifact hashes. Ca FENCE xác nhận exact ACK waiter trước khi release |
| AC07, catalog và report gates | R05 CMD04 `82f0699e-d8c4-4430-89ed-0e06700a2b87`: PASS 39. Node24.22.1 CLI/report tests PASS 19. R08 Python report-gate doubles PASS 7 | Catalog kiểm schema, refs và mapping. Gate tests kiểm cách thu evidence |
| AC06/07, closing matrix | Required commands và affected selectors trong phần validation | Learning CMD04 đầy đủ, DATA-22, CMD06 lab, R06c regressions và các evidence cần kiểm lại binding |
| DONE04/05, review và integration | Candidate `de35532`; registry IN_PROGRESS | Còn format/privacy review, candidate fix commit và Control Center acceptance; không mở task kế tiếp |

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

## Checkpoint gỡ blocker / scope và contract đã chốt — 03/11/2026

- Người dùng yêu cầu mở rộng scope để chat implementation chạy tiếp. Control Center chọn bổ sung
  hai prerequisite vào chính R08; không tạo sibling, không sửa acceptance bằng cách bỏ auth/pin refs.
- Đã cấp write scope Identity canonical admission + Content private bundle producer/shared schema +
  Learning consumer, đúng phần nối R08. Contract owner: admission/learning-allocation.md và
  catalog/allocation-v3.md, schema private-case-v3; các owner README đã link. V2 giữ nguyên, v3 là
  explicit discriminator, legacy allocation fail closed và staged rollout consumer-first.
- Registry `BLOCKED → READY`; đây là gỡ blocker quyết định/scope, chưa phải runtime prerequisites
  đã implement. Executor chuyển IN_PROGRESS để implement prerequisites rồi allocation theo AC.
- Required evidence bổ sung canonical action/method/read/mutation, producer/replica v2/v3 + actual
  publication path, good-head preservation và private-sentinel regression cả ba service. Existing
  foundation DONE không cấp PASS cho các contract mới. Validation lượt này là docs/schema-only.
- Git: giữ cùng nhánh codex/bl-r08-learning-allocation, base review ab0e8ff. Lượt scope/contract này
  có commit riêng; mỗi lượt implement/fix tiếp theo validation/commit xong chuyển NEED_REVIEW.
- Validation thật: Node24.22.1 hiện có + existing Ajv strict compile schema v3 đạt; một positive
  shape được nhận, bốn negative shapes (missing allocation, version2, invalid mode, unknown metadata)
  bị reject. Kiểm18 local links và diff whitespace đạt. Lượt đầu dùng Node cũ trên PATH thiếu
  structuredClone nên diagnostic dừng, không báo PASS cho lượt đó. Đây chỉ schema/docs evidence;
  semantic references và runtime compatibility vẫn là acceptance executor phải chứng minh.

```text
Tiếp tục BL-R08 theo registry và checkpoint mới nhất tại
docs/features/f11-learner-work/tasks/BL-R08.md trên codex/bl-r08-learning-allocation.
Blocker scope/contract đã gỡ: được sửa Identity canonical admission cho create/read attempt,
Content private-case-v3 producer/shared contract và Learning verified replica consumer theo
contracts/http/admission/learning-allocation.md và contracts/catalog/allocation-v3.md.
Implement prerequisites tuần tự, giữ v2 compatibility, rồi allocation API-L01/L02 + JRN-01 trước
disclosure theo AC01–07. Chỉ sửa scope brief đã mở rộng; không hardcode runtime metadata hoặc
lấy expected làm prompt. Chuyển IN_PROGRESS khi code; validation, checkpoint và commit mỗi lượt.
Candidate sẵn sàng chờ Control Center review thì NEED_REVIEW; không tự DONE hoặc mở task khác.
```

## Checkpoint điều phối native executor — 03/11/2026

- Người dùng yêu cầu Control Center tự điều phối thay manual paste. Chuyển Mode B sang Mode A;
  goal/write scope/AC không đổi. Registry IN_PROGRESS khi giao implementation.
- Control Center sở hữu file task và registry; native Backend/Platform executor là writer duy nhất
  cho source/contracts/helpers/fixtures trong scope. Executor được commit phần implementation sau
  validation theo Git workflow, trả candidate/validation/findings/limits để main review.
- Executor không sửa registry/task file, không DONE/integrate hoặc mở task khác. Main cập nhật
  checkpoint/NEED_REVIEW từ evidence thật, review candidate và điều phối fix tới nghiệm thu.

## Checkpoint Control Center tiếp tục candidate e3d7c36 — 04/11/2026

- Người dùng hỏi trạng thái và yêu cầu tiếp tục nếu chưa xong. R08 vẫn IN_PROGRESS; native executor
  đã được giao tiếp phần AC còn thiếu, cùng writer/scope/branch. Không cần manual paste hoặc task mới.
- Candidate `e3d7c36`, parent `0b15cc4`, working tree sạch lúc kiểm. Đã có Identity admission,
  Content/Learning v3 compatibility và allocation PG/journal/API implementation; chưa đủ DoD.
- Handoff ignored evidence:
  `artifacts/quality/R08/d1fc28ed-3972-4cb7-a66c-7e4d900c4bd4/candidate-handoff.json`.
  Main đọc và kiểm report hashes bốn runs khớp handoff: CMD03 `36704a09-f4ae-4330-81fb-e99d8e4afcaf`,
  CMD04 `5bfbb98a-543e-4818-a2c7-d4f5d78bb761`, CMD05 `d1fc28ed-3972-4cb7-a66c-7e4d900c4bd4`,
  CMD06 `20261103T161150466Z`. PASS chỉ áp dụng registered subset, không rollup AC/DONE toàn leaf.
- Còn thiếu: exact ACK-before-final crash barrier (AC04); controlled auth/context/credential,
  epoch/pending/deleted và publication ordering quanh journal/final/read (AC05); mở rộng metadata/
  admission negatives, dedicated affected selectors và original R06c regressions (AC01–03/06–07).
- Main source review bước đầu xác nhận coordinator journal ngoài local transaction, fresh admission
  trước final callback; query bị deny ở boundary trước handler. Chưa nghiệm thu chỉ từ source.
- Chấp nhận pool từ immutable RELEASED replicas cho single-case bundle, với current manifest gating
  và absence fallback chỉ khi active v3 cùng rubricVersion/CAP. Executor phải bổ sung negative tests
  v2/withdraw/unmatched-policy. Structural draft-07 subset là explicit compatibility limit; cần kiểm
  malformed schema/keyword parity và không claim full runtime draft-07 implementation.
- Bước tiếp theo đang giao executor: khép required matrix, validate candidate mới, commit riêng và
  handoff để main review. Khi còn implementation/validation giữ IN_PROGRESS; đủ bàn giao mới NEED_REVIEW.

## Checkpoint Control Center tiếp tục — 05/11/2026

- Người dùng yêu cầu theo dõi đến khi xong, tự nghiệm thu và giao fix khi có lỗi. Executor cũ không
  còn live trong runtime khi tiếp tục; đã chuyển sole-writer ownership cho native `/root/r08_finish`,
  khởi tạo thực với `gpt-5.6-sol`, `xhigh`, context tối thiểu. Main sở hữu registry/task/execution docs.
- Review candidate `de35532`: raw-byte immutability đã được bổ sung; evidence từng lớp nằm trong bảng
  hiện hành. Hai docs có thay đổi chưa commit từ trước lượt này; giữ nội dung, không destructive restore.
- Lỗi chặn hiện tại là packaged CLI prepare trong canonical setup. SQL transaction rollback và schema
  probe từng đạt không chứng minh nguyên nhân timeout/config; chỉ là diagnostic. Không lặp full suite
  khi chưa có quan sát mới. Giữ production fail-closed gates và immutable constraints.
- Giao fix helper: scan privacy từng CLI attempt trước khi overwrite kết quả; giữ diagnostic exit/retry
  an toàn, negative helper không retry. Giao format control-flow trong allocation symbols theo AGENTS.
- Bước tiếp theo: bounded diagnostic/fix → targeted regression → đóng băng candidate và required matrix
  → implementation commit → main review/acceptance. R08 vẫn IN_PROGRESS tới khi đủ handoff.

## Checkpoint Control Center tiếp tục validation — 06/11/2026

- Canonical Learning từng thoát ở DB clock readiness. Log cho thấy kết nối bị ngắt trong cùng khoảng
  checkpoint PostgreSQL kéo dài. Chưa phân biệt được latency, skew hoặc binding là nguyên nhân trực tiếp.
- Harness giữ diagnostic an toàn và cho phép một lần khởi động lại khi child thoát trước readiness
  với lỗi clock đã nhận diện. Production gate giữ nguyên. Đã sửa race ở cuối vòng chờ để retry chỉ
  xảy ra sau phân loại. `db8a2d32-dcff-42a7-82df-3b4c39a21332` đạt 11 ca trước sửa race, nên evidence
  hiện hành dùng `8cc5a9af-ea0e-42c0-8e0b-029d170cf4b9` đã kiểm source binding sau sửa.
- Control Center kích hoạt lại sole writer `/root/r08_finish` để hoàn tất matrix trong bảng DoD.
  Chỉ bàn giao NEED_REVIEW sau khi có candidate commit và evidence phù hợp. Registry vẫn IN_PROGRESS.

### Delta sau kiểm tra Content

- Run `3b3db54a-220a-464b-a2b7-d7ccedf141da` lỗi client timeout ở ca FENCE vs journal ACK.
  Ca kế tiếp nhận 503 khi FENCE còn hiệu lực. Run `4c02298b-3328-49b4-8ad3-da78336bb96f` lỗi trước
  cases ở `UnixDomainSockets.connect0` khi JVM Learning khởi tạo selector. Cả hai lab đã cleanup.
- Harness giới hạn HTTP budget 20s cho ca FENCE và truy vấn PostgreSQL chứng minh exact waiter còn
  chờ ngay trước release. Các JVM con có IPC namespace riêng trong run. Content CMD04 hiện PASS
  theo bảng evidence. Executor đang chạy full Learning CMD04 trên cùng input snapshot.
- Full Learning CMD04 chạy đủ 23 ca trên cùng snapshot. Ca FENCE đạt, một ca nhận 503 tại create
  dương tính trước khi kiểm thử thay đổi current target. Giữ report FAIL. Bước kế tiếp là ghi mã
  lỗi an toàn và snapshot gate để phân loại trước khi chọn cách sửa.
