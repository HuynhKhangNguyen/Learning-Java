# Thiết kế triển khai R1 v2

> Cập nhật30/09/2026: backend chuyển sang microservices theo yêu cầu người dùng. ADR-01 một-app/shared-DB và cơ chế guard/publication xuyên module dưới đây là baseline cũ, không là chỉ dẫn topology triển khai. [ADR-03](../../architecture/microservices-review.md) và [architecture BE](../../architecture/backend.md) giữ hướng mới; MS01–04 đặc tả lại service/auth/data/ops trước code. Invariant owner/evidence và atomic learner result vẫn cần giữ hoặc đổi minh bạch. ADR-02 cùng repo vẫn hiệu lực.

> BL-MS01 chốt [service/data/API boundaries](../../architecture/service-boundaries.md): ba DB/artifact riêng, T1/T2 local Learning. Bảng API dưới là baseline; catalog hiện hành 28 browser routes, outline anonymous/body login, private case/hint/solution qua Learning. MS02/03 đã thay guard/publication/reset/restore một-DB bằng protocol riêng, không dùng các đoạn cũ làm chỉ dẫn triển khai.

> MS02 [session](../../features/f10-identity-access/sessions/protocol.md)/[security](../../architecture/security-design.md), MS03 [data](../../architecture/data-protocols.md)/[publication](../../features/f08-content/publication/protocol.md)/[recovery](../../architecture/lifecycle-recovery-protocol.md), MS04 [implementation plan](../implementation-plan.md)/[test-release](../test-release-plan.md) và backlog v3 thay guard/build/ops/test/dependency một-app bên dưới. Baseline major/versions đã đối chiếu, actual manifests/qualification/reports thuộc runtime leaves; chưa implementation/CODE. ADR-02 layout vẫn hiệu lực.

BL-D08 · hoàn thiện 30/09/2026 · quyết định thiết kế, **chưa là chỉ dẫn triển khai code**. Nguồn hành vi: [requirements](../../product-requirements.md), [system](../../system-overview.md), [identity S02](../../features/f10-identity-access/business.md), [dữ liệu S03](../../features/f11-learner-work/data-contract.md), [DevOps S04](../../curriculum/devops-catalog.md), [bộ bài D06](../../features/f04-assessment/collections/cases.md), [UX D07](../../features/f02-learning-experience/collections/experience.md). Backlog thực thi có một nguồn ở [delivery-backlog](../../delivery-backlog.md), BL-R thay baseline BL-I.

## 1. Kiểm kê thực tế và quyết định giữ/chuyển

Đã đọc source và package, không suy từ prototype thành chức năng đã có. Đường dẫn trong bảng kiểm kê dưới là trước R00; nay src/public/tests/package/config ở frontend/, còn helper scripts/project.ps1 ở root. Architecture source hiện hành: [FE](../../architecture/frontend.md), [BE](../../architecture/backend.md).

| Tài sản | Thực tế | Hướng R1 |
|---|---|---|
| `src/components/AppShell.tsx`, `PageHeading.tsx`, `PreparationPanel.tsx`, styles | Shell, navigation và màn chuẩn bị | Giữ cấu trúc/token/icon; thêm trạng thái auth/dữ liệu, không viết lại thương hiệu |
| `src/pages/JourneyPage.tsx`, `src/data/journey.ts` | Outline tĩnh, topic ID là stage-index; gợi ý prerequisite là chặng trước | Giữ tương tác mở/đóng và bố cục; render từ public catalog, prerequisite CAP thật; bỏ quy tắc “chặng trước” |
| `src/content/types.ts` | Lesson schema1, đáp án cùng object, LessonProgress chỉ là interface | Đóng băng làm legacy; tạo schema2 phân tách public/private, không mở rộng status v1 thành mastery |
| `src/content/catalog.ts`, `lessons/apiDebug500.ts`, `validate.ts` | Một bài API 500 draft, validator chạy lúc import | Giữ fixture/test v1 để đọc đề cương; không xuất bản hoặc suy CAP từ evidenceIds v1 |
| `src/pages/LearningPage.tsx` | Màn thông báo chuẩn bị, chưa player/chấm | Giữ renderer legacy riêng; player Collections mới theo topic/activity public version |
| `src/App.tsx`, `routes.ts` | Route learn dùng lessonId/stepId, shortcut trỏ API500 | Giữ đường cũ có nhãn draft; thêm routes canonical, chuyển CTA chính sang mục Collections khi published |
| `tests/`, `scripts/project.ps1`, lockfile | Có content tests/check/build/Playwright helper | Giữ Node24/bundled runtime và lockfile; thêm backend/contract tests, không nâng package FE trong task nền |
| Lưu dữ liệu | `rg` chỉ thấy interface LessonProgress, không thấy localStorage/sessionStorage persistence trong src/tests | Không có cơ sở tuyên bố đã tồn tại DB học cần migrate; vẫn cung cấp import legacy khi người học có file |

