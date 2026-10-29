# Review tổng hợp R0

> Kết luận R0 dưới đây là bằng chứng lịch sử trước yêu cầu microservices30/09/2026. Readiness cho một backend đã bị thay; [review ADR-03](../../architecture/microservices-review.md) có findings phân tán cần MS01–04 xử lý trước triển khai.

MS01 và MS02 hiện đã DONE ở mức thiết kế; [service catalog](../../architecture/service-boundaries.md), [security design](../../architecture/security-design.md) và [session protocol](../../features/f10-identity-access/sessions/protocol.md) là nguồn hiện hành. MS03/04 vẫn là điều kiện trước rebaseline/công việc runtime.

BL-D09 · 30/09/2026 · review thiết kế, không phải nghiệm thu runtime. Nguồn điều phối: [backlog](../../delivery-backlog.md). Kết luận: đủ đầu vào thiết kế để bắt đầu BL-R01 khi người dùng cho phép code; chưa có chỉ dẫn đó. Không có quyết định business còn chặn lát cắt đầu sau các sửa đổi dưới đây.

## 1. Sản phẩm sẽ bàn giao trong R1

Người học có tài khoản riêng, khám phá hai roadmap Java Backend/DevOps, học mẫu Collections bằng kiến thức và mô hình tương tác, luyện, kiểm tra và áp dụng nhỏ. Nháp/bài nộp/lịch sử/tiến độ do backend lưu theo owner. Đã xem, đã chứng minh năng lực và đã áp dụng là ba chiều riêng.

Khung có 95 TOP/190 CAP; đó là phạm vi catalog, không phải 95 bài đã hoàn thiện. Collections có bảy CAP mục tiêu, năm bài luyện, tám case kiểm tra và một bài áp dụng mô hình. Các mục khác vẫn planned; DevOps có đường vào riêng và Docker thuộc DevOps. Mục dùng chung chỉ có một nguồn nội dung/progress. Admin sau R1; runner Java/Docker thật, AI tutor, community và thanh toán chưa thuộc R1.

## 2. Truy vết thiết kế đến triển khai

| Yêu cầu | Hợp đồng thiết kế | Task nghiệm thu runtime |
|---|---|---|
| REQ-01/02/10/18 | Catalog nền tảng/API/security/DevOps, template, D08 manifest | R05–07/14/15/30: ID, prerequisite, planned/published, shared, đường ôn |
| REQ-03/04/12 | Collections spec/assessment và D07 | R16–19/23: mô hình/feedback đúng, không giả thực thi Java |
| REQ-05/06/16 | D05 rubric, D06 case, S03 T1/T2 | R08–13/20/21: owner, snapshot, kết quả theo CAP, retry/exposure |
| REQ-07/08 | D05, D07, readiness catalog | R21–23: resume/ôn, CL-U-01 khác P1; không cấp milestone từ readiness |
| REQ-09/14/15 | S02/S03, D08 schema/guards/recovery | R02–04/09/11/13/24–29/32: A/B, revoke, conflict, reset/restore |
| REQ-11/13 | D07 và quality process | Mỗi task UI + R30–34: keyboard/mobile, test/review và learner test thật |
| REQ-17 | System v2; quyết định admin sau R1 | S05/E06 sau R1, không chặn R01 |

Các ID Rxx trong bảng là BL-Rxx. [D08](implementation-v2.md) nối các nhóm trên với module và case IA/LD/AP/CA/UX; [backlog](../../delivery-backlog.md) giữ dependency/acceptance cụ thể. Không tạo backlog thực thi thứ hai trong báo cáo này.

## 3. Walk-through review bằng tài liệu

