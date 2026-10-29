# Bàn giao thiết kế R0/MS — lịch sử

Trạng thái hiện hành đọc tại [backlog](../../delivery-backlog.md); các checkpoint giữ bằng chứng tại thời điểm ghi.

## Trạng thái và thứ tự

- READY: đủ đầu vào để bắt đầu; PLANNED: dự kiến, còn dependency hoặc cần chia nhỏ; IN_PROGRESS/REVIEW/DONE/BLOCKED theo execution-model.
- WIP mặc định: một task. Không dùng phần trăm chủ quan để báo tiến độ task.
- Bộ tài liệu hiện hành để người dùng review: [HLD và 7 LLD](../../design/README.md), BL-D10 DONE.
- BL-R00 **DONE**: FE frontend/, backend vùng riêng. MS01–04 v3 chốt [catalog](../../architecture/service-boundaries.md)/[session](../../features/f10-identity-access/sessions/protocol.md)/[data](../../architecture/data-protocols.md)/[implementation](../implementation-plan.md)/[test-release](../test-release-plan.md). [ADR-03](../../architecture/microservices-review.md) giữ findings; không one-app D08. Nội dung draft/planned; backend có ba app bootstrap, R02a DB lab và R02b service trust kỹ thuật; chưa canonical auth/session hoặc nghiệp vụ học; admin sau R1.
- Mỗi task bên dưới có goal, phạm vi, dependency và nghiệm thu; bằng chứng/checkpoint thêm ngay tại task khi làm. Không tự DONE theo việc hoàn thành tài liệu này.
- Chuỗi hiện hành: MS01/MS02/MS03/MS04/D14 DONE ở thiết kế → R01a–d/R01 DONE nền build/ba bootstrap → R02a DB lab → R02b service trust/common-wire → R03 local credentials DONE → tiếp R04a session/preauth/CSRF. R02 parent còn chờ R02c; các runtime leaf khác chưa thực thi. Admin BL-S05 sau R1, không tự tạo chat/agent song song.

## R0 — Thiết kế đủ để xây mẫu

### BL-D14 — Review độ đầy đủ và khả năng thực thi sau MS04 [DONE]
- Goal/đầu ra chính: [readiness review](../../architecture/readiness-review.md), finding có mức độ/nguyên nhân/owner và phần bổ sung vào nguồn thiết kế hiện hành. Dependency: MS01–04/R00 DONE; yêu cầu review trực tiếp của người dùng 01/10/2026. REQ-09–16/18, MD01/02/06/09/10/11.
- Phạm vi: rà task→fixture→môi trường thực, edge/service trust, wire API chung, checkpoint backup và vòng review trải nghiệm. Loại trừ: code/runtime, cài dependency, đổi service/business, thêm admin/runner hoặc deploy/commit.
- Context/rule: AGENTS, checkpoint MS04, requirements/module/quality; MS01–04 và source FE/BE liên quan. Một luồng docs-only; không agent/chat mới, không tự coi review là CODE.
- Plan: đối chiếu nguồn → ghi finding/expected → sửa contract/dependency/owner trong phạm vi → kiểm link/ID/DAG và handoff.
- Acceptance: D14-AC01 rà business/auth/data/test/ops; AC02 mỗi finding có trigger/ảnh hưởng/owner/quyết định và phân biệt kế hoạch/runtime; AC03 nguồn hiện hành nhất quán, invariant giữ; AC04 static checks và scope đạt, trạng thái runtime chưa thay. AC chi tiết trong đầu ra chính.
- Kiểm tra: walk-through theo expected riêng, dependency không vòng và môi trường test có trước consumer; primary sources khi cần xác minh kỹ thuật, không app build/E2E hoặc tuyên bố runtime PASS.

Checkpoint BL-D14 — hoàn thành 01/10/2026:
- D14-AC01–04 đạt ở review tài liệu:5 findings RV-F01–05 gồm3P1/2P2 có trigger/ảnh hưởng/owner/expected và bổ sung vào nguồn hiện hành; không exploit/runtime findings hoặc controls đã PASS. Giữ business R1,3service, owner server, viewed/proven/applied, journal trước disclosure, admin/runner sau.
- Sửa6 dependency edges thiếu: R14/R24a→R31a;R03/R06a→R02b;R12→R01d;R30→R31c. R05 tự tạo standalone catalog validator/CMD-04 branch, không claim full HTTP/trust. R31a base services/edge/TLS CMD-07 trước FE, R24 auth fixtures sau; targeted/partial layers vẫn không full-case PASS. Runtime graph73IDs/58leaves giữ, edges163→169, không vòng; MS04 checkpoint trước giữ lịch sử.
- Chốt public API8080 HTTPS/mTLS edge-only/private8443 service-operator-only, upstream CA/SAN/SNI/client identity, giữ prefix /api/v1 và no private proxy request/response temp/cache/store/log; actual sentinel chunked/slow/limit/export/error tests SEC-14/28 thuộc R31a. Common wire catalog §4.1: bigint decimal string/range, JSON duplicate/encoding/privileged keys, error/status/pending/version, idempotency và cursor owner/epoch/asOf;6WIRE fixtures có leaf owner/FX/CMD/evidence, REL-02 giữ coverage.
- Lifecycle §5.1: durable maintenance state ngoài snapshots/env/operation/generation, stop toàn writer/restart fence/NOLOGIN/drain confirmed disconnect, fixed journal heads và dumps; incomplete/crash/stale resume giữ đóng. Resume đúng prior role attrs, maintenance recovery pump có allowlist trước RUN để không tự deadlock pending operations. FX-08/DATA-24/30/REL-07 còn actual reports R32a/b, không online/HA/zero-loss.
- R15/R20 có checkpoint demo/review sớm; R34 learner acceptance vẫn bắt buộc. Demo/prototype/technical review không giả feedback learner. Host/provider/key/recovery/tải là input/evidence có owner gần lúc cần, chưa input business mới để chặn R01a.
- Files: architecture/readiness-review.md là đầu ra chính; implementation-plan/test-release/service-boundaries/lifecycle-recovery/security/FE/BE/index, backlog/trace/DoD/quality/LLD-07/README đồng bộ. Nguồn primary01/10 RFC7493/Nginx/PostgreSQL dump/role attributes/server signaling đã đọc, links trong report/protocol; convention/interlock là quyết định app.
- Kiểm Python từ root bằng PowerShell here-string (`@'…'@ | python -`):61Markdown/604local links, encoding/fences/whitespace,73unique IDs/169edges/DAG, command-creator dependencies (R05 standalone explicit), leaf owners SEC30/DATA30/WIRE6/FX10/CMD11/REL8,28 API routes/owner/collision và18REQ trace,5findings/4AC/state/scope — PASS, exit0 tại review. SHA256 package/lock khớp R00; backend không pom/Java, helper services.ps1 chưa có. Không build/app/E2E/DB/mTLS/provider/scan/backup/drill/Mermaid render/learner test vì chỉ docs; không commit/deploy/agent/chat mới.
- Bàn giao: D14/MS01–04/R00 DONE ở phạm vi của chúng; mọi runtime parent/leaf PLANNED, CODE chưa có. Bước đầu vẫn R01a theo brief/toolchain; không cần thêm service/framework/rule lặp, không tự chuyển sang runtime từ yêu cầu review.

### BL-D13 — Review chuyển backend sang microservices [DONE]
- Goal/đầu ra: architecture/microservices-review.md (ADR-03), architecture BE đề xuất và tác động system/backlog.
- Dependency: yêu cầu hướng microservices trực tiếp30/09/2026, D08–12/R00. Requirements: REQ-09/12–16/18; MD01/04–07/09–11.
- Phạm vi: ranh giới service/data/security/transaction/recovery/ops, findings và owner bước thiết kế tiếp theo; thay topology cũ rõ ràng.
- Loại trừ: triển khai runtime, quyết định transport/framework mới chưa được phân tích, tự nới cam kết logout/withdrawal hoặc đóng findings bằng sơ đồ.
- Acceptance: có đề xuất boundaries/source, trade-off, findings theo mức, kế hoạch xử lý; phân biệt hướng người dùng chọn với chi tiết đề xuất; baseline cũ không còn là chỉ dẫn code. Review DONE không đồng nghĩa microservice design READY.
- Kiểm tra: đối chiếu S02/S03/D05/D08, nguồn kiến trúc chính thức/primary, links/tasks và invariants có owner.

Checkpoint BL-D13 hoàn thành30/09/2026:
- Đầu ra [ADR-03/review](../../architecture/microservices-review.md), architecture BE mới và baseline modular lưu riêng; cập nhật system/HLD/LLD/source index/requirements/module/DoD/traceability/README/AGENTS với cổng microservices. User chọn hướng;3service và HTTP outbox là đề xuất cần MS01/MS03 chốt.
- Review9finding MS-F01–09: giữ T1/T2 local Learning, revoke/commit, publication lag, delete/restore nhiều DB, service trust, retry/events, ops/test, quyền content body và rebaseline. Chưa đóng các P1 ở mức LLD/runtime; MS01–04 có owner/output/AC/ngoài phạm vi và thứ tự. D13 DONE chỉ là đầu ra review, không khẳng định architecture phân tán READY.
- Nguồn30/09: Microsoft boundaries, microservices.io database-per-service/outbox và OWASP microservices security, links ở báo cáo. Quyết định áp dụng cho app phân biệt với khuyến nghị nguồn; không claim benchmark/security audit.
- Kiểm Python:346link local trong52tài liệu,9finding duy nhất,4task MS tồn tại, R00 DONE/34task R01–34 PLANNED, backend chưa pom — PASS trước checkpoint. Review thủ công invariant S02/S03, scope/permission và baseline superseded. Không build/test app hoặc render Mermaid vì chỉ đổi docs; không service/DB mới.
- Tiếp theo MS01 READY, MS02–04 PLANNED. Chưa cần input bổ sung để làm rõ boundaries theo đề xuất; nếu đổi cam kết business logout/withdrawal phải nêu để người dùng xem. Không tự chạy R01 topology cũ.

