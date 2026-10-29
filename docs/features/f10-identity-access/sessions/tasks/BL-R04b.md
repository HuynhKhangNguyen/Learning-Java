# BL-R04b — Content admission/fence receiver

[Registry](../../../../delivery-backlog.md) · [Protocol](../protocol.md) · [DoD](../../../../delivery/contract.md)

## Goal, scope và quyền thực thi

Ngày05/10/2026 người dùng cho phép đợt tuần tự R04b → R04c → R02c → R04d trong cùng chat, tạo Goal, nghiệm thu từng leaf, không commit/deploy; ghi checkpoint khi cần quyết định. Chỉ R04b đang thực thi. Dependency R04a/R02b đã DONE. REQ-13/14/15; MD10/MD06.

Đầu ra: Content có online admission client/request binding và local durable account gate/session fence/command receipt; INT-02 chỉ Identity trên private mTLS, ACK sau commit. Own PostgreSQL/roles, không đọc DB Identity hoặc import source/service-domain JAR. Không Content catalog/API-C business, Learning receiver, coordinator/global COMPLETED/reauth/relay/FE/edge trong leaf này.

INT-01 provider canonical thuộc R04d theo registry. R04b dùng actual mTLS provider-contract fixture với phản hồi kiểm soát để kiểm receiver; không gọi fixture là Identity canonical hoặc auth end-to-end. Protected adapter là Java request-scoped port, chưa mở Content learner route trước R07. Runtime INT-02 chạy qua profile owned-lab `admission`; `bootstrap`/`transport` giữ hành vi hiện có.

## Acceptance và kiểm chứng

| ID | Điều kiện | Bằng chứng bắt buộc |
|---|---|---|
| AC01 | Strict admission provider/audience/action/nonce/request/context/owner/realm/time binding; mỗi request gọi Identity, lỗi không ALLOW cache; credential không log/echo | Unit schema negatives + actual mTLS contract client/fault tests |
| AC02 | Own DB gate insert/lock, monotonic epoch, absent tombstone, late install deny; effect và admission-use atomic; final deadline/clock check giữ khóa tới commit | PostgreSQL two-connection/replica races, rollback/cutoff/replay/owner tests CMD-03 |
| AC03 | INT-02 caller/path/schema/digest/realm/topology/generation checked; immutable command receipt; duplicate same command/covered floor ACK, changed payload conflict; ACK chỉ sau durable fence | PostgreSQL + packaged mTLS CMD-04/05 SEC-02/04/09/15, lost ACK/restart |
| AC04 | Explicit validated runtime config/own roles/schema/readiness; bootstrap/trust regressions; strict suite/case gates, artifacts/reports/cleanup phù hợp source | Existing helper/PG18.6/JDK21/Boot4.1.1, dependency/hash/secret/scope review |

DoD: AC01–04 và DONE-01–05 cùng đạt, không P0/P1 trong receiver scope; actual reports không missing/zero/skip. Verification và closing checkpoint giữ tại file này; registry giữ trạng thái. Provider canonical/coordinator/business/production recovery không được claim từ fixture.

## Kế hoạch và checkpoint 05/10

1. Chốt wire contract và migration security riêng; tạo gate cùng test race độc lập.
2. Online peer adapter và private HTTP receiver; profile fail-closed/config/own generation.
3. Actual DB/TLS/negative/fault qualification, strict helper và regression.
4. Review AC/limits/artifact/source/docs, nghiệm thu leaf rồi mới R04c.

Đã đọc entrypoints/registry/R04a handoff, MS02/security/implementation budgets và Content DB/trust code. Repo toàn untracked từ trước; giữ nguyên dữ liệu sẵn có, không coi empty diff là baseline. Chưa chạy test R04b hoặc claim acceptance PASS.

### Implementation checkpoint 05/10, chưa nghiệm thu

- V2 own security migration, immutable deny/receipt/use rows, monotonic account floor; strict INT-01/02 codecs/client/transaction gate và explicit `admission` profile đã thêm. R04a command digest được giữ tương thích, receiver thêm envelope digest cho operation/context/generation. [Wire contract](../../../../../contracts/http/admission/README.md) ghi fields/budgets và provider-fixture limit.
- DB run `a616ef8c-956a-4419-ba3e-db3a9ac0508d` FAIL trước tests: JVM native-memory allocation, owned cluster/listener đã đóng. Máy có khoảng729MiB free commit tại lần kiểm; không đóng app người dùng hoặc thay OS pagefile. Lab hỗ trợ heap cap explicit192MiB/SerialGC và kiểm in-process bằng MAVEN_ARGS; không production/load evidence. Quick build tiếp theo phát hiện hai generic AssertJ compile ambiguities trong test, đã sửa explicit UUID type; chưa PASS.
- Actual DB rerun đang thực hiện. Packaged receiver/provider TLS/fault, helper strict case gates, hashes và closing review còn thiếu; giữ IN_PROGRESS.
- Reset account được người dùng cho phép: helper riêng ignored `.tools/overnight-reset-20261005.py` dùng official app-server RPC, chỉ một earned credit sắp hết hạn, hẹn05:40 trước expiry05:55. Chưa có kết quả redeem; không tính quota percent thành token budget. Product source không phụ thuộc helper này.

