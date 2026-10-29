# Backend — Backend Lab

Quy ước source áp dụng dần theo feature ở từng service: xem [architecture BE](../docs/architecture/backend.md). Các lớp build/DB/browser cần đầu vào riêng; đọc [prerequisite browser Identity](../docs/platform/environment/identity-browser-qualification.md) trước CMD-04 Identity trên checkout mới.

Hướng hiện hành: microservices theo yêu cầu30/09/2026, cùng repository với frontend. R01a có parent POM/Wrapper/helper; R01b có [Identity bootstrap](services/identity-service/README.md), R01c có [Content bootstrap](services/content-service/README.md), R01d có [Learning bootstrap](services/learning-service/README.md), mỗi app có tests/artifact riêng. R02a có [DB lab/migration harness](../docs/platform/database/verification.md); R02b có transport trust; R03 có [local credentials/maintenance](../docs/features/f10-identity-access/credentials/verification.md). R04a có [session/preauth/CSRF và opt-in lab auth TLS/browser](../docs/features/f10-identity-access/sessions/verification.md), không receiver/admission/full reauth/production edge hoặc nghiệp vụ học; trạng thái nghiệm thu ở [backlog](../docs/delivery-backlog.md).

- [Service catalog/API boundaries](../docs/architecture/service-boundaries.md): ba service đã chốt ở MS01, data owner, public/internal contracts và walk-through.
- [Architecture source hiện hành](../docs/architecture/backend.md): source tree đích và boundary build/migration riêng.
- [Review microservices / ADR-03](../docs/architecture/microservices-review.md): findings, consistency/security và kế hoạch MS01–04.
- [Auth/session](../docs/architecture/session-protocol.md) và [data/publication/lifecycle/recovery](../docs/architecture/data-protocols.md): MS02/03 contract hiện hành; runtime chỉ đạt các leaf/layer có evidence, không toàn protocol hoặc business readiness.
- [Implementation plan v3](../docs/architecture/implementation-plan.md) và [test-release](../docs/architecture/test-release-plan.md): stack/ports/secrets/journal qualification/fixture/cmd/REL gates.
- [Backlog](../docs/delivery-backlog.md): trạng thái/goal của từng leaf; parents R02/R04 rollup DONE trong scope children, không toàn hệ thống/production readiness.
- [Durable relay R02c — qualified foundation runbook](../docs/platform/delivery/README.md): own delivery profile/SQL probe, CMD03 và CMD05 FX04/DATA10–15 đã đạt DoD leaf, chưa production publication/lifecycle/coordinator/restore hoặc toàn auth DONE. [Task checkpoint](../docs/platform/delivery/tasks/BL-R02c.md) giữ exact runs và limits, không dùng hashes lịch sử làm candidate hiện hành.

## Toolchain hiện có

- Java21, baseline Microsoft Build of OpenJDK21.0.12.1+1-LTS Windows x64 portable, không cài vào hệ thống.
- Maven3.9.16 qua Apache Wrapper3.3.4 `only-script`, distribution kiểm SHA256. Không cần Maven toàn hệ thống hoặc wrapper JAR.
- Spring Boot parent/BOM4.1.1, compiler release21, Enforcer3.6.3 yêu cầu JDK21 và Maven3.9.16. Parent `packaging=pom`, khai báo đúng ba modules Identity/Content/Learning thật, không executable/shared-domain JAR.
- Pin `tomcat.version=11.0.26` thay BOM11.0.24 theo advisory đã review. Identity/Content/Learning đã có actual trees/runtime assertions11.0.26; transport profile đã kiểm R02b, business/Compose profiles còn owner sau. Parent validate không là service/security test.
- [Dependency manifest](../ops/release/dependencies.json) ghi URL/hash, runtime/advisory và evidence còn thiếu. Đây chưa là SBOM/release scan.

Từ root repo, PowerShell7.4 trở lên:

```powershell
pwsh -NoProfile -File scripts/setup-backend.ps1
pwsh -NoProfile -File scripts/services.ps1 -Task toolchain
pwsh -NoProfile -File scripts/services.ps1 -Task validate
```

Setup tải đúng ZIP JDK Windows x64 đã pin, kiểm SHA256 rồi giải nén vào `.tools/jdk/`. Helper dùng JDK portable nếu có; tiếp theo JAVA_HOME hoặc Java trên PATH, hoặc chỉ định `-JavaHome 'C:/path/to/jdk-21'`. Java17/JRE/JavaHome sai trả nonzero. Helper chỉ đổi environment trong process, khôi phục cwd/environment khi kết thúc; không sửa registry hoặc PATH hệ thống. Override JDK21 khác phải ghi runtime thực khi review/release.