### BL-MS01 — Chốt service ownership và API boundaries [DONE]
- Goal/đầu ra: [service catalog/API ownership matrix](../../architecture/service-boundaries.md) và architecture source v3; REQ-01/09/14–16/18. Dependency: D13. Brief/AC-ID trong catalog mục 1.
- Scope: Identity/Content/Learning theo đề xuất, mapping use case→owner→DB, endpoint public/internal và pinned content replica. Loại trừ: code, chọn broker hoặc security algorithm trước MS02.
- AC: mỗi dữ liệu có owner, không shared DB/cross-service import; build/deploy độc lập; outline anonymous/body login thống nhất; giao diện FE và service contract có nơi sở hữu. Review MS-F01/08.
- Test/review thiết kế: walk-through login→attempt→T1/T2 và outline; links/ID/reference, không giả runtime.

Checkpoint BL-MS01 hoàn thành 30/09/2026:
- Session goal/đầu ra: chốt ba service Identity/Content/Learning, [service catalog/API boundaries](../../architecture/service-boundaries.md) và [source BE](../../architecture/backend.md); cập nhật source FE/system/module/HLD/index/requirements/traceability/quality và con trỏ repo. Brief, rules, plan và MS01-AC1–5 ở catalog mục 1; acceptance đạt ở mức thiết kế.
- OWN-01–14 có canonical/replica/lifecycle owner; 26 browser routes API-I/C/L, 6 namespace INT và 3 contract OPS có receiver/caller/contract source. Outline anonymous, body login; private case/hint/solution qua Learning exposure. MD06 persistence theo từng service, MD02/07 definition Content nhưng guidance/readiness cá nhân Learning. FE ghép shared TOP/CAP theo stable ID và revision.
- MS-F01 xử lý ranh giới T1/T2 local Learning, không network trong transaction; MS-F08 xử lý quyền body/support. Review MS01-W01–10: login→attempt→draft/support→T1→worker/T2→history, A/B, auth/content outage, publish/reset/delete và release. Logout không tự hủy job đã nhận; account lifecycle gồm auth metadata theo user nếu service lưu. Findings chưa đóng ở mức protocol/runtime.
- Kiểm Python từ root (đọc Markdown UTF-8, kiểm local links/code fences/ID/table, chuẩn hóa path parameters, route owner, trạng thái task và scope): 378 links trong 52 tài liệu trước checkpoint, 14 OWN/26 API/9 INT-OPS/5 AC/10 walk-through duy nhất, không route collision — PASS. R00 DONE, R01–34 PLANNED; backend chưa pom/Java. Review thủ công boundary và nguồn S02/S03/D05/D08. Không build/E2E/API/DB test hoặc render Mermaid vì chỉ sửa tài liệu; không claim runtime microservices đã có.
- Giới hạn: internal namespace/owner đã chốt, verb/subpath/schema/admission/revoke ở MS02; publication/transport/lifecycle/journal/restore ở MS03; lệnh build/deploy/compatibility test và R01–34 rebaseline ở MS04. Không chọn broker, đổi business, cài dependency hoặc tạo service runtime.
- Bước tiếp theo: MS02 READY — dùng catalog và INT-01/02/06 để đặc tả security/phiên/threat model; MS03/04 PLANNED. Chưa cần thêm input của người dùng để bắt đầu thiết kế; runtime/nghiệp vụ R1 vẫn cần chỉ dẫn code ngoài R00 đã được cấp.

### BL-MS02 — Security và phiên trong hệ phân tán [DONE]
- Goal/đầu ra: security design/threat model + identity/revocation LLD v3; REQ-09/13–15. Dependency: MS01.
- Scope: caller/resource matrix, service identity, session/CSRF/revoke barrier, XSS/SQL/headers/secrets/scanning/incident control và acceptance/test; giữ S02 hoặc nêu thay đổi business cần review. Loại trừ: admin/MFA/signup công khai/code.
- AC: MS-F02/05/08 được xử lý ở contract, session registration/revoke race/expiry/partial failure có expected; không coi introspection đầu request là commit guard; mỗi control có task/test owner.
- Review: giả mạo caller/header, A/B, partition, late request, credential rotation; đọc nguồn bảo mật phù hợp.

Checkpoint BL-MS02 hoàn thành 30/09/2026:
- Goal/đầu ra: threat model/control matrix [security design](../../architecture/security-design.md) và session/admission/revocation LLD v3 [session protocol](../../features/f10-identity-access/sessions/protocol.md), nối ownership ở [catalog](../../architecture/service-boundaries.md); đồng bộ identity/S03/system/HLD/LLD/index/backlog. REQ-09/13–15; MS02-AC1–5 đạt ở mức thiết kế. MS-F02/05/08 có protocol/control/expected, chưa có implementation.
- Invariants AUTH-INV01–08: Identity opaque session; mỗi protected request online admission; CNT/LRN static participants dù chưa có local session; monotonic local DB gate dùng chung với mutation, deny không bị install trễ hồi sinh; Identity chỉ COMPLETED sau durable ACK cả hai; request đã admitted trước đó có thể kết thúc trong REVOKING, UI báo pending; logout không hủy job đã nhận; T2 kiểm epoch/eligibility/job riêng.
- API-I08/09 và INT-01/02/06 có wire shape/purpose/request binding/idempotency; reauth chỉ dùng purpose/intent tương ứng. Threat model TH-01–11, caller/resource matrix, CTL-01–16, SEC-01–30; bao gồm A/B, forged headers, late admission, CSRF, cert audience/rotation, XSS/SQL, secrets/cache/logs, abuse limits, backup/readiness. Logout/status không clear cookie cũ bằng Set-Cookie để tránh response cũ xóa cookie phiên mới; cookie bị revoke phía server và có thể còn trong browser đến khi bị thay/hết hạn. Đây là tradeoff cleanup được ghi công khai, không phải browser cookie đã bị xóa.
- Kiểm tài liệu: 438 local links trong 54 tài liệu, code fences cân bằng, 28 route và owner duy nhất/không collision, AUTH-INV 8, TH 11, CTL 16, SEC 30, AC 5, trạng thái MS01/02 DONE, MS03 READY, MS04 PLANNED, R00 DONE/R01–34 PLANNED — PASS. Nguồn primary OWASP/Spring; password/session/CSRF guidance đối chiếu tại thời điểm làm. Review thủ công thứ tự lock/admission/fence/ACK, absent-session install, lost ACK/retry, expiry/clock budget, KDF ngoài lock, scope reauth, logout A/login B, T1/T2 sau logout và fail-closed khi dependency down. Không chạy race/DB/browser/mTLS/SCA test và không tuyên bố runtime controls đã đạt.
- Giới hạn: cookie revoked có thể còn ở browser đến replacement/hard absolute expiry (tối đa12h); server từ chối ngay. Điều này lệch khuyến nghị xóa cookie client tức thì để tránh response logout cũ xóa cookie login mới; MS04 phải review/test hard max-age, replay deny và response reorder, hoặc đề xuất cơ chế generation-specific nếu cần xóa ngay. Publication/content activation, delete/reset/restore realm/journal thuộc MS03; runtime config, listener/cert/clock/KDF benchmark, artifact scans/test commands và R01–34 mapping thuộc MS04. Microservices chưa READY code.
- Bước tiếp theo: MS03 READY — chốt publication, event/lifecycle/reset/delete/backup/restore và failure/replay contract theo INT-03/04/05, OWN-12/13 và SEC-13/16. Chưa cần input business mới; giữ logout barrier, history, owner và admin sau R1.

### BL-MS03 — Data/events/publication/lifecycle và restore [DONE]
- Goal/đầu ra: contract phân tán v3, event schema/sequence/state/failure matrices; REQ-05/06/09/10/15/16. Dependency: MS02.
- Scope: local T1/T2, content activation/withdrawal, chọn transport có lý do, outbox/inbox, reset/delete/restore nhiều DB. Loại trừ: code/runner, exactly-once hoặc global transaction giả.
- AC: MS-F01/03/04/06 có giải pháp, linearization/ACK/idempotency/ordering/replay rõ; fail/timeout không ghi pass/xóa thành công giả; restore watermark/reference/tombstone đủ test.
- Review: crash trước/sau ACK, duplicate/out-of-order, poison event, auth/content outage, publish/T2 và delete/restore/replay.

Đầu ra review: [data protocols](../../architecture/data-protocols.md) (MS03-AC1–5, DATA-INV01–10, EVT-01–05, DATA-01–30 và INT-03/04/05); [publication LLD](../../features/f08-content/publication/protocol.md) (PUB-01–07); [lifecycle/recovery LLD](../../architecture/lifecycle-recovery-protocol.md) (JRN-01–04, LIFE-01–06). Design only, chưa app/DB/service runtime.

### Checkpoint BL-MS03 — hoàn thành 30/09/2026

