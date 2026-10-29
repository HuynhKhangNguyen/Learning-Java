# Khối bài học — FE prototype BL-P03

14/10/2026. [Chuẩn song ngữ](bilingual-standard.md) và [template mục học](topic-template.md) vẫn là chuẩn biên soạn. Tài liệu này mô tả renderer đã xây cho bài HTTP, không thay contract Content/Learning hoặc schema publication R1.

## Hình thức nội dung

Source type: `frontend/src/content/lesson-blocks.ts`; bài mẫu: `frontend/src/content/httpLesson.ts`; renderer: `frontend/src/components/LessonReader.tsx`. Lesson có ID/version, prerequisite, objectives, sections và sources; section/block có ID ổn định, không tạo ID theo ngôn ngữ.

| Kind | Dùng khi |
|---|---|
| text | Giải thích khái niệm, cơ chế và bối cảnh |
| callout | Nhấn mạnh giới hạn hoặc hiểu lầm thường gặp |
| diagram | Giao tiếp giữa hai endpoint, hai chiều và caption; chưa hỗ trợ đồ thị tùy ý |
| message | Message/code tĩnh với giải thích từng dòng |
| table | So sánh hoặc phân loại, cuộn ngang trong vùng riêng trên mobile |
| example | Method, request/response, before/after và pitfall |
| check | Một đáp án, feedback riêng từng lựa chọn, cho thử lại |

Mọi nội dung giải thích có Pair EN/VI, gồm captions/options/feedback. Protocol literal và JSON dùng chung. Không nhận HTML/JavaScript tùy ý. `check` có đáp án trong bundle công khai nên chỉ là formative practice, không dùng chấm năng lực chính thức.

## Cách biên soạn và kiểm tra

1. Chốt mục tiêu vừa sức, prerequisite và contract ví dụ thống nhất; không yêu cầu mọi bài phải có đủ bảy loại block.
2. Giải thích từ vấn đề đến cơ chế, minh họa, ví dụ và lỗi thường gặp; thêm câu dự đoán/áp dụng nơi cần kiểm tra hiểu bài.
3. Đính nguồn chính thức cho từng section, ngày review và tăng version khi thay nội dung. Hai bản ngôn ngữ phải tương đương về ý nghĩa.
4. `project.ps1 -Task check` kiểm translations, ID, đáp án, kích thước bảng, diagram và nguồn qua `validateBlockLesson`. Validator dành cho dữ liệu typed trong repo, chưa là bộ kiểm payload API không tin cậy. Reviewer vẫn phải kiểm độ đúng của nội dung và nguồn.
5. Chạy content tests, build và browser theo phạm vi. Kiểm keyboard, 320/390px, ba chế độ ngôn ngữ, feedback và việc đổi section không mất đáp án.

## Trạng thái học và hệ thống

Reader giữ lựa chọn theo block ID trong React state; đổi section/global locale không reset. Locale lấy từ root provider, không có control riêng trong lesson. Rời bài hoặc reload xóa dữ liệu bài làm; locale preference vẫn được giữ. Đếm câu đã nộp và số đúng chỉ để tự kiểm tra; Next, xem sơ đồ và đọc bài không cấp mastery.

HTTP Fundamentals ở `/learn/http-foundations`; bài prototype cũ đi sâu controller/service/database ở `/learn/request-lifecycle`. Các ví dụ không gửi HTTP hoặc chạy backend thật.

## Hướng mở rộng Interactive Workbench

BL-P03 đã có source nội dung HTTP, stable block IDs, bilingual pairs và formative checks; không tạo một lesson source thứ hai chỉ để dựng workbench. [BL-E07a](../../f02-learning-experience/tasks/BL-E07a.md) sẽ chốt descriptor tái sử dụng cho scene/scenario/step/hotspot/inspector và ranh giới giữa content data với MD03 runtime. [BL-E07b](../../f02-learning-experience/tasks/BL-E07b.md) mới được phép mở rộng schema/renderer cho pilot sau khi contract đạt.

Extension phải declarative, versioned, allowlist field/kind, có stable ID và text/static equivalent; không chứa executable HTML/JavaScript, arbitrary URL, private expected/rubric hoặc secret. 2D là baseline; 3D/WebGL không phải default hay dependency. Run/replay/reset chỉ thay simulation state. Viewed mapping vẫn do unit manifest/server owner resolve; Proven và Applied chỉ đến từ official evidence owner.

Không thay topology microservices, auth hoặc database trong P03. Khi mở R05/R07/R16, cần thiết kế mapping block/version vào contract publication đã có, bảo đảm tương thích renderer, validation payload và progress theo owner; không đưa nguyên prototype vào API như một schema đã được phê duyệt. CMS, assets upload, lưu tiến độ và assessment chính thức thuộc task riêng.

## Mapping mục tiêu bài HTTP

| Mục tiêu | Nội dung hỗ trợ | Tự kiểm tra |
|---|---|---|
| Nhận diện request/response | request, response | check-headers, apply-empty |
| Chọn method | methods | apply-filter, apply-patch |
| Hiểu state và idempotency | exchange, methods, before/after | check-stateless, check-idempotent; câu tự giải thích cuối bài |
| Phân biệt thành công và lỗi | status, practice | apply-validation, apply-auth |

Các câu này kiểm một phần mục tiêu, chưa chứng minh người học tự xây API hoặc thành thạo toàn bộ HTTP. [BL-P03](../../f02-learning-experience/tasks/BL-P03.md) giữ acceptance và evidence.
