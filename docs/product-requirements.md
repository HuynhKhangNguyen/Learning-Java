# Yêu cầu sản phẩm và tiêu chí truy vết

> Kiến trúc 30/09/2026: microservices R1/admin sau R1, MS01 [ownership](architecture/service-boundaries.md), MS02 [security](architecture/security-design.md)/[session](features/f10-identity-access/sessions/protocol.md), MS03 [data](architecture/data-protocols.md)/[publication](features/f08-content/publication/protocol.md)/[recovery](architecture/lifecycle-recovery-protocol.md), MS04 [implementation](delivery/implementation-plan.md)/[test-release](delivery/test-release-plan.md). Preparing/disclosure journal/terminal suppression/credential recovery giữ REQ-05/09/15/16, không REQ/feature mới. Runtime evidence thuộc leaves, chưa CODE.

Cập nhật 26/09/2026. Đây là bản hệ thống hóa business đã thống nhất, không phải tuyên bố các tính năng đã triển khai. [Business design](business-design.md) giữ mục tiêu/định nghĩa; tài liệu này giữ ID để tham chiếu trong task. Quy tắc business về rubric/evidence/progress đã chốt ở D05; case Collections, UX và storage đã được cụ thể hóa ở D06–D08; kết quả review R0 nằm ở [r0-review](delivery/history/r0-review.md).

## Phạm vi

Điều chỉnh 26/09/2026: nền tảng tự học nhiều người dùng với backend và tài khoản login/logout ngay trong R1, desktop chính, mobile đọc/ôn được. Hai roadmap Java Backend và DevOps; Docker là một chặng DevOps, dùng chung nội dung cần thiết với Backend. Bài tập, nháp, bài nộp và tiến độ lưu theo tài khoản trên server. Admin được người dùng chọn để giai đoạn sau. Community, leaderboard, thanh toán và AI tutor chưa thuộc R1. Xem [system v2](system-overview.md); đây là thay đổi thiết kế, chưa cho phép chuyển sang code.

## Yêu cầu có ID

| ID | Hành vi / bất biến cần đạt | Dấu hiệu nghiệm thu | Phạm vi |
|---|---|---|---|
| REQ-01 | Xem hai roadmap Java Backend và DevOps, phân biệt lõi/mở rộng | Backend giữ K01–K23/S00–S10; DevOps có đường vào riêng, Docker là một chặng; TOP/CAP dùng chung không bị sao chép | R1 có toàn cảnh, chưa đủ bài |
| REQ-02 | Mục học có năng lực, prerequisite và ôn đủ dùng | Người mới biết cần gì trước Collections; tự khai không cấp mastery; được khám phá trước | R1 |
| REQ-03 | Học cơ chế qua trực quan phù hợp | Đổi input tạo kết quả đúng mô hình; pause/step/reset khi có diễn tiến; text thay thế và giới hạn rõ | R1 mẫu Collections |
| REQ-04 | Luyện tập có feedback và hỗ trợ | Retry/hint/lời giải; lưu mức hỗ trợ; bài kinh điển chỉ có khi phù hợp | R1 |
| REQ-05 | Kiểm tra dùng tình huống mới và rubric theo năng lực | Feedback nêu tiêu chí đạt/chưa đạt; không cấp pass từ xem đáp án/Next | R1 |
| REQ-06 | Tiến độ tách đã xem, năng lực và áp dụng | Evidence/attempt/version riêng; bài dùng chung không tính hai lần; “cần ôn” không xóa lịch sử | R1 |
| REQ-07 | Tiếp tục học và gợi ý đúng | Reload về phiên dở; gợi ý nội dung published có đường vào; thay đổi version không cấp lại tiến độ sai | R1 |
| REQ-08 | Áp dụng khi đủ nền tảng | P0 bài nhỏ, P1 sau Collections, P2–P7 theo readiness; mở mốc khác hoàn thành mốc | R1 áp dụng nhỏ; R2 đủ P1–P7 |
| REQ-09 | Dữ liệu theo tài khoản bền vững trên server và phục hồi được | Mất mạng/lỗi DB hiện chưa lưu; retry không trùng; conflict không âm thầm ghi đè; import sai/giả nguồn không cấp pass hoặc phá dữ liệu tốt | R1 |
| REQ-10 | Xuất bản nội dung có kiểm soát, chất lượng nội dung và song ngữ EN–VI | ID/reference/prerequisite/version hợp lệ; nguồn và ngày review thực; theory/examples/diagram/assessment/feedback đủ và EN/VI tương đương theo [chuẩn authoring](features/f08-content/authoring/bilingual-standard.md); published có năng lực/assessment cần thiết | R1 trở đi; prototype không thay publication gate |
| REQ-11 | Dùng được bằng keyboard và trên màn nhỏ | Focus/label rõ, không tràn ở 320px, không cần màu/motion để hiểu, reduced-motion có hiệu lực | Mọi UI task |
| REQ-12 | Bằng chứng đúng loại | Phân biệt mô phỏng, tự khai và thực thi được xác minh; không chạy lệnh máy người học từ nội dung tùy ý | R1; runner ở R3 |
| REQ-13 | Review chất lượng có bằng chứng | Test phù hợp, ghi giới hạn, feedback learner chỉ ghi khi thử thật | Mọi task |
| REQ-14 | Nhiều user login/logout và quản lý phiên | Có cách cấp tài khoản; logout thu hồi phiên server; hết hạn/đổi tài khoản không lộ cache hay gửi nháp sai chủ | R1 |
| REQ-15 | Cô lập dữ liệu người học bằng quyền server | User B không đọc/sửa/nộp/export dữ liệu user A bằng đổi ID; server không tin owner/role do client tự khai | R1 |
| REQ-16 | Lưu đề, nháp, bài nộp và lịch sử | Đề/rubric có version; nháp có revision; submission gắn user/version, lịch sử bất biến; backend chấm/xác minh kết quả | R1 |
| REQ-17 | Admin theo dõi học viên và bài nộp | Quyền riêng, nhận xét có actor; đánh giá lại có lý do/lịch sử, không sửa mất evidence | Sau R1, phạm vi chi tiết còn đề xuất |
| REQ-18 | DevOps có mục tiêu và điều kiện học độc lập | Có Linux/network/Git/automation/container/CI/CD/cloud/IaC/observability/recovery/security; không cần học hết Java; dùng chung progress theo user + TOP/CAP | Khung R0/R1; bài chi tiết R2 |
| REQ-19 | Chuyển English/Tiếng Việt toàn trải nghiệm | Một preference `vi|en` áp dụng shell, route, lesson, trạng thái và accessibility copy; giữ qua navigation/reload, không reset bài làm/progress; mode Việt giữ thuật ngữ kỹ thuật English theo [chuẩn authoring](features/f08-content/authoring/bilingual-standard.md) | Prototype local ở BL-P04; account sync nếu có thuộc task profile sau |