- MS03-AC1–5 đạt ở mức thiết kế: DATA invariant/matrix/wire; publication state/linearization; lifecycle journal/epoch/tombstone; multi-DB restore readiness và source/findings trace. MS-F01/03/04/06 có giải pháp protocol; không ghi runtime findings PASS.
- Chọn authenticated HTTP relay + local outbox/inbox/exact bytes/digest/sequence/ACK/retry; auth fences delivery độc lập, poison/gap giữ pending/quarantine và audited maintenance recovery. T1/T2 cùng Learning, không network/journal dưới domain locks.
- Publication STAGE→FENCE ACK→Content canonical COMMIT→Learning RELEASE; mất ACK/participant down giữ pending, không serve old current mastery sau commit. Cosmetic/shared CAP/history/current policy/withdrawal/invalid rubric có expected, không sửa historical result.
- Reset intent→external journal→Learning epoch/receipt; delete Identity auth barrier→terminal journal→mandatory Content/Learning suppression ACK→logical deleted→live purge. Exposure/case/hint/solution/import known floor durable ngoài snapshot trước disclosure/final, active floor chỉ compact bằng durable checkpoint. Reset simulation riêng, reset học giữ exposure/account; retry cũ không xóa work epoch mới.
- Backup controlled application checkpoint ba DB, manifest/inventory/cursors/private bundle refs; isolated restore delete/reset/exposure/account state→new realm/generations→publication reconciliation→projection/jobs→readiness. Restored accounts RECOVERY_REQUIRED ngăn old password sống lại; orphan/missing journal/provenance giữ quarantine. Work RPO có thể mất sau snapshot, không zero-loss/SLA claim.
- Đồng bộ catalog/architecture FE/BE/security/session, HLD và LLD-01–04/06, S03/D08 banners, business/requirements/module/trace, README/PROJECT_PLAN/TASKS/backend README/AGENTS. D13 và checkpoint cũ giữ lịch sử; không đổi 28 browser routes hoặc owner, chỉ wire INT-03/04/05/state/schema dự kiến.
- Kiểm Python từ root (đọc UTF-8 Markdown/regex tables/link resolution): 521 local links trong 58 tài liệu, code fences/whitespace/encoding, unique 10 DATA-INV/30 DATA cases/5 EVT/7 PUB/4 JRN/6 LIFE/5 AC, 28 browser routes collision-free/đúng owner, 5 internal wire paths, 18 REQ/12 MD references và trạng thái/scope — PASS. MS01/02/03 DONE, MS04 READY, R00 DONE/R01–34 PLANNED. Review thủ công publish/T1/T2, hint/submit/reset/delete, lost ACK/poison, mixed snapshots/old credentials và exposure floors. Không app build/E2E/API/DB/journal fault/drill hoặc render Mermaid vì docs thuần; backend vẫn không pom/Java, không commit/deploy.
- Nguồn primary đã đọc: transactional outbox/idempotent consumer và PostgreSQL SQL dump; links ở data/recovery LLD. HTTP/gates/journal/credential recovery là quyết định riêng của app; không suy ra bảo đảm durability mặc định từ nguồn.
- Bàn giao MS04 READY: rebaseline R01–34 và task con, provider/durability/backup checkpoint/quiesce/credential recovery ops, numeric retry/clock/quotas, executable contracts/cert/build/test/CI/release gates; DATA-01–30 nối SEC/IA/LD/AP/CA/UX fixtures/owners. MS-F07/09 còn kế hoạch MS04. R00 DONE, R01–34 PLANNED; chưa code nghiệp vụ, không cần input business mới để làm MS04.

### BL-MS04 — Rebaseline triển khai/test/ops microservices [DONE]
- Goal/đầu ra: [implementation plan v3](../implementation-plan.md), [test/release plan](../test-release-plan.md), backlog R1 v3 và release DoD; REQ-11/13/14/15/16 + trace R1. Dependency MS03 DONE, MS04-AC1–5 ở plan.
- Scope: cập nhật hoặc tách R01–34, giữ ID lịch sử; compose/service artifacts/DB credentials, tracing, contract compatibility và recovery; baseline versions phải được kiểm khi chọn. Loại trừ: tự code/deploy/mua cloud.
- AC: MS-F07/09 đóng ở kế hoạch, không còn P0/P1 kiến trúc chưa có giải pháp; mỗi task đủ input/owner/test, DAG không vòng; runtime permission vẫn theo chỉ dẫn người dùng.
- Review: trace toàn requirements, service lifecycle/fault/release/restore và source boundary; không coi R00 FE tests là microservice tests.

Đầu ra review:35 ID R00–34 giữ,38 child/14 parent/58 runtime leaves; SEC30/DATA30 từng ID có leaf owner/fixture/command, FX10/CMD11/REL8. R28a journal trước allocation/support/import; R32b staging restore trước R31d pilot, không cycle. R01a brief đã điền, mọi runtime PLANNED/CODE chưa có. Không tạo service/config/helper mới hoặc chạy app tests trong task docs này.

### Checkpoint BL-MS04 — bắt đầu 30/09, chốt 01/10/2026

- MS04-AC1–5 đạt thiết kế/kế hoạch: implementation-plan.md giữ stack/topology/config/secrets/S3 qualification/budgets/session/inputs; test-release-plan.md giữ SEC/DATA từng ID leaf owner/fixture/command/expected/evidence, CI/compatibility/REL gates. MS-F07/09 có giải pháp kế hoạch; MS-F01–06/08 protocol giữ, không runtime findings PASS hoặc self-approve CODE.
- Rebaseline ngay backlog hiện hành:73 unique IDs gồm35 R00–34 giữ/38 child;14 parent rollup/58 runtime leaves,163 dependency edges không vòng. R28a local journal trước R08/R10/R27, R32b actual-provider staging restore trước R31d pilot. Parent không giao nguyên session, không tạo chat/agent/active goal; mỗi leaf inherits DONE/REQ13 và có owner/REQ/input/deps/AC/test/exclusion. R00 checkpoint/bằng chứng lịch sử giữ nguyên.
- Stack baseline Java21/Boot4.1.1/Maven3.9.16/PG18.6 đối chiếu primary30/09; giữ FE/runtime/helper/lockfile. Actual JDK patch/checksum/image/CLI/dependency/advisory evidence R01a/R31c, không install/download/service/DB từ task docs. Source public/internal listeners, per-DB roles/migration, online admission/mTLS/relay/epoch/pub/journal vẫn theo MS01–03.
- Chọn S3 API production adapter với record/index/CAS head/readback/prefix/compaction qualification, actual provider/keys/namespace input R28b; local filesystem chỉ lab, không proof offhost. R31a edge/Compose, R31b correlated bounded metrics/budget measurement, R31c build/scans/SBOM/N-N-1/registry/local-CI parity, R31d explicit pilot scope; R32a/b checkpoint/drill trước traffic, credential RECOVERY_REQUIRED không dùng old hash. Không auto cloud/deploy/schedule hay SLA/zero-loss.
- Test planning:30SEC/30DATA/10FX/11CMD/8REL/5AC unique; IA24/LD30/AP22/CA14/CM25/UX20 nguồn và registry plan. R04d receiver-gate fixture không đủ actual T1/T2, R11/R13b phải có submission/evidence oracles. Missing required suite/0tests/skip fail release; mock/FE smoke không chứng minh distributed rights/durability. CMD-01 FE có thật, CMD-02–11/helpers còn dự kiến; chưa chạy.
- Đồng bộ architecture index/FE/BE/security/session/data/recovery/findings, HLD/LLD-07/index, system/REQ/module/D08 banners, trace/DoD/quality/execution/R01a brief và root README/PROJECT_PLAN/TASKS/backend README/AGENTS. R0/D13/D08 và checkpoint cũ là history, không sửa số test/link lịch sử thành evidence mới.
- Kiểm cuối 01/10/2026 từ root bằng PowerShell here-string Python (`@'…'@ | python -`, đọc UTF-8/link/fence/whitespace/encoding/table IDs/DAG/reference/registry/state/scope):586 local links trong60Markdown,73 task IDs/163edges/DAG/leaf-owner coverage,28 API routes collision-free/owner đúng,18REQ trace,SEC30/DATA30/FX10/CMD11/REL8/AC5 — PASS, exit0, không findings; MS01–04 DONE/R00 DONE/runtime parent-leaf PLANNED. Get-FileHash và Python hashlib xác nhận frontend package/lock SHA256 khớp checkpoint R00 (`1325BF98729E8ED2F5E1132EA6E3E32699E95434171A26877528035DCD6C5AC8`, `086DCFE75DD85EC84B8CD8479DE3F57F40E38DBB0FB35B1241925F9D279F71D4`); backend không pom/Java, scripts/services.ps1 chưa có. Git vẫn untracked từ trước, không dùng diff làm bằng chứng baseline; edits chỉ docs, không commit/deploy.
- Review thủ công AC/findings/source ownership/dependencies/permission: no parent-child self-cycle, support journal cần trước case, restore drill không chờ deployment, pub history/current distinction, revoke-cookie A/B, provider inputs không chặn local foundation. Không build/E2E/API/DB/mTLS/storage fault/scan/benchmark/drill/render Mermaid hoặc learner test, vì pure docs; không tuyên bố chúng PASS.
- Handoff: MS01–04 DONE ở design, R00 DONE, tất cả runtime parent/leaf PLANNED. **Task kế tiếp R01a**, brief trong task-brief-template.md, chờ người dùng cho phép chuyển sang code theo backlog v3. Khi có CODE ghi scope/chỉ dẫn thực rồi R01a READY; không xin lại ở leaf sau nếu authorization đã bao phủ. Pilot host/provider/recovery channel/learner input có owner gần lúc cần, chưa cần input business mới.