Frontend phiên bản khóa trong package: React19.3.0, TypeScript6.0.3, Vite8.3.0, React Router8.4.0, Playwright1.63.0. Đây là quan sát repo, không là đề xuất upgrade.

## 2. ADR-01: một backend, một DB, cùng origin

Quyết định: React hiện có + Java21 + Spring Boot **4.1.1** + Maven Wrapper **3.9.16** + PostgreSQL **18.6**, baseline thiết kế theo nguồn cuối tài liệu. Không dùng “latest” trong manifest khi triển khai. Pin image digest/JDK patch và dependency tree thực tế trong BL-R01/R31; kiểm advisory tại thời điểm cài, thay baseline nếu cần phải ghi lý do, không tự nâng frontend. Java21 khớp chuẩn ngữ nghĩa bài học, không yêu cầu phiên bản Java mới nhất.

Backend dùng Spring MVC/Security/Validation, JDBC (`JdbcClient`/`JdbcTemplate`, SQL tường minh), Flyway migration, Spring Session JDBC; PostgreSQL driver và starter/plugin version theo Boot BOM/parent. Chọn JDBC thay ORM trong R1 để revision/owner/lock/atomicity nhìn rõ, không có graph entity phức tạp cần JPA. JSONB chỉ giữ payload đã validate, không thay FK/unique cho identity/quyền. Worker chấm nằm trong cùng process và dùng DB jobs; chưa thêm Redis, message broker, microservice, AI chấm hoặc runner.

Layout dự kiến, chưa tạo thư mục rỗng: `backend/pom.xml`, `backend/src/main/java/lab/backend/{identity,catalog,work,assessment,progress,transfer,maintenance}`, `backend/src/main/resources/db/migration/`, `backend/src/test/`; `contracts/` cho OpenAPI/JSON schemas/fixtures; `content-v2/` cho authoring; `frontend/src/features/` và `frontend/src/api/` cho adapter FE; `ops/` cho Compose/runbook. Module giao tiếp qua application service, không tự mở DB transaction ở controller hay component React.

Dev: Vite5173 proxy `/api` tới backend8080, PostgreSQL private port chỉ localhost khi cần; cùng origin ở browser. Prod pilot: một VM Linux chạy reverse proxy TLS + static `dist` + một backend + PostgreSQL volume riêng, DB không public; backup sang storage khác host. Có thể dùng managed PostgreSQL nếu hosting đã có, giữ cùng migration/runbook; không mua/tạo cloud từ thiết kế này. TLS/proxy production cấu hình ở BL-R31, provider/domain do operator cấp khi triển khai.

## 3. Auth, session và guard tại transaction

Spring Security giữ cookie HttpOnly/Secure/SameSite=Lax, CSRF và rotate session; Spring Session JDBC giữ session cùng DB. Production `__Host-` cookie, Path=/, không Domain; local profile cookie riêng không Secure chỉ trên localhost HTTP, startup prod từ chối profile ngoại lệ. Session idle30m/absolute12h theo S02, background poll không gia hạn thời gian hoạt động học. `GET /api/v1/session` bootstrap identity/context + CSRF, private response no-store.

**Guard quyền bền vững tách khỏi việc framework lưu session:** `account_guard` giữ trạng thái/credentialEpoch/learningEpoch/dataRevision; `session_guard` giữ digest session ID, context UUID, issuedAt/lastInteractiveAt/revokedAt/credentialEpoch. Chỉ security adapter tin cậy mới tạo guard sau xác thực; thiếu guard là không hợp lệ. Filter authenticate chưa đủ cho mutation.

