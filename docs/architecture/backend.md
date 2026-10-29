# Architecture source code — Backend microservices

Cập nhật convention 05/10/2026 · MS01–04/R01a–d/R02a/b/R03/R04a và D17. Ba service theo [catalog](service-boundaries.md), [security](security-design.md)/[session](../features/f10-identity-access/sessions/protocol.md)/[data](data-protocols.md); [implementation plan](../delivery/implementation-plan.md) và [test-release](../delivery/test-release-plan.md) giữ stack/tasks/build/CI/ops/evidence. [ADR-03](microservices-review.md) giữ findings. R01a–d có build/ba bootstrap, R02a có [DB lab/migration harness](../platform/database/verification.md), R02b có [transport trust/common-wire foundation](../platform/service-trust/verification.md), R03 có [local credential/maintenance](../features/f10-identity-access/credentials/verification.md). R04a có local Identity session/preauth/CSRF và opt-in lab HTTP/browser; receiver/coordinator/owner và nghiệp vụ học theo task hiện hành. Registry giữ trạng thái; D17 chỉ thống nhất cấu trúc, không nghiệm thu hoặc đổi trạng thái leaf sản phẩm.

## 1. Services và ranh giới dữ liệu

| Service | Module nghiệp vụ bên trong | Database riêng |
|---|---|---|
| Identity | account/credential/session/revocation/auth audit/account lifecycle | identity_db |
| Content | topic/CAP/placement/unit/exercise/case/publication coordinator | content_db |
| Learning | work/assessment/progress/transfer/lifecycle | learning_db |

Nháp/submission/result/evidence/progress nằm cùng Learning để giữ T1/T2 local atomic. Identity và Content có API/đồng bộ riêng; không dùng FK/join/transaction xuyên DB. Một PostgreSQL instance local có thể chứa ba database, runtime/migration credentials của service chỉ có quyền database của nó. userId trong Learning là opaque reference; Learning giữ private pinned content replica có checksum/provenance, không authoring canonical thứ hai.

Edge reverse proxy cung cấp cùng origin/TLS/route. Gateway không quyết owner hay ghi dữ liệu nghiệp vụ. Các service tự enforce identity/quyền. Worker chấm là phần Learning ở R1; admin và runner sau R1.

## 2. Source tree đích

```text
backend/
  pom.xml / mvnw / mvnw.cmd / .mvn/
  services/
    identity-service/
      pom.xml
      src/main/java/lab/backend/identity/
        credential/ session/ lifecycle/ delivery/
        configuration/ transport/ api/     api/ giữ bootstrap adapter hiện có
      src/main/resources/db/migration/
      src/test/
    content-service/
      pom.xml
      src/main/java/lab/backend/content/
        catalog/ publication/ identityaccess/ delivery/
        configuration/ transport/ api/     api/ giữ bootstrap adapter hiện có
      src/main/resources/db/migration/
      src/test/
    learning-service/
      pom.xml
      src/main/java/lab/backend/learning/
        work/ assessment/ progress/ transfer/ lifecycle/
        contentreplica/ identityaccess/ configuration/ transport/
        delivery/               inbox/outbox/HTTP relay, local receipts
      src/main/resources/db/migration/
      src/test/
contracts/http/{identity,content,learning}/
contracts/events/
ops/compose/ edge/ observability/ backup/
```

Hiện có backend/pom.xml/wrapper, scripts/services.ps1/setup-backend.ps1 và ops/release/dependencies.json ở R01a; R01b/c/d thêm services/identity-service, services/content-service và services/learning-service, mỗi app có POM/entry/config/api/bootstrap tests và dependency snapshot. Parent tham chiếu đúng ba module thật; R02a có own V1 marker/migration tests, ops/db-lab layout và portable cluster helper; R02b có own transport package/config/guard, common schemas/fixtures và packaged TLS suites; deployment/domain contracts còn dự kiến. Test fixtures không đóng gói; transport dùng own DB clock, bootstrap và domain chưa persistence API. Mỗi service có app entry/config/build artifact/migration/test riêng. Maven parent dùng conventions/version, không executable chung. Không shared entity/repository JAR; service không import Java source của nhau.

