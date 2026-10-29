# Kiến trúc source code

[Tài liệu theo feature](../features/README.md) giữ business/LLD/brief/checkpoint/evidence của từng phần. Mục này giữ nguồn chung xuyên feature: source FE/BE, service ownership, security và data/recovery. Session/publication cùng implementation/test-release đã được link tới owner mới bên dưới; file cũ là trang chỉ dẫn.

V3 MS01–04: ba service/auth/data và implementation/test/release plan thay topology một-app. [ADR-03](microservices-review.md) giữ rationale/findings, [baseline modular](backend-modular-baseline.md) chỉ là lịch sử. R01/R02 foundation, R03 credentials, R04 session/admission/revoke/reauth owned-lab và R05 catalog schema2 đã có evidence theo leaf. Publication, learner business, FE integration và production platform còn theo [backlog](../delivery-backlog.md); architecture docs không giữ snapshot trạng thái cạnh tranh.

30/09/2026 · BL-R00. Cùng một repo, build FE và BE riêng. Docs/contracts/content/ops dùng chung ở root; không thêm monorepo framework/npm workspace chỉ để phân thư mục.

| Tài liệu | Phạm vi |
|---|---|
| [Review cấu trúc dự án — D16](project-structure-review.md) | Cây hiện có, ranh giới FE/BE/service, findings về Git/hygiene/test/package và thứ tự xử lý |
| [Frontend](frontend.md) | Source hiện có, cấu trúc đích, component/state/API boundaries và test |
| [Backend](backend.md) | Package dự kiến, application/domain/infrastructure, owner/transaction/worker và test |
| [Service catalog / API boundaries](service-boundaries.md) | Ba service, data/module owner, API browser/nội bộ, replica content và walk-through MS01 |
| [Security design](security-design.md) | Threat/caller/asset matrix, controls/task owner, SEC-01–30 và nguồn primary |
| [Session protocol](../features/f10-identity-access/sessions/protocol.md) | Opaque session, online admission, local fence, logout ACK barrier, reauth, expiry và failure behavior |
| [Data protocols v3](data-protocols.md) | Transport/envelope/inbox/outbox, INT-03/04/05, invariants và DATA-01–30 |
| [Publication LLD](../features/f08-content/publication/protocol.md) | STAGE/FENCE/canonical COMMIT/RELEASE, withdrawal/current evidence và crash/abort |
| [Lifecycle/recovery LLD](lifecycle-recovery-protocol.md) | Exposure/reset/delete journals, ba DB checkpoint/restore, credential recovery và readiness |
| [Implementation plan v3](../delivery/implementation-plan.md) | Stack/baseline/ports/config/secrets/journal provider/budgets, parent-leaf/session và inputs |
| [Test/release plan v3](../delivery/test-release-plan.md) | SEC/DATA leaf owners, fixtures/commands/CI compatibility/evidence và REL-01–08 |
| [Review sau MS04](readiness-review.md) | RV-F01–05: test environment trước FE, edge trust/spill, backup interlock, common wire và early UX checkpoint; bổ sung D14 |

```text
AGENTS.md / README.md         rule và điểm vào toàn repo
docs/                        business, HLD/LLD, architecture, backlog, DoD
scripts/project.ps1          điều phối FE và chọn runtime hiện có
frontend/                    source/config/package/lockfile/test FE đã di chuyển
backend/                     parent/wrapper/ba app bootstrap + DB/transport foundation
contracts/http/common/       schema/fixtures common-wire R02b; domain API còn dự kiến
content-v2/                  dự kiến: authoring có phần private, không nằm trong FE
ops/db-lab/                  DB layout/lab README R02a; deployment/backup/restore dự kiến
```

Thư mục dự kiến chỉ được tạo khi task có nội dung thực. Hai tài liệu kiến trúc không là trạng thái task: [backlog](../delivery-backlog.md) giữ nguồn điều phối. [D08](../delivery/history/implementation-v2.md) giữ ADR-01/02; [LLD](../design/README.md) giữ behavior contracts. Khi chỉnh boundary, cập nhật cả nguồn liên quan trong cùng task.

Evidence kỹ thuật nằm trong verification/task của owner; [backlog](../delivery-backlog.md) giữ trạng thái hiện hành. Một leaf foundation/auth `DONE` không tự chứng minh learner business hoặc production deployment.
