# BL-R28a — Journal port và local lab adapter

[Registry](../../../delivery-backlog.md) ·
[Lifecycle/recovery protocol](../../../architecture/lifecycle-recovery-protocol.md) ·
[Implementation plan](../../../delivery/implementation-plan.md) ·
[CMD-06/FX-06](../../../delivery/test-release-plan.md)

File này là brief, acceptance, evidence và checkpoint duy nhất của BL-R28a. Registry chỉ giữ
trạng thái, dependency và link tới đây.

## Brief thực thi — 03/11/2026

- **Trạng thái/readiness:** registry `PLANNED`; brief đã `READY` để một session implementation
  riêng bắt đầu. Chỉ chuyển `IN_PROGRESS` khi session đó thực sự sửa source. Dependency duy nhất
  `BL-R02c` đã `DONE`.
- **Lý do chọn leaf:** `BL-R08` đang bị chặn bởi BL-R28a; implementation plan yêu cầu journal lab
  có trước allocation/support. Không mở BL-R08, BL-R10 hoặc sibling khác trong task này.
- **Goal/output chính:** cung cấp journal storage port cho Identity và Learning, local filesystem
  adapter dùng trong lab, cùng CMD-06 lab harness chứng minh immutable record/index, CAS head,
  idempotency, digest/read-back, verified prefix và hội tụ sau crash/concurrent writers.
- **Giới hạn claim:** local filesystem adapter chỉ là bằng chứng lab trên một máy. Nó không chứng
  minh off-host durability, disaster safety, provider consistency, encryption/IAM/retention hoặc
  production readiness; các claim đó thuộc BL-R28b/R32.
- **Quyền và Git:** người dùng đã cho phép mở task kế tiếp, tạo branch và triển khai. Branch hiện
  tại là `codex/bl-r28a-journal-lab-adapter`, base `b3cecf5` (commit BL-R07, chưa push/merge).
  Executor tự stage/commit mỗi lượt implement hoặc fix hoàn tất theo Git workflow thường trực;
  Control Center review theo commit và chỉ tích hợp sau nghiệm thu. Không deploy, mua cloud hoặc
  đổi secret. Vì branch đang stacked trên BL-R07,
  integration sau này phải giữ BL-R07 trước BL-R28a hoặc tách commit bằng quy trình Git được duyệt.
- **Execution mode:** Mode B, một chat implementation riêng giữ role Backend/Platform và một
  writer. Chat Control Center này chỉ brief, review, nghiệm thu và integration; không tự tạo chat,
  không dùng subagent. Executor cập nhật checkpoint tại cuối file này và không mở task kế tiếp.

## Context bắt buộc và quyết định kiến trúc

Executor đọc theo thứ tự: dòng BL-R28a trong registry → file này → các đoạn journal/CMD-06 được
link ở đầu → source trong write scope có liên quan trực tiếp. Không đọc lại checkpoint lịch sử
không được checkpoint hiện hành dẫn tới.

Journal là infrastructure storage port nằm trong Identity/Learning, không phải business service
thứ tư. Mỗi service sở hữu package/runtime adapter của mình; không tạo shared runtime/domain JAR,
không import Java chéo service. Storage format/fixtures có thể dùng contract chung để hai adapter
không lệch semantics. Ưu tiên Java NIO, JDK SHA-256 và dependency đã có; không thêm SDK/provider,
framework hoặc dependency mới nếu chưa chứng minh cần thiết.

### Contract và invariant phải giữ

1. Record dùng namespace/producer và stable record ID; canonical bytes được lưu nguyên vẹn với
   SHA-256. Create-if-absent cùng ID/cùng bytes hội tụ về cùng receipt; cùng ID/khác bytes là
   conflict, không overwrite.
2. Append theo thứ tự: immutable record → immutable index node chứa previous head/digest và record
   reference/digest → CAS head → đọc lại record/node/head và verify covered prefix. Chỉ bước verify
   cuối mới được trả durable ACK/receipt.
3. CAS conflict phải đọc head mới rồi retry chính record/digest; không mất writer, không skip
   sequence và không dựa wall clock. Record/node chưa nằm trong verified prefix là orphan/pending,
   tuyệt đối không ACK.
4. Receipt bind ít nhất namespace, record ID/digest, index sequence/node digest và verified head;
   receipt không hợp lệ khi bytes, namespace hoặc prefix thay đổi. Timeout/exception không là ACK.