### BL-D12 — Cập nhật layout FE/BE cùng repo [DONE]
- Goal/đầu ra: ADR-02 trong implementation-design, HLD/LLD layout mới và BL-R00 trước R01.
- Dependency: D11 và yêu cầu cập nhật của người dùng. Requirements: REQ-11/13; MD09/nền repo.
- Phạm vi: thiết kế frontend/backend riêng thư mục, docs/contracts/ops root; mapping file/helper/test và dependency.
- Ngoài phạm vi: di chuyển source, sửa config/runtime, cài dependency, backend hoặc CODE authorization.
- Acceptance: layout không còn hai hướng đích, inventory hiện tại phân biệt rõ; R00 có đầu ra/DoD/dependency, R01 chờ R00; command hiện tại chưa đổi; cập nhật con trỏ dự án.
- Kiểm tra: links, DAG/35 task IDs, review ownership thư mục và bảo toàn lockfile/hành vi trong DoD di chuyển.


Checkpoint BL-D12 hoàn thành 30/09/2026:
- ADR-02, HLD/LLD-07/system, task brief R01, traceability và con trỏ README/PROJECT_PLAN/TASKS đã đồng bộ. Thêm R00 PLANNED; R01 phụ thuộc R00; không đổi ID các task cũ.
- Python kiểm35 ID duy nhất/dependency hợp lệ/DAG không vòng và103 link local trong10 tài liệu — PASS. Kiểm src/package vẫn ở root; chỉ sửa docs, không di chuyển code/config hay đổi lệnh thực thi hiện tại. Không chạy FE suite vì chưa thay sản phẩm.
- DoD di chuyển yêu cầu giữ dependency/lockfile, sửa helper cwd/relative path/ignore/output và kiểm content/check/build/smoke/dev/preview/deep link. Các kết quả runtime đó còn thuộc R00, chưa được coi là đạt.
- Tiếp theo R00 khi có chỉ dẫn CODE; yêu cầu hiện tại chỉ cập nhật architecture/kế hoạch, chưa cho phép di chuyển source hoặc dựng backend.

### BL-D11 — Hợp đồng goal/context/DoD và task brief [DONE]
- Goal/đầu ra: delivery-contract.md, delivery-traceability.md, task-brief-template.md và ví dụ R01; nối project đến từng phần bằng nguồn/evidence rõ.
- Dependency: D10 và yêu cầu bổ sung trực tiếp của người dùng. Requirements: REQ-01–18, mọi module theo phạm vi release.
- Phạm vi: Ready/Done theo cấp, 18 REQ trace, brief/context/rule/plan/test/handoff; sửa tham chiếu template/I14 cũ.
- Loại trừ: code, active goal công cụ, skill mới, tạo session và coi mọi mục học đã chi tiết.
- Acceptance: đủ 6 cấp,12 module,18 REQ; mẫu có goal/scope/context/rules/plan/AC/test/DoD/handoff, một ví dụ cụ thể chưa giả runtime; nguồn và trạng thái thống nhất.
- Kiểm tra: links/IDs/coverage, review DoD kế thừa và phân biệt design-complete/published; không chạy app test.


Checkpoint BL-D11 hoàn thành 30/09/2026:
- Đầu ra: [delivery-contract](../contract.md), [traceability](../traceability.md), [task brief](../task-brief-template.md); 6 cấp,12 module,18 REQ, DONE-01–05 và ví dụ R01. Đồng bộ execution-model/topic-template/design index; I14 learner test đổi thành R34 hiện hành.
- Kiểm tra Python: 18 REQ duy nhất,12 module,6 cấp,62 link local trong7 tài liệu, code fences cân bằng,34 BL-R vẫn PLANNED — PASS trước checkpoint. Review thủ công nguồn/DoD/evidence dự kiến và R01 command chưa tồn tại; không giả test hoặc cấp phép CODE.
- Acceptance đạt ở mức tài liệu; không tạo95 hồ sơ rỗng. Mỗi task sắp thực hiện điền brief theo mẫu; từng mục học dùng template khi biên soạn. Không thêm code/dependency/skill/session hoặc active goal công cụ.
- Bước tiếp theo: người dùng review HLD/LLD và hợp đồng; R01 vẫn cần chỉ dẫn chuyển sang code đã nêu trong AGENTS.

### BL-D10 — Bộ tài liệu review HLD và LLD [DONE]
- Goal/đầu ra: docs/design/README.md, HLD và LLD theo phần để người dùng review từ tổng thể đến chi tiết.
- Dependency: D09 và yêu cầu trực tiếp 30/09/2026. Requirements: REQ-01–18; toàn module theo phạm vi R1, admin sau R1.
- Phạm vi: tổ chức lại quyết định hiện hành, sơ đồ, data/API/state/failure/test và liên kết nguồn; không đổi business hay triển khai code.
- Loại trừ: SQL/OpenAPI thực thi, dựng backend, runtime test, tự mở session.
- Acceptance: có đường đọc HLD→LLD, mỗi phần có trách nhiệm/luồng/dữ liệu/lỗi/kiểm tra/điểm review, ID phản hồi và nguồn; không trình bày thiết kế là tính năng đã chạy.
- Kiểm tra: links, ID review, bao phủ module/requirements và đối chiếu các invariant S02/S03/D05/D08.


Checkpoint BL-D10 hoàn thành 30/09/2026:
- Đầu ra: [bộ review](../../design/README.md), HLD và 7 LLD (identity/content/assessment/progress/experience/data/platform); 25 ID góp ý, sơ đồ architecture/entity/sequence/state, API/data/failure/test và nguồn theo từng phần. README dẫn vào bộ review.
- Acceptance: người dùng đọc tổng thể trước rồi từng module; phạm vi/giới hạn và nguồn quyết định rõ. Không thêm backlog thực thi thứ hai. Làm rõ kiểm credential version/account khi login sau KDF, đồng bộ D08 để tránh race reset/disable.
- Kiểm tra Python: 9 tài liệu,53 link relative,21 ID LLD duy nhất +4 ID HLD, code fence cân bằng — PASS. Kiểm loại block Mermaid và đọc luồng bằng tay, chưa render sơ đồ bằng renderer. Review đối chiếu invariant owner/shared/progress/T1-T2/reset với S02/S03/D05/D08; không chạy app/DB/E2E vì không sửa code sản phẩm.
- Giới hạn: LLD là hợp đồng review, chưa là SQL/OpenAPI thực thi; chưa có CODE hoặc sự chấp thuận thiết kế từ người dùng trong task này. Tiếp theo nhận phản hồi HLD/LLD; BL-R01 chỉ bắt đầu khi có chỉ dẫn code.

### BL-S01 — Điều chỉnh phạm vi nền tảng nhiều user [DONE]
- Goal/đầu ra: system-overview v2 và phạm vi/backlog nhất quán theo yêu cầu mới.
- Requirements: REQ-01,09,14–18; F01/F07/F10–12, MD01/04–06/09–12. Dependency: yêu cầu mới của người dùng, D05/D06a đã có.
- Phạm vi: backend/login/logout, bài tập/nháp/submission/progress server, roadmap DevOps, admin sau R1; thay phạm vi local-only. Không triển khai code hoặc cấu hình auth/DB.
- Acceptance: owner dữ liệu và biên FE/BE rõ; admin không chặn R1; Docker nằm trong DevOps; ID dùng chung giữ nguyên; tài liệu hiện hành/backlog không còn dẫn thực thi local-only.
- Kiểm tra: review yêu cầu mới → system/module/task, links và ID, phân biệt đề xuất stack với triển khai thật.

Checkpoint BL-S01 hoàn thành 28/09/2026:
- System v2, business/REQ-14–18, F10–12/MD10–12, roadmap DevOps DS00–DS11/DP0–DP5 và thứ tự task đã cập nhật. Admin sau R1 theo câu trả lời trực tiếp của người dùng.
- Baseline BL-I local-only đã ghi lịch sử; D06b chờ contract server. D05 cập nhật owner/server authority/import/AP-17, giữ quy tắc năng lực; TOP/CAP hiện có không đổi.
- Review phạm vi, module, nguồn dữ liệu và đường học: đạt acceptance. PowerShell đọc 16 tài liệu kiểm 95 link local: PASS; rà con trỏ hiện hành và ngoại lệ lịch sử. Không chạy app/DB/Java, không thay code hoặc dependency; stack vẫn là đề xuất đến D08.
- Tiếp theo BL-S02; S01 DONE không chứng minh authentication hay catalog DevOps cấp mục đã hoàn chỉnh.

### BL-S02 — Hợp đồng tài khoản, phiên và quyền [DONE]
- Goal/đầu ra: docs/identity-access-design.md, đủ hành vi cho login/logout và cách cấp tài khoản thử nghiệm nhiều user.
- Feature/module/requirements: F10, MD10/09; REQ-14/15. Dependency: BL-S01.
- Phạm vi: cấp tài khoản/định danh/mật khẩu và phục hồi, lifecycle phiên/login/logout/hết hạn, owner resource, cache/nháp khi đổi user; nguyên tắc role admin tương lai.
- Loại trừ: triển khai auth, admin UI, SSO/đa tổ chức, chọn provider email thực tế nếu chưa cần.
- Acceptance: ma trận learner/anonymous/admin chưa bật; server enforce ownership; quy trình cấp/khôi phục tài khoản có quyết định rõ; logout khi online/offline và request đang chạy không lộ dữ liệu. Không tự coi xóa cookie client là thu hồi server.
- Kiểm tra: case A/B cho đọc/sửa/list/submit/export, expired session, đổi user và giả owner; đối chiếu nguồn chính thức khi chốt kỹ thuật.

