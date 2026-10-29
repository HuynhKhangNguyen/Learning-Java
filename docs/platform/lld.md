# LLD-07 — Nền tảng, API chung, vận hành và test

> MS04 v3 hiện hành: [implementation plan](../delivery/implementation-plan.md), [test/release plan](../delivery/test-release-plan.md), [architecture BE](../architecture/backend.md) và [backlog](../delivery-backlog.md). Trang này tóm tắt để review; D08 một-app/source/ops chỉ lịch sử. FE sau R00 giữ nguyên, chưa backend runtime.

[D14 review](../architecture/readiness-review.md) bổ sung edge/TLS lab trước FE, common wire, proxy privacy, checkpoint interlock và review UX sớm. Các mục bên dưới đọc cùng catalog §4.1/lifecycle §5.1, không dùng common API của D08 cũ khi khác quy ước hiện hành.

[Mục lục](../design/README.md) · MD01–11 trong phạm vi R1, trừ runner MD08 · BL-R00/01/02/31–34

## Cấu trúc triển khai dự kiến

Giữ React/TypeScript/Vite/lockfile/helper hiện có. MS04 đối chiếu baseline Java21/Spring Boot4.1.1/Maven3.9.16/PostgreSQL18.6, chọn ba app/DB/roles riêng. R01a pin JDK distribution/patch/wrapper checksum, R01b–d mỗi bootstrap/artifact riêng, R02a DB isolation; exact images/advisory/CLI/provider qualification có task owner trước pilot, không giả đã chạy cùng nhau.

```text
AGENTS.md / README.md
docs/                        business, thiết kế, backlog và DoD
scripts/                     helper điều phối ở root
frontend/
  src/api/                   fetch/DTO/session boundary
  src/features/              các màn và hành vi học
  tests/                     tests FE
  scripts/                   scripts runtime/content của FE
  package.json / package-lock.json
  public/ / index.html       assets và entry FE
  vite/ts/eslint/playwright configs
backend/
  pom.xml / mvnw / mvnw.cmd / .mvn/
  services/identity-service/   own pom/entry/resources/db/migration/tests
  services/content-service/   own pom/entry/resources/db/migration/tests
  services/learning-service/  own pom/entry/resources/db/migration/tests
contracts/                   OpenAPI, JSON schema, fixtures
content-v2/                  authoring public/private và manifest
ops/                         Compose, proxy, backup/restore runbook
```

Đích cùng repo ADR-02, FE đã chuyển R00, backend chỉ README. Java source tree/api/features còn dự kiến. Controller→application local transactions, SQL/JDBC/Flyway per-service, no cross-DB FK/join/source imports. Learning work/result/evidence/progress giữ T1/T2 local, worker nội bộ; Identity online admission/auth fences, Content publication coordinator và Learning private replica. Không admin/runner R1.

## Schema và boundary chung

Learning work/result/progress owner+epoch, FK composite trong learning_db; submission/result/evidence unique. user UUID không FK identity_db, pinned content private replica không join content_db. Exposure/receipts xuyên epoch, external journal durable trước disclosure/reset/delete. Content versions immutable giữ history refs; UUID server/TOP-CAP stable/UTC. HTTP relay outbox/inbox/exact digest/sequence, no exactly-once/global transaction claim.

API `/api/v1`, JSON schema theo operation; DTO public tách table/private rubric. Mỗi task cập nhật OpenAPI/examples/schema/TS boundary DTO và test mình sở hữu. Không chờ task audit cuối mới viết contract.

| Quy tắc | Contract |
|---|---|
| Learning mutation có receipt | Cookie+CSRF+X-Session-Context+Idempotency-Key; owner từ principal; Identity key/preauth consume theo MS02 |
| Fingerprint | SHA-256 canonical business payload, gồm resource/epoch/version/revision; không cookie/CSRF/context |
| Response lỗi | schemaVersion/code/traceId/messageKey, operationId/retryAfterSeconds/fieldErrors? theo catalog §4.1; không echo stack/secrets/answer |
| HTTP | 400 input;401 session;403 CSRF/quyền;404 absent/owner;409 conflict;413 quota;429 throttle;503 dependency |
| Wire types | Bigint counters decimal string exact/range checked, UTC timestamp; JSON duplicate/privileged key reject; cursor opaque bound owner/epoch/filter/asOf vẫn cần auth |
| Giới hạn pilot | Answer64KiB, trace1000action, model12phần tử,20draft mở/user,1preview/user; paging20/max100 |
| Lock | User: auth gate→learner guard→shared pub gate→resource; T2: learner→pub→job; publication exclusive gate; HTTP/storage/KDF ngoài mọi domain lock |

