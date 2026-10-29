# R01d — Bằng chứng bootstrap Learning

Ngày 01/10/2026. Scope nền LRN/MD04/11, REQ-13/16. [Backlog](../../delivery-backlog.md) giữ brief/status/checkpoint; [Learning README](../../../backend/services/learning-service/README.md) giữ lệnh. Bootstrap chưa nghiệm thu bài làm, grading, progress, DB, auth hoặc service trust.

> R02a thêm migration/test dependencies và cập nhật current snapshots/JAR hashes trong [DB evidence](../database/verification.md). Số liệu bootstrap bên dưới giữ checkpoint lịch sử; current manifest không là bản artifact cũ.

## Đầu ra

[POM Learning](../../../backend/services/learning-service/pom.xml), entry point `lab.backend.learning.LearningApplication`, initializer guard, status controller, hai properties và hai Java test suites. [Parent](../../../backend/pom.xml) tham chiếu đúng ba module thật Identity/Content/Learning. Selected Learning reactor chỉ build parent + Learning; không source import xuyên service, shared domain JAR hoặc package nghiệp vụ/migration rỗng.

Dependencies trực tiếp chỉ starter-webmvc và starter-test (test scope). Giữ Java 21 / Boot 4.1.1 / Tomcat 11.0.26. Executable JAR có Start-Class đúng, 19,936,087 bytes / 34 runtime libraries, không test/JUnit/Mockito/Identity/Content/Security/JDBC/Flyway/PostgreSQL runtime.

[Learning snapshot](../../../ops/release/learning-dependencies.json) giữ actual Maven tree + 68 application/test artifacts, 39 compile / 29 test; coordinate/scope/hash/source/schema/order thống nhất hai service trước. [Manifest](../../../ops/release/dependencies.json) ghi snapshot/JAR hashes và qualified runtime. Không immutable lockfile, signed SBOM, toàn build-plugin graph hoặc full SCA; phải cập nhật khi thêm dependencies/nghiệp vụ.

## Acceptance và kết quả

Windows x64, Microsoft JDK 21.0.12.1+1-LTS portable, Maven 3.9.16 qua wrapper, PowerShell/Python hiện có. Build và dependency tree chạy offline với cache đã đủ. Không đổi pins, tải/cài runtime mới hoặc sửa source hai service trước/FE.

| Acceptance | Lệnh / expected | Kết quả thực |
|---|---|---|
| R01d-AC01 | Own artifact/Start-Class; parent ba child thật, selected Learning không cần peer artifact | POM/source/JAR/tree inspection đạt; baseline hashes và service metadata giữ nguyên |
| R01d-AC02 | Root: `pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service learning -Offline`, socket-temp override như README | exit 0 / BUILD SUCCESS; 42 tests / 0 failures / 0 errors / 0 skips; helper đọc reports thật |
| R01d-AC02 HTTP | RANDOM_PORT/main method + HTTP client/server thật | 24 case đạt: status JSON/no cookie; 21 route/method pairs trả 404 (16 API-L, session/login/catalog, internal publication, Actuator); POST bootstrap 405; actual Tomcat 11.0.26 |
| R01d-AC03 config | Real SpringApplication; config sai bị từ chối trước listener | 18 case đạt: explicit profile, 5 profile sai/mixed, 4 bind ngoài loopback, 5 port sai/reserved/privileged, 3 TLS/management/headless |
| R01d-AC03 JAR | Root: `python -X utf8 scripts/tests/learning_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets` | exit 0 / 7 checks: 5 negative startups nonzero trước Tomcat initialized, HTTP từ independent working directory, process/listener stopped |
| R01d-AC04 | Maven dependency-plugin 3.10.0 tree offline + source/manifest/hash review | exit 0; actual 68 artifacts, 34 packaged libraries, snapshot/JAR/smoke hashes khớp; toolchain/pins/peer snapshots giữ nguyên |

Bootstrap chỉ explicit profile `bootstrap`, HTTP `127.0.0.1:18083` hoặc random0 và `businessReady=false`; không public8080/private8443/pilot. Cấu hình sai bị từ chối trước listener. Những HTTP 404 này chứng minh route chưa có trong bootstrap, không thay test quyền owner, logout, idempotency hoặc publication fence của task sau.

## Giới hạn và review

R01d tiếp tục dùng process-local `jdk.net.unixdomain.tmpdir` tới `.tools/jvm-ipc-sockets` theo [xác minh R01b](identity-verification.md). Giữ default selector provider; không mock/skip HTTP, sửa environment persistent hoặc tắt TLS validation. Không gọi default-temp đã PASS hoặc suy nguyên nhân Windows mới. Mockito dynamic-agent warning chỉ trong test log; không có Mockito trong runtime JAR.

Evidence ignored `artifacts/quality/R01d/`: `learning-verify.log`, `learning-dependency-tree.json`, `dependency-tree.log`, `artifact-*.log`, `artifact-smoke.json`, baseline manifests và static-validation report. Smoke dừng tất cả Java process được tạo; listener không còn reachable sau dừng. Không commit artifact/log/report hoặc bàn giao background deployment.

Review DONE-01–05: scope/config/listener/route boundaries, own artifact/runtime libs, actual reports/snapshot hashes, links/ID/DAG và baseline. Không P0/P1 còn mở trong bootstrap. FE/UI/learner/DB/mTLS/journal/recovery không bị thay đổi, chưa có PASS từ R01d; các gate đó vẫn bắt buộc ở owner task sau.

Conventions real server/main method theo [Spring Boot testing](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html), đã đối chiếu trong lượt R01c. Loopback guard và nhãn business readiness là quyết định bootstrap của app. Advisory manual R01a giữ giới hạn; runtime patch assertion không thành full vulnerability scan mới.

## Rollup R01 và bàn giao

R01 là parent chỉ rollup evidence các children, không có executable/shared domain riêng. Khi R01d được nghiệm thu, các điều kiện của R01 được bao phủ như sau; trạng thái cuối nằm ở backlog.

| Child | Bằng chứng riêng | Phạm vi đã kiểm |
|---|---|---|
| R01a | [Build foundation](../toolchain/verification.md) | Toolchain/wrapper/hash/parent/helper; 15 helper cases là fixture, không app business proof |
| R01b | [Identity bootstrap](identity-verification.md) | Own executable artifact, 26 Java tests, 7 packaged JAR checks, actual snapshot |
| R01c | [Content bootstrap](content-verification.md) | Own executable artifact, 28 Java tests, 7 packaged JAR checks, actual snapshot |
| R01d | Tài liệu này | Own executable artifact, 42 Java tests, 7 packaged JAR checks, actual snapshot |

Rollup không phải một lần full-suite/cross-service integration mới. R01d giữ nguyên source/helpers/FE và metadata hai peers; không cần chạy lại các suites đã đạt khi chỉ thêm child độc lập. Chưa nghiệm thu R1/business/trust/deployment.

Bước kế tiếp **BL-R02a — three-DB migration harness**: môi trường DB lab, `identity_db`/`content_db`/`learning_db`, app/migration roles riêng, migration lifecycle/failed-rerun/two-connection tests và CMD-03. Chưa triển khai R02a trong lượt R01d; PostgreSQL tool/image pin và môi trường phải được xác minh ở task đó. R02b mở trust/wire; auth/work/grader/progress theo dependencies, không nhảy cả module.