| Tình huống | Kết quả thiết kế phải có | Kết quả review |
|---|---|---|
| Người mới chưa đủ generic/equality | Vào trang mục, ôn R0–R3 tại chỗ, quay lại; không buộc hoàn thành toàn OOP | Có trong D06/D07, kiểm runtime R15 |
| Học C1–C6, thử/reset mô hình | Xem kiến thức và state chữ; reset mô hình không xóa bài nộp | Có; R16/17 kiểm thực tế |
| Làm luyện, dùng hint rồi nộp kiểm tra | Exposure server ghi trước trả hỗ trợ; kết quả có hỗ trợ không là pass độc lập | Có D05/S03/D06; R10/13/19 |
| CAP-A đạt nhưng CAP-B thiếu | Hiện riêng từng CAP; COL-006-B chưa đánh giá, không ghi cả Collections đã thành thạo | Có; R20/22 |
| Fail cả hai case trong một nhóm | Luyện tiếp, báo chưa có tình huống mới để xác minh; không shuffle/reset exposure để cấp lại | Có; ngân hàng thêm do E01, không hứa vô hạn |
| Áp dụng CL-U-01 | Rubric U1–U5; ghi applied trong mô hình, chưa P1 hoặc Java thật | Có; R23, P1 còn prerequisite ngoài mẫu |
| Mở Docker từ hai roadmap | Cùng TOP/CAP và tiến độ owner; người DevOps không phải học Java trước | Có S04; R05/14/22 |
| A hết phiên, B đăng nhập khi response A về trễ | Bỏ response/cache A, không gửi nháp A cho B; RAM chưa lưu được xử lý rõ | Có S02/D07/D08; R04/24 |
| Hai tab sửa/nộp, timeout hoặc worker chết | CAS, receipt, T1/T2 atomic và lease/fencing; chưa xác nhận khác fail | Có; R09/11/13/20 |
| Reset trong lúc chấm; restore snapshot cũ | Journal ngoài snapshot, pending trước commit, epoch chặn hồi sinh, restore fail closed nếu thiếu journal | Có S03/D08; R28/29/32 |
| Import file sai hoặc giả pass | Preview/validate, merge atomic, unverified; không ghi đè dữ liệu tốt | Có S03; R26/27 |

Đây là kiểm tra chuỗi quyết định trên tài liệu, không phải người học đã đi qua app hay race test đã chạy.

## 4. Findings và cách xử lý

| ID / mức | Finding | Xử lý / trạng thái |
|---|---|---|
| RV-01 · P1 | Gói Collections nhiều TOP nhưng route learn có một TOP; thiếu mapping canonical có thể nhân viewed/progress | Đã bổ sung package ID chỉ điều hướng, unit→TOP/CAP/canonicalTopicId và server resolve viewed trong D08; R05/06/07/15/16 kiểm |
| RV-02 · P2 | System/module còn mô tả stack chưa chọn dù D08 đã chốt | Đã sửa con trỏ sang baseline D08; vẫn ghi chưa cài/triển khai |
| RV-03 · P2 | Template chỉ dẫn ba catalog cũ, bỏ các TOP DevOps mới | Đã thêm catalog DevOps vào nguồn canonical; không đổi ID |
| RV-04 · P2 | D09 hoàn tất có thể bị hiểu là đã được phép code | Backlog giữ BL-R PLANNED cho đến chỉ dẫn CODE; D09 DONE chỉ là review thiết kế |

Không còn P0/P1 thiết kế mở trong phạm vi review này. RV-01 chưa có test runtime; việc sửa contract không thay nghiệm thu triển khai. Không thực hiện lại review kỹ thuật từng ví dụ Java đã ghi ở D06, không tuyên bố nguồn bên ngoài được kiểm lại trong D09.

## 5. Đã có và chưa được kiểm chứng

Đã có: shell React/schema1/Journey tĩnh từ T01–T05; API500 draft. Tài liệu S01–S04/D01–D08 có catalog, policy, mẫu nội dung, prototype D07 và thiết kế R1. Bằng chứng D07 là prototype độc lập, không chuyển thành bằng chứng backend.

Chưa có: backend sản phẩm, login/logout thật, migrations/DB, autosave/submission/grader/progress runtime, restore drill hay learner test R1. Chưa đo KDF/RPO/RTO/capacity; chưa pin digest triển khai, chọn corpus password hoặc host/domain/storage production. Các đầu vào này có owner R01/03/31/32; không chặn việc bắt đầu nền backend trong môi trường lab.

Giữ frontend/helper/lockfile, thích nghi routes/IDs; giữ API500 legacy draft, không suy mastery từ v1. Backend một process chia module, PostgreSQL và worker DB; không microservices hoặc runner. Lát cắt đầu theo D08 là login → mở assessment → lưu nháp → submit → result; chưa thay thế các task player/practice/áp dụng/recovery còn lại.

## 6. Cổng tiếp theo và session

R0 đủ để trình người dùng xem. Sau chỉ dẫn rõ cho phép triển khai code, ghi chỉ dẫn vào backlog và chuyển BL-R01 READY rồi thực hiện. Một session theo một BL-R với acceptance/test/checkpoint; task lớn có thể tách hậu tố con. Không tạo session tự động. Session mới mang task ID, dependency đã đạt, tài liệu nguồn, giới hạn và kết quả kiểm tra, không cần sao chép toàn lịch sử.

Kiểm tra tự động của D09 và checkpoint nằm dưới BL-D09 trong backlog. Tài liệu thuần không cần chạy build/E2E app không đổi; tất cả runtime evidence phía trên vẫn là việc phải làm.
