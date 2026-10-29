# R01b — Bằng chứng bootstrap Identity

Ngày01/10/2026, scope R01b/MD10, REQ-13/14 technical foundation. [Backlog](../../delivery-backlog.md) giữ brief/status/checkpoint; [service README](../../../backend/services/identity-service/README.md) giữ commands. Không nghiệm thu tài khoản/login/logout/DB/mTLS hoặc ba service chỉ từ bootstrap.

> Phạm vi số liệu bên dưới là checkpoint R01b. R01c sau đó thêm Content module và [evidence riêng](content-verification.md); không ghi đè kết quả/hashes lịch sử của Identity.

> R02a thêm migration/test dependencies và cập nhật current snapshots/JAR hashes trong [DB evidence](../database/verification.md). Số liệu bootstrap bên dưới giữ checkpoint lịch sử; current manifest không là bản artifact cũ.

## Đầu ra

[Identity POM](../../../backend/services/identity-service/pom.xml), entrypoint `lab.backend.identity.IdentityApplication`, initializer guard, bootstrap status controller, application/profile properties và hai Java test suites. Parent [backend/pom.xml](../../../backend/pom.xml) có duy nhất module thật `services/identity-service`; không shared-domain/Content/Learning imports. Dependencies trực tiếp chỉ starter-webmvc và starter-test(test scope), Boot4.1.1 đúng baseline; Tomcat11.0.26 inherited override. Chưa Security/JDBC/Session/Flyway/PostgreSQL/Actuator runtime, không tạo các package/migrations rỗng.

Artifact `backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar` là executable Boot JAR (19,936,084bytes tại lần build này), Start-Class `lab.backend.identity.IdentityApplication`,34packaged runtime libraries; không test/Mockito/JUnit hoặc runtime của service khác. [Manifest](../../../ops/release/dependencies.json) ghi artifact/snapshot hashes; [Identity dependency snapshot](../../../ops/release/identity-dependencies.json) ghi actual Maven tree +68application/test artifacts và computed SHA256.39compile/29test theo Maven tree, không phải toàn build-plugin graph, signed SBOM, immutable lockfile hoặc full SCA. Metadata-only R01a không bị gọi thành tree ba app.

## Acceptance và kết quả thực

Windows11 amd64, Microsoft JDK21.0.12.1+1-LTS, Maven3.9.16, PowerShell7.6.5, Python hiện có. Generated logs/reports trong ignored `artifacts/quality/R01b/`, source/tests trong repo. Không commit artifact/report, log không dùng password/token/learner data.

| AC | Kiểm tra / expected | Kết quả |
|---|---|---|
| R01b-AC01 | Selected reactor parent+Identity, artifact executable/start class riêng; no Content/Learning Java/runtime và FE unchanged | JAR/POM/tree/scope inspection đạt; chỉ một child module; FE package/lock hash khớp R00 |
| R01b-AC02 | `pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service identity` với process socket-temp override như README; CMD-02 report required không skip | exit0, BUILD SUCCESS,26tests/0failure/0error/0skip; helper đọc26tests thật |
| R01b-AC02 HTTP | SpringBootTest RANDOM_PORT/useMainMethod ALWAYS chạy server+HTTP client thật | 8case đạt: status service/phase/businessReady=false và không cookie;5route/method API/private/management chưa có404;POST bootstrap405; actual Tomcat11.0.26 |
| R01b-AC03 config | Real SpringApplication trước refresh/listener, profile/bind/port/extra-listener/headless sai bị từ chối | 18case đạt: explicit profile;5unknown/mixed/unimplemented;4bind;5reserved/privileged/out-of-range port;3TLS/management/headless |
| R01b-AC03 artifact | `python scripts/tests/identity_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets`, JAR từ independent working directory | exit0,7checks:5bad startup configs nonzero/trướcTomcat initialized; bootstrap HTTP thật; process và listener stopped |
| R01b-AC04 dependencies | Maven dependency-plugin3.10.0 do Boot BOM pin, `-f services/identity-service/pom.xml ...:tree -DoutputType=json` | exit0;68artifacts, snapshot/hash; packaged Tomcat11.0.26, actual runtime assertion đạt |

