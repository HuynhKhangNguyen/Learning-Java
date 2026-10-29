# R01c — Bằng chứng bootstrap Content

Ngày 01/10/2026. Phạm vi nền CNT/MD01, REQ-10/13; [backlog](../../delivery-backlog.md) giữ brief, trạng thái và checkpoint. [README Content](../../../backend/services/content-service/README.md) giữ lệnh build/chạy. Đây là bootstrap executable, chưa nghiệm thu nội dung published, publication, auth hoặc DB.

> Số liệu/module/handoff bên dưới thuộc checkpoint R01c. R01d sau đó thêm Learning và [evidence riêng](learning-verification.md); trạng thái hiện hành và R01 rollup ở backlog, không ghi đè kết quả Content.

> R02a thêm migration/test dependencies và cập nhật current snapshots/JAR hashes trong [DB evidence](../database/verification.md). Số liệu bootstrap bên dưới giữ checkpoint lịch sử; current manifest không là bản artifact cũ.

## Đầu ra

[Content POM](../../../backend/services/content-service/pom.xml), entry point `lab.backend.content.ContentApplication`, initializer guard, status controller, hai properties và hai Java test suites. Parent [POM](../../../backend/pom.xml) khai báo hai child thật Identity/Content. Selected reactor Content chỉ chạy parent + Content; source/service JAR không import Identity/Learning hoặc shared-domain JAR. Không tạo package domain/migration rỗng.

Dependencies trực tiếp chỉ Boot starter-webmvc và starter-test (test scope). Giữ Boot 4.1.1, Java 21 và inherited Tomcat 11.0.26. Artifact executable có Start-Class đúng, 19,936,051 bytes và 34 runtime libraries; không JUnit/Mockito/test libs, Security/JDBC/Flyway/PostgreSQL hoặc service khác trong JAR.

[Content dependency snapshot](../../../ops/release/content-dependencies.json) ghi actual Maven tree, 68 application/test artifacts với computed SHA256 và nguồn Maven Central: 39 compile / 29 test. [Manifest](../../../ops/release/dependencies.json) giữ snapshot/JAR hashes và giới hạn qualification. Đây không phải immutable lockfile, signed SBOM, toàn build-plugin graph hoặc full SCA. Metadata R01a và snapshot Identity vẫn được giữ.

## Acceptance và kết quả

Windows x64, Microsoft JDK 21.0.12.1+1-LTS portable, Maven 3.9.16 qua wrapper, PowerShell hiện có; Python smoke dùng standard library hiện có. Build và dependency tree chạy offline từ cache đã chuẩn bị ở R01a/b. Không đổi dependency pins, tải/install runtime mới, FE hoặc source Identity.

| Acceptance | Cách kiểm / expected | Kết quả thực |
|---|---|---|
| R01c-AC01 | Selected parent + Content; entry/JAR riêng, không cross-service source/runtime | POM/JAR/tree inspection đạt; 34 runtime libraries, Start-Class đúng; baseline hashes được đối chiếu |
| R01c-AC02 | Root: `pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service content -Offline`, socket-temp override như README | exit 0, BUILD SUCCESS; 28 tests, 0 failure/error/skip; helper đọc reports thật |
| R01c-AC02 HTTP | RANDOM_PORT + main method; server và Java HTTP client thật | 10 case đạt: JSON status/no cookie; 7 route catalog/topic/unit/private bundle/auth/progress/management trả 404; POST bootstrap 405; actual Tomcat 11.0.26 |
| R01c-AC03 config | Chạy SpringApplication thật; cấu hình sai không được mở listener | 18 case đạt: không explicit profile; 5 profile sai/mixed; 4 bind khác loopback; 5 port sai/reserved/privileged; 3 TLS/management/headless |
| R01c-AC03 artifact | Root: `python scripts/tests/content_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets` | exit 0; 7 checks: 5 startup negative nonzero trước Tomcat initialized, HTTP từ JAR, process và listener stopped |
| R01c-AC04 | Actual Maven dependency-plugin 3.10.0 tree + hashes, metadata/POM/JAR thống nhất | exit 0; 68 dependencies, manifest/snapshot/smoke hash khớp; Tomcat patch xác minh trong actual test runtime và JAR |

Bootstrap explicit `bootstrap` chỉ chạy HTTP loopback `127.0.0.1:18082` hoặc random port, trả `businessReady=false`. Không profile, lab/private/pilot/unknown/mixed, bind-all và cổng final public/private bị từ chối trước listener. HTTP 404 của business route chưa có không phải bằng chứng quyền anonymous/owner đã triển khai. Wire/error/trust contract cuối ở R02b, không bị thay bởi status bootstrap.

## Giới hạn và review

R01c dùng process-local `jdk.net.unixdomain.tmpdir` tới ignored `.tools/jvm-ipc-sockets`, theo [lỗi và xác minh R01b](identity-verification.md). Giữ default selector provider; không mock/skip HTTP, đổi environment persistent hoặc tắt TLS validation. Không chạy lại default-temp để gọi nó PASS; chưa kết luận nguyên nhân Windows/runner. Qualification hiện tại có điều kiện này. Mockito dynamic-agent warning trong test log là test-only, không library runtime JAR.

Logs/reports thực trong ignored `artifacts/quality/R01c/`: `content-verify.log`, `content-dependency-tree.json`, `dependency-tree.log`, `artifact-*.log`, `artifact-smoke.json`, `baseline.json` và `missing-learning.log`. Missing Learning selected verify trả exit 1 đúng expected; chưa tạo module không được in PASS. Mọi process trong packaged smoke đã dừng; không service nền được bàn giao như đã deploy.

Review theo DONE-01–05: scope/config/route boundaries, actual reports, JAR/runtime privacy, dependency DAG/links/status và baseline hashes. Không có P0/P1 còn mở trong scope bootstrap. FE/UI/learner/DB/edge/mTLS/journal/recovery không áp dụng cho output này và chưa có bằng chứng PASS từ R01c; các gate đó vẫn bắt buộc ở task tương ứng.

Nguồn kỹ thuật đọc 01/10: [Spring Boot real-server/main-method testing](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) xác nhận RANDOM_PORT tạo server thật và ALWAYS gọi main method. Loopback-only/explicit profile/no business readiness là quyết định bootstrap của app, giữ conventions R01b. Advisory manual R01a không được ghi thành review mới/full scan; dependency snapshot phải cập nhật khi thêm business libraries.

## Bước tiếp theo

BL-R01d: Learning bootstrap với child POM/entry/config/tests/artifact riêng, selected verify và actual dependency snapshot. R01 parent còn chờ child này; không tự DONE toàn build ba app hoặc R1. Sau đó R02a DB isolation, R02b trusted listeners/wire, R05 schema catalog, R06 publication theo dependency; bootstrap Content không mở sẵn những API đó.
