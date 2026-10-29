# BL-R04c — Learning admission/fence receiver

[Registry](../../../../delivery-backlog.md) · [Protocol](../protocol.md) · [DoD](../../../../delivery/contract.md) · [R04b handoff](BL-R04b.md)

## Goal, scope và quyền thực thi

Ngày05/10/2026 người dùng cho phép đợt tuần tự R04b → R04c → R02c → R04d trong cùng chat, không commit/deploy/agent/chat. R04b đã DONE qua final-source audit; dependency R04a/R02b cũng DONE. Chỉ R04c đang thực thi. REQ-13/14/15; MD10/MD06/MD11 boundary.

Đầu ra: Learning có strict online mutation admission client, own durable account gate/session tombstone/command receipt/admission-use và private INT-02 receiver. Java mutation hook gọi Identity trước khi mở transaction, sau đó giữ auth gate tới commit và bắt domain callback dùng cùng `Connection`; fence cùng user phải chờ callback commit/rollback. Không đọc DB Identity, import source/shared-domain JAR hoặc lấy owner từ browser.

Hook đầu tiên bind action thật `LEARNING_DRAFT_SAVE`, PUT canonical future path `/api/v1/drafts/{draftId}`, empty query, validated body SHA-256, expectedContextId, Origin và CSRF token. Đây là application port chuẩn bị cho R09, **không** đăng ký API-L03 hoặc triển khai draft/CAS/T1. Callback fixture ghi local mutation probe/receipt để chứng minh ordering/atomicity; production business receipt thuộc task endpoint sở hữu.

INT-01 canonical provider thuộc R04d. R04c dùng actual mTLS controlled provider-contract fixture để kiểm consumer; không gọi fixture là canonical Identity/end-to-end auth. Profile owned-lab `admission` mở INT-02 private8443, public8080 vẫn foundation status. Không T1/T2/learner_guard/content eligibility/job/progress/reset proof/relay/coordinator/global COMPLETED/FE/edge/production recovery.

## Acceptance và Definition of Done

| ID | Điều kiện | Bằng chứng bắt buộc |
|---|---|---|
| AC01 | Mutation request bind exact action/method/path/body/context/origin/CSRF; response bind provider/caller/audience/nonce/realm/user/session/epoch/request và `csrfVerified=true`; fresh online call, no ALLOW cache/secret echo | Unit schema/binding/clock cases + actual mTLS controlled provider/fault |
| AC02 | Own DB insert/lock gate, monotonic floor, absent tombstone/late admission deny; admission-use + callback effect/receipt atomic; owner/context/cutoff checked; gate giữ tới commit | Actual PG two-connection SEC01–05/09 races, rollback/replay/owner/deadline tests CMD03/05 |
| AC03 | INT02 chỉ Identity/private path; strict digest/realm/topology/generation; immutable deny/receipt, exact duplicate/lost ACK/restart; changed envelope conflict; ACK chỉ sau commit | Packaged mTLS/PG SEC02/04/09/15, caller/body/config/restart negatives CMD04/05 |
| AC04 | Explicit lab runtime config/own roles/schema/readiness; no auto-migrate/provision/reset; bootstrap/trust regressions; strict named suite/case gates, exact artifact/dependency/input/secret/cleanup audit | JDK21/Boot4.1.1/PG18.6 helpers, smoke/guards/review report |

DoD kế thừa DONE-01–05: từng AC map tới implementation và actual evidence; no missing/zero/skip; review không còn P0/P1 trong supported receiver/hook scope; docs/contracts/runbook/backlog thống nhất; checkpoint ghi limits/handoff. SEC01/03/05 ở leaf này chỉ chứng minh **local Learning mutation hook ↔ local fence ordering**; không claim real T1/T2 hoặc Identity global COMPLETED trước R04d.

## Plan và checkpoint mở task 07/10

1. Tạo Learning V2 auth receiver schema/least privilege và lab provision, đổi fixture migration namespace không đụng production V2.
2. Tạo strict Learning mutation/fence wire, own DB gate và Java transaction hook; test race/rollback/owner/cutoff trước HTTP.
3. Thêm explicit `admission` profile, direct mTLS provider client và INT02 adapter/error budget; packaged runtime/fault/restart tests.
4. Mở rộng helper strict Learning suites/SEC01–05/09/15, chạy final CMD03/04/05, smoke/guards/dependency/source/secret audit; chỉ DONE rồi mới R02c.

