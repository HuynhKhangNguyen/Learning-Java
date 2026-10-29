# Content-first và song ngữ English–Vietnamese

Quyết định người dùng 14/10/2026: giá trị cốt lõi là chất lượng nội dung học. Giao diện và hạ tầng phục vụ khả năng hiểu, luyện tập và áp dụng kiến thức. Chuẩn này áp dụng bài mới và bài được biên soạn lại; chưa tuyên bố toàn bộ curriculum đã được dịch.

## Cấu trúc nội dung

Mỗi bài có audience/prerequisite, mục tiêu, phạm vi, lý thuyết đủ để giải quyết bài tập, ví dụ có giải thích, sơ đồ đúng cơ chế, hiểu nhầm thường gặp, thực hành khi phù hợp, kiểm tra và giải thích đáp án, nguồn/version/ngày review. Không rút gọn lý thuyết chỉ để giảm chữ; chia thành khối dễ đọc và dùng hình để giải thích quan hệ. Quiz phải kiểm mục tiêu đã dạy, không kiểm khả năng đoán nghĩa từ tiếng Anh.

## Song ngữ

- English dùng thuật ngữ chuẩn theo primary sources; tiếng Việt giải thích tự nhiên, không dịch máy từng chữ. Bản Anh/Việt phải tương đương về ý, điều kiện biên, caveat và đáp án.
- Frontend có một switch toàn hệ thống giữa `Tiếng Việt` và `English`; mặc định prototype là Tiếng Việt. Preference áp dụng cả shell, roadmap, trạng thái, accessibility copy và bài học, được giữ khi chuyển route/reload. Không tạo switch riêng trong từng lesson hoặc mode EN+VI cho sản phẩm hiện hành. Đổi language giữ nguyên URL, câu trả lời, mô phỏng và kết quả trong phiên; ngôn ngữ không tạo topic/CAP/progress mới.
- Mode Tiếng Việt giữ tên API/class/field/method/status/code identifier và thuật ngữ kỹ thuật English. Ví dụ `request` (yêu cầu), `response` (phản hồi), `client`, `server`, `endpoint`, `HTTP method`, `header`, `body`, `status code`, `database`, `validation` (kiểm tra tính hợp lệ), `authentication` (xác thực), `authorization` (phân quyền), `idempotency` (lặp cùng thao tác có cùng hiệu ứng dự kiến như một lần). Giải nghĩa ngắn khi term xuất hiện lần đầu nếu người mới có thể chưa biết; không dịch term sang nhiều từ Việt khác nhau. Không đánh đồng authentication/authorization hoặc idempotency/deduplication.
- Giữ thuật ngữ không có nghĩa là giữ nguyên cả câu English. Dùng động từ và từ nối tiếng Việt tự nhiên: “chạy lệnh”, “cấu hình môi trường”, “triển khai ứng dụng”, “kiểm thử hành vi”; tránh kiểu “chạy command”, “deploy application”, “configuration có thể lặp lại”. Khi cần giữ một term ít quen thuộc, viết `term` kèm nghĩa Việt ở lần đầu rồi dùng nhất quán, ví dụ `representation` (dữ liệu biểu diễn tài nguyên).
- Biên tập bản Việt như một văn bản độc lập: ưu tiên câu chủ động, chủ ngữ rõ, mỗi câu một ý chính và cách xưng hô nhất quán là “bạn”. Không sao chép trật tự từ hoặc danh từ hóa của câu English nếu cách đó khiến tiếng Việt cứng. Sau khi đối chiếu semantics, đọc lại bản Việt mà không nhìn bản English để kiểm nhịp câu.
- Dịch cả mục tiêu, lý thuyết, caption/alt-text, đề, lựa chọn, hint/feedback/lời giải và hướng dẫn tương tác; không chỉ dịch heading. Code sample giữ nguyên hành vi và có giải thích song ngữ sát code. Comment source do developer viết vẫn theo AGENTS tiếng Việt.
- Mỗi cặp nội dung có key ổn định và một đáp án/rubric chung; không fork bài thành hai bản tiến độ. Missing translation phải được ghi rõ trước publish, không fallback âm thầm như đã dịch.

## Review và DoD nội dung

Review: độ đúng theo primary sources, đủ lý thuyết so với mục tiêu, glossary nhất quán, EN/VI giữ cùng semantics, diagram/case/feedback khớp nhau, ví dụ phản chứng và giới hạn mô phỏng rõ. Đọc từng bản độc lập; kiểm global switch trên route/deep link/reload, `html lang`, accessible labels và không reset bài/score. Có text thay hình, dùng keyboard/mobile. Không lấy số lượng bài hoặc số chữ làm thước đo chất lượng.

Bản mẫu FE là thử nghiệm trình bày; server content schema/localization/publication vẫn phải được thiết kế và qualified trong task owner tương ứng, không tự thay contract backend từ prototype.