Checkpoint BL-S02 hoàn thành 28/09/2026:
- Đầu ra: [identity-access-design.md](../../features/f10-identity-access/business.md), tài khoản pilot theo lời mời, loginName/password, activate/reset một lần, operator bảo trì không phải admin UI, session/CSRF/logout và ownership.
- Acceptance: có cách cấp/khôi phục tài khoản cụ thể, ma trận anonymous/learner/role admin chưa bật; 24 case IA-01–24 cho A/B, phiên hết hạn, CSRF, logout online/offline, response/autosave trễ và commit trước/sau revocation. Không giả thu hồi server khi mới khóa UI.
- Review: đọc OWASP Authentication/Forgot Password và Spring CSRF/logout; rà chuỗi cấp tài khoản → login → học/nháp → hết hạn/đổi user → logout/reset. Ngưỡng TTL/throttle là quyết định cấu hình ban đầu, chưa là số đo hoặc default framework.
- Kiểm tra: PowerShell kiểm 23 link local trong 4 tài liệu liên quan và 24 ID case duy nhất — PASS (trước thêm link checkpoint này); review hành vi bằng tay. Không chạy app/backend test hoặc learner test; chưa triển khai auth, CLI operator hay storage.
- Bàn giao: BL-S03 chốt owner/context, draft RAM/server, concurrency/idempotency, transaction khi revoke và import. D07 chốt màn; D08 cấu hình/framework/API. Không còn quyết định business chặn trong scope S02; stack/cấu hình kỹ thuật chưa được coi là đã kiểm chứng.

### BL-S03 — Hợp đồng bài nộp và tiến độ server [DONE]
- Goal/đầu ra: docs/server-learning-data-design.md, vòng đời nháp/nộp/chấm/lưu nhất quán.
- Feature/module/requirements: F04/05/07/11, MD04–06/10/11; REQ-05–09,15/16. Dependency: BL-S02, D05.
- Phạm vi: ExerciseVersion/Draft/Submission/Attempt/Evidence/Progress, owner/version/revision/idempotency, lỗi chấm/DB/mạng, import/legacy/retention/backup; xuất bản nội dung từ repo tới server.
- Loại trừ: code DB/API, file upload tùy ý, runner, admin review engine.
- Acceptance: đề khác bài làm; draft sửa được khác submission bất biến; rubric ghim version; không cấp official mastery từ client/import giả; conflict và transaction không mất dữ liệu; cập nhật AP-17/18/19 và loại bằng chứng D05 cho backend.
- Kiểm tra: state/case matrix A/B, retry, lỗi giữa các bước ghi, đổi đề, nhiều tab, reset/export/import đúng owner; liên kết sang D06b/D08.

Checkpoint BL-S03 hoàn thành 28/09/2026:
- Đầu ra: [server-learning-data-design.md](../../features/f11-learner-work/data-contract.md), model/version/owner, revision/conflict, T1 nhận bài và T2 chốt kết quả, idempotency, nguồn evidence, publish/import/reset/retention/backup. Đồng bộ D05 AP-17/18/19 và nguồn bằng chứng; system/requirements/module và con trỏ bàn giao.
- Acceptance review: đề/version khác draft/submission; rubric ghim, một submission/attempt; không pass từ client/import; transaction rollback, job retry, exposure/hint đa tab và guard revoke/reset/publish có hành vi xác định. Có 30 case LD-01–30 và handoff D06b/D07/D08.
- Kiểm tra: PowerShell đọc link và ID — PASS 25 link local trong 5 tài liệu nghiệp vụ, 30 ID LD liên tục/duy nhất; review bằng tay từng acceptance và các ca race/lỗi/ngoài phạm vi. Chưa test app/Java/DB/restore hoặc người học vì chỉ đổi tài liệu; không cài dependency/code sản phẩm.
- Giới hạn: D08 còn chọn schema/lock/session store, cấu hình giới hạn/retention, job worker và đo restore; chính sách backup pilot chưa là SLA. D06b phải có evaluator/case thật trước khi tuyên bố CAP được chấm. Không còn acceptance thiết kế S03 chưa xử lý; tiếp theo S04, chưa chuyển sang code.

### BL-S04 — Catalog nối roadmap DevOps [DONE]
- Goal/đầu ra: docs/curriculum/devops-catalog.md và gap list, ánh xạ bộ khung DS00–DS11 vào TOP/CAP/prerequisite.
- Feature/module/requirements: F01/F08, MD01/02; REQ-01/02/10/18. Dependency: BL-S01, D03; chạy sau S03 theo WIP một task.
- Phạm vi: dùng lại ENV/CTR/OPS phù hợp, thêm mục còn thiếu cho đường cốt lõi, điều kiện vào DP0–DP5, phân biệt mở rộng; chưa biên soạn toàn khóa hoặc chọn mọi tool.
- Acceptance: đường vào không kéo toàn Java/P5/P7; 12 nhóm có mapping hoặc gap có owner; TOP/CAP không trùng; Docker là phần DevOps và Backend có liên kết chung; DP là mốc riêng không tự cộng progress.
- Kiểm tra: reference/DAG/coverage/readiness và walk-through người mới DevOps.

Checkpoint BL-S04 hoàn thành 28/09/2026:
- Đầu ra: [devops-catalog.md](../../curriculum/devops-catalog.md), 12 DS, 17 TOP/34 CAP mới, mapping shared, DP0–DP5, 12 case DV và 8 gap DG có owner. Catalog tổng cộng 95 TOP/190 CAP, vẫn planned; Docker thuộc DevOps, Backend dùng chung ID/evidence.
- Review phát hiện prerequisite Compose/CI/deploy/observability cũ kéo Spring/JPA. Đã sửa 12 hàng canonical trong core-foundations/core-security-operations về nền app mẫu độc lập ngôn ngữ, không đổi nghĩa CAP; giữ SEC-008 trực tiếp ở readiness P7 để không mất yêu cầu quyền của project Java. Đồng bộ roadmap/extension/system/requirements và con trỏ tiếp theo.
- Kiểm tra PowerShell đọc trực tiếp 4 catalog: PASS 95 ID TOP duy nhất/95 cặp CAP, reference hợp lệ, DAG không vòng; DP0–DP5 không kéo JAVA/COL/FILE/SPR/DATA/ENV-001; DP0 không SQL/Docker, DP0–DP4 không Kubernetes, DP3 không deploy pipeline, DP4 không IaC; P5 ⊆ P6 ⊆ P7. PASS đủ 12 DS/12 DV/8 DG duy nhất. Bộ đọc kiểm tra đã được giới hạn đúng bảng khai báo CAP, không tính bảng giải thích prerequisite là khai báo trùng.
- Review bằng tay 12 walk-through và ranh giới evidence/milestone; đối chiếu nguồn Kubernetes concepts/components, Terraform locking, Google SRE SLO cho phạm vi mới. Kiểm tra cuối PowerShell: PASS 95 link local trong 11 tài liệu thay đổi và con trỏ S04 DONE → D06b READY. Chưa chạy app/Java/Docker/cloud/learner test; chỉ thiết kế và kiểm tra tài liệu.
- Giới hạn: gap lesson/lab/provider/assessment DG-01–06 do E05/E03, graph/render do D08 (DG-07), provenance runner do E04 (DG-08). Không tuyên bố toàn khóa đã có nội dung hoặc app đã nhận catalog mới. Không còn acceptance thiết kế S04 chưa xử lý; tiếp theo D06b, chưa chuyển sang code.

### BL-S05 — Phạm vi admin theo dõi học viên [PLANNED, sau R1]
- Goal/đầu ra: docs/admin-design.md, quyền và luồng xem tiến độ/bài nộp/nhận xét.
- Requirements: REQ-15/17; F12/MD12. Dependency: R1 được nghiệm thu; không chặn nội dung R2 hoặc R1.
- Phạm vi đề xuất: danh sách user, lọc progress, xem submission, nhận xét và audit; khả năng đánh giá lại cần tiêu chí riêng. Không mặc định sửa điểm hoặc CMS đầy đủ.
- Acceptance/kiểm tra: ma trận quyền admin/learner, reason/actor/history và dữ liệu nào được xem; chưa READY đến khi phạm vi đủ cụ thể.

### BL-D01 — Catalog nền tảng S00–S03 [DONE]
- Goal: người mới có chuỗi mục học/năng lực rõ đến Collections và CLI lưu file.
- Feature/module: F01/F02/F08; MD01/02. Requirements: REQ-01,02,10.
- Input/dependency: bộ khung và đường học v1 đã có. Đầu ra: docs/curriculum/core-foundations.md.
- Phạm vi: mục học ID ổn định, năng lực quan sát được, prerequisite cấp mục, lõi/tùy chọn, chặng/mốc; chưa viết lesson/quiz/code.
- Nghiệm thu: mọi mục S00–S03 có đầu ra; đủ đường vào Collections; không prerequisite vòng/mất tham chiếu; các phần song song không bị biến thành khóa bắt buộc; ID được quy ước để dùng tiếp D02/03.
- Kiểm tra: rà bảng ID/reference/DAG và walk-through persona từ S00 đến P2; ghi phần cần ôn cho mẫu.

