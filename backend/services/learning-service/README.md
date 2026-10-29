# Learning service — bootstrap, DB và transport foundation

Executable artifact riêng `lab.backend:learning-service:0.1.0-SNAPSHOT`, Java 21 / Boot 4.1.1. [Backlog](../../../docs/delivery-backlog.md) giữ trạng thái/phạm vi; [bằng chứng R01d](../../../docs/learning-bootstrap-verification.md) ghi kết quả thực. Work, grading, worker/job, progress, app persistence API thuộc các task sau; service trust foundation đã có ở R02b.

Từ root repo:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service learning
```

Helper chạy selected `clean verify`, reactor chỉ parent + Learning. Reports phải có test thật và không failure/error/required skip. Thêm `-Offline` khi cache đã đủ; R01d đã chạy offline với cache R01a/b/c. Không cần Identity/Content artifact, source import xuyên service hoặc shared-domain JAR.

JAR: `backend/services/learning-service/target/learning-service-0.1.0-SNAPSHOT.jar`. Entry point: `lab.backend.learning.LearningApplication`.

## Chạy bootstrap

```powershell
& './.tools/jdk/jdk-21.0.12.1+1/bin/java.exe' -jar './backend/services/learning-service/target/learning-service-0.1.0-SNAPSHOT.jar' --spring.profiles.active=bootstrap
```

Listener mặc định chỉ `127.0.0.1:18083`; `--server.port=0` chọn cổng ngẫu nhiên. Dừng bằng Ctrl+C. GET `/bootstrap/status` trả:

```json
{"service":"learning-service","phase":"bootstrap","businessReady":false}
```

POST endpoint này trả 405. Các route API-L01–16 và route Identity/Content/internal/Actuator được kiểm trả 404, không phát cookie. Đây là bootstrap process, chưa readiness cho bài làm/chấm/tiến độ hoặc traffic người học. HTTP 404 ở bootstrap chưa chứng minh quyền owner/logout/publication đã được triển khai.

Chỉ active profile `bootstrap` được phép. Không profile, lab/private/pilot/unknown/mixed bị từ chối trước listener. Chỉ literal bind `127.0.0.1`; port 0 hoặc 1024–65535, trừ 8080/8443. TLS, management listener riêng và headless mode bị từ chối. Public HTTPS/mTLS 8080 và private 8443 ở R02b; phải thay/extend guard kèm tests khi mở trusted profiles, không dùng bootstrap để bypass business trust.

## Điều kiện của runner Windows

R01d dùng process-local socket-temp override đã qualified ở R01b/c. Giữ default selector provider, không sửa environment persistent. Chưa qualify default-temp trên runner này hoặc Linux/container.

Lệnh thực đã đạt, từ root:

```powershell
$ipcDirectory = Join-Path (Get-Location).Path '.tools/jvm-ipc-sockets'
New-Item -ItemType Directory -Path $ipcDirectory -Force | Out-Null
$savedJvmOptions = $env:JDK_JAVA_OPTIONS
try {
  $env:JDK_JAVA_OPTIONS = '"' + "-Djdk.net.unixdomain.tmpdir=$ipcDirectory" + '"'
  pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service learning -Offline
} finally {
  $env:JDK_JAVA_OPTIONS = $savedJvmOptions
}
```

Chạy JAR trên runner này thêm `"-Djdk.net.unixdomain.tmpdir=$ipcDirectory"` trước `-jar`. Máy khác kiểm runtime/temp trước áp dụng; đây không phải config production. [R01b](../../../docs/identity-bootstrap-verification.md) giữ lỗi và cách xác minh, không suy root cause mới từ R01d.

Packaged JAR smoke:

```powershell
python -X utf8 scripts/tests/learning_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets
```

Bảy kiểm tra đã đạt: năm startup negative, HTTP từ JAR trong working directory độc lập, process/listener dừng. Logs/report trong ignored `artifacts/quality/R01d/`; smoke không để service chạy nền. Dependency snapshot không là SBOM/full scan. DB, auth, grader, journal, recovery và deployment được kiểm ở các task tương ứng sau bootstrap.

## DB migration harness R02a

[Lab/evidence](../../../docs/database-lab-verification.md) kiểm PostgreSQL thật, roles/database learning riêng và own V1 migration. JDBC runtime cho transport clock R02b; Flyway test-scoped; bootstrap runtime chưa connect/migrate DB hoặc cung cấp persistence API. Transaction fixtures ở V100/V101 chỉ test resources; production V2 auth receiver thuộc R04c.

```powershell
pwsh -NoProfile -File scripts/setup-postgresql.ps1
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service learning -Offline
python -X utf8 scripts/tests/learning_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets --evidence-dir artifacts/quality/R02a/artifact-learning
```

Bỏ -Offline nếu Maven cache chưa đủ. Mỗi CMD-03 tạo/stop cluster tạm; không DSN tùy ý, no missing/zero/skipped suite PASS. [Ops README](../../../ops/db-lab/README.md) ghi môi trường/permissions/credentials/cleanup; checkpoint R01d phía trên là lịch sử bootstrap, evidence R02a không ghi đè report đó.

## Transport foundation R02b

Profile explicit `transport` thêm HTTPS8080 edge-only và mTLS8443 service/operator/action trong owned lab. [Trust design/evidence](../../../docs/service-trust-verification.md) giữ config/cert/clock/wire/limits và kết quả. Profile này luôn `businessReady=false`, không admission/session/owner/API học. Bootstrap vẫn giữ guard/HTTP cũ; Compose lab/pilot chưa có.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service learning -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service learning -Case SEC-15 -Offline
```