Các leaf sau đã được cấp quyền nhưng chỉ mở khi tới lượt: R04c phải chứng minh cùng gate tại Learning và mutation hook (không T1/T2); R02c phải chứng minh state/outbox/inbox/effect atomic, lease/sequence/digest/ACK/gap/poison recovery, auth độc lập; R04d chỉ DONE khi INT-01/06, reauth và revoke hoàn tất sau đủ ACK đúng generation của cả hai participant cùng race/failure evidence. Acceptance chuẩn vẫn ở registry/protocol, mỗi leaf có brief trước IN_PROGRESS.

### Tiếp tục 05/10 sau gián đoạn qua đêm — R04b vẫn IN_PROGRESS

- Run `e242dbf3-853c-4ebd-8ea7-bfe14148c99e` FAIL native-memory khi đã tới Flyway. Run `9d02c506-0de5-4049-9d4b-8576c8765942` FAIL test compilation: `Request` trùng giữa JDK HTTP fixture và admission request; đã dùng tên explicit. Hai cluster đều closed; không quy lỗi compile cuối cho memory.
- Kiểm lại lúc15:21: reset helper handle đã mất, không process/helper-result JSON. Official app-server đọc được primary30%/weekly37%, chỉ còn hai credit xa hạn; credit hết hạn05:55 không còn. **Không có bằng chứng redeem thành công**, không tự tiêu credit khác để bù. Giữ audit giới hạn này; không có cơ chế reset usage tùy ý. Goal batch active, không mở chat/agent.
- CMD03 `pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -EvidenceTask R04b -Offline`, env `BACKEND_LAB_JVM_MAX_HEAP_MB=384`, normal Maven forks: run `02a1d823-b4f5-4b08-8d7b-1338b94bfbde` PASS37DB IT (27isolation/10gate)+50unit/bootstrap (22wire/28bootstrap),0failure/error/skip; PG port59656/owned cluster closed. JAR `aa1e2716b443283e5ab3def0209e601fa950c7605be9817f944afa29b4eab6ce`. Sau run thêm private upload/error-envelope/test nên chưa coi hash này là candidate cuối.
- Helper đã thêm strict Content SEC02/04/09 selectors: SEC04 chạy toàn bộ isolation+gate DB prerequisites, SEC02/09 actual mTLS runtime tags; required named cases cho CMD03/04/05. Helper doubles mới đang chờ chạy. Fixture transport port được áp dụng bounded close/reuse giống qualification R04a; không kill foreign process.
- CMD04 đang chạy packaged admission profile/own PG/mTLS controlled provider fixture. Chưa có full receiver PASS; còn final CMD03, SEC fault runs, helper/smoke/guard, dependency/source/artifact review và closing DoD.

### Continuation 06/10 — verification và clock review

- CMD04 `176dd00c-ec28-43ba-88a6-d9b949e226e4` đã kết thúc PASS94IT (19admission runtime/46transport/29wire)+50unit/bootstrap,0failure/error/skip, port60644/cluster closed; retained JAR `8f68c5f2ea03f5f0dd8871c208e19852e70124c68de3acbcdb58d78f2a2eaadb`. Handle cũ không còn, kết quả được xác minh từ report/log/input hashes, không khởi chạy lại chỉ vì mất handle.
- CMD03 `c0899526-cfa2-4a29-963f-a5f751bed1c2` cùng source PASS37DB/50unit; SEC02 `278cb453-34bf-4f0a-bcee-278528dfb6fc` và SEC04 `fe719d5d-4025-4411-96b5-af706aa2ef9a` PASS, các owned clusters closed. SEC09/15 và helper đang chạy tuần tự.
- Review phát hiện admission clock hiện chỉ dùng receivedAt−2500ms, chưa dùng DB time trước network call đã đo được. Chưa nghiệm thu AC01: cần bound serverTime bằng khoảng gửi/nhận thực tế±500ms, thêm test provider clock lùi2s nhưng issuedAt/TTL nội bộ vẫn hợp lệ, và test response chậm hợp lệ không bị deny nhầm. Các runs trước thay đổi này giữ evidence lịch sử, final qualification sẽ kiểm lại.
- Đã đọc D17/backend architecture §2.1: feature `identityaccess` nhỏ giữ phẳng; không refactor package/test qualified hoặc tạo folder rỗng. No commit/deploy/agent/chat. Goal batch vẫn active và đầy đủ bốn leaf; R04b chưa DONE.
- SEC09 `70222381-4558-47d8-b26f-69ecd8a0a5f1` và SEC15 `a4af3f47-c623-45ba-a156-d12f781e4352` pre-clock-fix PASS; clusters closed. Sau khi tất cả Maven runs kết thúc, đã sửa ContentAccess/AdmissionWire dùng sentAt/receivedAt thực đo; thêm unit fast-old-clock/legitimate-delayed-response/backward-local-clock và actual mTLS CLOCK_PAST. Chưa claim các tests mới PASS. [Ops runbook](../../../../../ops/content/README.md) ghi provisioning/config/ownership và không-restore limits. Helper doubles chạy độc lập trên copied script, không product DB/runtime.