Cache wrapper ở `.tools/maven-user-home`, Maven repository ở `.tools/m2/repository`; đều ignored. Lần đầu cần HTTPS đến nguồn vendor/Maven Central với TLS hợp lệ; lỗi mạng/chứng chỉ trả lỗi, không tắt TLS validation. `-Offline` chỉ chạy Maven từ cache đã đủ, wrapper vẫn cần Maven distribution có sẵn. Setup repeat kiểm archive hash; cache hỏng không tự coi là cài thành công. Linux/macOS dùng JDK21 tự chuẩn bị và `./mvnw`; chưa được qualified trong R01a, parity thuộc R31c. Checkout phải giữ executable bit cho `mvnw` khi đưa vào Git.

Wrapper trực tiếp (cwd `backend/`) cũng hỗ trợ `./mvnw.cmd --version` và `./mvnw.cmd -N validate`; đặt JAVA_HOME trỏ JDK21, MAVEN_USER_HOME/cache repo tương ứng nếu không dùng helper. Reactor có parent + Identity + Content + Learning; selected verify chỉ build parent và service được chọn.

## Contract build từng service

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service identity
pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service content
pwsh -NoProfile -File scripts/services.ps1 -Task verify -Service learning
```

Identity đã có app/tests thật: helper chạy selected `-pl services/identity-service -am clean verify`, đọc JUnit reports và từ chối missing/zero/skipped/failed suite; exit code native được giữ. [Identity evidence](../docs/identity-bootstrap-verification.md) ghi26tests và điều kiện socket-temp của Windows runner. Content có [evidence R01c](../docs/content-bootstrap-verification.md): 28 tests và packaged JAR smoke với cùng socket-temp override. Learning có [evidence R01d](../docs/learning-bootstrap-verification.md): 42 tests/packaged JAR smoke với cùng override. Ba app đều có selected verify và CMD-03 DB lab; business suite chưa có. `verify` bắt buộc một service identity/content/learning; không `all` hoặc tùy ý `skipTests`. CMD-04/05 có receiver/common-wire layer R02b; full-case auth/data và CMD-06–11 (journal/E2E/scan/backup/restore/all) chưa triển khai và trả nonzero, owners trong test-release.

Kiểm helper/setup với fixture riêng trong ignored `.tools/test-r01a`, không build app:

```powershell
pwsh -NoProfile -File scripts/tests/backend-build.Tests.ps1
# Có JDK ngoài 21 để kiểm thêm ca từ chối:
pwsh -NoProfile -File scripts/tests/backend-build.Tests.ps1 -UnsupportedJavaHome 'C:/path/to/jdk-17'
```

Log/report sinh ở `artifacts/quality/R01a/` và `R01b/` không commit; [bằng chứng nền build](../docs/backend-build-verification.md)/[Identity](../docs/identity-bootstrap-verification.md) ghi kết quả lịch sử, giới hạn và handoff. D08 một-app không phải scaffold hiện hành. Canonical auth owned-lab R04d đã nghiệm thu (mục dưới); chưa business/edge deployment. Receiver và DB foundation có evidence riêng theo leaf, không kế thừa qualification từ bootstrap.

## DB lab R02a

Ba DB/roles/migrations riêng, PostgreSQL18.6-5 portable Windows. Hướng dẫn môi trường/credentials/cleanup ở [ops/db-lab](../ops/db-lab/README.md), actual reports và giới hạn ở [R02a evidence](../docs/database-lab-verification.md).

```powershell
pwsh -NoProfile -File scripts/setup-postgresql.ps1
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service all -Offline
```

Bỏ -Offline nếu Maven cache chưa đủ. Profile bootstrap không connect/migrate DB; CMD-03 khởi động cluster riêng rồi dừng trong finally. Các profile opt-in dùng own DB theo leaf; Flyway test-scoped, không full SEC/DATA/release PASS. R02/R04 parents và R03/R04a/b/c/d đã DONE trong phạm vi riêng. Registry là nguồn trạng thái hiện hành.

R02b technical trust foundation và bằng chứng của leaf ở [tài liệu review](../docs/service-trust-verification.md); trạng thái/evidence cuối ở backlog. Riêng R02b không chứng minh login/session/owner/business/edge deployment; các auth leaf sau có evidence riêng.

## Service trust/common wire R02b

[Review/evidence](../docs/service-trust-verification.md) ghi config/cert/caller-action/clock và common schemas. Profile explicit `transport` chỉ lab, HTTPS8080 edge-only/private8443 service/operator; probe luôn businessReady=false. Managed commands kiểm packaged JAR/stop, không có Compose/edge deployment/canonical auth.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service all -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service identity -Case SEC-15 -Offline
```

