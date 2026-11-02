# Backlog hiện hành — registry

Cập nhật tổ chức tài liệu04/10/2026. **File này là nguồn trạng thái và dependency.** Brief, acceptance, bằng chứng và checkpoint nằm ở file task được link bên dưới; đó là phần chi tiết của cùng backlog. Không giữ hai bản checkpoint.

Đọc [mục lục feature](features/README.md) để chọn phần cần làm. Đợt tuần tự được giao R04b → R04c → R02c → [R04d canonical coordinator/reauth](features/f10-identity-access/sessions/tasks/BL-R04d.md) đã nghiệm thu từng leaf; parents R02/R04 rollup DONE trong owned-lab scope, chưa toàn feature/FE/production/lifecycle journal. Exact evidence/limits/handoff ở task; chưa tự mở task tiếp. Việc tổ chức doc không tự cấp thêm quyền code.

## R0/MS — sổ đăng ký thiết kế

| Task | Trạng thái đã ghi | Brief/bàn giao lịch sử |
|---|---|---|
| BL-D14 | DONE | [Review độ đầy đủ và khả năng thực thi sau MS04](delivery/history/design-checkpoints.md#bl-d14--review-độ-đầy-đủ-và-khả-năng-thực-thi-sau-ms04-done) |
| BL-D13 | DONE | [Review chuyển backend sang microservices](delivery/history/design-checkpoints.md#bl-d13--review-chuyển-backend-sang-microservices-done) |
| BL-MS01 | DONE | [Chốt service ownership và API boundaries](delivery/history/design-checkpoints.md#bl-ms01--chốt-service-ownership-và-api-boundaries-done) |
| BL-MS02 | DONE | [Security và phiên trong hệ phân tán](delivery/history/design-checkpoints.md#bl-ms02--security-và-phiên-trong-hệ-phân-tán-done) |
| BL-MS03 | DONE | [Data/events/publication/lifecycle và restore](delivery/history/design-checkpoints.md#bl-ms03--dataeventspublicationlifecycle-và-restore-done) |
| BL-MS04 | DONE | [Rebaseline triển khai/test/ops microservices](delivery/history/design-checkpoints.md#bl-ms04--rebaseline-triển-khaitestops-microservices-done) |
| BL-D12 | DONE | [Cập nhật layout FE/BE cùng repo](delivery/history/design-checkpoints.md#bl-d12--cập-nhật-layout-febe-cùng-repo-done) |
| BL-D11 | DONE | [Hợp đồng goal/context/DoD và task brief](delivery/history/design-checkpoints.md#bl-d11--hợp-đồng-goalcontextdod-và-task-brief-done) |
| BL-D10 | DONE | [Bộ tài liệu review HLD và LLD](delivery/history/design-checkpoints.md#bl-d10--bộ-tài-liệu-review-hld-và-lld-done) |
| BL-S01 | DONE | [Điều chỉnh phạm vi nền tảng nhiều user](delivery/history/design-checkpoints.md#bl-s01--điều-chỉnh-phạm-vi-nền-tảng-nhiều-user-done) |
| BL-S02 | DONE | [Hợp đồng tài khoản, phiên và quyền](delivery/history/design-checkpoints.md#bl-s02--hợp-đồng-tài-khoản-phiên-và-quyền-done) |
| BL-S03 | DONE | [Hợp đồng bài nộp và tiến độ server](delivery/history/design-checkpoints.md#bl-s03--hợp-đồng-bài-nộp-và-tiến-độ-server-done) |
| BL-S04 | DONE | [Catalog nối roadmap DevOps](delivery/history/design-checkpoints.md#bl-s04--catalog-nối-roadmap-devops-done) |
| BL-S05 | PLANNED, sau R1 | [Phạm vi admin theo dõi học viên](delivery/history/design-checkpoints.md#bl-s05--phạm-vi-admin-theo-dõi-học-viên-planned-sau-r1) |
| BL-D01 | DONE | [Catalog nền tảng S00–S03](delivery/history/design-checkpoints.md#bl-d01--catalog-nền-tảng-s00s03-done) |
| BL-D02 | DONE | [Catalog HTTP, SQL, Spring và persistence](delivery/history/design-checkpoints.md#bl-d02--catalog-http-sql-spring-và-persistence-done) |
| BL-D03 | DONE | [Catalog security, vận hành và nhánh mở rộng](delivery/history/design-checkpoints.md#bl-d03--catalog-security-vận-hành-và-nhánh-mở-rộng-done) |
| BL-D04 | DONE | [Template chuẩn cho một mục học](delivery/history/design-checkpoints.md#bl-d04--template-chuẩn-cho-một-mục-học-done) |
| BL-D05 | DONE | [Hợp đồng assessment, evidence và progress](delivery/history/design-checkpoints.md#bl-d05--hợp-đồng-assessment-evidence-và-progress-done) |
| BL-D06 | DONE | [Mục mẫu Collections hoàn chỉnh trên giấy](delivery/history/design-checkpoints.md#bl-d06--mục-mẫu-collections-hoàn-chỉnh-trên-giấy-done) |
| BL-D07 | DONE | [Luồng trải nghiệm mẫu và nghiệm thu UI](delivery/history/design-checkpoints.md#bl-d07--luồng-trải-nghiệm-mẫu-và-nghiệm-thu-ui-done) |
| BL-D08 | DONE | [Tác động kỹ thuật và chia task triển khai](delivery/history/design-checkpoints.md#bl-d08--tác-động-kỹ-thuật-và-chia-task-triển-khai-done) |
| BL-D09 | DONE | [Review tổng hợp R0](delivery/history/design-checkpoints.md#bl-d09--review-tổng-hợp-r0-done) |

## R1 v2 — Nhiều user học mẫu Collections, hai roadmap

**Rebaseline v3 BL-MS04, 30/09/2026:** R00 giữ DONE và checkpoint lịch sử. R01–34 giữ ID, cập nhật dưới đây theo ba service; task lớn là parent rollup, child suffix là đơn vị một session. Runtime tasks vẫn PLANNED tới CODE và dependency đạt; không đổi trạng thái hàng loạt. [Implementation plan](delivery/implementation-plan.md) + [test/release plan](delivery/test-release-plan.md) thay backlog/ops/test một-app D08; MS01–03 giữ business/auth/data contracts.

Đầu ra bắt buộc: backend/DB và cấp tài khoản/login/logout; nội dung versioned; lưu nháp/submission/progress server theo owner; frontend trọn luồng Collections; đề cương Java Backend/DevOps; kiểm chứng quyền A/B và phục hồi dữ liệu. Admin không thuộc mốc này. Thiết kế [BL-D08](delivery/history/implementation-v2.md) phân rã BL-R00–34 dưới đây (R00 bổ sung theo ADR-02), thay toàn bộ baseline BL-I cũ. R00 DONE theo chỉ dẫn cấu trúc repo; R01–34 PLANNED tới khi được yêu cầu triển khai runtime/nghiệp vụ tương ứng. Cổng D09 và dependency R00 đã đạt.

### Backlog BL-R hiện hành v3 — parent và leaf

Đường dẫn là **đích dự kiến**, chưa khẳng định tồn tại. `CODE` là chỉ dẫn runtime/nghiệp vụ thực của người dùng, ghi trước READY; MS04 docs DONE không là CODE. D09/MS01–04/R00 là gates thiết kế/cấu trúc. Parent có task con chỉ rollup AC, không thực thi nguyên parent; không suy từ số thứ tự. Mỗi leaf kế thừa DONE-01–05/REQ-13, AGENTS/quality và MS protocol, cập nhật own contract/fixture. ID suffix lowercase a–d; mọi trạng thái/chuyển trạng thái ở đây, không file trạng thái thứ hai.

| Task / trạng thái | Goal / đầu ra và file chính | Dependency | Acceptance và kiểm tra | Ngoài phạm vi |
|---|---|---|---|---|
| BL-R00 · DONE | Tách FE trong cùng repo — frontend/, config/test/scripts FE; scripts/project.ps1 và README root | D09,CODE | Giữ dependency/lockfile và hành vi; helper root chọn cwd frontend; content/check/build/smoke hiện có đạt, dev/preview/deep link đúng; relative paths/ignore/output cập nhật, scope review; REQ-11/13, MD09 | Backend, đổi framework/version, feature mới, tách repo |
| BL-R01 · DONE | Parent build ba app, owner nền/MD06; REQ-09/13/14/15 | BL-R01a,BL-R01b,BL-R01c,BL-R01d | Mỗi child đạt; selected-service artifacts/test riêng, manifests pinned | Không giao toàn parent vào session |
| BL-R02 · DONE | Parent DB/trust/delivery harness, nền/MD06/10 | BL-R02a,BL-R02b,BL-R02c | DB role isolation + mTLS config + relay/fault layers đạt | Không shared DB guard/runtime JAR |
| BL-R03 · DONE | Identity credential workflow — identity-service credential/maintenance; MD10 REQ-14/15 | BL-R02a,BL-R01b,BL-R02b | Invite/reset/disabled/RECOVERY_REQUIRED, single-use/KDF race, Argon2 Unicode/benchmark/corpus/throttle; generic errors, no secret log; IA/SEC-12/22 credential layers CMD-03/04/05, full browser/session ở R04/R24 | Session authority/receiver, email/admin UI |
| BL-R04 · DONE | Parent session/admission/revoke/reauth, IDN/CNT/LRN MD10 | BL-R04a,BL-R04b,BL-R04c,BL-R04d | MS02 static ACK/local gates/CSRF/context đạt cả ba apps trong owned-lab scope của children | Không toàn feature/FE/production/lifecycle journal |
| BL-R05 · DONE | [Content schema2 — brief/DoD/evidence](features/f08-content/tasks/BL-R05.md); contracts/catalog, content-v2/catalog + standalone catalog CMD-04; CNT MD01 REQ-01/02/10/18 | BL-R01c | 95TOP/190CAP/DS/DP/shared IDs, prerequisite selectors/DAG, public/private/viewed mappings; schema/validator/fixtures và CMD-04 catalog branch, schema negatives thực, giữ legacy v1 | Publish DB/common HTTP trust suite, bài toàn khóa |
| BL-R06 · DONE | Parent publication v3, CNT/LRN MD01 | BL-R06a,BL-R06b,BL-R06c | Stage/fence/canonical commit/release/abort và impact gates đạt | Không transaction xuyên DB/CMS |
| BL-R07 · DONE | [Content browser API — brief/checkpoint](features/f08-content/tasks/BL-R07.md); content-service browser/public DTO; MD01 REQ-01/02/10/15 | BL-R04d,BL-R06c | Outline anonymous/body login/no-store, no expected/private case; role/CSRF/schema/artifact negative SEC-16/25/28 CMD-04/05 | Journey UI/chấm/private support bypass |
| BL-R08 · PLANNED | Learning allocation — learning-service/work+exposure; MD04/11 REQ-05/09/15/16 | BL-R04d,BL-R06c,BL-R28a | Server chọn case clean/mode/pinned version, one assignment/guard, preparing→journal→disclosure auth recheck; self-encounter chỉ future, missing receipt không T1; DATA-22/CA/LD CMD-03/05/06 | Save/chấm, tự sinh case, production journal qualification |
| BL-R09 · PLANNED | Learning draft CAS — work API/receipt; MD11 REQ-09/15/16 | BL-R08 | Auth gate/learner guard/epoch/revision/owner FK, conflict/same-key fingerprint, DB errors giữ draft; LD-03–05/12/13 SEC-17/26 CMD-03/04/05 | T1/FE autosave |
| BL-R10 · PLANNED | Learning support disclosure — exposure/support; MD04 REQ-04/05/09/15 | BL-R08,BL-R28a | Monotonic marker→external journal ACK→reauth current context/epoch/pub before hint; hint/submit race, closed result immutable; DATA-22 SEC-16 CMD-03/05/06 | Grader/client support authority |
| BL-R11 · PLANNED | Learning T1 — submission/job/receipt migrations/API; MD11 REQ-05/09/15/16 | BL-R09,BL-R10,BL-R06c | Save/freeze/submission/job/receipt local atomic, ready exposure/eligibility/shared pub gate, unique attempt; fault/revoke/timeout lookup CMD-03/05 | T2/queue service |
| BL-R12 · PLANNED | Pure Collections grader — learning-service/assessment + independent fixtures; MD04 REQ-05/06/08/12 | BL-R05,BL-R01d | Learning bootstrap/build đã có trước grader;8case/7CAP/U1–U5, each criterion, list/map order/support, CA expected CMD-02; no eval learner source | Worker/DB/AI/runner |
| BL-R13 · PLANNED | Parent accepted-job/T2/result projection, LRN MD04/05/11 | BL-R13a,BL-R13b | Job fencing và atomic result/evidence/progress/current policy đạt | Không tách T2 sang service |
| BL-R14 · PLANNED | FE catalog journey — JourneyPage/adapter/routes; MD02/09 REQ-01/02/11/18 | BL-R07,BL-R31a | Stable/shared TOP, DS independent/alias explicit/prerequisite real, canonical/applied revision pending; Back/deep link/stale response DATA-09 CMD-01/07 trên edge/backend lab thật | Player/all lessons published/client progress |
| BL-R15 · PLANNED | FE topic/refresh cards — topic page; MD02/09 REQ-02/11/12 | BL-R14 | Bảy CAP/giới hạn/prerequisite, planned non-CTA, no dead-end review; UX-S01 keyboard/320px CMD-01/07; checkpoint demo chọn mục/topic/next step, ghi review và finding owner, feedback learner chỉ nếu thử thật | CAP nền official assessment |
| BL-R16 · PLANNED | Parent player/viewed API, FE+LRN MD03/05/09 | BL-R16a,BL-R16b | Player/owner viewed mapping đạt, không Next→pass | Không triển khai whole runtime module |
| BL-R17 · PLANNED | FE model reducers — Collections transitions/fixtures; MD03 REQ-03/04/11/12 | BL-R16a | CM-01–25 independent expected, order/null/replay/input/reset simulation separate DATA-18; reducer + CMD-01/07 | JVM/performance/official grader |
| BL-R18 · PLANNED | FE practice workflow — pages/practice; MD03/04/09 REQ-04/09/11/12 | BL-R17,BL-R09,BL-R10,BL-R24c | Five CL-P, owner saves/preparing hints/feedback supported no mastery; CA/UX errors/keyboard CMD-07 | Independent assessment |
| BL-R19 · PLANNED | FE assessment editor — attempt page/autosave; MD09/11 REQ-05/09/11/15/16 | BL-R09,BL-R10,BL-R15,BL-R24c | Preparing no prompt until ready, schema/CAS/snapshot ACK/hint confirm/RAM/context/expiry, no private expected; UX/SEC-25 CMD-07 | Browser official grading |
| BL-R20 · PLANNED | FE submit/result workflow — submission route; MD09 REQ-05/06/09/11/16 | BL-R19,BL-R13b | T1 timeout→receipt, pending/error/superseded_content/history-only, CAP provenance/support, no replay/pass local; UX/CA CMD-07; checkpoint demo editor→submit→result/feedback/error, không claim full Collections hoặc feedback learner chưa thử | Learner source execution |
| BL-R21 · PLANNED | Parent history API + FE, LRN/FE MD11/09 | BL-R21a,BL-R21b | Owner history/resume/paging/error đúng | Không admin/user search |
| BL-R22 · PLANNED | Parent current progress/guidance + FE, LRN/FE MD02/05/07/09 | BL-R22a,BL-R22b | Current overlay/shared/readiness và pending UX đúng | Không leaderboard/spaced repetition |
| BL-R23 · PLANNED | Parent CL-U-01 model apply, LRN/FE MD04/07/09 | BL-R23a,BL-R23b | U1–U5/applied provenance đúng, không P1 giả | Không Java project runner |
| BL-R24 · PLANNED | Parent session-aware FE, MD09/10 | BL-R24a,BL-R24b,BL-R24c | Bootstrap/client + forms + logout/expiry/multitab đạt | Không giao whole auth UI module |
| BL-R25 · PLANNED | Owner export workflow — learning-service/transfer + FE download; MD06/09 REQ-09/15/16 | BL-R13b,BL-R24c | One Learning snapshot/as-of, owner/no secrets/private expected, failed stream not valid fake JSON; LD-27/SEC-18 CMD-03/04/07 | Distributed backup/restore |
| BL-R26 · PLANNED | Import preview — transfer validator/staging + FE preview; MD06/09 REQ-09/15 | BL-R25 | Quota/schema/owner/legacy unverified/source fingerprint/TTL24h/CAS preview; LD-21–23 SEC-27 CMD-03/04/07 | Apply/pass promotion |
| BL-R27 · PLANNED | Import apply workflow — transfer receipt + FE confirm; MD06/09 REQ-09/15/16 | BL-R26,BL-R28a | Known exposure journal before final; CAS/context/epoch all-or-nothing/draft-unverified only, pending/errors keep good data; DATA-22 LD-23/24 UX-17 CMD-03/05/06/07 | Arbitrary upload/trusted pass/clean known case |
| BL-R28 · PLANNED | Parent journal adapters, IDN/LRN infra MD06/10 | BL-R28a,BL-R28b | Lab durable boundary và real provider qualification riêng | Không journal fourth business service |
| BL-R29 · PLANNED | Parent reset/delete/retention, IDN/LRN/CNT/FE MD06/10/11 | BL-R29a,BL-R29b,BL-R29c | Reset own epoch, terminal account suppression, retention/cleanup đạt | Không reset per roadmap/admin UI |
| BL-R30 · PLANNED | Final Collections publish report — content-v2/manifest + release report; CNT/content reviewer MD01 REQ-10/12/13 | BL-R18,BL-R20,BL-R23,BL-R27,BL-R29,BL-R31c | D06 input/expected/runtime/coverage/source match, privacy artifact, full learning paths, final bundle only via MS03 protocol; CMD-04/05/07/08 với scan runner R31c thật | Publish all95topics/draft prod fixture |
| BL-R31 · PLANNED | Parent pilot platform/release, ops MD06/09/10 | BL-R31a,BL-R31b,BL-R31c,BL-R31d | Edge/observability/CI/artifacts/explicit pilot scope đạt | Không mua host/publish tự động |
| BL-R32 · PLANNED | Parent checkpoint/isolated recovery, ops MD06/10 | BL-R32a,BL-R32b | Real encrypted backup/index/restore/credential recovery reports | Không live overwrite/zero-RPO claim |
| BL-R33 · PLANNED | R1 integration/a11y audit report — artifacts/quality/review; reviewer toàn R1 REQ-11/13/15/16 | BL-R31d,BL-R32,BL-R30 | Every SEC/DATA/IA/LD/AP/CA/CM/UX mapped/run, all applicable REL-01–07, screen reader/keyboard320px/fullstack; no P0/P1, registry missing/skip rejects CMD-11 | Fake learner feedback/full-course claim |
| BL-R34 · PLANNED | Learner acceptance report — docs/release/r1-acceptance; learner+reviewer REQ-13/all R1 | BL-R33 | Real learner Collections/login/resume/understanding/errors, fix regression, REL-08/REQ-01–16/18 evidence; REQ-17 excluded | DONE without actual learner/restore feedback |

### Task con v3 — từng dòng một session goal

`IDN/CNT/LRN/FE/OPS` là owner kỹ thuật, không người/agent được tạo. Owner module/REQ inherited từ parent và ghi rõ khi cần; contract/test paths xem implementation/test plan. `CMD` là command contract dự kiến, chưa chạy. Không child nào bắt đầu chỉ vì đã liệt kê ở đây.

| Task / trạng thái | Goal / đầu ra chính, owner/module/REQ | Dependency | Acceptance và kiểm tra | Ngoài phạm vi |
|---|---|---|---|---|
| BL-R00a · DONE | [Initial reviewed Git baseline — brief/checkpoint](platform/repository/tasks/BL-R00a.md); repository hygiene/index/commit evidence | BL-R00 | Inventory/ignore/secret/large/binary/line-ending/validation/staged review đạt; authorized root commit trên `main` đã được review, STR-01 đóng | Feature/refactor, remote/push/PR/merge/tag/release |
| BL-R01a · DONE | Toolchain/build contract — backend/pom.xml + wrapper, scripts/services.ps1 contract, release deps manifest; nền MD06 REQ-13 | BL-R00 | Pin Java21/Boot4.1.1/Maven3.9.16, checksum/advisory/runtime record; parent validate + wrapper version; selected build contract, không nonexistent module refs; FE unchanged | Java app/auth/DB, app-test PASS trước bootstrap |
| BL-R01b · DONE | Identity bootstrap artifact — backend/services/identity-service/pom+entry/config/tests; IDN MD10 REQ-14 | BL-R01a | Independent clean verify/bootstrap actually runs, loopback/private profile/listener config, own artifact; CMD-02 identity | Credentials/session business |
| BL-R01c · DONE | Content bootstrap artifact — backend/services/content-service/; CNT MD01 REQ-10 | BL-R01a | Own entry/config/artifact/bootstrap verify CMD-02 content, no IDN/LRN Java import | Catalog/publish logic |
| BL-R01d · DONE | Learning bootstrap artifact — backend/services/learning-service/; LRN MD04/11 REQ-16 | BL-R01a | Own entry/config/artifact/bootstrap verify CMD-02 learning, no service source import | Work/grader/job |
| BL-R02a · DONE | Three-DB migration harness — ops lab DB/roles + each-service migrations/test harness; MD06 REQ-09/15 | BL-R01 | Own app/migration roles only, no crossDB access; clean/rerun/failed migration, two connections FX-02; CMD-03 all | Business tables beyond foundational auth gates/tokens, ORM/cloud |
| BL-R02b · DONE | Service trust/config/wire harness — each-service TLS/config/admission HTTP adapter + contracts/http/common/FX-03; MD10 REQ-14/15/16 | BL-R02a | Public8080 HTTPS/mTLS edge-only và private8443 service/operator-only; caller/action/CA/SAN/env/time budget, internal deny, missing config/clock fail closed; wire bigint-string/error/version fixtures WIRE-01–06 CMD-04; SEC-14/15/20 receiver layers CMD-04/05, actual edge ở R31a | Canonical sessions/domain permission policy, claim proxy tests trước R31a |
| BL-R02c · DONE | Durable relay harness — per-service delivery/outbox/inbox + FX-04; MD06 REQ-09/15/16 | BL-R02a,BL-R02b | State+outbox/inbox+effect atomic, sequence/digest/ACK/lease, gap/poison audit recovery; auth separate; DATA-10–15 CMD-03/05 | Broker/business publish/delete effect |
| BL-R04a · DONE | Identity session authority — identity-service/session + API-I01–07/09; IDN MD10 REQ-14/15 | BL-R03,BL-R02b | Opaque cookie/preauth/CSRF/context/login/idle/absolute, REVOKING canonical deny/status, no logout Set-Cookie race; SEC-05/10/11/21/22 local layers CMD-03/04; I04/I07 fail-closed pending R04d reauth | Claim global COMPLETED before R04d static ACK; full reauth/FE/production edge |
| BL-R04b · DONE | Content admission/fence receiver — content-service/identityaccess + INT-02; CNT MD10 REQ-14/15 | BL-R04a,BL-R02b | Online admission binding/local gate, absent tombstone/late install/multi-replica; SEC-02/04/09/15 CMD-03/05 | Identity DB access/session authority |
| BL-R04c · DONE | Learning admission/fence receiver — learning-service/identityaccess; LRN MD10 REQ-14/15 | BL-R04a,BL-R02b | Same monotonic gate/fence with mutation tx hook, absent/local late install deny; SEC-01–05/09/15 CMD-03/05 | T1/T2 learning logic |
| BL-R04d · DONE | [Distributed revoke/reauth coordinator — brief/DoD/evidence](features/f10-identity-access/sessions/tasks/BL-R04d.md); Identity INT-01/06/API-I08 + mandatory participants; MD10 REQ-14/15 | BL-R04b,BL-R04c,BL-R02c | Complete only both durable correct ACK, request binding/expiry/epoch/proof purpose/reservation, password reset/change/disable race; SEC-01–12/19/20/23 DATA-15 CMD-04/05 | Admin UI, lifecycle delete/external account state journal (R29b) |
| BL-R06a · DONE | [Content candidate/version store — brief/DoD/checkpoint](features/f08-content/tasks/BL-R06a.md); content-service/publication migrations/validator/INT-03/CLI; CNT MD01 REQ-10 | BL-R02a,BL-R05,BL-R02b | Immutable private bundle GET trên trust harness có thật/digest/reference/DAG/review/impact/manifest, invalid candidate keeps good head; DATA-01 CMD-03/04; only lab fixtures | Activate canonical without fence |
| BL-R06b · DONE | [Learning verified bundle replica — brief/DoD/checkpoint](features/f08-content/tasks/BL-R06b.md); learning-service/contentreplica + INT-04 stage/gates; LRN MD01 REQ-05/10/16 | BL-R02b,BL-R02c,BL-R06a | Scope pull INT-03, verify bytes/checksum/pinned refs, stage/fence/release handlers+receipt/generation, shared gate test; DATA-01/02 SEC-16 CMD-03/04/05 | Content authoring authority/FE expected |
| BL-R06c · DONE | [Publication coordinator — brief/DoD/checkpoint](features/f08-content/tasks/BL-R06c.md); CNT publication phase outbox/CLI; CNT/LRN MD01 REQ-10/16 | BL-R06a,BL-R06b,BL-R02c | FENCE ACK→canonical commit+RELEASE atomic, complete after RELEASE, beforecommit abort only; DATA-02–07/14 CMD-05 crash/withdraw/impact | Production draft publish/CMS/global DB tx |
| BL-R13a · PLANNED | Accepted-job lifecycle — learning-service/assessment/jobs + result pending API; LRN MD04/11 REQ-09/16 | BL-R11 | DB lease/heartbeat/fencing/retry/error state, one job/submission/no learner fail infra; logout keeps accepted owner; DATA-08 FX-07 CMD-03/05 | Official result/evidence finalization |
| BL-R13b · PLANNED | T2 atomic finalization — Learning result/evidence/projection store; MD04/05 REQ-05/06/16 | BL-R13a,BL-R12,BL-R06c | learner_guard/shared pub/job order, epoch/pending/current eligibility, result+evidence+progress/job same commit; withdrawn history-only/invalid rubric superseded; DATA-06–08/17/29 CMD-03/05 | External grader/runner/admin regrade |
| BL-R16a · PLANNED | FE C1–C6 player — learning route/unit renderer; FE MD03/09 REQ-02/03/11/12 | BL-R15,BL-R24c,BL-R16b | Next/Back/resume/views source/version, text/reduced motion/320px, no Next-pass, legacy API500 draft; CMD-01/07 | Model/grader |
| BL-R16b · PLANNED | Viewed-event API — learning-service/progress/viewed; LRN MD05 REQ-06/15/16 | BL-R04d,BL-R06c | Server resolve unit→TOP mapping, owner/epoch/pub guard/receipt, dedupe/CAS position, no client CAP list/mastery; CMD-03/04/05 | FE/player or official evidence |
| BL-R21a · PLANNED | Owner history read model — learning-service/work/history; LRN MD11 REQ-07/15/16 | BL-R13b | Paging detail/list/count owner, pinned immutable vs open drafts, reset/deleted hidden, pub fence history distinction; IA/LD CMD-03/04 | User/admin search |
| BL-R21b · PLANNED | FE history/resume — history page/route; FE MD09 REQ-07/09/11 | BL-R21a,BL-R20 | Resume exact attempt/result, pending/empty/error/Back/context/keyboard; UX-19 CMD-07 | Progress inference |
| BL-R22a · PLANNED | Current progress/guidance API — learning-service/progress/readiness; LRN MD02/05/07 REQ-02/06/07/08 | BL-R13b,BL-R21a,BL-R16b | Current pub overlay/reconcile+epoch/shared CAP, published-only prerequisites, case exhaustion, three dimensions; DATA-07 AP-09–22 CMD-03/05 | Publish/official pass import |
| BL-R22b · PLANNED | FE home/progress guidance — home/readiness views; FE MD02/09 REQ-06/07/08/11 | BL-R22a,BL-R21b,BL-R14 | Current/as-of/pending/reviewReason, discard incompatible stale DATA-09, no coverage=learner%, keyboard/error CMD-07 | Client authority/leaderboard |
| BL-R23a · PLANNED | CL-U-01 apply persistence — Learning project/attempt mapping; LRN MD04/07 REQ-08/12/16 | BL-R13b,BL-R22a,BL-R12 | U1–U5 pinned rubric/T1/T2 provenance, applied model/support, not P1/extra CAP; CA-12/AP CMD-03/04 | Java execution/project runner |
| BL-R23b · PLANNED | FE CL-U-01 form/result — apply views; FE MD07/09 REQ-08/11/12 | BL-R23a,BL-R22b | Model independent/supported label/readiness vs completed, hints/support journal, UX-07/CA-12 CMD-07 | P1 CLI implementation |
| BL-R24a · PLANNED | FE session client — frontend/src/api + session provider; FE MD09/10 REQ-14/15 | BL-R04d,BL-R07,BL-R31a | Bootstrap/CSRF/authGeneration/owner RAM/no-store/unavailable not anonymous, wire conventions/error/counter exact, abort/discard stale; auth fixtures mở rộng base R31a; SEC-18/21 WIRE CMD-04/07 | Login/credential forms/business evidence |
| BL-R24b · PLANNED | FE credential forms — login/activate/reset/change screens; FE MD09/10 REQ-11/14 | BL-R24a | Preauth/Origin/generic errors/token/proof RAM/password only IDN, keyboard/validation/recovery-required states; SEC-19/21/22 CMD-07 | Signup/social/email/admin |
| BL-R24c · PLANNED | FE revoke/expiry UX — logout/status/cache/context flow; FE MD09/10 REQ-09/14/15 | BL-R24b | Pending vs completed/offline, no false logout success, RAM options no owner swap, cookie A/B reorder/tab/Back/expiry; SEC-06/11/21 UX-11/12/20 CMD-07 | Client cookie clear that destroys newer session |
| BL-R28a · PLANNED | Journal port/local adapter — IDN/LRN infra + lab journal harness; MD06/10 REQ-05/09/15 | BL-R02c | Immutable record/index/CAS/idempotent/digest/readback/prefix, pending/floor durable before effect; process-crash/concurrent-writer fixtures CMD-06 lab, no offhost claim | Real provider qualification/cloud purchases |
| BL-R28b · PLANNED | S3 adapter qualification report — IDN/LRN infra + provider lab; MD06/10 REQ-09/15 | BL-R28a | Actual supplied offhost endpoint/ACL/encryption/key/retention/index/CAS/crash qualification; DATA-25/28 CMD-06 qualification, SDK pinned; missing inputs no fake PASS | Create paid bucket/claim all compatible providers safe |
| BL-R29a · PLANNED | Owner learning reset workflow — LRN reset/API receipt + FE confirm; MD06/09 REQ-09/14/15 | BL-R28a,BL-R13b,BL-R24c | Reauth intent/T0→journal→epoch receipt atomic; pending/crash/autosave/import/T2/key old after new work; keep exposure/account DATA-16–19 SEC-23 CMD-05/06/07 | Per-roadmap/simulation reset/admin deletion UI |
| BL-R29b · PLANNED | Account lifecycle coordinator — IDN maintenance + CNT/LRN INT-05; MD10/06 REQ-14/15 | BL-R28a,BL-R04d,BL-R13b | Disable/enable ACCOUNT_STATE journal gate, terminal delete auth+JRN→both suppression ACK→logical deletion/status, absent/replay/data hidden; DATA-20/21/14 CMD-05/06 | Admin UI/email, compensating resurrection |
| BL-R29c · PLANNED | Retention/purge reconciliation — per-service maintenance + journal floor checkpoint; MD06 REQ-09/15 | BL-R29a,BL-R29b | Live cleanup≤7days, backup/replay inventory+grace, active exposure compact durable, tombstone/UUID no reuse; DATA-28 CMD-03/06 audit | Delete all backups immediately/GC missing inventory |
| BL-R31a · PLANNED | Compose edge/config + base E2E fixture — ops/compose/edge/secrets/config schema; OPS REQ-14/15 | BL-R02b,BL-R04d,BL-R06c | Same-origin TLS/cookie lab, public8080 HTTPS edge identity/private8443 deny, CA/SAN/SNI/header/prefix verified, proxy temp/cache/log sentinel/no mutation auto-retry; 3DB secrets/profiles, CMD-07 start/health/teardown trước R14/R24a; SEC-14/28/29 CMD-04/05/07 | Deploy/public host/buy cloud, auth UI hoặc full learner E2E PASS từ base harness |
| BL-R31b · PLANNED | Observability/budget report — per-service metrics/logs + ops monitoring; OPS REQ-09/13/14 | BL-R31a,BL-R13b,BL-R29c | Trace correlation across phases, bounded tags/redacted logs, pending/auth/clock/cert/journal/backup alerts; measure KDF/tx/queue/gate, SEC-24 CMD-05 | SLA/mandatory cloud tracing |
| BL-R31c · PLANNED | CI/artifact compatibility gates — release manifest/images/quality registry; OPS REQ-13/15 | BL-R00a,BL-R31a,BL-R13b,BL-R29c | Independent service build/image pin/SBOM/advisory/Trivy-Gitleaks, N/N-1 contract, runner parity/strict missing suite fail; SEC-30 REL-01–04/05 CMD-02–08/11 | Push/PR/activate CI/publish without scope |
| BL-R31d · PLANNED | Pilot rollout report — ops/release runbook + verified bundle; OPS REQ-09/13/14/15 | BL-R31a,BL-R31b,BL-R31c,BL-R30,BL-R28b,BL-R32b | REL-01–07 evidence before pilot traffic, explicit deploy/input host/domain, preflight/smoke A/B/same digest/compatible rollback/key recovery; DATA-30 | Auto publish/paid host/SLA/R1 accepted before learner |
| BL-R32a · PLANNED | Checkpoint backup workflow — ops/backup/maintenance interlock/manifest/drain/dump/scheduler template; OPS MD06 REQ-09/15/16 | BL-R31a,BL-R28b,BL-R29c | Durable env/operation/generation gate ngoài snapshots; stop/restart fence toàn app/writer, NOLOGIN own writer roles + drain/no surviving connection, fixed journal heads/3DB dump encrypted offhost30d; crash/stale resume/head đổi→incomplete/maintenance closed, verified resume; DATA-24/30 CMD-09 | Turn on prod schedule before deployment scope, online/HA backup |
| BL-R32b · PLANNED | Isolated restore drill report — ops/recovery/readiness/credential recovery; OPS MD06/10 REQ-09/14/15/16 | BL-R32a,BL-R31c,BL-R06c | DATA-19/23–30 SEC-13/29, actual provider + mixed backups/ref/tombstone/exposure/realm/generation/job/RECOVERY_REQUIRED; measured RPO/RTO, missing source deny; CMD-10 | Live overwrite/zero-loss/fake old credential enable |

Nhóm R01–34 vẫn nối feature/module cũ; v3 task owner/REQ/AC/test ở bảng và [trace](delivery/traceability.md). Parent dependencies chỉ children; leaf không phụ thuộc parent của chính nó. Journal lab R28a lên trước R08/R10/R27; actual provider R28b chỉ trước pilot/recovery. R32b staging drill **trước R31d pilot**, không dependency vòng deploy→restore→deploy. R24c hoàn thành trước UI dùng dữ liệu riêng.

Lát cắt đầu login→case chuẩn bị→draft→T1→T2→result/progress dùng R01a–d/R02a–c/R03/R04a–d/R05/R06a–c/R28a/R08–12/R13a/b + R14/15/R24a–c/R19/20/R16b/R21a/R22a. Sau đó player/practice/full history/guidance/apply/transfer/lifecycle/pilot/recovery/audit/learner. Chỉ lát cắt chạy không là R1 DONE. Handoff gốc MS04 là R01a khi có CODE; nay R01/R02a/R02b/R03 có evidence theo leaf, bước kế tiếp R04a session/preauth/CSRF; R02 parent chờ c. Brief/checkpoints dưới giữ quyền/evidence thực, không dùng handoff MS04 cũ để xin lại quyền task đã được giao.

## Sau R1 — Chỉ phân rã gần lúc thực hiện

| Epic / trạng thái | Đầu ra mong muốn | Điều kiện và cách tách tiếp |
|---|---|---|
| BL-E01 · PLANNED | Nội dung S00–S03 liên tục đến P2 | Sau nghiệm thu R1 v2; mỗi batch nhỏ có author/review/publish, đóng gap trước mở rộng |
| BL-E02 · PLANNED | S04–S07 và hướng dẫn project P3/P4 | Sau E01; chia theo mục HTTP, SQL, Spring, persistence; lab terminal có tiêu chí và bằng chứng thật |
| BL-E03 · PLANNED | S08–S10 và P5–P7 | Sau E02; chia security/Docker/delivery/recovery; kiểm thử lab và môi trường công cụ tại thời điểm biên soạn |
| BL-E04 · PLANNED | Runner thật | Sau R1 và yêu cầu phạm vi riêng; tách contract/execution/UI/recovery; không phụ thuộc hoàn tất E03 |
| BL-E05 · PLANNED | Nội dung roadmap DevOps | Sau BL-S04 và R1 v2; batch theo năng lực/DP, dùng chung TOP/CAP; không buộc chờ hết Java |
| BL-E06 · PLANNED | Admin theo dõi học viên | Sau BL-S05; chia task quyền/read model/nhận xét/audit theo phạm vi được chốt |
| BL-E07 · PLANNED | [Interactive Backend Workbench — parent/brief](features/f02-learning-experience/tasks/BL-E07.md) | Roll up BL-E07a–d; không giao parent vào một session. Chỉ kích hoạt contract sau R15, runtime nối theo dependency R1 |

Epic chưa phải task để giao nguyên vào một session. Khi gần thực hiện, phân rã theo cùng mẫu goal/scope/dependency/acceptance/test; không viết hàng trăm task giả chi tiết ngay bây giờ. BL-E07 đã được phân rã vì người dùng chốt pilot và yêu cầu contract/validation độc lập.

### BL-E07 — leaf và dependency hiện hành

| Task / trạng thái | Brief | Dependency |
|---|---|---|
| BL-E07a · PLANNED | [Product/UX contract và reusable scene model](features/f02-learning-experience/tasks/BL-E07a.md) | BL-P03,BL-P04,BL-P05,BL-R05,BL-R06c,BL-R15 |
| BL-E07b · PLANNED | [HTTP Foundations interactive pilot](features/f02-learning-experience/tasks/BL-E07b.md) | BL-E07a,BL-R16a |
| BL-E07c · PLANNED | [Viewed/Proven/Applied integration](features/f02-learning-experience/tasks/BL-E07c.md) | BL-E07b,BL-E02,BL-R16b,BL-R20,BL-R22b,BL-R23b |
| BL-E07d · PLANNED | [Accessibility, responsive và performance qualification](features/f02-learning-experience/tasks/BL-E07d.md) | BL-E07b,BL-E07c |


## Brief và checkpoint runtime đã có

| Task | File phải đọc/cập nhật |
|---|---|
| BL-R00 | [Brief, acceptance, checkpoint](platform/repository/tasks/BL-R00.md) |
| BL-R00a | [Initial reviewed Git baseline — brief/checkpoint](platform/repository/tasks/BL-R00a.md) |
| BL-R01 | [Brief, acceptance, checkpoint](platform/bootstrap/tasks/BL-R01.md) |
| BL-R01a | [Brief, acceptance, checkpoint](platform/toolchain/tasks/BL-R01a.md) |
| BL-R01b | [Brief, acceptance, checkpoint](platform/bootstrap/tasks/BL-R01b.md) |
| BL-R01c | [Brief, acceptance, checkpoint](platform/bootstrap/tasks/BL-R01c.md) |
| BL-R01d | [Brief, acceptance, checkpoint](platform/bootstrap/tasks/BL-R01d.md) |
| BL-R02a | [Brief, acceptance, checkpoint](platform/database/tasks/BL-R02a.md) |
| BL-R02b | [Brief, acceptance, checkpoint](platform/service-trust/tasks/BL-R02b.md) |
| BL-R02c | [Brief, acceptance, checkpoint](platform/delivery/tasks/BL-R02c.md) |
| BL-R03 | [Brief, acceptance, checkpoint](features/f10-identity-access/credentials/tasks/BL-R03.md) |
| BL-R04a | [Brief, acceptance, checkpoint](features/f10-identity-access/sessions/tasks/BL-R04a.md) |
| BL-R04b | [Brief, acceptance, checkpoint](features/f10-identity-access/sessions/tasks/BL-R04b.md) |
| BL-R04c | [Brief, acceptance, checkpoint](features/f10-identity-access/sessions/tasks/BL-R04c.md) |
| BL-R07 | [Content browser API — brief/checkpoint](features/f08-content/tasks/BL-R07.md) |

[Checkpoint môi trường](platform/environment/task-log.md) · [Baseline BL-I và lịch sử tổ chức](delivery/history/legacy-backlog.md).

## Công việc tài liệu hiện tại

| Task | Trạng thái | Brief/acceptance/checkpoint |
|---|---|---|
| BL-D15 | DONE | [Tổ chức doc theo feature](delivery/tasks/BL-D15.md) |
| BL-D16 | DONE | [Review cấu trúc dự án](delivery/tasks/BL-D16.md) |
| BL-D17 | DONE | [Hoàn thiện cấu trúc sau review](delivery/tasks/BL-D17.md) |
| BL-D18 | DONE | [Quy tắc comment giải thích code](delivery/tasks/BL-D18.md) |
| BL-D19 | DONE | [Tích hợp workflow điều phối Codex](delivery/tasks/BL-D19.md) |
| BL-P01 · prototype FE | DONE | [Bài học mẫu HTTP](features/f02-learning-experience/tasks/BL-P01.md) |
| BL-P02 · prototype FE/content | DONE | [Nội dung song ngữ EN–VI](features/f02-learning-experience/tasks/BL-P02.md) |
| BL-P03 · prototype FE/content | DONE | [HTTP chuẩn và content blocks](features/f02-learning-experience/tasks/BL-P03.md) |
| BL-P04 · prototype FE/i18n | DONE | [Locale English/Tiếng Việt toàn frontend](features/f02-learning-experience/tasks/BL-P04.md) |
| BL-P05 · prototype FE/content | DONE | [Biên tập lại tiếng Việt tự nhiên](features/f02-learning-experience/tasks/BL-P05.md) |
