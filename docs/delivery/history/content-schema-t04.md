# Schema nội dung — T04

Phiên bản schema hiện tại: `1`. Mục tiêu của lớp dữ liệu là để UI đọc bài học từ catalog, và chặn nội dung hỏng trước khi build. Schema chưa lưu tiến độ; `LessonProgress` mới là hợp đồng cho T09/T18.

## Cấu trúc

- `Lesson`: metadata, trạng thái xuất bản, prerequisite, mục tiêu, tiêu chí bằng chứng, step, exercise, simulation và nguồn.
- `LessonStep`: một hoạt động có instruction và tham chiếu đến objective/exercise/evidence; `nextStepId` tạo luồng tuyến tính của bài.
- `Exercise`: hiện hỗ trợ lựa chọn đơn và ma trận case. Đáp án đúng có feedback cụ thể.
- `SimulationDefinition`: ghi rõ đây là mô phỏng, giới hạn, patch, input case và kết quả mong đợi của từng cặp case × patch.
- `LessonProgress`: version bài, bước hiện tại, input/patch, attempt, hint và evidence. T04 chỉ định nghĩa kiểu; T09 mới triển khai storage.

Nguồn chính nằm ở [types.ts](../../../frontend/src/content/types.ts), fixture bài mẫu ở [apiDebug500.ts](../../../frontend/src/content/lessons/apiDebug500.ts), catalog và lookup ở [catalog.ts](../../../frontend/src/content/catalog.ts).

## Bất biến được validator kiểm tra

- ID lesson, step, exercise, objective, evidence, patch và case không trùng trong phạm vi tương ứng.
- Entry step, next step, objective, exercise, evidence, prerequisite và simulation case phải tồn tại.
- Prerequisite không tạo chu trình.
- Choice phải trỏ đến option có thật; mỗi simulation case phải có kết quả cho mọi patch.
- Trong fixture API 500, response 201 phải ghi đúng một record; 400/500 không được ghi.
- Bài `published` phải có objective, completion evidence và nguồn tham chiếu.

`npm run validate:content` được gọi trong `npm run check`, nên dữ liệu sai chặn bước kiểm tra và build bàn giao. `npm run test:content` dùng dữ liệu cố ý làm hỏng để chứng minh validator bắt được từng nhóm lỗi.

## Bài mẫu API 500

Bài đang ở trạng thái `draft`: 8 bước, 5 mục tiêu, 5 tiêu chí hoàn thành, 6 exercise và ma trận 6 input × 4 patch. Dữ liệu mô tả contract từ đặc tả T01; T07 mới triển khai state machine chạy ma trận. UI chỉ hiển thị đề cương và nhãn đang biên soạn, chưa ghi evidence hoặc tiến độ.