## Quy tắc chung

- Phần trăm độ bao phủ nội dung của app khác tiến độ học. Draft/planned không vào mẫu số tiến độ nội dung hiện có.
- Sửa/reset mô phỏng không xóa dữ liệu học. Reset dữ liệu học phải là hành động riêng rõ phạm vi và có xác nhận khi triển khai.
- Không đưa số liệu hiệu năng/khả dụng tự đặt thành SLA. Đo baseline rồi mới chốt ngưỡng theo mục đích ở task UX/kỹ thuật.
- Review kỹ thuật nội dung dùng nguồn chính thức tại phiên bản chọn cho bài; không coi nguồn của bộ khung là xác minh mọi ví dụ chi tiết.

## Các phần còn cần đặc tả, không tự suy thành requirement đã chốt

1. [Bộ bài Collections](features/f04-assessment/collections/cases.md) đã có ở BL-D06b: 5 bài luyện, 8 case kiểm tra/bảy CAP và bài áp dụng mô hình; D06 hoàn thành thiết kế trên giấy. [Trải nghiệm D07](features/f02-learning-experience/collections/experience.md) đã có đặc tả/prototype được kiểm; [D08](delivery/history/implementation-v2.md) đã chốt schema/API và BL-R01–34; triển khai/chấm/kiểm thử sản phẩm còn ở R1, nội dung chưa published.
2. Contract tài khoản đã có ở [identity-access-design](features/f10-identity-access/business.md): pilot dùng lời mời, session server, admin sau R1. [Contract dữ liệu server](features/f11-learner-work/data-contract.md) chốt bài nộp/progress/conflict/import/legacy ở BL-S03; cấu hình/schema/API/stack cụ thể đã chốt ở BL-D08, chưa triển khai.
3. [Catalog DevOps BL-S04](curriculum/devops-catalog.md) đã nối 12 chặng, prerequisite và DP0–DP5; gap DG-01–08 có owner. Lesson/lab/assessment chi tiết ở BL-E05 sau R1, schema/render catalog ở D08; [roadmap DevOps](curriculum/devops-learning-path.md) giữ toàn cảnh.
4. Admin chi tiết sau R1; runner, hosting/provider và công cụ lab chọn trong task tương ứng. Backend học tập đã thuộc R1, không tiếp tục coi là tùy chọn tương lai.