R04b source được dùng làm oracle về topology/digest/budgets, không copy package/import runtime xuyên service. D17 cho phép feature `identityaccess/` phẳng; transport/configuration chỉ composition/trust. Chưa có `ops/learning` hoặc `contracts/http/learning`; tạo content thật khi implementation cần, không scaffold rỗng. Repo toàn untracked từ trước; giữ dữ liệu sẵn có và không dùng Git diff làm baseline.

## Checkpoint 07/10 — DB/runtime/helper đã tạo, chưa DONE

Production own V2 auth receiver + forward-only provision tại `ops/learning`; fixture migrations chuyển V100/V101, không thay production schema bằng test tables. Feature `identityaccess` có strict mutation/fence wire, gate/receipt/use transaction, `LearningAccess.mutate` và opt-in admission composition/private receiver. Application hook chỉ SQL cùng Connection; chưa API-L03/draft/T1/T2/business receipt/coordinator/canonical provider.

Thêm actual two-connection mutation-holds-gate/fence waits với `pg_blocking_pids`; committed-fence-first concurrent replica deny; rollback/use replay/owner/time bounds. Runtime suite dùng packaged JAR/own PG/mTLS controlled provider, có fresh call/unavailable/no cache, nonce/audience/context/CSRF/clock/expiry/redirect/oversize/slow negatives, Identity-only fence, schema/digest/generation/method/body limits, stalled-upload408/no receipt, wrong-Origin no provider call, duplicate/conflict/lost ACK/restart. Helper yêu cầu exact named suites/cases Learning SEC01–05/09/15; report doubles chỉ kiểm helper, không gọi chúng là DB/TLS proof.

Các lượt thất bại được giữ: default-temp JDK HTTP pipe denied/invalid Unix-domain socket; managed lab dùng process-local workspace `.tools/jvm-ipc-sockets` như runner đã qualified, không sửa environment persistent. CMD03 `f03102ad-c2f6-48dd-8fd5-624349976f32` compile test mới lỗi ambiguous Request; đã sửa qualified type, clusterStopped/listener closed. CMD03 `8cc132bf-e303-4d8d-8c03-8f3bd1a0dd98` Java tests 38DB/68unit không fail/error/skip nhưng helper **FAIL** do runbook/contract đổi giữa run; không chọn làm qualified evidence. Final-source CMD03 đang chạy lại; chưa có final CMD04/fault/helper/smoke/guard/audit PASS. Giữ IN_PROGRESS, không mở R02c/R04d.

Final-source CMD03 `4c6a9482-0b29-47df-8e6e-6dda04d08977` PASS38DB (27isolation/11gate)+68unit (26wire/18config/24HTTP), zero failure/error/skip, PG18.6 port49549/cluster/listener closed và retained artifact/input snapshots. CMD04 đang kiểm actual packaged TLS/runtime; task vẫn IN_PROGRESS, hash candidate chỉ chốt sau full final audit.

Final CMD04 `860ad024-bd26-414a-bcd1-32fd9181c90d` PASS98IT (23receiver/46transport/29wire)+68unit, zero failure/error/skip. PG18.6 port57936/cluster và JAR8080/8443 đóng; retained JAR SHA256 `900944d2cd44bb3f7f5ad5c582b98a9474d47e7699814b96932bc34e424dad11`. Bảy CMD05 SEC selectors đang chạy tuần tự; helper/smoke/guard/tree/audit chưa hoàn tất, không DONE.

CMD05 tuần tự cùng final inputs: SEC01 `83368544-2e6f-4ce5-affe-8d01b8329659` PASS1runtime+68unit; SEC02 `26f90338-12ba-4c40-9571-f8871faebe6c` PASS1runtime+68unit. Zero failure/error/skip, clusters/listeners closed. SEC03/04/05/09/15 và final audit còn tiếp; không chọn pre-final failures làm candidate.

SEC03 `7292a97f-5cbc-46ee-bb75-fa81807b6d26` và SEC04 `32ff086e-0b52-4875-8724-a5a8b25ad147` PASS38DB+68unit mỗi lượt, zero failure/error/skip, owned clusters/listeners closed. Chứng minh actual local mutation/fence order, rollback/use replay/owner/cutoff trong own DB, không T1 hoặc all-participant completion. SEC05/09/15 đang chạy tiếp; chưa full final audit/DoD.

SEC05 `36a9a508-ecd1-4f69-877b-97395265ac3b` PASS12runtime+68unit, zero failure/error/skip, cluster/listener closed: provider unavailable/no ALLOW cache, changed binding/CSRF/clock/deadline/redirect/oversize/slow và wrong-Origin no provider call. SEC09 `44cad06f-dc9c-4766-a939-c10519bef4cf` Java/helper PASS2runtime+68unit; chờ report cleanup xác nhận trước chọn final evidence. SEC15 và helper/smoke/guard/dependency/final audit còn lại.