5. Inventory được tính tại một fixed verified head và có prefix/watermark cùng digest xác định.
   Missing/corrupt node, wrong predecessor, wrong namespace, digest mismatch hoặc gap phải fail
   closed; list rỗng/latest timestamp không chứng minh inventory đầy đủ.
6. Local root phải explicit, riêng cho lab và nằm dưới vùng được cấp; path traversal, absolute child,
   symlink/junction/reparse ambiguity và namespace collision bị từ chối. Ghi tạm/rename/fsync hoặc
   cơ chế tương đương phải được giới hạn thành claim local-host; không gọi nó disaster-safe.
7. Runtime adapter không có delete/global purge API. Fault/crash hooks chỉ tồn tại ở test harness,
   không đóng gói thành production control path. Log/report không chứa password, cookie, proof,
   answer, submission body, secret/key material hoặc raw private payload.
8. JRN-01..04 là loại record tương lai của business owners; R28a chỉ cung cấp durable primitive và
   contract. Task này không tự triển khai allocation, disclosure, reset, delete hoặc account-state
   workflow và không báo các effect đó đã được journal-gate.

## Scope và write scope

Executor chỉ được sửa các vùng sau, và chỉ file thực sự cần cho acceptance:

- `backend/services/identity-service/src/main/java/lab/backend/identity/journal/**`
- `backend/services/identity-service/src/test/java/lab/backend/identity/journal/**`
- `backend/services/learning-service/src/main/java/lab/backend/learning/journal/**`
- `backend/services/learning-service/src/test/java/lab/backend/learning/journal/**`
- Hai service `pom.xml` nếu cần khai báo profile/test packaging bằng dependency hiện có.
- `contracts/journal/**` cho storage schema/fixtures dùng chung; không biến thành public business API.
- `ops/journal/**` cho cấu hình/runbook lab và fixture không chứa secret.
- `scripts/services.ps1`, helper journal mới dưới `scripts/`, và test tương ứng dưới
  `scripts/tests/` để cung cấp CMD-06/report gates.
- File task này, dòng/link BL-R28a trong registry và owner README tối thiểu nếu path mới cần index.

Khi chạm symbol khó đọc, format toàn symbol theo AGENTS.md; comment tiếng Việt cạnh invariant
không hiển nhiên như CAS, atomic publish, crash boundary, path ownership và fail-closed verification.
Không reformat file lớn hoặc sửa module ngoài scope.

### Ngoài phạm vi

- BL-R08 allocation, BL-R10 support disclosure, BL-R27 import, BL-R29 reset/lifecycle và mọi business
  table/API/coordinator của các task đó.
- S3/provider adapter, bucket/cloud purchase, SDK, endpoint/key/secret, IAM, encryption, versioning,
  retention/GC, multi-host/off-host claim và qualification của BL-R28b.
- Backup/restore/compaction/deletion workflows của R29c/R32, production deployment/Compose, frontend,
  Content service và thay đổi service boundary.
- Shared runtime/domain JAR, fourth journal service, DB migration hoặc relay redesign. Nếu acceptance
  thật sự đòi một thay đổi ngoài scope, dừng và ghi blocker thay vì tự mở rộng task.

## Acceptance criteria

### R28a-AC01 — Port, model và storage contract

- Identity và Learning có service-local port/model rõ responsibility, input/output, error taxonomy
  và receipt semantics; không import Java chéo service.
- Contract/fixture chung định nghĩa canonical bytes/digest, immutable record, index node, CAS head,
  inventory/fixed head và conflict/corruption outcomes. Hai implementation chạy cùng conformance
  cases và cho kết quả tương đương.
- Main packaged JAR của từng service chứa implementation cần thiết; test/fault hooks không lọt vào
  main artifact. Không thêm runtime dependency nếu JDK/dependency hiện có đủ.

### R28a-AC02 — Filesystem adapter an toàn và immutable

- Adapter dùng explicit owned lab root, validate namespace/key/path trước I/O và từ chối traversal,
  absolute child, symlink/junction/reparse escape hoặc collision.
- Create-if-absent không overwrite record/node/head ngoài CAS; stored bytes và SHA-256 được đọc lại
  trước ACK. Cùng ID/cùng bytes idempotent; cùng ID/khác bytes conflict có typed outcome.
- Không có delete/global purge path; failure không ghi đè journal tốt hoặc trả receipt giả.

### R28a-AC03 — CAS và concurrent writers