Mọi mutation user: transaction READ COMMITTED → khóa account_guard `FOR UPDATE` → kiểm account/session guard/context/credentialEpoch/idle/absolute theo giờ DB → khóa publication nếu cần → kiểm revision/resource/receipt → ghi → commit. Logout/disable/reset-password cũng khóa cùng account trước revoke. Nhờ vậy revoke hoàn tất trước mutation commit thì mutation không được chấp nhận; nếu mutation thắng trước, nó thuộc owner cũ. Late flush Spring Session có thể ghi session record nhưng **không được hồi sinh guard đã revoke**. HTTP logout dùng pipeline Security để clear cookie/invalidate sau guard, lỗi cleanup không đảo việc revoke. Login kiểm hash ngoài lock rồi kiểm lại credential version/account status dưới guard trước cấp phiên để chặn reset/disable trong lúc tính hash. Login phải lưu session/guard thành công trước báo thành công; orphan guard không có session bị từ chối và cleanup.

Lock order thống nhất: account_guard → publication singleton (nếu thao tác liên quan nội dung) → resource/receipt/job theo ID. Publisher chỉ khóa publication, không khóa từng account trong cùng transaction; thay impact epoch để read model tính hiệu lực ngay. Worker T2 khóa account rồi publication rồi job. Acquire lease job trong transaction ngắn riêng rồi thả lock trước tính; không giữ job lock trong lúc chờ account. Không gọi password encoder, HTTP hoặc object storage khi giữ các lock này. Timeout lock5s trả retryable503; mutation chỉ retry cùng key sau tra receipt, không retry mù lỗi input.

Password encoder: Spring Argon2id, salt16byte/output32byte, memory19456KiB, iterations2, parallelism1; thêm BouncyCastle vì encoder cần nó. BL-R03 pin dependency resolved, benchmark cấu hình trên host và ghi duration/concurrency; không tự giảm tham số dưới baseline để chạy nhanh. Encode ID chứa phiên bản/tham số để nâng hash khi login. 15–128 Unicode code point, không truncate/trim; test chuỗi >72byte và Unicode. Không tuyên bố tuân thủ FIPS. Blocklist password common/compromised dùng corpus offline versioned/hash manifest; BL-R03 chuẩn bị nguồn/phạm vi/licence và fixture, production fail startup nếu thiếu corpus đã cấu hình, không gửi raw password ra ngoài.

Rate limit trước KDF: S02 account-key5/15m và IP30/15m, bộ đếm PostgreSQL với TTL30m, key HMAC(loginName normalized) không lộ tên; cap100k key hoạt động, khi chạm cap từ chối key mới bằng429 để không tăng vô hạn. Proxy chỉ tin forwarded IP từ proxy đã cấu hình. Dummy hash cho unknown user, generic error; không log secret/answer. Operator CLI ngoài HTTP tạo invite/reset-token/disable/delete, token256bit random chỉ lưu SHA-256 digest/purpose/expiry, token đầu ra chỉ giao qua kênh riêng. Không có public admin endpoints.

## 4. Schema quan hệ và invariant

UUID do server cấp cho resource; stable string ID cho TOP/CAP/activity/case; UTC timestamps, revision bigint không âm. Bảng bài làm/kết quả/progress có user_id + learning_epoch, FK owner-composite khi nối các bảng này. Exposure và receipt chống lặp thuộc owner xuyên epoch; không bị cleanup theo epoch. Không tin owner trong JSON. JSON schema2 reject unknown privileged fields, limit trước parse/chấm.

