# Identity service — bootstrap, transport, credentials và session core

Artifact riêng `lab.backend:identity-service:0.1.0-SNAPSHOT`, Java21/Boot4.1.1. [Backlog](../../../docs/delivery-backlog.md) giữ trạng thái và scope; [bootstrap evidence](../../../docs/platform/bootstrap/identity-verification.md) giữ checkpoint foundation. R02b có trusted transport; R03 thêm credential core/maintenance. R04a DONE cho [canonical session và opt-in lab auth TLS/browser](../../../docs/features/f10-identity-access/sessions/verification.md), chưa production edge/full reauth/receiver hoặc distributed ACK.

## Session core và HTTP adapter R04a — explicit lab profile

`session/SessionAuthority` và production V3 giữ canonical guard, opaque handle256bits/digest-only, preauth/context/CSRF, login qua R03 locked callback + Spring Session JDBC4.1.1 backing store trong cùng transaction. Idle30m/absolute12h theo own DB clock; bootstrap/status không touch idle. Logout/expiry chỉ canonical deny + durable PENDING_FENCES và hai intents Content/Learning; không có writer COMPLETED hoặc delivery/network trong SQL lock.

`CredentialWorkflow.ConsumedAction`/singleton `CredentialService` join credential+preauth transaction. Activation không auto-login; reset chia sẻ operation ID với credential transition, account pending/epoch mới + canonical deny mọi phiên + xóa backing rows + hai static intents + initiating status binding cùng commit. Callback failure rollback toàn bộ; CLI R03 overload cũ giữ nguyên, không được giả browser-bound operation.

`session/SessionHttpHandler` có [contract](../../../contracts/http/identity/README.md), strict JSON/Origin/context/body/header và cookie emission decisions; servlet unit doubles không phải actual HTTP/TLS/browser proof. Chỉ POST `/api/v1/session` explicit credential bootstrap và login được phát cookie, deadline từ DB; GET bootstrap/logout/status/activate/reset không Set-Cookie. I04/I07 fail-closed khi chưa reauth R04d, không password-only bypass.

Không đăng ký `SessionRepositoryFilter`/`@EnableJdbcHttpSession`, không container `HttpSession`/serialized SecurityContext hoặc auto Set-Cookie. V3 framework rows dùng digest làm session ID, không raw cookie; canonical terminal guard vẫn deny orphan/late saves. Profiles `bootstrap`/`transport` giữ allowlist cũ; chỉ profile `session` đăng ký API-I01–03/05/06/09 sau config/schema/role/clock/realm checks và public8080 mTLS edge/source-IP binding. I04/I07 không có reauth capability. Migrator/operator provision realm trước use; runtime không tự tạo/migrate/reset. Sweeper bounded và từng access đều kiểm expiry; actual qualification/limits ở evidence, không full SEC hoặc R1 PASS từ core/servlet tests.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service identity -EvidenceTask R04a -Offline
```

## Credential maintenance R03

`lab.backend.identity.credential.IdentityMaintenanceCli` là entry point ngoài HTTP cho operator, hỗ trợ `invite`, `issue-reset`, `reissue-activation`, `disable`, `enable`, `require-recovery`. [Runbook](../../../ops/identity/README.md) ghi cấu hình/lệnh, [review/evidence](../../../docs/identity-credential-verification.md) giữ qualification hiện hành. Invite/reset token thô chỉ in một lần ra stdout để chuyển qua kênh operator đã xác minh; không đưa stdout đó vào CI/application log. DB chỉ lưu SHA-256 digest.

Mật khẩu giữ nguyên 15–128 Unicode code point, không trim/normalize/truncate. Argon2id cố định memory19,456KiB/iterations2/parallelism1/salt16byte/output32byte. Corpus trong `ops/identity/` chỉ là fixture lab xác định; production phải chọn corpus offline phù hợp và xác minh hash manifest trước khi nhận credential. R03 không tạo cookie/session, endpoint browser, email hoặc distributed revoke completion.

Từ root repo, dùng toolchain R01a:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service identity
```

Helper chạy selected `clean verify`, kiểm JUnit reports đủ/không zero hoặc required skip. JAR ở `backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar`, Maven parent chỉ build conventions; không cần Content/Learning artifact hoặc shared-domain JAR.

## Chạy local bootstrap

```powershell
& './.tools/jdk/jdk-21.0.12.1+1/bin/java.exe' -jar './backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar' --spring.profiles.active=bootstrap
```

HTTP chỉ `127.0.0.1:18081`; có thể `--server.port=0` để chọn random port. Dừng bằng Ctrl+C. GET `/bootstrap/status` trả:

```json
{"service":"identity-service","phase":"bootstrap","businessReady":false}
```

Đây là trạng thái bootstrap của process, không readiness cho login/logout, DB, service trust hoặc traffic người học. POST cùng endpoint405; API-I01–09, internal/maintenance/Actuator chưa triển khai. Không cấp cookie/login thành công giả.

Ở scope bootstrap R01, startup yêu cầu **chỉ profile explicit `bootstrap`**; không profile/default-only/unknown/mixed/lab/private/pilot bị từ chối trước listener. Chỉ literal bind127.0.0.1, port0 hoặc1024–65535 trừ8080/8443; không TLS hoặc separate management listener ở bootstrap. Public8080 HTTPS/mTLS edge-only và private8443 thuộc R02b, không được chạy bằng bootstrap HTTP. Guard tạm này phải được thay/extend có tests khi thêm trusted profiles, không dùng bootstrap để bypass protected routes.

## Điều kiện môi trường Windows đã gặp khi kiểm

