# Content service — bootstrap, DB, trust và admission receiver

Artifact riêng `lab.backend:content-service:0.1.0-SNAPSHOT`, Java 21 / Boot 4.1.1. [Backlog](../../../docs/delivery-backlog.md) giữ trạng thái và phạm vi; [bằng chứng R01c](../../../docs/content-bootstrap-verification.md) ghi kết quả bootstrap. BL-R06a sở hữu immutable candidate/private INT-03; BL-R06c đang triển khai durable coordinator và chưa tự đồng nghĩa production release.

## BL-R06a publication candidate

V4 lưu exact bundle bytes/digest và candidate atomically; runtime role không được update/delete immutable rows hoặc sửa canonical head. `PublicationLabCli validate|prepare <candidate> <bundle>` chỉ validate/prepare và trả output bounded. INT-03 contract/fixture nằm ở `contracts/http/content/internal/publication/` và `contracts/fixtures/publication/`. Profile `publication` dùng cùng named mTLS topology; chỉ Learning gọi private GET. Không dùng source này để claim STAGE/FENCE/COMMIT/RELEASE hoặc content semantic review.

## BL-R06c publication coordinator

V5 lưu operation, exact phase outbox/receipt, lease, canonical head và
learning-ready revision. `PublicationLabCli start|dispatch|status|abort
<operationId>` dùng topology/capability cố định trong owned lab config; không nhận
DSN hoặc peer URL. `deliverySequence` tăng global qua mọi operation, kể cả sau
ABORT; canonical P+1 và RELEASE outbox là một transaction, còn COMPLETE chỉ sau
exact RELEASED receipt. CMD-05 DATA-02–07/14 là selected coordinator evidence;
DATA-02/06/07 không claim learner T1/T2/current-progress behavior.

Từ root repo, dùng toolchain R01a:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service content
```

Helper chạy selected `clean verify` với reactor parent + Content, không cần Identity artifact. JUnit reports phải có test thật, không failure/error/required skip. Khi cache đã đủ, thêm `-Offline`; lần kiểm R01c dùng cách này.

JAR: `backend/services/content-service/target/content-service-0.1.0-SNAPSHOT.jar`. Entry point: `lab.backend.content.ContentApplication`. Service không import source Identity/Learning hoặc shared-domain JAR.

## Chạy local bootstrap

```powershell
& './.tools/jdk/jdk-21.0.12.1+1/bin/java.exe' -jar './backend/services/content-service/target/content-service-0.1.0-SNAPSHOT.jar' --spring.profiles.active=bootstrap
```

HTTP chỉ bind `127.0.0.1:18082`; dùng `--server.port=0` để chọn cổng ngẫu nhiên. Dừng bằng Ctrl+C. GET `/bootstrap/status` trả:

```json
{"service":"content-service","phase":"bootstrap","businessReady":false}
```

Đây là trạng thái process bootstrap; chưa readiness cho catalog/publish, auth, DB hoặc traffic người học. POST cùng endpoint trả 405. API-C01–03, private content bundle, API của Identity/Learning và Actuator chưa được cung cấp; các route được kiểm trả 404, không phát cookie.

Profile `bootstrap` yêu cầu chỉ active chính nó. Không profile, lab/private/pilot/unknown/mixed bị từ chối trước listener. Chỉ literal bind `127.0.0.1`; port 0 hoặc 1024–65535, trừ 8080/8443. Bootstrap không cho TLS, management listener riêng hoặc headless mode. Trusted profiles `transport` (R02b) và `admission` (R04b) được mô tả riêng bên dưới, không dùng bootstrap để mở business routes.

## Điều kiện của runner Windows

R01b đã gặp lỗi NIO pipe ở temp mặc định và xác minh socket-temp riêng trong repo. R01c tiếp tục dùng override đó, giữ default selector provider. Qualification này không khẳng định default temp trên mọi máy Windows đã đạt.

Lệnh đã chạy thành công trong runner hiện tại, từ root:

```powershell
$ipcDirectory = Join-Path (Get-Location).Path '.tools/jvm-ipc-sockets'
New-Item -ItemType Directory -Path $ipcDirectory -Force | Out-Null
$savedJvmOptions = $env:JDK_JAVA_OPTIONS
try {
  $env:JDK_JAVA_OPTIONS = '"' + "-Djdk.net.unixdomain.tmpdir=$ipcDirectory" + '"'
  pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service content -Offline
} finally {
  $env:JDK_JAVA_OPTIONS = $savedJvmOptions
}
```

Chạy JAR trên runner này thêm argument `"-Djdk.net.unixdomain.tmpdir=$ipcDirectory"` trước `-jar`. Override chỉ trong process; không đổi registry/PATH hệ thống hoặc provider. Máy khác kiểm runtime/temp trước khi áp dụng; đây không phải config production.

Packaged artifact smoke:

```powershell
python scripts/tests/content_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets
```

Smoke chạy JAR từ working directory độc lập trong ignored `.tools/`, kiểm startup sai, HTTP thật và process/listener dừng. Bảy kiểm tra đã đạt; logs/report nằm ở ignored `artifacts/quality/R01c/`. Không để service nền chạy sau smoke. Linux/container, DB/trust, SBOM/full scans và publication vẫn thuộc task sau.

## DB migration harness R02a

[Lab/evidence](../../../docs/database-lab-verification.md) kiểm PostgreSQL thật, roles/database content riêng và own V1 migration. JDBC runtime cho transport clock R02b; Flyway test-scoped; bootstrap runtime chưa connect/migrate DB hoặc cung cấp persistence API. R04b thêm production V2 admission receiver; fixture transaction/failure/recovery đổi namespace sang V100/V101, không đóng gói trong JAR.

```powershell
pwsh -NoProfile -File scripts/setup-postgresql.ps1
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -Offline
python -X utf8 scripts/tests/content_artifact_smoke.py --ipc-temp-dir .tools/jvm-ipc-sockets --evidence-dir artifacts/quality/R02a/artifact-content
```

Bỏ -Offline nếu Maven cache chưa đủ. Mỗi CMD-03 tạo/stop cluster tạm; không DSN tùy ý, no missing/zero/skipped suite PASS. [Ops README](../../../ops/db-lab/README.md) ghi môi trường/permissions/credentials/cleanup; checkpoint R01c phía trên là lịch sử bootstrap, evidence R02a không ghi đè report đó.

## Transport foundation R02b

Profile explicit `transport` thêm HTTPS8080 edge-only và mTLS8443 service/operator/action trong owned lab. [Trust design/evidence](../../../docs/service-trust-verification.md) giữ config/cert/clock/wire/limits và kết quả. Profile này luôn `businessReady=false`, không admission/session/owner/API học. Bootstrap vẫn giữ guard/HTTP cũ; Compose lab/pilot chưa có.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -Case SEC-15 -Offline
```