Quota là cấu hình ban đầu cần đo, không là giới hạn Java. Canonicalization giữ thứ tự array và nội dung string; contract fixtures phải kiểm fingerprint nhất quán. Retry vẫn kiểm auth/owner trước trả receipt, không dùng key để bỏ qua quyền.

## Môi trường và release

Dev Vite5173→loopback edge8080→three public **HTTPS/mTLS edge-only** listeners8080/container; TLS browser lab8443 kiểm __Host-cookie. Internal mTLS8443/container direct/service-operator-only, no edge route; pilotHTTPS443/public origin/static/API, threeDB/private roles. Edge verify upstream CA/SAN/SNI với client cert riêng, không private payload temp/cache/store/log hoặc auto-retry mutation. R31a tạo base CMD-07 trước R14/R24a. Journal local lab riêng, S3 adapter production phải provider qualification trước pilot. Host/domain/credentials/key custody cần ở task ops; không tự mua cloud/deploy.

DB migration role khác app role; app không auto-DDL. Release dùng cùng artifact digest staging→prod; migration preflight/smoke A/B trước mở traffic. Rollback image không tự đảo migration phá dữ liệu. Secrets không commit/log. Readiness kiểm DB/schema; liveness không phụ thuộc dịch vụ ngoài.

Correlation across admission/phase/outbox/job, bounded metrics/no answer/token/user cardinality. R31b đo budgets/alert; audit30 ngày baseline, backup/cleanup/floor compaction theo MS03. Controlled daily checkpoint có durable maintenance interlock ngoài snapshots/stop toàn app-writer/NOLOGIN roles/no surviving connections; crash/partial cut/stale resume không complete hoặc tự mở traffic. Capture ba DB + verified fixed journal/index/manifest encrypted/offhost30 ngày; verified resume/recovery pump theo lifecycle §5.1. Isolated restore trước pilot, new realm/generations và RECOVERY_REQUIRED mọi restored account. R32a/b actual pause/RPO/RTO + fail-closed report, không SLA/online backup giả.

## Chiến lược kiểm tra và bàn giao

| Lớp | Chứng minh gì | Khi nào |
|---|---|---|
| Schema/content validation | ID/reference/DAG/coverage/public-private | Mỗi thay đổi nội dung/contract |
| Unit grader/reducer | Expected độc lập, biên null/order/index, không copy implementation | R12/17 và sửa logic |
| DB integration | Owner/FK/CAS/atomicity/locks/fencing | Task thay dữ liệu/quyền; PostgreSQL thật, không H2 thay bằng chứng |
| API/contract | Status/DTO/quotas/CSRF/không đáp án private | Endpoint tương ứng |
| Browser E2E | Login→draft→submit→result, expiry/change user, Back/keyboard/mobile | Task UI và tích hợp |
| Restore/release | Journal replay, invalidation, smoke, RPO/RTO đo | R31/32 trước nghiệm thu |
| Learner test | Người học hiểu và dùng trọn vòng Collections | R34, không thay bằng automation |

Race dùng barrier/latch điều khiển hai connection, không sleep đoán thời điểm. Fault injection kiểm DB cuối, không chỉ HTTP code. Docs thuần kiểm links/IDs/consistency; không chạy full app test nếu code không đổi. Full suite trước release; sửa hẹp chạy kiểm tra phù hợp rồi broaden nếu có lý do.

CMD-01 FE có thật, CMD-02–11 backend/DB/fault/journal/E2E/scan/backup/restore/all còn command contract chưa tạo/chạy. SEC-01–30/DATA-01–30/WIRE-01–06 có leaf owners/10fixtures/expected evidence; IA/LD/AP/CA/CM/UX registry expand từng ID. REL-01–08 là release DoD: artifacts/compatibility/security/integrity/privacy/UX/recovery/learner, không required skip/0tests PASS. R15/R20 checkpoint demo review slice sớm, không thay R34 feedback learner thực.

Mỗi session một leaf task, parent chỉ rollup, dependencies không suy từ số. R28a journal trước attempt/hint, R32b drill trước R31d pilot. Thiết kế đủ input không thay CODE; task đầu R01a chờ chỉ dẫn. Runtime/performance/restore chưa kiểm chứng.

- **LLD-07-A:** ba service/DB riêng, journal off-host và gates trước pilot có phù hợp hướng đã chọn không?
- **LLD-07-B:** test/review/learner test và nhịp một session một task có phù hợp không?
- **LLD-07-C:** phần nào cần bổ sung trước khi cho phép code?

Nguồn: [D08](../delivery/history/implementation-v2.md), [quality process](../quality-process.md), [execution model](../execution-model.md), [backlog](../delivery-backlog.md).