| Nhóm bảng | Key/constraint chính | Nội dung/giới hạn |
|---|---|---|
| app_user / account_guard | user PK, login_name UNIQUE, guard1:1 | Hash, active/disabled/invited, credentialEpoch/learningEpoch/dataRevision |
| activation_token | token_digest UNIQUE, user/purpose | Expiry/single use; consume+credential change atomic |
| session_guard / Spring session | session digest UNIQUE, context UNIQUE | Không xuất cookie/guard qua API; context không phải credential |
| auth_throttle / security_audit | keyed TTL buckets / event PK | Counter bounded; audit metadata30ngày, không answer/secret |
| topic / competency | TOP PK; CAP PK + TOP FK | Một source chung hai roadmap; 95TOP/190CAP theo S04 |
| roadmap_stage / placement / prerequisite | roadmap+DS/S key; TOP/CAP FK | Placement nhiều nơi, không thêm progress theo roadmap; prerequisite DAG |
| content_version / publication | entity+version UNIQUE; singleton revision | Public body/private grading tách cột/bảng/DTO, checksum bất biến; manifest impacts theo CAP |
| exercise_version / case_version | exercise+version, case+version UNIQUE | Rubric/grader/family/targetCAP và input schema; case expected chỉ backend |
| learner_attempt / draft | user+epoch+attempt UNIQUE; draft attempt UNIQUE | Case/mode do server chọn; draft revision CAS, response/context snapshot |
| support_exposure | user+equivalence_family UNIQUE | Monotonic mức hỗ trợ/known-case, không thuộc reset epoch; cả case đã nộp coi đã dùng |
| submission | attempt UNIQUE + owner composite FK | Snapshot answer/version/support bất biến, acceptedAt |
| grade_job / result | submission UNIQUE ở mỗi bảng | Lease token/expiresAt/tryCount; final result một bản trong R1 |
| evidence | result+target+kind UNIQUE, owner FK | Chỉ evaluator được ghi; source/mode/support/policyVersion |
| viewed_event / progress_projection | user+epoch+TOP/CAP hoặc milestone key | Viewed/proven/applied riêng; provenance và reviewReason; rebuild được |
| mutation_receipt | user+operation+key UNIQUE | canonical fingerprint/resource/outcome; không giữ answer lặp trong receipt |
| import_staging / imported_record | owner+preview UUID / source+sourceId UNIQUE theo owner | TTL24h, checksum/dataRevision/epoch, chỉ unverified hoặc draft mới |
| suppression_outbox / reset_receipt | operation UUID UNIQUE | Metadata reset/delete để đồng bộ nhật ký phục hồi ngoài snapshot; không answer |

Submission/result/evidence không UPDATE nội dung qua learner API; constraint/FK bảo vệ ownership kể cả dev viết nhầm query. Cleanup theo epoch chỉ xóa dữ liệu học đã reset trong7ngày; giữ receipt metadata tối thiểu và exposure theo S03. FK catalog dùng RESTRICT để còn đọc được lịch sử; không cascade xóa version đã được tham chiếu. Migration forward-only bằng Flyway, không dùng auto-DDL; migration session schema cũng quản lý version, tắt auto-create ngoài local bootstrap rõ ràng.

## 5. API contract v1

OpenAPI được biên soạn trước consumer; fixtures là JSON request/response cùng schemas trong `contracts/`. R1 dùng DTO TypeScript viết tay ở boundary, kiểm bằng contract fixtures/schema; mỗi task cập nhật endpoint và DTO mình sở hữu. Chưa thêm code generator. Tên bảng không là DTO public.

Authenticated mutation có CSRF, `X-Session-Context`, `Idempotency-Key` UUID. Fingerprint = SHA-256 của canonical JSON business payload theo schema version: object key sorted, array giữ thứ tự, string nguyên vẹn, không chấp nhận số không hữu hạn; operation/resource/epoch/version/revision nằm trong payload fingerprint, cookie/CSRF/context không nằm trong fingerprint. Đổi session vẫn phải kiểm context mới và tra receipt owner cũ trước hành động chủ động. Retry cùng payload trả outcome cũ sau auth, payload khác409.