Quy ước thống nhất từ05/10/2026: mỗi service chia theo feature ở cấp đầu. Bên trong feature, api→application→domain; infrastructure hiện thực repository/port. Feature nhỏ giữ các class phẳng; chỉ thêm layer/package khi có trách nhiệm cần tách. Transaction local thuộc application use case, kể cả khi chưa tách folder application. Module Learning phối hợp T2 cùng database, không biến lời gọi nội bộ thành HTTP. `contentreplica` giữ bundle/stage/publication gate/applied policy; `identityaccess` giữ admission/auth gates, không sở hữu canonical content/session. Content application có publication coordinator; Identity có account lifecycle coordinator. Mỗi service có delivery adapter/inbox/outbox riêng, không domain JAR chung. Learning lifecycle/exposure và Identity lifecycle dùng journal port qua infrastructure adapter ngoài snapshot; ops recovery kiểm inventory/manifest/realm. Chỉ tạo package con khi task cần, không scaffold toàn cây rỗng.

### 2.1. Cấp feature và layer

```text
<service>/src/main/java/lab/backend/<service>/
  <feature>/                  credential, session, catalog, publication, work, progress...
    <classes>                 phần nhỏ giữ phẳng
    api/                      HTTP adapter của feature, khi cần
    application/              use case/transaction, khi cần
    domain/                   policy/model, khi cần
    infrastructure/           DB/HTTP/journal adapters, khi cần
  configuration/ transport/   composition và trust nền của service
```

Phần cây đích này thay quy ước layer ở cấp đầu trước D17 cho Identity/Content; không thay topology, ownership, transaction hoặc protocol. Source hiện có vẫn ở package hiện hành; áp dụng dần khi leaf tạo/mở rộng feature, không refactor qualified source chỉ để khớp cây. `api/` bootstrap hiện tại được giữ, endpoint mới thuộc feature sở hữu thay vì gom mọi controller vào một thư mục chung. Cross-feature gọi qua entrypoint/port rõ; main source không import sang service khác.