Checkpoint hoàn thành 23/09/2026:
- Session goal: tạo đường mục học có năng lực/prerequisite từ S00 đến Collections/P2; tiếp tục trong session hiện tại theo yêu cầu, không tạo hội thoại mới.
- Đầu ra: [core-foundations.md](../../curriculum/core-foundations.md), 28 topic (27 C/1 M), 56 competency A/B, quy ước TOP/CAP/version/status để D02/D03 dùng tiếp.
- Acceptance: đủ K/S/mức/P và năng lực; prerequisite all-of với selector competency khi chỉ cần một phần; đường vào Collections và ôn; readiness P1/P2 khác hoàn thành; sáu ca walk-through bảo vệ đường học không bị khóa thừa.
- Kiểm tra: script đọc bảng đạt unique ID, đủ competency, reference/selector hợp lệ, DAG không vòng, closure Collections/P1/P2 và link tồn tại. Review lại các nhánh song song, ngưỡng project và giới hạn nội dung. Không chạy app test vì không sửa app; chưa learner test/biên soạn lesson.
- Quyết định: Collections mẫu ghép các topic List/Set/Map/chọn cấu trúc, không tạo topic trùng progress; Git không chặn Java, Stream là mở rộng. Quyết định nằm trong catalog, không thay goal sản phẩm.
- Còn lại: không còn acceptance của D01 chưa xử lý. Rubric/đáp án/nguồn phiên bản thuộc D05/D06; D02 nối tiếp S04–S07 theo competency cần thật sự, không buộc phải hoàn thành P2 để đọc mọi bài.

### BL-D02 — Catalog HTTP, SQL, Spring và persistence [DONE]
- Goal: xác định mục học S04–S07 đủ để làm P3/P4.
- Feature/module: F01/F06/F08; MD01/02/07. Requirements: REQ-01,02,08,10.
- Dependency: D01. Đầu ra: docs/curriculum/core-api-data.md.
- Phạm vi: cùng cấu trúc D01; giữ SQL/HTTP song song; chưa viết bài chi tiết.
- Nghiệm thu: S06 không đợi SQL; S07 đủ SQL/API prerequisite; JDBC trước ORM; không vòng Docker/Testcontainers; năng lực transaction và test có đầu ra rõ.
- Kiểm tra: reference tới D01, nhánh hợp nhất và readiness P3/P4.

Checkpoint hoàn thành 23/09/2026:
- Session goal: nối catalog S04–S07 sau khi người dùng yêu cầu tiếp tục; cùng session, không tạo task hội thoại mới.
- Đầu ra: [core-api-data.md](../../curriculum/core-api-data.md), 26 topic C/52 competency mới; readiness P3/P4 và tám ca walk-through.
- Acceptance: HTTP/SQL độc lập, API trong bộ nhớ không chờ DB; JDBC trước ORM; SQL transaction trước transaction ứng dụng; P4 hợp nhất API/DB/test; không vòng Docker/Testcontainers.
- Kiểm tra: script đọc D01+D02 xác nhận 54 topic/108 competency, reference/selector/K mapping/link hợp lệ, không vòng; closure P3 không kéo SQL hoặc file adapter, SQL không kéo Java/HTTP/Spring, P4 có rollback/conflict/integration và không Docker. Review đường học/giới hạn đạt; chưa test code Java hoặc thử người học.
- Không sửa code app. Phần nội dung vẫn planned, không publish; không còn acceptance D02 chưa xử lý.
- Bước tiếp: D03 catalog security/operations và extension index, dùng lại TOP/CAP cùng hai catalog đã có.

### BL-D03 — Catalog security, vận hành và nhánh mở rộng [DONE]
- Goal: đủ catalog lõi S08–S10 và điểm vào cho các nhánh còn lại.
- Feature/module: F01/F06/F08; MD01/02/07. Requirements: REQ-01,02,08,10,12.
- Dependency: D02. Đầu ra: docs/curriculum/core-security-operations.md và extension-index.md.
- Phạm vi: mục học lõi chi tiết ở mức năng lực; nhánh mở rộng chỉ topic/entry condition, chưa phân rã thành hàng trăm bài.
- Nghiệm thu: 23 nhóm đều có ánh xạ; P5–P7 có readiness và bằng chứng; Docker dùng chung Backend/DevOps; kiểm tra quyền, recovery và giới hạn mô phỏng không thiếu.
- Kiểm tra: toàn graph cấp mục; rà coverage K01–K23 và P0–P7. Đóng phần catalog của B02 khi D01–03 đạt.

Checkpoint hoàn thành 23/09/2026:
- Session goal: hoàn thiện S08–S10 và chỉ mục nhánh mở rộng; phần chỉ mục được làm song song trong cùng task, cùng nguồn catalog và đã tích hợp khi review.
- Đầu ra: [core-security-operations.md](../../curriculum/core-security-operations.md) gồm 24 TOP C/48 CAP; [extension-index.md](../../curriculum/extension-index.md) phủ K01–K23, điều kiện vào theo TOP/CAP thật và một tiến độ dùng chung Backend/DevOps.
- Acceptance: P5–P7 có readiness/bằng chứng; anonymous/wrong owner/CSRF và dữ liệu cuối có test âm; container volume/network/health; release fail/rollback/restore được phân biệt; nhánh mở rộng có mức và điều kiện học, không nhân đôi Docker.
- Kiểm tra: script đọc ba catalog và chỉ mục đạt 78 TOP/156 CAP, S00–S10, 23/23 K, P0–P7, 51 tham chiếu TOP mở rộng và link hợp lệ, prerequisite DAG không vòng; closure P5 ⊆ P6 ⊆ P7, Docker cơ bản không buộc auth. Review ca biên trong tài liệu. Chưa chạy test app hoặc learner test vì chỉ thay thiết kế.
- Giới hạn: chưa có bài, đáp án, rubric chấm hoặc chứng thực công cụ/phiên bản lab; chúng thuộc D04–D06 và task nội dung/triển khai. Không còn acceptance của D03 chưa xử lý.
- Bước tiếp: D04 template mục học; D05 rubric/progress; D06 mẫu Collections. Phần B02 catalog đã DONE, chưa coi toàn R0 DONE.

### BL-D04 — Template chuẩn cho một mục học [DONE]
- Goal: tác giả có mẫu biên soạn nhất quán, không ép mọi chủ đề thành cùng một trò tương tác.
- Feature/module: F02/F03/F08; MD01/03/04. Requirements: REQ-02,03,04,10,12.
- Dependency: D03. Đầu ra: docs/topic-template.md.
- Phạm vi: mục tiêu, prerequisite/ôn, kiến thức, trực quan, luyện tập, bài kinh điển nếu phù hợp, assessment, áp dụng/ôn, nguồn/version/status; chưa chốt schema TypeScript.
- Nghiệm thu: trường bắt buộc/tùy chọn rõ; một ví dụ Collections và một đối chiếu Volume chứng minh template không lệ thuộc chủ đề; draft/published có checklist riêng.
- Kiểm tra: thử điền template, tìm mục thiếu và sự trùng lặp với business design.

Checkpoint hoàn thành 23/09/2026:
- Session goal: chuẩn hóa phần cần biên soạn cho một topic và kiểm chứng với hai loại kiến thức.
- Đầu ra: [topic-template.md](../../features/f08-content/authoring/topic-template.md), 15 phần với mức bắt buộc/điều kiện/tùy chọn; mẫu sao chép, ma trận CAP→học→luyện→kiểm tra→áp dụng/evidence, checklist planned/draft và published riêng.
- Điền thử: Map (TOP-COL-004) trong gói Collections và Volume (TOP-CTR-005), dùng CAP thật; cả hai là thiết kế planned, không giả lesson/đáp án/runtime đã hoàn chỉnh.
- Acceptance: prerequisite/ôn, knowledge/visual, classic problem nếu phù hợp, support/feedback, assessment, progress/áp dụng, nguồn/version/review đủ phần. Sơ đồ tĩnh không buộc mô phỏng; lab cần evidence đúng loại; không tạo progress trùng cho gói Collections/DevOps.
- Kiểm tra: script xác nhận 15 phần ở định nghĩa và ở mỗi ví dụ, TOP/CAP/link tồn tại, trạng thái planned/pending rõ; review mười tình huống và đối chiếu business. Không sửa source app, không test runtime hoặc xác minh nguồn kỹ thuật bài mẫu trong task này.
- Giới hạn: chính sách điểm/hint/retry và progress chuyển sang D05; bài/case/đáp án/nguồn Collections chuyển D06; schema/ID activity/storage chuyển D08. Không còn acceptance D04 chưa xử lý.
- Tiếp theo: BL-D05. DONE của template không làm các topic trong catalog thành published.

### BL-D05 — Hợp đồng assessment, evidence và progress [DONE]
- Goal: xác định chính xác khi nào được ghi đã xem/đạt/áp dụng/cần ôn.
- Feature/module: F04/F05/F07; MD04/05/06. Requirements: REQ-05,06,07,09,12.
- Dependency: D04. Đầu ra: docs/assessment-progress-design.md.
- Phạm vi: attempt/support/rubric/evidence, version, retry, reset, shared progress, resume, review; chính sách nhập/xung đột ở mức business.
- Nghiệm thu: bảng chuyển trạng thái và case expected result cho Next-only, hint/lời giải, pass/fail/retry, evidence trùng, version đổi, bài shared/planned, storage lỗi/import sai; không gộp mọi thứ thành một điểm.
- Kiểm tra: review invariant/case matrix. Chưa chọn storage library hay triển khai chấm.