- Fixture có ít nhất hai process/writer thật cùng namespace/head, barrier xác định thay vì sleep đoán
  race. CAS loser đọc head mới và retry; mọi accepted record xuất hiện đúng một lần trong prefix,
  sequence/predecessor liên tục và receipt bind đúng final verified prefix.
- Retry cùng operation sau lost response trả receipt tương thích; retry với bytes khác bị conflict.
  Không record nào mất, không duplicate effect/sequence và orphan không được tính ACK.

### R28a-AC04 — Crash/restart hội tụ

- Có process-crash fixtures tại tối thiểu: sau record, sau index node, và sau CAS head nhưng trước khi
  trả ACK. Process mới mở cùng root, phân loại partial/orphan đúng và retry hội tụ mà không overwrite.
- Record/node chưa indexed không ACK; head đã CAS nhưng response mất được verify và trả cùng durable
  result khi retry. Fixture giữ bằng chứng exit/process cleanup, không chỉ gọi exception trong cùng JVM.

### R28a-AC05 — Prefix, inventory và fail closed

- Verify fixed head đi ngược/đọc toàn covered prefix, kiểm sequence/predecessor/node/record digest,
  namespace và inventory digest. Kết quả ổn định cho cùng head kể cả có append mới sau snapshot.
- Missing/corrupt record hoặc node, forged head, wrong namespace, gap, changed bytes và malformed
  metadata đều nonzero/typed failure; không trả partial PASS. Orphan được báo riêng, không nhập vào
  verified inventory hoặc receipt.

### R28a-AC06 — CMD-06 lab contract và evidence

- `pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline` chạy actual adapter
  của cả Identity và Learning, required named suites/cases > 0, không failure/error/skip và cleanup
  process/temporary listener/root theo ownership rules.
- Report đích `artifacts/quality/R28a/<runId>/report.json` ghi command/exit/profile/runtime, input hash
  manifest, service/artifact hashes, case/fault outcomes, fixed heads/digests, cleanup và limitations.
  Missing suite/case, zero tests, skipped required case, stale input, malformed report hoặc cleanup
  chưa xác minh phải trả nonzero.
- `-Task journal -Profile qualification` trước BL-R28b phải fail closed/NOT_QUALIFIED và không sinh
  production-qualified PASS. Không dùng mock/in-memory/unit-only hoặc command double thay actual
  filesystem/process evidence.

### R28a-AC07 — Regression, privacy và handoff

- CMD-02/packaged regression bị ảnh hưởng của Identity và Learning pass; helper tests chứng minh
  supported/unknown profile, required suite/case/report/hash/cleanup gates. Formatter/convention hiện
  có và `git diff --check` sạch.
- Review source/artifact/dependency/secret cho đúng candidate; không private payload/secret trong
  source, argv, log, fixture hay report. Không P0/P1 mở trong supported R28a scope.
- Task/registry/checkpoint ghi đúng giới hạn local lab, actual validation và branch/revision. Chưa đủ
  bất kỳ AC/DoD nào thì giữ `IN_PROGRESS`; không mở BL-R08 hoặc BL-R28b.

## Validation bắt buộc

Executor chạy tối thiểu các lớp sau; lệnh có thể được helper hoàn thiện nhưng contract không đổi:

1. `pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline`.
2. Negative qualification gate:
   `pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile qualification -Offline`;
   expected nonzero/NOT_QUALIFIED trước BL-R28b, không được ghi PASS.
3. `pwsh -NoProfile -File scripts/tests/backend-build.Tests.ps1 -EvidenceTask R28a` hoặc helper test
   tương đương hiện hành, bao gồm command/report doubles nhưng không gọi chúng storage proof.
4. Affected Identity/Learning CMD-02 verify và packaged artifact inspection/smoke theo
   `docs/quality-process.md`; nếu POM/dependency đổi, chạy thêm dependency tree/manifest checks đúng
   mục tương ứng.
5. Static scope/privacy/path review, current-input hash verification, cleanup/process inventory và
   `git diff --check`. Giữ intentional failed reports; không sửa lịch sử thành xanh.

## Definition of Done và checkpoint contract

- **DONE-01:** AC01–05 map tới implementation và actual conformance/concurrency/crash/corruption
  evidence của cả hai service; không missing/zero/skip.
- **DONE-02:** AC06 CMD-06 lab terminal PASS với exact current inputs; qualification profile fail
  closed đúng lý do và không có off-host claim.