Bootstrap chỉ loopback127.0.0.1/cổng18081 hoặc random0, explicit profilebootstrap, không public8080/private8443 hay pilot. Private/lab profiles chưa được triển khai và bị startup deny. Endpoint bootstrap báo false business readiness, không phỏng đoán DB/auth readiness. Common wire/security/error contracts ở R02b/R04 không bị thay bởi bootstrap endpoint.

## Lỗi môi trường và cách xác minh

Build đầu compile/config tests đạt nhưng8HTTP tests lỗi `Unable to establish loopback connection` → `UnixDomainSockets.connect0 Invalid argument`; lần thử provider WindowsSelector vẫn lỗi và **không PASS**. Log hai lần thử giữ riêng. Mã JDK đã pin ở `lib/src.zip` cho thấy cả WEPoll/WindowsSelector dùng pipe AF_UNIX; `UnixDomainSocketsUtil` đọc `jdk.net.unixdomain.tmpdir` trước TEMP/java.io.tmpdir. Đặt property tới `.tools/jvm-ipc-sockets` làm NIO preflight mở selector thành công với default WEPoll, sau đó CMD-02 và packaged JAR HTTP đạt. Không claim chắc chắn lỗi sản phẩm hay nguyên nhân AppContainer/path limit chưa chứng minh; không giữ provider override thử sai trong source.

Qualification tại phiên này có **process-local socket temp override**, không tự nói default environment đã pass. Java/main/HTTP tests không mock, không skip, không hạ listener guard hoặc TLS validation. Download/build/smoke qua network/process escalation đã được tool duyệt; không auto-review rejection. Lệnh/socket-temp prerequisites ở README, không sửa JAVA_HOME/PATH/JDK_JAVA_OPTIONS persistent của máy. JVM warnings test-only Mockito dynamic agent được log; không có Mockito trong runtime JAR.

Review trước đóng task:63Markdown/642local links(root/docs/backendREADMEs),73task IDs/169dependency edges/DAG và trạng thái chỉ R01b đang thực thi trước đóng;5Java source files thuộcIdentity,26JUnit/7smoke/68dependencyartifacts vàmanifest/JAR hashes khớp;FEpackage/lockhashkhớpR00. Python AST/helper missingContent nonzero/ignore target-cache-report/no defaultport listener đạt. Checker ban đầu thiếu explicitUTF8 trênWindows đã sửa/rerunexit0, không ghi đó là lỗi product. Không có P0/P1 còn mở trong scopebootstrap; release gates sau vẫn chưa được miễn.

Nguồn đọc01/10: [Boot MVC](https://docs.spring.io/spring-boot/reference/web/servlet.html), [Boot real-server/main-method testing](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html), [external config](https://docs.spring.io/spring-boot/reference/features/external-config.html) và local source của JDK vendor đã verify ở R01a. Profile guard/status/không cổng8080/8443 là quyết định scope app, không tự suy từ Spring default. Advisory/manual review R01a vẫn có giới hạn; runtime patch assertion không full vulnerability scan.

## Handoff

Chỉ R01b bootstrap được nghiệm thu; R01 parent chờ R01c/d. Bước kế tiếp R01c Content bootstrap độc lập, không đưa Identity Java source vào Content hoặc sinh nghiệp vụ catalog/publication ở bootstrap. R02a thêm DB/migration isolation; R02b trusted listeners/validated service trust/wire; R03/R04 credential/session; R31c current advisory/full scans/SBOM/Linux/container parity. Khi thêm dependencies/behavior phải cập nhật snapshot/manifest/tests, không coi snapshot R01b là release final.