Managed helper tạo DB/PKI riêng, build own JAR rồi kiểm TLS thật từ working directory độc lập, đóng owned listeners/process/cluster trong finally. Chỉ receiver/common-wire layer; thiếu config/suite/zero/skip không PASS. Các số/hash R01/R02a phía trên giữ lịch sử, runtime JDBC/snapshot hiện hành ở release manifest.

## Learning admission/fence — R04c đang kiểm chứng

[Task/DoD/checkpoint](../../../docs/features/f10-identity-access/sessions/tasks/BL-R04c.md), [wire contract](../../../contracts/http/admission/README.md), [owned-lab provisioning](../../../ops/learning/README.md). Profile explicit `admission` giữ public8080 foundation và private8443 INT02 Identity-only; không đăng ký API-L03 hay triển khai draft/T1/T2/job/progress. Bootstrap và transport vẫn không business-ready.

`LearningAccess.mutate` gọi direct mTLS Identity admission mới cho mỗi mutation, bind `LEARNING_DRAFT_SAVE`/PUT/draft UUID/body/context/Origin/CSRF. `csrfVerified=true` bắt buộc. Callback phải ghi SQL bằng Connection được cấp; account gate, admission-use và effect giữ trong cùng transaction tới commit. Không dùng callback cho external effect hoặc tự commit/đóng connection. Owner từ admission, resource owner phải kiểm bằng `AdmissionGate.requireOwner` trong transaction.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service learning -EvidenceTask R04c -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service learning -EvidenceTask R04c -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service learning -Case SEC-03 -EvidenceTask R04c -Offline
```

SEC01/02/03/04/05/09/15 có strict required suites/cases. Các lệnh này cần actual owned lab; kết quả hiện hành chỉ lấy từ checkpoint/evidence, không suy PASS từ danh sách lệnh. Controlled INT01 provider fixture không thay canonical Identity R04d; local mutation hook không là full draft/revoke business qualification.

## Learning publication participant — R06b candidate REVIEW

[Task/DoD/checkpoint](../../../docs/features/f08-content/tasks/BL-R06b.md), [INT-04 contract](../../../contracts/http/learning/internal/publication/README.md), [owned-lab provisioning](../../../ops/learning/README.md). Profile explicit `publication` giữ packaged Content tại `8080/8443` và Learning tại `18083/18443`; topology cố định `r06b-content-learning-loopback-v1`, không nhận peer port tùy ý.

Learning kéo exact private bundle + manifest qua INT-03 trước khi mở publication transaction, rehash/validate rồi mới lưu immutable replica trong V4. INT-04 chỉ ở private listener và chỉ certificate `content` được gọi. STAGE/FENCE/RELEASE/ABORT có durable receipt và cursor `content-global` atomic với effect; exact retry trả lại cùng bytes, còn gap, stale, same-sequence changed command hoặc stale phase bị conflict. Local content-sensitive consumer tương lai phải đi qua `PublicationRuntime.contentSensitive`: callback giữ shared gate đến khi xong và trả `publication_pending` khi durable fence đang mở, kể cả sau restart. Leaf này chưa đăng ký learner T1/T2/progress callback và không sở hữu Content canonical commit/coordinator.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service learning -EvidenceTask R06b -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service learning -EvidenceTask R06b -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service learning -EvidenceTask R06b -Case DATA-01 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service learning -EvidenceTask R06b -Case DATA-02 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service learning -EvidenceTask R06b -Case SEC-16 -Offline
```

Các lệnh tạo PostgreSQL/PKI/process riêng, giữ report + packaged artifacts và đóng tài nguyên do run sở hữu trong `finally`. Chỉ checkpoint R06b xác định run hiện hành; command liệt kê ở đây không tự là PASS. Candidate hiện ở `REVIEW` sau khi consumer fault matrix, packaged ABORT/callback barrier và strict helper negatives đạt DoD; chỉ Control Center được quyết định `DONE`.