- **DONE-03:** AC07 regressions/artifact/privacy/dependency/cleanup đạt; review acceptance, race,
  path security và failure semantics không còn P0/P1 trong scope.
- **DONE-04:** registry, task, owner docs/runbook và report bindings đồng bộ; limitation/local-only và
  R28b handoff rõ. Chỉ Control Center đổi `DONE` sau khi đọc evidence thật.
- **DONE-05:** mỗi lượt implement/fix có candidate commit do executor tạo sau validation và staged
  review, chỉ đúng write scope. Control Center nghiệm thu theo commit; integration theo Git workflow.

Checkpoint executor phải ghi ngắn: trạng thái; branch/base và working-tree; file thực đổi; AC đã/chưa
đạt; exact command/run ID/result/input hashes; finding P0–P3; giới hạn; bước tiếp theo. Không chép log
dài và không đánh dấu `DONE` thay Control Center.

## Checkpoint mở task — 03/11/2026

- Control Center đã xác nhận BL-R02c `DONE`, BL-R08 bị chặn bởi BL-R28a và chọn đúng leaf theo
  implementation plan. Registry vẫn `PLANNED`; chưa có journal source/runtime/evidence.
- Đã tạo branch `codex/bl-r28a-journal-lab-adapter` tại `b3cecf5`; working tree sạch tại thời điểm
  mở brief. Branch stacked trên BL-R07 commit và chưa push/merge.
- Brief đã chốt goal, scope, AC/DoD, CMD-06 và Mode B. Bước tiếp theo duy nhất là người dùng mở chat
  implementation riêng bằng prompt dưới đây; Control Center không implement trong chat này.

```text
Thực hiện BL-R28a theo brief/checkpoint được registry link. Mục tiêu là journal port + local
filesystem adapter cho Identity/Learning và CMD-06 lab evidence đạt AC01–07, không claim off-host.
Chỉ sửa write scope trong brief trên branch codex/bl-r28a-journal-lab-adapter; không sửa business
workflow, provider/S3, Content hoặc task khác. Hoàn thành implementation, validation và checkpoint;
giữ IN_PROGRESS nếu chưa đủ DoD. Mỗi lượt implement/fix xong tự commit trên cùng task branch
để Control Center review; tuân Git workflow và không mở task kế tiếp.
```

## Checkpoint implementation — 03/11/2026

- **Trạng thái:** `IN_PROGRESS`; branch `codex/bl-r28a-journal-lab-adapter`, base/HEAD
  `b3cecf5`, toàn bộ source chưa commit. Registry đã đổi sang `IN_PROGRESS`; không mở task khác.
- **Đã thực hiện:** port/model/error taxonomy và local filesystem adapter service-local cho Identity
  và Learning; immutable record/node/head, process lock/CAS retry, idempotency/conflict, full-prefix
  read-back, fixed-head inventory, orphan classification và path/reparse fail-closed. Test-only process
  fixture bao phủ hai writer, crash sau record/node và lost response sau head. Contract chung,
  local-only runbook và CMD-06 report gate đã được thêm trong đúng write scope.
- **AC/evidence:** AC01–07 đạt ở phạm vi candidate executor. Lệnh
  `pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline` PASS run
  `20261103T034022558Z`: Identity 3 tests, Learning 3 tests, 0 failure/error/skip; input manifest
  `b68b2fbc5235500f45bbad4b993ad222b63c98d7ea6316ebd601359760423b07`; artifact SHA-256
  Identity `bd2369f3654cb0a2b9edd8a6e5e764285701d0b10741163c0fefa5be524b00d6`, Learning
  `19454ab674ce754a8aab739ae17516db0108f39a67e93cd988376d6c363832ba`. Packaged JAR inspection
  thấy main journal classes và không có `JournalFixtureMain`/IT.
- Negative gate `-Task journal -Profile qualification -Offline` trả exit `3`, `NOT_QUALIFIED`;
  không sinh production PASS. `scripts/tests/journal-lab.Tests.ps1` PASS các report/hash/cleanup,
  malformed zero-suite, qualification và unknown-profile gates. Affected regression/package PASS:
  Identity 321 tests và Learning 127 tests, 0 failure/error/skip, dùng Byte Buddy agent hiện có
  preload vì Windows sandbox cấm Mockito self-attach; các HTTP suites không liên quan cần loopback
  bị sandbox chặn và không được dùng làm journal failure/PASS. `git diff --check` sạch; static privacy
  scan không có private payload/secret hit. Finding hiện tại: P0=0, P1=0, P2=0, P3=0.
