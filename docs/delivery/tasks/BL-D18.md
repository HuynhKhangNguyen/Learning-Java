# BL-D18 — Quy tắc comment giải thích code

08/10/2026. Người dùng yêu cầu thêm rule comment để giải thích code. [Registry](../../delivery-backlog.md) giữ trạng thái.

## Goal và phạm vi

Một đầu ra chính: quy tắc comment giúp người biết lập trình cơ bản, mới học Java/backend đọc hiểu code. Nguồn quy tắc ở AGENTS.md, nối vào review/DoD qua quality process. Dependency: quy trình review hiện có; không phụ thuộc runtime leaf.

Ngoài phạm vi: thêm comment hàng loạt vào source cũ, sửa runtime/migrations, chạy qualification sản phẩm, đổi trạng thái task sản phẩm.

## Plan và acceptance

1. Thêm quy tắc về ngôn ngữ, vị trí, mức giải thích và bảo trì comment.
2. Nối quy tắc vào cổng review hiện có; giữ một nguồn quy định.
3. Kiểm tra nội dung, link và phạm vi file. DoD: cả ba bước đạt; ghi giới hạn và bàn giao.

## Checkpoint

08/10/2026 — Hoàn thành acceptance trong phạm vi tài liệu. AGENTS.md giữ quy tắc canonical; quality-process.md dẫn tới quy tắc trong cổng review/DoD; registry nối task này.

Kiểm tra: PowerShell `Select-String`/`Test-Path` kiểm heading, link review, file task và link registry: 4/4 đạt, exit 0. Đọc lại toàn section để review ngôn ngữ, phạm vi, tính nhất quán và giới hạn qualification. Chỉ sửa AGENTS.md, quality-process.md, registry và file task này; không sửa source/runtime, không chạy app tests vì thay đổi thuần tài liệu.

Bàn giao: áp dụng quy tắc cho các lượt viết/sửa code tiếp theo; chưa bổ sung comment hàng loạt cho code hiện có. Không thay trạng thái hoặc evidence của task sản phẩm.
