# Backend Lab — bộ tài liệu review

**Tổ chức doc04/10/2026:** Đây là mục lục bộ review BL-D10 ở thời điểm thiết kế. Để làm việc hiện tại, dùng [mục lục theo feature](../features/README.md), [registry](../delivery-backlog.md) và verification/task của feature. Các câu “chưa triển khai/chưa CODE” trong phần lịch sử bên dưới phản ánh thời điểm viết, không thay trạng thái runtime hiện hành.

**Cập nhật microservices 30/09/2026:** HLD/LLD-01–07 đồng bộ MS01–04 ở phần liên quan. [Catalog](../architecture/service-boundaries.md)/[session](../features/f10-identity-access/sessions/protocol.md)/[data](../architecture/data-protocols.md)/[publication](../features/f08-content/publication/protocol.md)/[recovery](../architecture/lifecycle-recovery-protocol.md) giữ contracts; [implementation plan](../delivery/implementation-plan.md)/[test-release](../delivery/test-release-plan.md) giữ task/stack/ops/DoD. [ADR-03](../architecture/microservices-review.md) giữ findings, chưa runtime/CODE; leaf đầu R01a.

30/09/2026 · BL-D10 · Thiết kế R1, **chưa triển khai backend hoặc luồng học mới**. Bộ này trình bày lại các quyết định hiện hành để review; không phải yêu cầu mới hay bằng chứng người dùng đã duyệt.

Đọc **HLD trước** để xem sản phẩm sẽ làm gì, các phần nối nhau thế nào và phạm vi có đúng ý không. Sau đó đọc từng LLD để xem cách một phần hoạt động, dữ liệu được lưu và xử lý lỗi. Không cần đọc toàn bộ lịch sử task.

| Thứ tự | Tài liệu | Câu hỏi cần trả lời |
|---|---|---|
| 1 | [HLD — toàn hệ thống](hld.md) | Sản phẩm, trải nghiệm, kiến trúc và phạm vi R1 có đúng ý không? |
| 2 | [LLD-01 — tài khoản và quyền](../features/f10-identity-access/lld.md) | Ai vào được, phiên hết hạn/đổi tài khoản xử lý thế nào? |
| 3 | [LLD-02 — nội dung và roadmap](../features/f08-content/lld.md) | Một mục học gồm gì, hai roadmap dùng chung ra sao? |
| 4 | [LLD-03 — bài làm và chấm bài](../features/f11-learner-work/lld.md) | Lưu, nộp, chấm và retry có mất/trùng bài không? |
| 5 | [LLD-04 — tiến độ và áp dụng](../features/f05-progress/lld.md) | Khi nào được ghi nhận đã xem, đạt năng lực, đã áp dụng? |
| 6 | [LLD-05 — giao diện học](../features/f02-learning-experience/lld.md) | Người mới đi qua màn nào, tương tác gì, gặp lỗi làm gì? |
| 7 | [LLD-06 — import, reset và phục hồi](../features/f07-data-lifecycle/lld.md) | Dữ liệu sai, reset hoặc khôi phục backup có làm mất/hồi sinh dữ liệu không? |
| 8 | [LLD-07 — nền tảng và vận hành](../platform/lld.md) | Cấu trúc code, môi trường, API chung, triển khai và test thế nào? |

Các sơ đồ là thiết kế. Tên DTO/service là vai trò hoặc hình dạng dự kiến, chưa là file/class đã tồn tại. LLD ở mức đủ review luồng và invariant; DDL, OpenAPI đầy đủ và fixture chạy được thuộc task BL-R tương ứng.

## Cách góp ý

Mỗi tài liệu có ID review ở cuối. Có thể trả lời: **“HLD-02: muốn bổ sung …”**, **“LLD-03-B: đổi hành vi retry thành …”** hoặc **“Đồng ý HLD, cần sửa LLD-05-C …”**. Không cần duyệt từng task kỹ thuật; phản hồi ở đây tập trung quyết định sản phẩm và hành vi hệ thống.

| Ký hiệu | Ý nghĩa |
|---|---|
| Baseline | Quyết định đang dùng để thiết kế, chưa đồng nghĩa đã được triển khai |
| Cần đo/chọn khi làm | Có owner/task cụ thể, chưa khẳng định số đo hoặc provider |
| Sau R1 | Không đưa vào acceptance bản đầu |

## Nguồn và kiểm soát thay đổi

[Requirements](../product-requirements.md) giữ REQ; [business](../business-design.md) giữ mục tiêu; S02/S03/D05 giữ hợp đồng nghiệp vụ; [D08](../delivery/history/implementation-v2.md) giữ quyết định kỹ thuật; [backlog](../delivery-backlog.md) giữ task/trạng thái. Bộ HLD/LLD là lớp trình bày để review các nguồn đó. Khi đổi quyết định, sửa nguồn tương ứng và trang review liên quan trong cùng task, không để hai hướng cùng hiệu lực.

[Review R0](../delivery/history/r0-review.md) liệt kê findings và giới hạn bằng chứng. Chưa có chỉ dẫn chuyển sang code; tạo tài liệu này không mở cổng CODE.

## Goal, plan, context, rule và DoD

- [Hợp đồng theo cấp](../delivery/contract.md): project, release, module, task, mục học, session.
- [Bảng truy vết](../delivery/traceability.md): goal → 18 requirement → module → task → DoD → evidence.
- [Mẫu task và ví dụ BL-R01a](../delivery/task-brief-template.md): toolchain leaf, context/rule/AC/command/handoff; chưa CODE.

## Architecture source code

[Frontend](../architecture/frontend.md) mô tả cấu trúc thực tế sau R00 và feature boundary đích. [Backend](../architecture/backend.md) mô tả package/transaction/test dự kiến; backend runtime chưa được tạo.