Managed helper tạo DB/PKI riêng, build own JAR rồi kiểm TLS thật từ working directory độc lập, đóng owned listeners/process/cluster trong finally. Chỉ receiver/common-wire layer; thiếu config/suite/zero/skip không PASS. Các số/hash R01/R02a phía trên giữ lịch sử, runtime JDBC/snapshot hiện hành ở release manifest.

## Content admission receiver R04b

[Task/AC/checkpoint](../../../docs/features/f10-identity-access/sessions/tasks/BL-R04b.md) giữ trạng thái nghiệm thu và evidence hiện hành. Feature `identityaccess/` cung cấp Java `ContentAccess.read`: route adapter phải tạo request sau validation, gọi Identity mỗi request trước transaction rồi thực hiện local SQL dưới account gate. `requireOwner` dùng principal được xác thực, không userId từ browser. Port này chưa mở API-C03/body route, catalog hoặc publication.

Profile riêng `admission` dùng own-lab transport config và `backendlab.admission.config` trong cùng owned trust directory. Startup xác minh realm/generation/topology/own DB role/schema/readiness; runtime không tự migrate hoặc reset DB. `ops/content/provision-admission-realm.sql` chỉ khởi tạo lab trống bằng migrator, exact retry được phép; khác realm/generation hoặc retained security state bị từ chối. Đây không phải restore/production operator flow.

Private8443 chỉ Identity mTLS được PUT INT-02, reject browser headers/cookie/Origin; JSON body tối đa8192bytes, upload idle timeout3s và sanitized JSON error. Tombstone/receipt commit trước ACK; runtime role không xóa deny/receipt hoặc hạ epoch. Public8080 vẫn chỉ foundation status qua edge cert, không body API. `bootstrap`/`transport` không mở INT-02.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -EvidenceTask R04b -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R04b -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -Case SEC-02 -EvidenceTask R04b -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -Case SEC-04 -EvidenceTask R04b -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -Case SEC-09 -EvidenceTask R04b -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -Case SEC-15 -EvidenceTask R04b -Offline
```

CMD03 gồm own isolation và admission gate. SEC04 chạy cùng DB prerequisites/races, SEC02/09 chọn runtime TLS cases, SEC15 giữ transport + admission negatives. CMD04 đầy đủ transport/common-wire/receiver; unit admission suite và critical cases bắt buộc, không missing/zero/skip PASS. INT-01 provider là controlled contract fixture qua mTLS thật; canonical Identity provider/coordinator thuộc R04d. Không full auth/COMPLETED/reauth/FE/production release từ receiver proof. Không claim performance SLA từ budget lab; heap cap `BACKEND_LAB_JVM_MAX_HEAP_MB=384` chỉ là tùy chọn process lab trên host thiếu bộ nhớ.
