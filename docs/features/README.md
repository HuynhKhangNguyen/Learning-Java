# Tài liệu theo feature

[Toàn bộ tài liệu](../README.md) · [Registry task](../delivery-backlog.md)

Một feature là một giá trị người học nhận được, không mặc định là một service. Mỗi README chỉ dẫn tới source business/LLD/task/evidence; không sao chép state/hash/checkpoint từ nơi khác. Feature lớn có thư mục con khi đã có contract/task thật.

| Nhóm lớn | Feature nhỏ và điểm vào |
|---|---|
| Nội dung và đường học | [F01 · Khám phá và chọn đường học](f01-journey/README.md) · [F08 · Nội dung, version và xuất bản](f08-content/README.md) |
| Trải nghiệm học | [F02 · Học qua nội dung và tương tác](f02-learning-experience/README.md) · [F03 · Luyện tập và feedback](f03-practice/README.md) |
| Đánh giá và bài làm | [F04 · Kiểm tra và bằng chứng năng lực](f04-assessment/README.md) · [F11 · Nháp, nộp bài và lịch sử](f11-learner-work/README.md) |
| Tiến độ và áp dụng | [F05 · Tiến độ, tiếp tục và ôn tập](f05-progress/README.md) · [F06 · Áp dụng vào project](f06-projects/README.md) |
| Tài khoản và dữ liệu | [F10 · Tài khoản, phiên và quyền](f10-identity-access/README.md) · [F07 · Giữ, chuyển và phục hồi dữ liệu học](f07-data-lifecycle/README.md) |
| Phần mở rộng | [F09 · Lab thật và output xác minh được](f09-labs/README.md) · [F12 · Admin theo dõi và nhận xét](f12-administration/README.md) |

## Tách feature nhỏ tới mức nào?

Tách khi có mục tiêu/contract, dữ liệu hoặc task kiểm chứng độc lập: F10 có credentials và sessions; F08 có authoring và publication; Collections có nội dung trực quan ở F02 và case/rubric ở F04. Cùng nội dung dùng một source rồi link, không nhân bản vì nhiều feature cùng dùng.

Một task leaf có một brief/checkpoint, nằm cạnh phần sở hữu của feature. Parent là rollup, không giao cả parent/module cho một phiên. Task chưa làm nằm trong registry; không tạo hàng loạt file trống. Foundation/DB/trust/runtime/ops ở [platform](../platform/README.md); contracts xuyên feature ở [architecture](../architecture/README.md).