| Nhóm endpoint | Input → response chính | Quyền/hành vi |
|---|---|---|
| GET /session; POST /auth/login, /logout, /logout-all | credentials khi cần → identity/context hoặc204 | CSRF cả anonymous credential flows; logout-all reauth |
| POST /auth/activate, /password-reset, /password-change | token/password hoặc current/new | Token consume atomic; revoke guard; không tự login sau reset |
| GET /catalog, /topics/{id}, /topics/{id}/units/{unit} | publicationRevision → public DTO | Outline anonymous; chỉ published body khả dụng, không expected/private rubric |
| POST /attempts | exerciseId, mode, learningEpoch | Server chọn case sạch, version/owner;201 attempt+draft(revision0) |
| GET /attempts/{id}; PUT /drafts/{id} | expectedRevision, answer, position → revision | Owner/epoch, unknown fields400, CAS409; attempt đã đóng409 |
| POST /attempts/{id}/support | hintLevel/solution → support+hint | Ghi exposure trước trả hint; same-account lock sắp thứ tự với submit |
| POST /attempts/{id}/abandon | expectedRevision → closed | Không fail; draft giữ history theo retention |
| POST /attempts/{id}/submissions | expectedRevision, answer, epoch →202 id/status | T1 save+freeze+job+receipt atomic, tối đa1/attempt |
| GET /submissions/{id}; GET /receipts/{operation}/{key} | owner → pending/error/result hoặc404 | Lookup sau timeout; detail không lộ dữ liệu user khác |
| POST /viewed-events | TOP/contentVersion/unit ID → viewed revision | Chỉ published hợp lệ; không CAP pass |
| GET /progress, /history, /recommendations | cursor/filter → items/nextCursor | user lấy từ principal; limit mặc định20/max100; history stable timestamp+ID |
| GET /learning-data/export | schema2 JSON snapshot | REPEATABLE READ consistent snapshot, ownership, no-store; không private answers của đề |
| POST /learning-data/import-preview, /import-apply | payload → previewId; previewId+checksum → receipt | Validate all, explicit legacy ownerless claim; dataRevision/epoch stale409; merge atomic |
| POST /learning-data/reset | reauth proof, epoch, confirmation →202 processing hoặc receipt đã hoàn tất | Chỉ reset toàn dữ liệu học theo S03; idempotent, không xóa account |

Lỗi chuẩn `{code,message,correlationId,fieldErrors?,retryable}`; 400validation,401session,403CSRF/quyền,404absent/khác owner,409REVISION_CONFLICT/CONTEXT_CHANGED/KEY_REUSED/CASE_EXHAUSTED/VERSION_UNAVAILABLE,413quota,429throttle,503dependency. Không gửi stack trace hay bản draft khác owner. Conflict owner có thể trả currentRevision+link GET draft để hiển thị hai bản; không trả toàn dữ liệu nhạy cảm vào log. Không cố ép mọi Security filter trả401 nếu thứ tự CSRF tạo403; FE rebootstrap, không replay write.

Initial quotas: answer/code-text64KiB UTF-8, trace1000action/attempt, text field max theo schema bài, mô hình12phần tử theo D06; import5MiB/500record, draft tối đa20đang mở/user, staging1active/user. JSON depth20. Export streaming trong snapshot có timeout30s; vượt khả năng pilot báo retry/unavailable rõ, không cắt file hợp lệ giả. Các ngưỡng này là cấu hình ban đầu cần đo, không giới hạn ngữ nghĩa Java.

## 6. Transaction, worker và hiệu lực progress

T1 theo S03: account guard + publication check → expectedRevision + exposure + case eligibility → snapshot/close draft → submission/job/receipt → commit. Fail ở bất cứ insert nào rollback. T2: tính rubric pure ngoài lock từ immutable snapshot; commit sau guard/epoch/publication/job lease check, cùng result/evidence/progress/job complete. Không ghi tạm pass vào UI. Worker restart lấy lease hết hạn; lease60s, heartbeat20s nếu cần, tối đa3retry tự động với backoff, sau đó retryable_error được operator retry cùng job; không thành fail learner. Finalization kiểm fencing token để worker lease cũ không ghi sau worker mới.

Row publication bảo vệ order T2/publish. Publish không quét lock mọi user: tăng publicationRevision + impact rows; read progress xét evidence với impact hiện hành, projection cũ phải reconcile trước trả current mastery. Worker đã nhận được phép hoàn tất sau logout/disable, nhưng epoch reset/deletion loại khỏi progress và cleanup không tái tạo dữ liệu. Job epoch cũ được đánh dấu superseded/cancelled nội bộ, không retry vô hạn hoặc tính là learner fail; API không trả resource epoch cũ. Hai kết quả CAP đồng thời cập nhật projection dưới account guard để không ghi mất snapshot nhau. Viewed/events/import tăng dataRevision; preview import invalid nếu revision khác, apply+receipt một transaction.

