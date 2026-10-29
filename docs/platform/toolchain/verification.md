# R01a — Bằng chứng nền build backend

Ngày kiểm 01/10/2026. Trạng thái/brief/checkpoint duy nhất ở [backlog](../../delivery-backlog.md); R01a-AC01–04 theo [task brief](../../delivery/task-brief-template.md). Đây là nền build, chưa là runtime ba service hoặc nghiệm thu R1.

Mốc này giữ evidence tại R01a. Sau đó [R01b](../bootstrap/identity-verification.md) thêm Identity child/app/tests thật; các câu “chưa module/app” bên dưới mô tả checkpoint R01a, không thay trạng thái hiện hành trong backlog.

## Đầu ra và quyết định

- [Parent POM](../../../backend/pom.xml): `lab.backend:backend-lab-parent:0.1.0-SNAPSHOT`, packaging `pom`, không Java/dependency nghiệp vụ/module chưa có. Boot parent/BOM4.1.1; compiler release21, Enforcer3.6.3 yêu cầu JDK21/Maven3.9.16; Surefire fail khi không có tests, không bỏ qua lỗi.
- [Wrapper properties](../../../backend/.mvn/wrapper/maven-wrapper.properties): Apache Wrapper3.3.4 `only-script`; Maven3.9.16 distribution có SHA256. `mvnw`/`mvnw.cmd` giữ source Apache và LICENSE/NOTICE, không wrapper JAR/downloader Java. `.gitattributes` cố định LF/CRLF tương ứng.
- [Helper](../../../scripts/services.ps1): `toolchain`, `validate` và selected `verify`; thiếu service/module/report, zero/skipped/failed tests và task chưa triển khai trả nonzero. Chưa có CMD-03–11. Khôi phục cwd/process env, cache ở repo; không arbitrary goals/skipTests/all-service PASS.
- [Setup portable](../../../scripts/setup-backend.ps1): Microsoft Build of OpenJDK21.0.12.1+1-LTS x64 Windows, archive kiểm SHA256 trước extraction, không registry/PATH hệ thống. Chọn Microsoft sau khi Temurin download lỗi chain chứng chỉ cả PowerShell/curl; không tắt kiểm TLS. Corretto metadata đã khảo sát nhưng không cài. Runtime gốc Oracle17.0.4.1 vẫn còn.
- [Manifest](../../../ops/release/dependencies.json): versions/download URLs/checksums/runtime/advisory checkedAt và pending evidence. JDK/Maven archive SHA256/SHA512 vendor đã đối chiếu; wrapper scripts đối chiếu source release có SHA512 Apache (chỉ substitute token version và normalize newline). POM/BOM đối chiếu publisher SHA1 trên HTTPS và ghi SHA256 tính từ artifact, không gọi đó là publisher SHA256 hay signed verification. POM trong repository Maven thực sau validate có SHA256 khớp manifest.

Boot4.1.1 BOM quản lý Tomcat11.0.24; parent override11.0.26 sau review [Tomcat advisory](https://tomcat.apache.org/security-11.html), gồm request header mix-up và client certificate checks. Đây là patch pin cho service sắp tạo; chưa chứng minh runtime compatibility/mTLS. BOM Security7.1.1 nằm ở bản fix của [DPoP advisory](https://spring.io/security/cve-2026-41707/) và [WebAuthn advisory](https://spring.io/security/cve-2026-47841/). Review thủ công theo [Maven security](https://maven.apache.org/security.html)/[Microsoft release notes](https://learn.microsoft.com/en-us/java/openjdk/release-notes) và nguồn trong manifest; chưa full transitive/image scan, chữ ký vendor hoặc SBOM release.

## Kiểm tra đã chạy

Windows11 amd64; Microsoft JDK21.0.12.1, javac21.0.12.1, Maven3.9.16, PowerShell7.x hiện có. Lệnh từ root trừ khi ghi cwd khác; raw logs/generated reports ở ignored `artifacts/quality/R01a/`. Các kết quả dưới chỉ đại diện scope ghi trên dòng.

| AC / kiểm | Lệnh hoặc bằng chứng | Kết quả |
|---|---|---|
| AC01 archive/runtime | Vendor metadata/checksums + `scripts/setup-backend.ps1`; JDK `-version`, javac `-version`, manifest | SHA256 khớp; JDK portable chạy đúng21.0.12.1+1-LTS; không đổi Java hệ thống |
| AC01 wrapper provenance | Source release SHA512 + so sánh `only-mvnw` templates với scripts3.3.4 | Cả hai scripts khớp sau version-token/newline; properties pin Maven hash |
| AC02 wrapper version | `pwsh -NoProfile -File scripts/services.ps1 -Task toolchain` → wrapper `--version` | exit0; Maven3.9.16 chạy trên Microsoft JDK21.0.12.1 |
| AC02 parent validate | `pwsh -NoProfile -File scripts/services.ps1 -Task validate` → wrapper `-N validate` | exit0, BUILD SUCCESS; Java/Maven Enforcer rules passed; không chạy app tests |
| AC02 cached validate | Cùng lệnh thêm `-Offline` sau khi cache đầy đủ | exit0, BUILD SUCCESS từ cache dưới quyền thường |
| AC03 parent rejects wrong Java | Cwd backend, JAVA_HOME=Oracle17.0.4.1, wrapper `--offline -N validate`, repo/cache như helper | exit1 đúng expected; RequireJavaVersion failed, không đổi pin xuống17 |
| AC03 helper/setup behavior | `pwsh -NoProfile -File scripts/tests/backend-build.Tests.ps1 -UnsupportedJavaHome 'C:/Program Files/Java/jdk-17.0.4.1'` | 15 case PASS, exit0 sau sửa assertion native exit7 được giữ chính xác. Isolated command/report doubles kiểm gate helper, không Java app tests |
| AC04 FE/scope | SHA256 package/lock + inventory/hash review | Khớp baseline R00; không sửa frontend/helper FE/Java app/DB/auth/infra runtime |

15 case: service bắt buộc, module thiếu, service không hợp lệ, suite chưa có, validate không nhận service, JavaHome không có, JDK17, report thiếu, zero tests, required skip, failure dù process exit0, report sai, report đủ, native exit7 được giữ, JDK archive hỏng bị từ chối trước extraction. Test fixtures nằm riêng trong ignored cache, không phải runtime service giả. Harness có timeout30giây mỗi process; không nhận missing/skip là PASS.

Tải/build lần đầu phải được chạy ngoài network sandbox hạn chế của phiên này; public metadata/download đã qua approval tool và TLS validation. Không có auto-review rejection. Offline validate sau cache chạy trong sandbox thường; không ghi đây là thử mạng của mọi checkout. FE không có thay đổi, không chạy lại app E2E/type/build để làm bằng chứng cho backend.

## Handoff và giới hạn

Checkpoint backlog ghi R01a-AC01–04/DONE-01–05 đạt scope nền build; backlog là nguồn trạng thái. R01 parent chưa DONE, R01b/c/d chưa có app/test/artifact. Bước kế tiếp R01b bootstrap Identity: child POM/entrypoint/config/bootstrap tests thật, register module thật, own artifact, selected `clean verify` và actual tree/update manifest; không credential/session business trước R03/R04. Kiểm Tomcat override khi có MVC runtime. R01c/d tương tự service tương ứng.

CMD-02 application suite chưa chạy; CMD-03–11/DB/mTLS/E2E/benchmark/backup/restore/S3 qualification chưa có. R31c vẫn cần Linux/container parity, current advisory recheck, per-artifact SBOM/full scans; không có cloud/deploy/commit/new chat/agent trong R01a.