### Closing R04b — AC/DoD đạt, 07/10/2026

- **Goal và scope:** Content có request-scoped online admission port, own durable account gate/session tombstone/receipt/use và private INT02 ACK-after-commit. Không Identity DB/source import, learner HTTP route, Learning/coordinator/reauth/relay/FE/production edge. Controlled mTLS provider chỉ chứng minh consumer contract; canonical INT01 vẫn thuộc R04d.
- **AC01:** strict request/response schema, caller/audience/action/nonce/context/realm/request binding/bigint/timestamps; serverTime nằm trong own-DB sent/received interval±500ms, validUntil/deadlines/budget được kiểm. Final unit24 cases và actual runtime20 cases có fast old clock, future/past/expired/slow/oversize/redirect/provider binding; hai request tạo hai provider calls, outage không dùng cached ALLOW; credential redacted/no echo. Owner check nhận principal từ admission trước local SQL.
- **AC02:** V2 least-privilege/monotonic tables + shared per-user row lock. Final CMD03 run `12175851-34e8-4b2c-a143-5ddf5876b049`:37DB IT (27isolation+10gate)+52unit/bootstrap,0failure/error/skip, PG18.6 cluster closed. Actual two-connection blocking, absent tombstone/late admission, lower epoch, replay, owner/context, effect+use rollback, final cutoff/statement timeout đều đạt.
- **AC03:** final CMD04 `2096f815-fd08-42c5-8fb6-1f432b6ad6ad`:95IT (20receiver+46transport+29wire)+52unit/bootstrap,0failure/error/skip; port52433/JAR8080/8443 closed. Exact JAR SHA256 `3606875ef35c4f3e0d5662f3ac58ac1ec1c449b43cb095230614b99d5cd70cca`. Final CMD05: SEC02 `b4aa4e3d-5331-4915-aec5-24b816c21e94` 1runtime; SEC04 `68e7a189-3a0d-4048-9366-8e538f189e8e` 37DB; SEC09 `aaef7262-c1fa-49d7-80f3-36a9d93cc3a6` 2runtime; SEC15 `9d50a913-8815-4588-9096-a49c87f282a9` 35transport/runtime. Tất cả0failure/error/skip, own clusters/listeners closed; duplicate/lost ACK/restart giữ receipt, changed envelope conflict, wrong caller/path/body/generation/digest/config fail closed, stalled upload408 JSON/no effect.
- **AC04/DONE-02:** helper report/command doubles80/80, lab ownership/process guards8/8, exact retained JAR bootstrap smoke7/7 và offline dependency-plugin3.10.0 tree PASS. Candidate có40compile/1runtime/31test dependencies,36 packaged runtime libs; cache/embedded bytes, migration bytes, no test/PKI fixture packaging, source/service boundary, links/DAG, generated-secret scan và exact input hashes được audit tại `artifacts/quality/R04b/receiver-review.json`. Heap cap384MiB/SerialGC/C1 chỉ qualification process trên host này, không performance/production setting.
- **DONE-01/03/04:** AC01–04 map tới implementation và actual evidence ở trên; review lock order/account floor/deny immutability/deadline/clock/mTLS/caller/error envelope/config/readiness/provisioning/cleanup không còn P0/P1 trong supported receiver scope. Contract/runbook/backend architecture/D17/quality/test-release/feature indexes đồng nhất. Failures memory/compile và pre-clock runs giữ lịch sử, không dùng làm candidate; reset credit05/10 không có redeem evidence và không liên quan product DoD.
- **DONE-05/handoff:** registry chuyển riêng R04b DONE; R04/R02 parents vẫn PLANNED. Bước tiếp theo theo đợt đã cấp quyền là **BL-R04c Learning admission/fence receiver**: tạo brief riêng trước IN_PROGRESS, dùng same protocol nhưng transaction hook phải khóa gate cùng future Learning mutation, không tự mở T1/T2. R02c/R04d chỉ sau R04c theo thứ tự người dùng; không commit/deploy/agent/chat.