Grader Collections là mã Java xác định cho schema câu trả lời/trace, không eval source. D06 fixture reference dùng cả frontend simulator và backend tests nhưng không dùng frontend output làm authority. Với HashSet/Map compare bỏ thứ tự key, List và list con giữ thứ tự; CAP pass = all required criterion trong cùng submission, hỗ trợ không độc lập. CL-U-01 tạo applied mô hình riêng; không P1/BigO. 8family hữu hạn theo D06; version rename/shuffle không reset exposure. Case availability query phải loại cả used/known trước cấp attempt; hai tab mở cùng case có thể resume cùng attempt đang mở, không cấp hai độc lập cho một family.

## 7. Nội dung v2, adapter và frontend

Collections có package ID ổn định `PKG-COLLECTIONS-CORE`, chỉ để điều hướng/biên soạn, không có progress hay mastery riêng. Manifest gắn mỗi unit/activity với các TOP/CAP liên quan và một canonicalTopicId thuộc tập đó. Route learn dùng canonicalTopicId; mở từ TOP khác vẫn giữ returnTo hợp lệ trong app. Viewed-event gửi unit ID/version; backend resolve mapping từ manifest và chỉ ghi các phần bắt buộc của TOP được khai báo, không tin danh sách TOP do client gửi. BL-R05/06/07/15/16 sở hữu contract, publish và render mapping này.

`content-v2/` authoring bundle gồm catalog, units, practice public, assessment private, manifest/impact/references. Public build chỉ chứa nội dung học/luyện cho phép xem; không import authoring private trong Vite hoặc sourcemap. Publisher CLI validate schema/95TOP/190CAP/DS-DP/reference/DAG/coverage và reviewer metadata, preview diff rồi transaction publish; cùng ID/version khác checksum bị từ chối. R1 chỉ Collections đủ runtime mới published, DevOps là outline; dự thảo API500 vẫn draft. Preview môi trường tác giả tách credentials, không gắn draft thành public bằng query param.

FE giữ fetch wrapper cùng origin, credentials cookie/CSRF, abort + authGeneration bỏ response cũ; data adapter typed từ contract. Không thêm state/query library mặc định: hooks/reducer + một session provider đủ R1, request cache cá nhân RAM theo owner/context và xóa khi logout. Public catalog cache theo publicationRevision, service worker không cache dữ liệu private. Draft autosave debounce500ms, một request in-flight/draft; revision ACK chỉ đánh dấu snapshot đó. Submit chờ autosave pending rồi gửi snapshot cuối/expectedRevision. Lỗi/conflict không sửa answer đang gõ.

Routes chốt: `/journey/:roadmapId`, `/topics/:topicId`, `/learn/:topicId/:unitId`, `/practice/:exerciseId`, `/attempts/:attemptId`, `/submissions/:submissionId`, `/history`, `/account/learning-data`, `/login`, `/activate`, `/password-reset`. Trong `/learn`, prefix `TOP-` đi qua resolver v2; `api-debug-500` giữ legacy renderer draft, không redirect sang một TOP đoán mò. Link cũ `?topic=stage-index` có bảng alias explicit lúc BL-R14; nếu không ánh xạ duy nhất thì giữ chọn chặng/hiện outline cũ, không chọn nhầm CAP hoặc suy progress. Back/reload đọc server theo ID, không submit lại.

Legacy JSON schema1 chỉ qua validator cũ và adapter imported_record unverified với original blob có hạn mức; unsupported giữ file gốc không apply. Không tạo draft Collections từ answer API500. Export v2 roundtrip import vẫn unverified, không “restore pass”. V1 lesson content giữ test riêng đến khi có quyết định retire, không xóa chỉ để schema2 pass.

## 8. Backup, xóa dữ liệu và vận hành

Pilot migration chạy bằng DB role riêng trước app rollout; app role không có DDL. DB pool hữu hạn, readiness kiểm DB/migration; liveness không phụ thuộc mạng ngoài. App log correlation/action/result/duration, không raw answer/token; metrics auth errors, save conflicts, pending/job retry, DB pool và restore age, không label userId có cardinality cao. Secrets file/env riêng không commit; release artifact digest dùng lại qua staging→prod, kiểm smoke A/B và schema compatibility. DB migration destructive cần task riêng, không hứa rollback image đảo schema.