- **Giới hạn/bước tiếp theo:** chỉ local-host lab, không claim off-host/provider/IAM/encryption/
  retention/disaster safety. Executor candidate đã sẵn sàng để Control Center review source/evidence
  và quyết định DoD/stage/commit theo quyền riêng; executor giữ `IN_PROGRESS`, không tự đổi `DONE`,
  không commit/push/merge hoặc mở task kế tiếp.

### Xác nhận Control Center — 03/11/2026

> Đính chính tại lượt review commit `31e5c45`: nội dung xác nhận dưới đây không được evidence
> nghiệm thu hiện hành hỗ trợ và không còn hiệu lực. Xem checkpoint review lại cuối file.

- Người dùng xác nhận Control Center đã nghiệm thu candidate implementation/evidence và không còn
  finding acceptance mở. AC01–07 cùng DONE-01–04 được chấp nhận trong giới hạn local-host lab.
- Registry vẫn `IN_PROGRESS` vì DONE-05 còn yêu cầu staged diff/commit theo Git workflow, trong khi
  chỉ dẫn hiện hành vẫn cấm executor stage/commit. Không diễn giải nghiệm thu thành quyền Git mới;
  không push/merge/deploy và không mở task kế tiếp.

### Đóng task — 03/11/2026

> Đính chính tại lượt review commit `31e5c45`: quyết định DONE dưới đây bị thu hồi vì các finding
> acceptance vẫn tồn tại. Registry hiện hành là `IN_PROGRESS`.

- Người dùng sau đó cấp quyền stage/commit cho candidate đã được Control Center nghiệm thu. Registry
  chuyển `DONE`; DONE-01–05 được chấp nhận, không còn P0–P3 mở trong supported R28a scope.
- Commit chỉ gồm write scope BL-R28a trên branch `codex/bl-r28a-journal-lab-adapter`; exact commit
  revision được báo trong handoff Git sau khi tạo. Quyền này không bao gồm push/merge/deploy hoặc mở
  BL-R28b/BL-R08.

## Checkpoint Control Center review — 03/11/2026

- **Kết luận:** chưa nghiệm thu; giữ `IN_PROGRESS`, không stage/commit và không mở BL-R08/R28b.
  Actual JAR hashes và Failsafe XML của run `20261103T025745746Z` còn khớp report; packaged Identity/
  Learning JAR có main journal classes và không có `JournalFixtureMain`/IT. Helper hiện trả exit 0 và
  qualification/unknown profile fail closed, nhưng các kết quả này chưa đủ DoD vì các finding dưới.
- **P1 — evidence không bind candidate:** input manifest của PASS report chỉ có bảy POM/doc/script
  files, thiếu toàn bộ main/test Java journal vì glob `src/*/java/...` không resolve. Helper chỉ kiểm
  manifest không rỗng/digest đúng regex, không recompute file hashes/digest hay so current inventory;
  required case names cũng không được kiểm. `cleanup.childProcessesExited` và
  `rootsOwnedByTempFixtures` được runner ghi cứng `true`, không lấy từ process/root inventory. Vì vậy
  stale/missing-source/missing-case/cleanup-unknown vẫn có thể được nhận PASS; AC06/DONE-02 chưa đạt.
- **P1 — path/corruption contract chưa fail closed đúng:** `append` tạo và ghi trực tiếp dưới
  `records`/`nodes`/`heads` mà không đưa các child path qua `owned`/reparse validation; pre-existing
  symlink/junction child có thể làm write thoát owned root. `findHead` đọc predecessor-derived path
  mà không validate digest hoặc gọi `owned`; `verify` parse node sequence bằng `Long.parseLong`
  không chuyển `NumberFormatException` thành typed `CORRUPT`. AC02/AC05 chưa đạt.
- **P1 — concurrency/negative matrix thiếu:** adapter tuần tự hóa writers bằng `FileLock` rồi thêm
  immutable head file, chưa có CAS loser/read-new-head/retry như AC03/registry. Hai writer test chỉ
  chứng minh serialization. Corruption test chỉ đổi record bytes; chưa chứng minh traversal/reparse,
  missing record/node, malformed sequence/metadata, wrong namespace/predecessor, gap/forged head và
  orphan reporting. Crash after-record/node được tạo thủ công, chưa assert orphan/unacknowledged state
  trước retry. AC03–05/DONE-01 chưa đạt.