SEC09 report đã xác nhận PASS/clusterStopped/listener closed. SEC15 `97214e5d-b44b-4d61-bba5-eb0980ad658b` PASS25transport/receiver+68unit, zero failure/error/skip, cluster/listener closed. Bảy selector đều đạt trên final inputs. Helper/smoke/guards/dependency/source/secret audit đang thực hiện; registry vẫn IN_PROGRESS, chưa mở R02c.

## Closing R04c — AC/DoD đạt, 07/10/2026

- **Goal/AC01:** Learning có fresh direct mTLS mutation admission port, strict action/method/path/body/context/Origin/CSRF và ALLOW nonce/audience/caller/realm/time/binding; `csrfVerified=true` bắt buộc, không ALLOW cache/redirect/secret echo. Final26wire units +23actual runtime cases; two calls/two provider requests/two committed local effects, unavailable/changed response/wrong-Origin deny trước callback. Controlled provider chưa canonical Identity, hook chưa API-L03/draft/CAS/T1/T2/business receipt.
- **AC02:** own V2 account gate/session deny/immutable receipt/use và least-privilege roles; callback SQL cùng Connection/use commit hoặc rollback atomic, account lock giữ tới commit. Final CMD03 `4c6a9482-0b29-47df-8e6e-6dda04d08977`:38DB (27isolation/11gate)+68unit, no failure/error/skip; actual `pg_blocking_pids` chứng minh fence chờ mutation commit, committed-fence-first hai replica deny/no callback, absent/late/replay/owner/floor/clock/cutoff đều đạt. SEC03/04 chỉ local ordering, không all-ACK/T1.
- **AC03:** CMD04 `860ad024-bd26-414a-bcd1-32fd9181c90d`:98IT (23receiver/46transport/29wire)+68unit, no failure/error/skip. Packaged mTLS Identity-only/private receiver, strict schema/digest/generation/body/method/header denies; upload408/no receipt; duplicate/conflict/lost ACK/restart giữ durable deny. Final JAR `900944d2cd44bb3f7f5ad5c582b98a9474d47e7699814b96932bc34e424dad11`. CMD05 final SEC01/02/03/04/05/09/15 ở checkpoint trên: integration counts1/1/38/38/12/2/25, mỗi lượt68unit, zero failure/error/skip, tất cả own clusters/listeners closed.
- **AC04/DONE-02:** helper117/117 report/command doubles, actual ownership/process guards8/8 ngoài restricted sandbox, exact retained CMD04 JAR bootstrap smoke7/7, offline dependency-plugin3.10.0 tree PASS. Dependencies40compile/1runtime/31test,36 runtime libraries; every cache/embedded library byte, packager-injected jarmode provenance, production migration bytes, no test/PKI packaging, exact run input hashes/retained entries, secret scan/links/DAG/closed listeners được kiểm tại `artifacts/quality/R04c/receiver-review.json`. Identity45/Content31 qualified source inputs và FE package/lockfile2 unchanged, không cross-service import/JAR. Audit được sửa I/O mỗi-file-once và service-path normalization/-service guard trước final PASS; không coi preliminary audit chỉ4peer inputs là đủ.
- **DONE-01/03/04:** review lại AC → actual evidence → terminal/negative boundary → ngoài scope, lock/floor/receipt/deadline/caller/readiness/provisioning/error/cleanup không còn P0/P1 trong supported receiver/hook scope. Task/protocol/wire/runbook/architecture D17/quality/test-release/indexes thống nhất; build-input guard đã giữ FAIL cho lượt đổi doc giữa run. Heap384MiB/SerialGC/C1/short IPC là qualification process trên host Windows, không load/SLA/Linux/container/production settings. No auto-migrate/reset/restore bypass; SEC01/03/05 không full canonical/global business qualification.
- **DONE-05/handoff:** registry chỉ chuyển R04c DONE, R04/R02 parents và R02c/R04d vẫn PLANNED tại closing. Bước tiếp **BL-R02c durable relay harness**, tạo own brief/AC/DoD trước IN_PROGRESS, auth commands độc lập event sequence/gap; không broker hoặc business publication/delete effects. Sau R02c mới R04d theo batch đã cấp quyền. Goal toàn đợt vẫn active; không commit/deploy/agent/chat.