Checkpoint hoàn thành 26/09/2026:
- Đầu ra: [assessment-progress-design.md](../../features/f04-assessment/evidence-contract.md), hợp đồng business ba chiều độc lập (đã xem, đạt từng CAP, đã áp dụng), bằng chứng theo rubric/version và quy tắc hỗ trợ/retry/ôn/lưu/nhập.
- Acceptance: bảng chuyển trạng thái phân biệt xem, luyện, assessment, áp dụng và cần ôn; ma trận AP-01–AP-22 bao phủ Next/replay, hint/lời giải, multi-CAP, retry/dedupe, đổi version, shared/planned, self-report/mô phỏng, readiness, lỗi lưu/import và reset. Không gộp thành điểm tổng; lịch sử pass không bị xóa khi cần ôn.
- Review: đối chiếu business design, topic template, catalog CAP, schema LessonProgress v1 và các invariant lưu trữ; xác nhận tiến độ legacy không tự ánh xạ thành đạt CAP, mọi đường dùng chung lấy cùng TOP/CAP. Làm rõ chuỗi trạng thái cũ chỉ là nhãn tóm tắt.
- Kiểm tra: review tài liệu và case matrix bằng tay; không chạy test app vì chỉ thay đổi business design. Chưa kiểm chứng với người học và chưa chọn/triển khai storage, rubric/case cụ thể của Collections thuộc D06.
- Bước tiếp: BL-D06 — biên soạn mục Collections hoàn chỉnh trên giấy, gồm ví dụ/đáp án/feedback và biến thể assessment.

### BL-D06 — Mục mẫu Collections hoàn chỉnh trên giấy [DONE]
- Goal: một nội dung đủ để xây và kiểm chứng trọn luồng học.
- Feature/module: F02–F06/F08; MD01/03/04/05/07. Requirements: REQ-02–08,10,12.
- Dependency: D05. Đầu ra: docs/content/collections-spec.md cùng ma trận ví dụ/đáp án.
- Phạm vi: List/Set/Map, equality đủ dùng, thêm/xóa/tìm, chọn cấu trúc, đếm/nhóm; ôn prerequisite, luyện/assessment khác tình huống, bài áp dụng nhỏ; chưa viết app.
- Nghiệm thu: mỗi năng lực nối bài/hoạt động/assessment; đáp án và feedback đúng/sai có căn cứ; giới hạn mô phỏng rõ; có biến thể cho kiểm tra; nguồn chính thức được kiểm chứng thực tế.
- Kiểm tra: review kỹ thuật Java và walk-through người mới; không tuyên bố learner test nếu chưa thử. Nếu biên soạn quá lớn, tách D06a nội dung/mô hình và D06b assessment/review trước thực hiện.

Phân rã 26/09/2026: giữ nguyên nghiệm thu task cha; hoàn tất cả D06a và D06b mới DONE D06. Một phiên xử lý một đầu ra con, tiếp tục trong task hội thoại hiện tại.

#### BL-D06a — Kiến thức và mô hình Collections [DONE]
- Goal/đầu ra: docs/content/collections-spec.md có phạm vi CAP, đường ôn đủ dùng, nội dung kiến thức và hợp đồng mô phỏng xác định được kết quả.
- Feature/module/requirements: F02/F08, MD01/03; REQ-02,03,10,12. Dependency: D05.
- Phạm vi: List/Set/Map, equality và lựa chọn/đếm/nhóm; trạng thái/input/action/expected, ca biên, nguồn Java chính thức theo phiên bản.
- Loại trừ: bài luyện/đề assessment/đáp án/rubric đầy đủ thuộc D06b; không viết source app hoặc publish bài.
- Acceptance: CAP chính/hỗ trợ rõ; đủ kiến thức cho CAP chính; người mới có ôn ngay khi bài nền còn planned; mô hình có trace và ca biên không giả chạy JVM; claim kỹ thuật gắn nguồn đã đọc.
- Kiểm tra: review trace bằng tay, đối chiếu nguồn, ID/link/template và walk-through người mới trên giấy.

Checkpoint hoàn thành D06a — 26/09/2026:
- Session goal: hoàn thiện kiến thức/mô hình của mẫu trước khi biên soạn ngân hàng bài; giữ D06 cha IN_PROGRESS, không đổi acceptance để đóng sớm.
- Đầu ra: [collections-spec.md](../../features/f02-learning-experience/collections/knowledge.md), 7 CAP chính, 4 thẻ ôn R0–R3, 6 phần C1–C6, contract List/Set/Map/Counter/Grouping và 25 case CM-01–25; Java SE 21 làm chuẩn tham chiếu.
- Acceptance: nguồn TOP/CAP không đổi; CAP nền/Big O chỉ hỗ trợ; thẻ ôn có nội dung/expected tại chỗ; action/input/return/giới hạn/order/null/reset rõ; chín trang Java API chính thức đã được đọc và gắn claim. Đề luyện/assessment/rubric chưa hoàn chỉnh, giao D06b đúng phạm vi.
- Kiểm tra: PowerShell đọc Markdown kiểm 8 TOP reference, 8 CAP reference, 7 CAP chính và 25 CM ID duy nhất — PASS; lượt cuối kiểm 59 link local trong 8 tài liệu đã chạm — PASS. Review bằng tay trace, rỗng/index/trùng/ghi đè/null/giới hạn/replay, template và đường vào người mới trên giấy; đã xử lý thứ tự HashSet, giới hạn 12 không chặn update, grouping giữ trùng. Không chạy app/Java hoặc learner test.
- Theo câu hỏi system của người dùng: bổ sung [system-overview.md](../../system-overview.md), sơ đồ/module/luồng dữ liệu, phân biệt code hiện có và thiết kế R1; liên kết vào feature-module map. Đã đọc package/schema/catalog/LearningPage; lựa chọn storage/schema chi tiết vẫn D08.
- Giới hạn: nội dung vẫn draft, catalog vẫn planned; chưa có runtime/chấm/lưu/publish. Không sửa code sản phẩm hoặc cài dependency.
- Bước tiếp theo chính xác: BL-D06b, dùng ma trận CAP tại mục 7 của spec và hợp đồng D05; sau đó review đủ D06 trước D07.

#### BL-D06b — Luyện, kiểm tra và áp dụng Collections [DONE]
- Goal/đầu ra: docs/content/collections-assessment.md và hoàn thiện liên kết trong collections-spec.md để đóng D06.
- Feature/module/requirements: F03–F06/F08/F11, MD04/05/07/11; REQ-04–08,10,12,16. Dependency: D06a và BL-S03 để rubric/bài nộp khớp server; tiếp tục sau BL-S04 theo thứ tự WIP.
- Phạm vi: luyện/hint/đáp án/feedback từng CAP, bài toán đếm/nhóm, assessment với biến thể mới và rubric riêng, bài áp dụng nhỏ, progress/ôn và review tổng hợp D06.
- Loại trừ: engine chấm, thực thi Java, UI chi tiết và schema persistence.
- Acceptance: mọi CAP chính nối học→luyện→assessment; mỗi đề có input/expected/feedback và biến thể đủ khác; nguồn hỗ trợ lời giải, tình huống ứng dụng có tiêu chí; không cấp CAP hỗ trợ/P1 từ việc xem hoặc làm mẫu; đủ phần T01–T15 áp dụng của template.
- Kiểm tra: rà đáp án và trường hợp rỗng/trùng/thiếu, chính sách D05, đủ coverage CAP, nguồn chính thức và walk-through toàn gói. Chưa thử người học thì ghi rõ.

Checkpoint D06b và review tổng hợp D06 — 29/09/2026:
- Đầu ra: [collections-assessment.md](../../features/f04-assessment/collections/cases.md), liên kết với collections-spec; 5 bài luyện có hint/solution/feedback, 8 case thuộc 4 nhóm L/S/M/D, rubric 7 CAP, CL-U-01 với 5 tiêu chí áp dụng, 14 case CA và đối chiếu đủ T01–T15 (T13 không áp dụng).
- Acceptance: mỗi CAP nối học→luyện→assessment; có expected từng action/return/state, rỗng/trùng/index/thiếu/null/equality/aliasing và lý do cấu trúc. Câu lý do chọn/ghép có đáp án xác định, không cần AI chấm; criterion từng CAP, không ghép mảnh fail hoặc cấp CAP hỗ trợ/P1. Hai case mỗi nhóm khác cấu trúc suy luận, hết case thì chỉ luyện/đợi case mới; chưa hứa ngân hàng vô hạn.
- Kiểm tra: Node script đọc Markdown và đối chiếu mô hình độc lập — PASS 29 transition List/Map, 12 đáp án counter/grouping, 6 kiểm tra đáp án Set; PASS 14 activity ID/reference, 8 family, 7 CAP mục tiêu, 8 assessment/5 practice/14 CA/15 mục template. Đây là kiểm tra đáp án tài liệu, không là unit test evaluator sản phẩm hoặc chạy Java.
- Review bằng tay phần case biên, feedback/tiêu chí, policy hỗ trợ/exposure/pending/import theo D05/S03, walk-through người mới/biết trước/hết biến thể và toàn gói D06a+b. Đọc lại Java SE 21 List/LinkedHashSet/Map/Object ngày 29/09/2026. Sửa finding phản chứng lựa chọn cấu trúc và bổ sung bank lý do đóng cho view đếm/nhóm/áp dụng.
- Kiểm tra bàn giao cuối bằng PowerShell: PASS 74 link local trong 7 tài liệu thay đổi; con trỏ D06/D06b DONE → D07 READY nhất quán, checkpoint cũ giữ nguyên lịch sử.
- D06 DONE vì phần kiến thức/mô hình (D06a) và bài/rubric/áp dụng (D06b) đã đủ acceptance thiết kế trên giấy. Chưa learner test/app/Java, chưa publish hoặc đổi source/dependency. Còn D07 UI, D08 schema/evaluator/API/test mapping; E01 mở rộng ngân hàng case. Tiếp theo BL-D07 READY; không tự chuyển sang code.