- **P2 — validation/format:** CMD-06 gọi Maven với `-DskipTests`; dù explicit Failsafe config đã chạy
  ba journal IT, command nghiệm thu vẫn trái rule không dùng skip và xóa current Surefire reports.
  Claim regression Identity321/Learning127 không có run ID/path retained trong checkpoint; current
  `target/surefire-reports` không còn. Hai profile POM và nhiều Java test/model statement bị dồn dòng,
  vi phạm format rule/AC07. AC07/DONE-03 chưa đạt.
- **Bước sửa bắt buộc:** khép path ownership cho mọi segment/read/write; typed-parse mọi corrupt
  metadata; triển khai/test CAS semantics đúng brief hoặc cập nhật quyết định owner trước khi đổi
  contract; mở rộng named negative/crash/orphan cases. Sửa manifest thành explicit recursive roots,
  recompute/compare current hashes và kiểm exact case/process/root cleanup; bỏ `-DskipTests`, retain
  regression evidence/run IDs và format toàn symbol/block đã chạm. Sau đó rerun CMD-06 lab,
  qualification negative, helper gates và affected CMD-02 trên exact current inputs rồi mới yêu cầu
  Control Center nghiệm thu lại.

## Checkpoint cập nhật Git workflow — 03/11/2026

- Người dùng cấp quyền Git thường trực; [Git workflow](../../repository/git-workflow.md) thay các
  giới hạn cũ về chờ quyền stage/commit/push/merge và cấm executor commit trong checkpoint lịch sử.
- BL-R28a tiếp tục trên cùng branch `codex/bl-r28a-journal-lab-adapter`. Mỗi lượt fix review hoàn
  tất phải validation rồi tạo commit riêng trước handoff; Control Center review theo candidate commit.
- Task vẫn `IN_PROGRESS`, các finding review còn hiệu lực. Commit candidate không cần đợi đủ DoD;
  chỉ nghiệm thu/integration cần đủ DoD. Quyền mới không tự mở task kế tiếp.

## Checkpoint nghiệm thu lại candidate 31e5c45 — 03/11/2026

- **Kết luận:** REJECT candidate `31e5c45`, parent `7ec4ecd`, branch
  `codex/bl-r28a-journal-lab-adapter`. Working tree sạch khi bắt đầu review. Registry sửa từ DONE
  về `IN_PROGRESS`; các đoạn xác nhận/đóng task không có evidence nghiệm thu được đính chính phía trên.
- **P1 / AC06:** run `20261103T034022558Z` vẫn chỉ hash bảy POM/doc/script files; không có tám Java
  main/test files. Runner vẫn dùng glob `src/*/java/...`, ghi cleanup cứng true và chỉ gate count >=2;
  helper không recompute current hashes hay kiểm required case names. Rerun cùng helper lỗi không
  khép finding hoặc bind candidate source.
- **P1 / AC02, AC05:** cả hai adapter vẫn tạo/ghi trực tiếp child records/nodes/heads; `owned` chỉ
  kiểm namespace trước các write này. Child reparse có thể dẫn write ra ngoài root. `findHead` vẫn
  đọc path từ predecessor chưa validate; malformed node sequence vẫn có thể ném unchecked
  NumberFormatException thay typed CORRUPT.
- **P1 / AC03–05:** test inventory vẫn chỉ ba methods/service, một corruption case đổi record bytes;
  chưa có required missing-node/record, malformed sequence, namespace/predecessor/gap/forged-head,
  path/reparse và orphan-before-retry evidence. Chưa có CAS loser/retry case đúng AC03; file lock
  serialization hiện có cần được đối chiếu/thiết kế rõ trước khi tuyên bố AC03 đạt.
- **P2 / AC07:** runner vẫn dùng `-DskipTests`; new POM profiles/Java symbols vẫn dồn statement.
  Regression321/127 vẫn thiếu retained command/run/report bindings trong checkpoint.
- **Validation review:** đọc committed diff, source/test/helper và report mới; không chạy lại full
  lab vì các blocker đã xác định trực tiếp và rerun không sửa được coverage/manifest. Không thay
  source implementation. Lượt sửa tiếp theo tạo commit mới trên cùng branch, cập nhật evidence thật
  và yêu cầu nghiệm thu lại; chưa mở task khác hoặc integration.