Backup `pg_dump` consistent mỗi ngày, encrypt bằng key giữ ngoài DB và lưu khác host, giữ30ngày; cấu hình cron/operator ở BL-R32, không tạo automation chat. Local test dùng thư mục lab riêng; production object-store credentials/domain là input vận hành, không cần để bắt đầu code. Restore drill dựng DB riêng, restore catalog/users/work/jobs rồi kiểm FK/counts/A-B/rebuild/dedupe; ghi RPO/RTO đo thực, chưa có SLA. Không bật traffic khi bước đối soát chưa xong.

**Nhật ký suppression không rollback cùng snapshot:** chọn append-only JSON records có operationUUID/userUUID/epoch/cutoff/action, không answer, lưu durable ngoài DB backup (local dev thư mục riêng; prod storage versioned/access-controlled khác host). Quy trình reset/delete hai bước có khả năng phục hồi: trong transaction guard đánh dấu suppression pending và outbox, khóa ghi học của user; worker ghi journal idempotent và verify durable; transaction kế tăng epoch/xóa logical + receipt rồi bỏ pending. Response202processing khi chưa đủ journal, không báo reset thành công trước commit; journal lỗi giữ dữ liệu chưa xóa nhưng khóa ghi, cho retry operator, UI giải thích đang xử lý. Journal ghi trước final DB commit nghĩa là restore có thể tiếp tục reset đang được yêu cầu; đây là chủ đích đã xác nhận, không phục hồi dữ liệu cũ về phục vụ. S03 được làm rõ theo luồng này; không giả atomic giữa DB và storage ngoài.

Reset-receipt giữ key+outcome để retry không xóa bài mới. Account deletion cũng qua suppression pending, sau journal mới purge user/exposure/học; UUID không tái sử dụng. Journal giữ ít nhất tới khi mọi backup có thể chứa dữ liệu đó đã hết hạn (30ngày +7ngày grace), snapshot inventory phải được kiểm trước purge journal; backup giữ ngoại lệ kéo dài thì journal kéo dài tương ứng. Restore thiếu journal hoặc checksum không xác minh được: fail closed/offline. Khi phục hồi xong xóa toàn Spring sessions/guards và invalidate mọi invite/reset token cũ, ép login/reset qua kênh operator; không cho session trong backup sống lại.

## 9. Kiểm tra và cổng thực thi

Backend JUnit theo BOM + integration PostgreSQL18.6 thật trong DB lab tách biệt; ưu tiên Compose fixture ổn định, chưa thêm Testcontainers chỉ để có tên tool. SQL tests dùng barrier/latch điều khiển hai connection cho revoke/T1, hint/submit, reset/T2, publish/T2 và revision races; không sleep đoán timing. Assert dữ liệu cuối sau fault injection, không chỉ HTTP status. Unit grader từ CA/CM expected độc lập, không snapshot implementation.

Frontend giữ content/check/build và Playwright theo [quality process](../../quality-process.md); dùng Node24 helper, Chrome channel sẵn có khi bundled browser thiếu. Contract tests validate OpenAPI examples/public payload không có answer/private rubric; E2E browser+backend+DB cho session/owner/draft/submit/progress. H2/mock không chứng minh Postgres locks. Full suite trước release, test hẹp khi sửa hẹp.

| Nhóm requirement | Task chịu trách nhiệm | Evidence test |
|---|---|---|
| REQ-01/02/10/18 | BL-R05/06/07/14/15/30 | 95TOP/190CAP, DAG, DS/DP shared, planned/published, UX-01/14 |
| REQ-03/04/12 | BL-R16/17/18/19 | CM-01–25, CA-03–06, mô hình đúng/không Java claim |
| REQ-05/06/16 | BL-R08–13/20–23 | AP-01–22, CA-01–14, LD-03–20/29; result riêng CAP |
| REQ-07/11 | BL-R14–24/33 | UX-01–20, Back/resume/focus/mobile/theme/error |
| REQ-09/15 | BL-R02/04/08/09/11/13/25–29/32 | IA-01–24, LD-01–30, fault/race/export/import/reset/restore |
| REQ-14 | BL-R03/04/24/31 | Invite/reset/login/logout/expiry/CSRF/guard, generic errors/throttle |
| REQ-08 | BL-R22/23 | U1–U5, readiness khác applied, không P1 tự cấp |
| REQ-13 | Mỗi task + BL-R30–34 | Checkpoint/test/visual/learner evidence thật, không fake pass |
| REQ-17 | BL-S05 sau R1 | Không endpoint/role admin trong R1 |