Test đặt gần phần sở hữu; fixture package-private có thể ở package cần white-box access. Chuyển test/fixture phải cập nhật POM/required-suite checks và qualification trong cùng task; không mở public API production chỉ để đổi folder test. [D16/STR-05](project-structure-review.md#3-findings-và-mức-ưu-tiên) giữ cleanup follow-up. [Prerequisite browser Identity](../platform/environment/identity-browser-qualification.md) là source hướng dẫn test xuyên package.

## 3. Contract và trạng thái review

R04b mở rộng Content trong feature phẳng `identityaccess/`: strict admission/fence wire, own DB gate/receipt, request-scoped online port và explicit owned-lab runtime. Composition/trusted dispatch vẫn thuộc `configuration/transport`; không gom domain vào global repository/controller hay refactor package đã qualified. [Task/acceptance/evidence](../features/f10-identity-access/sessions/tasks/BL-R04b.md) giữ tiến độ; INT01 controlled provider fixture chưa là canonical Identity/R04d và chưa API-C03/R07.

R04c mở rộng Learning theo cùng D17 `identityaccess/`, nhưng entrypoint `LearningAccess.mutate` bind action/draft/body/context/Origin/CSRF và giữ own gate cùng callback SQL/use tới commit. Callback dùng cùng Connection, không external effect/transaction tự mở; business owner/CAS/receipt vẫn do future endpoint sở hữu. Private INT02 có own durable deny/receipt và ACK-after-commit, composition vẫn tại configuration/transport. [Task/DoD/evidence R04c](../features/f10-identity-access/sessions/tasks/BL-R04c.md) giữ trạng thái; chưa API-L03/draft/T1/T2/job/progress, canonical provider/coordinator hoặc global completion.

- HTTP public ở cùng origin; content outline anonymous, body theo login S02. Learning không nhận owner/pass do browser tự khai. API-I/C/L, INT/OPS và contract source owner ở [MS01 catalog](service-boundaries.md); private case/hint/solution không có Content browser endpoint bypass exposure.
- HTTP nội bộ dùng mTLS/service action allowlist; protected browser action cần online admission và local monotonic gate/fence. Identity hoàn tất revoke sau ACK cả CNT/LRN, kể cả session absent; [MS02 protocol](../features/f10-identity-access/sessions/protocol.md) giữ wire/state/race/expiry, không chỉ introspection đầu request.
- Content version private immutable/checksum; [publication LLD](../features/f08-content/publication/protocol.md) chốt STAGE/FENCE/canonical COMMIT/RELEASE, policy overlay và withdrawal.
- MS03 chọn HTTP relay + outbox/inbox; [data protocol](data-protocols.md) chốt exact bytes/digest/sequence/retry/generation, chưa broker.
- [Lifecycle/recovery](lifecycle-recovery-protocol.md) chốt exposure durable trước disclosure, reset/delete intent, ba DB/journal reconciliation và restored credential RECOVERY_REQUIRED.
- Code/test phải xử lý duplicate, out-of-order, timeout, partial outage, event replay và restore. Không lấy JWT TTL ngắn thay cam kết logout đã thu hồi ngay.
- Các giao thức là thiết kế; source tree chưa là code/DB/race evidence hoặc cổng READY triển khai.

## 4. Plan và evidence


MS04 chốt baseline Java21/Boot4.1.1/Maven3.9.16/PG18.6; R01a pin toolchain/hash, R01b Identity26tests/R01c Content28tests/R01d Learning42tests và packaged JAR riêng đã kiểm. Ở scope R01 bootstrap, ba app dùng explicit bootstrap HTTP127.0.0.1:cổng18081/18082/18083 hoặc random, businessReady=false; default/lab/private/pilot config bị deny trước listener, không substitue public8080/private8443. R02a roles/migrations và R02b trust/common-wire foundation có evidence riêng; R02c delivery foundation đã đạt DoD leaf theo [task/DoD hiện hành](../platform/delivery/tasks/BL-R02c.md). R28a journal lab trước attempt/support; R28b actual S3 qualification trước R32b recovery/R31d pilot. N-N-1/SEC/DATA/REL gates theo test plan, không full security/DB/release PASS từ bootstrap.
Test: local unit/DB ở mỗi service; cross-service contract compatibility và fault/race; deployment artifact độc lập; full-stack learner flow; restore nhiều DB với journal và watermark. Bootstrap có runtime evidence riêng ở R01b/c/d; review MS01–04 không là evidence business/DB/trust.

[D14 review](readiness-review.md) bổ sung common wire catalog §4.1/WIRE fixtures R02b, public-edge HTTPS8080/private-service8443 chains, base E2E R31a trước FE, checkpoint interlock R32a giữ writer/roles đóng qua restart/crash. R21a cursor signing key riêng Learning theo rotation inventory, không quyền learner. Nguồn hiện hành giữ chi tiết, không tạo package/config rỗng trong review.

[Baseline modular cũ](backend-modular-baseline.md) giữ lịch sử D08/R00, đã bị thay cho topology/source layout. Business và invariant học vẫn ở S02/S03/D05 trừ điểm được review thay đổi rõ.

R02b technical trust foundation và bằng chứng kiểm hiện hành ở [tài liệu review](../platform/service-trust/verification.md); trạng thái/evidence cuối ở backlog. Chưa login/session/owner/business/edge deployment.

R03 thêm own Identity `credential/` package, production V2 SQL/CLI và test V20/V30 namespace; `app_user` là account lock, hash/epoch/token/pending local atomic. Recovery không bật lại operator-disabled account hoặc old hash. [Credential evidence](../features/f10-identity-access/credentials/verification.md)/[runbook](../../ops/identity/README.md) giữ actual DB/KDF/packaging và giới hạn. R04a thêm production V3/own `session/`, Spring Session JDBC4.1.1 backing store, canonical terminal guard/static pending intents và credential-preauth-reset callback cùng local transaction. Profile `session` explicit own lab register public auth sau startup/mTLS edge/source-IP checks; bounded sweeper, provider schema và packaged TLS/browser fixtures ở [session evidence](../features/f10-identity-access/sessions/verification.md), trạng thái nghiệm thu theo registry. Không automatic framework cookie/filter, receiver admission/coordinator ACK, full reauth hoặc production edge. Pending không clear cho tới auth barrier và lifecycle journal gates tương ứng, không giả full credential-recovery drill.
