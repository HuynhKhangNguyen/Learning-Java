# Backend Lab — đọc tài liệu từ đây

Tổ chức theo feature ngày04/10/2026. **Bắt đầu bằng feature đang làm**, không đọc toàn bộ repo hoặc lịch sử mỗi phiên.

| Việc cần làm | Điểm vào |
|---|---|
| Review sản phẩm/business toàn hệ thống | [HLD](design/hld.md), [business](business-design.md), [requirements](product-requirements.md), [system](system-overview.md) |
| Làm một feature hoặc feature nhỏ | [12 feature F01–F12](features/README.md) → README của feature → source business/LLD → task leaf |
| Tìm task hiện hành/handoff | [Registry](delivery-backlog.md) → brief/checkpoint được dòng task link tới; trạng thái và dependency luôn đọc tại registry, không giữ snapshot task hiện hành trong mục lục này |
| Review kiến trúc xuyên feature/service | [Architecture](architecture/README.md): ownership/security/data/restore và source FE/BE |
| Review kiến thức cần dạy | [Curriculum](curriculum/README.md): Backend/DevOps, TOP/CAP/prerequisite và authoring |
| Build/lab/runtime/môi trường | [Platform](platform/README.md) và [backend README](../backend/README.md) |
| Git baseline/branch/worktree/commit/integration | [Git workflow](platform/repository/git-workflow.md); task initial baseline đọc từ registry |
| Goal/plan/Control Center/session/DoD/test/release | [Delivery](delivery/README.md), [quality](quality-process.md), [execution model](execution-model.md) |
| Xem thiết kế/bằng chứng cũ | [History](delivery/history/design-checkpoints.md), [baseline cũ](delivery/history/legacy-backlog.md); không tự thực thi backlog cũ |

## Cây tài liệu

```text
docs/
  README.md                    mục lục để làm việc
  delivery-backlog.md           registry: trạng thái + dependency + link task
  business-design.md            goal/business chung
  product-requirements.md       REQ-01–18 chung
  feature-module-map.md         F01–12 ↔ MD01–12
  design/                      HLD, mục lục review và assets prototype
  features/
    f01-journey/ ... f12-administration/
      README.md                goal/scope/REQ/module/source/task/DoD
      business.md / lld.md      khi đã có source thật
      credentials/ sessions/   các phần nhỏ của F10
        protocol.md            contract thuộc phần đó
        tasks/BL-Rxx.md         brief, acceptance, checkpoint duy nhất
        verification.md        bằng chứng/giới hạn triển khai
  architecture/                contracts/ownership xuyên feature
  curriculum/                  chương trình học Backend/DevOps
  platform/                    build/bootstrap/database/trust/environment
  delivery/                    plan/DoD/traceability/templates/history
```

File liệt kê trong cây là quy ước, không bắt mỗi feature có đủ file hoặc tạo file trống. Những đường dẫn cũ được giữ làm trang chỉ dẫn; sửa bản nguồn trong thư mục mới. Một thông tin có một nguồn, các nơi khác link tới nguồn đó.

## Quy tắc chọn cấp tài liệu

- **Toàn dự án:** goal, requirement, HLD, ownership/service, security/data convention và release gates dùng chung.
- **Feature:** hành vi người học, scope, module/REQ và các nguồn business/LLD liên quan.
- **Feature nhỏ:** tách khi có contract/dữ liệu/task kiểm chứng riêng; credentials, sessions và publication đã có phần riêng.
- **Task leaf:** một mục tiêu thực thi, acceptance, context, plan và checkpoint. Registry giữ trạng thái; task file là phần chi tiết của cùng backlog.
- **Verification:** kết quả đã chạy và limits; không chuyển design thành DONE chỉ vì có sơ đồ hoặc folder.

Với một task hiện hành: đọc registry → file task/checkpoint được link → README của owner → verification/protocol/code cần thiết. Không đọc lại toàn bộ lịch sử hoặc tự chuyển sang sibling; cách điều phối và delegation nằm trong [execution model](execution-model.md#9-control-center).