Mỗi BL-R trong backlog có dependency, đầu ra, acceptance/test và phần loại trừ. BL-R00 chuẩn bị cấu trúc trước BL-R01; BL-R01 vẫn PLANNED đến khi R00 đạt và người dùng cho phép code; không gọi task code READY chỉ vì thiết kế xong. D08 hoàn thành “sẵn sàng cho lát cắt đầu” ở mức đầu vào kỹ thuật, không vượt cổng cấp phép.

## 10. Nguồn và quyết định còn phụ thuộc đo thực

Đối chiếu chính thức 29/09/2026: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) cho baseline4.1.1/Java21 và Maven; [Maven history](https://maven.apache.org/docs/history.html) cho3.9.16; [PostgreSQL versioning](https://www.postgresql.org/support/versioning/) cho18.6. Đây là baseline được chọn, không khẳng định mới nhất mãi hoặc đã chạy cùng nhau trong repo.

[Spring Session JDBC](https://docs.spring.io/spring-session/reference/guides/java-jdbc.html) xác nhận backing relational DB; guard transaction là thiết kế bổ sung của app, không tính năng framework tự bảo đảm. [PostgreSQL row locks](https://www.postgresql.org/docs/18/explicit-locking.html) hỗ trợ thứ tự serial hóa ngắn; cần race test. [Spring password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) cho PasswordEncoder/BouncyCastle; [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html) là nguồn tham số baseline Argon2id, không thay benchmark.

Cần số đo/lựa chọn triển khai: JDK/image digest thực, thời gian KDF, corpus blocklist, host/domain/storage credential, restore duration/capacity và input quotas. Owner cụ thể BL-R01/03/31/32; không phải lý do tự thêm tính năng hoặc xin lại quyết định business. D09 phải rà consistency với S02/S03 và các task trước code. Thiết kế này không cài dependency, tạo DB/backend service hay deploy gì.

## 11. ADR-02: một repository, frontend và backend riêng thư mục

Cập nhật theo yêu cầu người dùng 30/09/2026: giữ chung repo, tách FE vào `frontend/`, backend vào `backend/`. Quyết định này thay layout FE ở root trong đề xuất D08 ban đầu; bảng kiểm kê mục1 vẫn mô tả vị trí hiện tại trước di chuyển, không phải đường dẫn đích.

- Root giữ `AGENTS.md`, `README.md`, `docs/`, `scripts/` điều phối; các tài liệu lịch sử vẫn giữ để đối chiếu.
- `frontend/` sở hữu src/public/index.html, package.json và lockfile nguyên phiên bản, cấu hình Vite/TypeScript/ESLint/Playwright, test và script chỉ dùng cho FE. Script runtime/content FE đi theo package; `scripts/project.ps1` ở root tiếp tục chọn runtime và chuyển cwd sang frontend.
- `backend/` sở hữu Maven/build/config/migrations/test riêng; không cần npm workspace, monorepo framework hay dependency mới chỉ để tách thư mục.
- `contracts/`, `content-v2/`, `ops/` ở root theo trách nhiệm đã chốt. Authoring private không thuộc Vite root/public và vẫn phải kiểm bundle/sourcemap không lộ expected.
- BL-R00 di chuyển FE trước R01: kiểm inventory/config/relative paths, test fixtures và helper; cập nhật ignore/output path (`frontend/dist` và report FE), README và deployment references có liên quan. Chỉ tạo thư mục cần di chuyển, không scaffold backend trong R00.
- Giữ hành vi và package/lockfile; không đổi framework, nâng version hoặc sửa feature. Không di chuyển/xóa dữ liệu người dùng hoặc generated directories hàng loạt. Khi thực thi phải kiểm path đích trong workspace, xử lý process đang dùng checkout và giữ khả năng phục hồi nếu lỗi.

DoD R00: content tests/check/build/smoke hiện có chạy qua helper root với cwd mới, dev/preview và deep link đang có hoạt động, không reference root cũ trong cấu hình thực thi; review scope và lockfile không đổi nội dung dependency. Lỗi môi trường ghi riêng, không tự PASS. R00 đã chuyển source/config/package FE và helper cwd theo quyết định này; kết quả kiểm tra/giới hạn nằm trong checkpoint backlog, không suy backend đã có runtime.