Trên runner của phiên01/10/2026, default JDK NIO pipe không connect được AF_UNIX ở temp mặc định; cả default và thử provider WindowsSelector đều lỗi trước HTTP test. Source JDK21.0.12.1 đã đọc cho thấy system property `jdk.net.unixdomain.tmpdir`. Selector preflight và actual tests/JAR chạy được khi đặt socket temp riêng trong repo, giữ default WEPoll provider. Chưa kết luận nguyên nhân Windows/runner từ stack trace.

Nếu gặp đúng lỗi này, dùng override process có kiểm chứng (không đổi registry/JDK hay tắt TLS):

```powershell
$ipcDirectory = Join-Path (Get-Location).Path '.tools/jvm-ipc-sockets'
New-Item -ItemType Directory -Path $ipcDirectory -Force | Out-Null
$savedJvmOptions = $env:JDK_JAVA_OPTIONS
try {
  $env:JDK_JAVA_OPTIONS = '"' + "-Djdk.net.unixdomain.tmpdir=$ipcDirectory" + '"'
  pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service identity
} finally {
  $env:JDK_JAVA_OPTIONS = $savedJvmOptions
}
```

Flag nằm trong child JVM khi verify, không skip hoặc mock HTTP. Chạy JAR trong runner này thêm một argument trước `-jar`: `"-Djdk.net.unixdomain.tmpdir=$ipcDirectory"`. Shell khác/máy khác kiểm lại runtime/temp trước áp dụng; property không là config production service.

Packaged artifact smoke (7 kiểm tra; tất cả Java process thử được dừng):

```powershell
python scripts/tests/identity_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets
```

Smoke chạy từ thư mục độc lập trong ignored `.tools/`, kiểm default/pilot/bind-all/private-port/management rejected, HTTP đúng nhãn và listener/process dừng. Không sửa source/DB hoặc để server chạy nền. Linux/container/mTLS/recovery/full scans vẫn chưa qualified.

## DB migration harness R02a

[Lab/evidence](../../../docs/database-lab-verification.md) kiểm PostgreSQL thật, roles/database identity riêng và own V1 migration. R03 thêm production V2 credential schema và CLI dùng JDBC own app role; R04a thêm production V3 session core. Flyway vẫn test-scoped, HTTP bootstrap/transport không auto-migrate. Identity transaction/failure/recovery fixtures giữ V20/V30, không đóng gói; Content/Learning giữ test V2/V3.

```powershell
pwsh -NoProfile -File scripts/setup-postgresql.ps1
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service identity -Offline
python -X utf8 scripts/tests/identity_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets --evidence-dir artifacts/quality/R02a/artifact-identity
```

Bỏ -Offline nếu Maven cache chưa đủ. Mỗi CMD-03 tạo/stop cluster tạm; không DSN tùy ý, no missing/zero/skipped suite PASS. [Ops README](../../../ops/db-lab/README.md) ghi môi trường/permissions/credentials/cleanup; checkpoint R01b phía trên là lịch sử bootstrap, evidence R02a không ghi đè report đó.

## Transport foundation R02b

Profile explicit `transport` thêm HTTPS8080 edge-only và mTLS8443 service/operator/action trong owned lab. [Trust design/evidence](../../../docs/service-trust-verification.md) giữ config/cert/clock/wire/limits và kết quả. Profile này luôn `businessReady=false`, không admission/session/owner/API học. Bootstrap vẫn giữ guard/HTTP cũ; Compose lab/pilot chưa có.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service identity -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service identity -Case SEC-15 -Offline
```

Managed helper tạo DB/PKI riêng, build own JAR rồi kiểm TLS thật từ working directory độc lập, đóng owned listeners/process/cluster trong finally. Chỉ receiver/common-wire layer; thiếu config/suite/zero/skip không PASS. Các số/hash R01/R02a phía trên giữ lịch sử, runtime JDBC/snapshot hiện hành ở release manifest.

## Identity session runtime R04a — lab opt-in

Trước CMD-04 trên checkout mới, đọc [prerequisite browser](../../../docs/platform/environment/identity-browser-qualification.md): Node phải có đúng đường dẫn launcher, modules lấy từ FE lockfile và Microsoft Edge đã cài. Build/DB service không tự chứng minh các đầu vào browser này đã sẵn sàng.

Profile `session` chỉ được mở với owned `backendlab.trust.config` và `backendlab.session.config`. Initializer kiểm own app role/schema/terminal triggers, provisioned realm, DB clock/run binding, external pinned corpus, nonzero32-byte HMAC và exact HTTPS origin trước listeners. Không migration/realm provision tự động hoặc SessionRepositoryFilter/automatic cookie renewal. Bootstrap và transport profile vẫn giữ giới hạn cũ.

Auth public8080 phải qua mTLS edge certificate và dedicated trusted client-IP binding. Private8443 giữ service-only deny; không INT admission/receiver/coordinator/reauth proof. Sweeper10s/batch≤100 chỉ ghi terminal deny + durable two static intents; access vẫn kiểm expiry nếu scheduler trễ. Tombstones/operations không purge hoặc COMPLETED.

CMD04 Identity còn kiểm JAR/auth HTTP/TLS + isolated Edge/Chromium cookie/context fixture; không từ đó suy production FE/edge/privacy hoặc full SEC/release PASS. [Goal/DoD/handoff R04a DONE](../../../docs/features/f10-identity-access/sessions/tasks/BL-R04a.md), [public contract](../../../contracts/http/identity/README.md), [operator config/provision](../../../ops/identity/README.md). Qualification theo exact current inputs/reports/artifact; failures phải lưu trước khi chạy lại, không checkpoint tự thành DONE.
