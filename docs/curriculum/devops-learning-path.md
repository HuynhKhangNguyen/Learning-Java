# Roadmap DevOps — bộ khung v1

28/09/2026. Nâng phần Docker thành **roadmap DevOps** theo yêu cầu người dùng. [Catalog BL-S04](devops-catalog.md) đã nối 12 chặng với TOP/CAP, prerequisite, DP0–DP5 và gap có owner. Đây vẫn là thiết kế, chưa là bài published hoặc UI đã nối catalog. Các công nghệ bên dưới là chủ đề dự kiến, không yêu cầu cài mọi công cụ; trạng thái theo [backlog](../delivery-backlog.md).

## Mục tiêu và đường vào

Chuẩn bị môi trường, tự động hóa build/test/release, triển khai, quan sát và khôi phục dịch vụ. Đầu vào là thao tác máy tính/lập trình cơ bản; không phải hoàn thành roadmap Java Backend. Có thể dùng app mẫu để học vận hành rồi áp dụng vào project Java.

| Chặng | Kiến thức chính | Đầu ra dự kiến | Nội dung dùng chung |
|---|---|---|---|
| DS00 Hệ điều hành | Linux filesystem, permission, user/process/service, terminal, package/env | Chạy dịch vụ mẫu, tìm process/log, quản lý cấu hình/quyền | K01, TOP-ENV phù hợp |
| DS01 Mạng và web | IP/subnet/routing, DNS, TCP/UDP, port, HTTP/TLS, proxy/load balancing, firewall | Theo đường request, tìm lỗi kết nối/chứng chỉ | K02/K08 |
| DS02 Git và tự động hóa | Git collaboration, shell/script, exit code, biến, config, idempotence | Script build/kiểm tra có xử lý lỗi và diff review được | K03, ENV-003/004 |
| DS03 Container — Docker | Image/container, Dockerfile/layer, registry/digest, network, volume, Compose, resource/secrets | App/DB đóng gói, persistence sau recreate, health | K18, TOP-CTR |
| DS04 CI/CD và artifact | Build/test gate, pipeline, artifact/version, credential, promotion/approval/rollback | Cùng artifact qua kiểm tra tới môi trường triển khai | K17/K19, TOP-OPS |
| DS05 Cloud và deployment | Compute/storage/network, IAM, DNS/TLS, VM/PaaS/managed service, config/cost | Dịch vụ triển khai với quyền phù hợp và cấu hình tái lập | K19/K22 |
| DS06 Infrastructure as Code | Resource, state/locking, plan/apply, drift, module, configuration management | Tạo/đổi môi trường có review, bảo vệ state | K22 |
| DS07 Observability/reliability | Log/metric/trace, health, dashboard, SLI/SLO, alert/capacity | Phát hiện và khoanh vùng lỗi, alert có hướng xử lý | K20, TOP-OPS |
| DS08 Operations/recovery | Incident/runbook, backup/restore, RPO/RTO, rollback, postmortem, cost review | Diễn tập sự cố và khôi phục có bằng chứng | K19/K20 |
| DS09 Orchestration | Kubernetes workload/service/ingress, scheduling, config/secrets/storage, rollout, Helm/GitOps | Dịch vụ nhiều instance sau nền container/network | K22 |
| DS10 Security xuyên suốt | Identity/secrets, dependency/image scanning, supply chain, hardening, network policy, audit | Phát hiện/sửa lỗi quyền và cấu hình pipeline/runtime | K16/K18/K19/K22 |
| DS11 Nâng cao/platform | Capacity/reliability nâng cao, multi-region, developer platform, policy/cost | Chọn giải pháp theo bài toán và đánh đổi vận hành | K20–K22, tùy chọn/chuyên sâu |

DS không phải thứ tự khóa cứng. DS00–02 học đan xen → DS03 → DS04/DS05; DS06 sau khi hiểu resource/state; DS07 bắt đầu từ dịch vụ đầu rồi sâu dần; DS08 đi cùng release. DS09 sau container/network/deploy cơ bản; DS10 bắt đầu ngay từ nền tảng, không đợi cuối. DB vận hành gồm connection/storage, migration, quyền, backup/restore và availability, dùng lại nội dung DB thích hợp, không buộc học ORM trước.

## Project — tóm tắt từ catalog

| Mốc DevOps | Nền cần có | Đầu ra khái quát; readiness CAP và bằng chứng ở catalog BL-S04 |
|---|---|---|
| DP0 | Terminal/Git/script | Script dựng/chạy/kiểm tra app mẫu, xử lý lỗi và exit code |
| DP1 | Container/network/config | App/DB container, network/volume/health và đối chứng recreate |
| DP2 | Pipeline/deploy cơ bản | Artifact có version → staging → deploy, kiểm tra và rollback |
| DP3 | Cloud resource/IaC | Môi trường tái lập, state bảo vệ và phát hiện drift |
| DP4 | Observability/recovery | Runbook, alert và diễn tập restore dữ liệu |
| DP5 mở rộng | Orchestration cơ bản | Rollout/scale/failure recovery trên cluster thử nghiệm |

DP khác P0–P7 của Java Backend. Tái dùng deliverable chỉ khi rubric/bằng chứng phù hợp, không tự cộng hoàn thành hai project khi mở hai roadmap. Lab cloud có phương án local hoặc môi trường thử phù hợp; không cần trả phí để đọc/học nội dung.

## Chuyển cấu trúc cũ

- K18 vẫn là Docker/container; K22 vẫn là cloud/DevOps sâu. Không đổi K18 thành toàn bộ DevOps hoặc đổi ID để giả đã có đủ catalog.
- S09/S10 Java Backend vẫn là mốc tích hợp vận hành trong project, liên kết nội dung chung DevOps.
- Dùng lại TOP-CTR/TOP-OPS/TOP-ENV phù hợp. BL-S04 đã sửa prerequisite vận hành dùng chung về nền độc lập ngôn ngữ; riêng project Backend vẫn giữ năng lực tự viết API/quyền.
- Chủ đề chưa phân rã theo TOP/CAP có owner DG-01–08 trong catalog, đặc biệt phần platform/chuyên sâu. Bộ khung DS chưa đồng nghĩa có bài/assessment.
- View `/journey/devops` hiện có là tài sản UI; task triển khai sau mới nối catalog này. Lượt thiết kế không đổi source hoặc tuyên bố UI đã cập nhật.