### BL-D07 — Luồng trải nghiệm mẫu và nghiệm thu UI [DONE]
- Goal: người học đi từ chọn mục đến kết quả/tiếp tục mà không mất ngữ cảnh.
- Feature/module: F01–F07; MD09 và module nghiệp vụ liên quan. Requirements: REQ-02–09,11,12.
- Dependency: D06, BL-S02/03. Đầu ra: docs/collections-experience.md và prototype/wireframe cần thiết; thêm login/logout/hết hạn, bài nháp/lịch sử, pending/conflict và đổi user.
- Phạm vi: trang mục/player/luyện/kiểm tra/kết quả/progress/áp dụng; trạng thái empty/error/loading; không thiết kế lại toàn bộ thương hiệu.
- Nghiệm thu: entry/exit/action/state từng màn; Back/reload, keyboard/mobile/reduced motion; ưu tiên tương tác giải thích cơ chế; ma trận UI acceptance nối requirement.
- Kiểm tra: thao tác trên prototype và review bố cục; ghi phản hồi thực tế nếu có.

Checkpoint BL-D07 hoàn thành 29/09/2026:
- Đầu ra: [collections-experience.md](../../features/f02-learning-experience/collections/experience.md), 11 hợp đồng màn entry/action/exit/state, sơ đồ luồng, routes đề xuất, nháp/conflict/pending/identity/import/reset và 20 case UX nối requirements. Bản mẫu tương tác `C:/Users/khang.nguyen-huynh/.codex/visualizations/2026/09/19/01a0b7fe-b132-7953-8ba2-dcec97125eaf/collections-experience.html` có 7 màn đại diện và bộ chọn trạng thái review; không là code sản phẩm.
- Kiểm tra Playwright qua Chrome headless hiện có + Node v24.19.0 của bundled runtime: PASS học/reset; luyện validation/focus/feedback; hint hủy/xác nhận, submit→pending→fixture result; expiry ẩn dữ liệu/không đổi owner; offline chặn submit, conflict/hủy, hết case và lịch sử rỗng. PASS 28 tổ hợp 7 màn × 320/390/1024/1440px không tràn ngang; 7 trạng thái lỗi ở 320px; Tab/Enter/Space và focus input lỗi. Không page error ở lượt kiểm chính.
- Visual review: đã mở ảnh `collections-desktop.png` (1024 light) và `collections-mobile.png` (390 dark), cả hai reduced motion; bố cục/nav/thẻ/input/CTA đọc được. Finding nhãn conflict sau lựa chọn có thể hiểu nhầm là đã lưu đã sửa thành chưa xác nhận lưu và kiểm lại đạt.
- Môi trường: lượt đầu Node hệ thống16 không chạy Playwright; đổi sang runtime sẵn có theo helper. Chromium bundle chưa có, dùng Chrome đã cài; sandbox chặn spawn nên chạy browser check với quyền công cụ được duyệt. Không cài browser/dependency, không đổi framework hoặc chạy full app suite.
- Review acceptance và policy D05/S02/S03/D06: đạt phạm vi D07. Prototype là dữ liệu giả và kết quả fixture, không grader/auth/storage thật. Các UX đánh dấu D (Back/reload/ownership/HTTP race/modal/reauth/import/reset) phải kiểm khi xây sản phẩm; chưa có learner test hoặc screen-reader test thực tế. D08 nhận routes/view model và ma trận test, không coi prototype là bằng chứng backend.
- Kiểm tra tài liệu: PowerShell PASS 8 link local trong đặc tả, 20 UX ID duy nhất/11 screen contract; kiểm tra bàn giao cuối PASS 71 link local trong 6 tài liệu thay đổi. Bước tiếp BL-D08 READY; D09 review gói thiết kế trước chỉ dẫn chuyển sang code.

### BL-D08 — Tác động kỹ thuật và chia task triển khai [DONE]
- Goal: biết giữ/sửa phần nào của code cũ và có backlog đủ đầu vào kỹ thuật cho lát cắt đầu; chỉ READY thực thi sau D09 và chỉ dẫn CODE.
- Feature/module: F01–F08/F10–12; MD01–07/09–11. Requirements: REQ-01–16,18; REQ-17 sau R1.
- Dependency: D07, BL-S02/03/04. Đầu ra: docs/implementation-design.md; ADR khi có lựa chọn quan trọng; backlog triển khai R1 v2 thay baseline BL-I cũ.
- Phạm vi: frontend/backend/API/DB/schema, identity/authorization, Topic/Competency/ExerciseVersion/Submission/Progress, transaction/migration, routes và deploy/test strategy. Chốt stack/version, tái sử dụng frontend hiện có; chưa viết code backend/runner trong task thiết kế.
- Nghiệm thu: schema v1/API 500 có cách giữ/chuyển rõ; owner/server failure/version có giải pháp; mỗi task R1 v2 có file/contract/acceptance đủ nhỏ; dependencies không vòng; thêm backend/library có lý do; có môi trường chạy/backup/restore dự kiến.
- Kiểm tra: trace requirement → feature → module → task → test; rà code và dữ liệu giả định, không coi sơ đồ là runtime đã chạy.

Checkpoint BL-D08 hoàn thành 30/09/2026:
- Đầu ra: [implementation-design.md](implementation-v2.md), ADR-01 backend modular/PostgreSQL; inventory source, schema/owner/session guards, API, T1/T2, migration/legacy, deployment và test strategy. Backlog BL-R01–34 có đầu ra/dependency/acceptance/loại trừ và mapping requirement/module/test.
- Review đã sửa: exposure/receipt xuyên epoch khác bảng bài làm; dependency lưu nháp practice và session của player; test fixture không đồng nghĩa publish draft; reset hai pha journal→final commit, pending/lỗi không báo đã xóa. Đồng bộ S03/D07/system/quality/execution và con trỏ dự án.
- Kiểm tra: Python kiểm 34 ID task duy nhất, cột bắt buộc/dependency hợp lệ và DAG không vòng — PASS; kiểm 95 link local trong 11 tài liệu trước checkpoint — PASS. Rà thủ công acceptance D08, code legacy, biên owner/revoke/reset/publish và phạm vi ngoài R1. Đối chiếu nguồn chính thức được dẫn trong thiết kế; tham số Argon2id kiểm lại 30/09.
- Giới hạn: chỉ thiết kế; không thay code sản phẩm, chạy Java/DB/build/E2E, cài dependency hay deploy. Chưa có bằng chứng performance/restore/runtime. Digest/corpus/host và số đo có owner R01/03/31/32. BL-R vẫn PLANNED, chưa có chỉ dẫn CODE.
- Tiếp theo: BL-D09 review tổng hợp R0. Tiếp tục cùng session được; lúc code, mỗi session nên theo một BL-R có đầu ra nghiệm thu, mang theo checkpoint/dependency và kết quả test. Không tự tạo session mới.

### BL-D09 — Review tổng hợp R0 [DONE]
- Goal: đưa bản thiết kế cụ thể đủ để người dùng xem trước khi chuyển sang code.
- Feature/module: toàn R1. Requirements: REQ-01–16,18; REQ-17 xác nhận nằm sau R1.
- Dependency: D08. Đầu ra: docs/r0-review.md và backlog đã xử lý finding.
- Phạm vi: rà tính nhất quán catalog/template/mẫu/assessment/UX/kỹ thuật; không mở thêm feature ngoài R1.
- Nghiệm thu: không còn quyết định chặn lát cắt đầu; finding có mức độ/xử lý; nêu rõ đã/chưa kiểm chứng; trình gói review cụ thể. Ghi việc người dùng cho phép code khi thực sự có, không tự suy từ việc task thiết kế DONE.
- Kiểm tra: checklist traceability, đường đi mới học → áp dụng, dữ liệu lỗi/recovery và phạm vi giữ lại từ T01–T05.

Checkpoint BL-D09 hoàn thành 30/09/2026:
- Đầu ra: [r0-review.md](r0-review.md), phạm vi R1, trace 18 requirement, 11 walk-through, 4 finding đã xử lý và giới hạn bằng chứng. RV-01 chốt package/unit→TOP/CAP và canonical route/viewed mapping trong D08; sửa con trỏ stack và nguồn catalog DevOps của template.
- Review acceptance: không còn quyết định business chặn BL-R01; frontend/schema1/API500 có hướng giữ/chuyển, admin/runner ngoài R1, chưa có CODE. Chưa còn P0/P1 thiết kế mở trong phạm vi; runtime phải nghiệm thu ở BL-R.
- Kiểm tra Python: 95 TOP riêng biệt trên 4 catalog; 5 practice/8 assessment/7 CAP mục tiêu; 34 task dependency không vòng; 203 link nội bộ trong 32 tài liệu — PASS trước checkpoint. Lần đếm đầu sai vì tính cả bảng năng lực/mapping như khai báo topic; đã sửa thành đếm ID riêng biệt, không sửa catalog để khớp test. Không chạy build/E2E/DB hay learner test vì chỉ đổi tài liệu.
- Handoff: chờ chỉ dẫn rõ chuyển sang code, sau đó BL-R01. Một session/một task BL-R; không tự mở session, cài dependency, commit hoặc deploy. Báo cáo review là đầu ra để người dùng xem, không đồng nghĩa đã chấp thuận triển khai.


## Mở đầu registry trước khi tổ chức lại

# Backlog hiện hành

Cập nhật 02/10/2026. Nguồn điều phối duy nhất cho việc mới; TASKS.md giữ lịch sử T01–T35. Đọc [execution-model](../../execution-model.md), [requirements](../../product-requirements.md) và [feature/module](../../feature-module-map.md). R01a nền build/R01b Identity/R01c Content/R01d Learning bootstrap được giao code01/10; R02a/b được giao code02/10 cho DB lab/service trust/common-wire foundation; chưa nghiệp vụ auth/catalog/publication/work/grader/progress hoặc khởi tạo task hội thoại/goal tự chạy.