JDBC runtime hỗ trợ own DB; Flyway/test migrations/PKI/test suites không vào JAR. CMD-05 foundation có SEC-12/22 Identity credentials và SEC-14/15/20 receiver tags; canonical selectors R04d ở mục dưới là layer khác. CMD-06–11 và full business/production qualification chưa có. [Identity runbook](../ops/identity/README.md) phân biệt operator CLI, standalone session và canonical lab cùng secrets/pending/limits; không lấy foundation transport làm public auth evidence.

## R04d canonical three-app driver — owned-lab qualified

Lệnh sau dành riêng cho owned Windows lab, không thay command `contract` lịch sử. Learning giữ driver test; Identity vẫn là canonical authority. Helper build và kiểm units Identity → Content → Learning tuần tự trước required baseline21variants, với ba database/app roles và named public18081–18083/private18441–18443. Test-only SQL probes không vào runtime JAR, không là learner API/T1/T2. Hai browser variants dùng named HTTPS relaylocalhost17441 và installed Edge/locked FE modules, không sửa FE hoặc production edge.

```powershell
$env:BACKEND_LAB_JVM_MAX_HEAP_MB = '384'
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service learning -EvidenceTask R04d -Offline
```

Runner tạo own PostgreSQL/PKI/config, giữ cả ba artifacts/reports/input hashes và dừng owned cluster trong `finally`. Không mở arbitrary endpoint override, install global/service hoặc deploy. `contract` bắt buộc full baseline và không nhận `-Case`. CMD05 canonical dùng cùng driver/profile, nhưng chọn exact methods bằng **một native token**; report phải có đủ variants đã chọn và không có case thừa. Browser reports chỉ bắt buộc ở selector thực chạy hai browser variants.

```powershell
$env:BACKEND_LAB_JVM_MAX_HEAP_MB = '384'
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service learning -Case SEC-23 -EvidenceTask R04d -Offline
```

| Canonical selector đã có source | Layer thực kiểm, chưa full feature/owner sau |
|---|---|
| SEC-01/03 | Actual mutation gate commit/rollback vs two-ACK completion; probe không T1/T2 |
| SEC-05 | Fresh canonical admission/deny và pending/restart, không framework-only allow |
| SEC-06 | Missing receiver/sensitive pending và browser locked/fresh-flow guard |
| SEC-07 | Lost ACK/Identity+receiver restarts, immutable commands/one effect |
| SEC-08 | Authenticated wrong ACK không complete hoặc chặn operation độc lập |
| SEC-11 | Actual sensitive response A trễ sau loginB/cookie/multitab; không FE/cache generation |
| SEC-10/12 · Identity | Full own-core DB bundle: canonical deadlines/poll/KDF/token/reset/disable/races/rollback cùng isolation/delivery regressions; không mTLS/browser layer |
| SEC-12 · Learning driver | Legacy CLI reset/disable pending → actual canonical worker/ACK/restart; không journal completion |
| SEC-19 | Canonical CSRF/Origin/context/sensitive proof và natural browser Origin |
| SEC-20 | Verified caller/context/request binding và wrong receiver certificates |
| SEC-23 | Purpose/intent/all reservation bindings/expiry/restart và sensitive browser retry |
| DATA-15 · Learning driver | Own-Learning production strict store giữ poison/gap, actual canonical two-ACK completion độc lập; không production publisher |

SEC02/04/09 vẫn thuộc receiver CMD03/05 layers. Identity core selectors dùng `-Task fault -Service identity -Case SEC-10` (hoặc SEC-12) `-EvidenceTask R04d`; chạy **full required CMD03-style own-core bundle**, không named method filter, legacy credential-only hay silently ignored TrustConfig. SEC12 Learning driver riêng chứng minh actual worker/process composition. Mã/layer chưa implement trả lỗi. R04d DONE13/10 sau full21 runtime/current strict audit và closing owner/helper/guards/smoke/dependency/source/secret/DoD. Không suy mọi selector command đã chạy riêng chỉ vì methods nằm trong full suite. Exact runs/failures/artifacts/impact delta/limits giữ tại [task/DoD R04d](../docs/features/f10-identity-access/sessions/tasks/BL-R04d.md); không feature/production DONE từ leaf này.
