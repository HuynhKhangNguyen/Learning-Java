# BL-P05 — Biên tập lại tiếng Việt tự nhiên

15/10/2026. Sau khi trải nghiệm BL-P04, user phản hồi bản Tiếng Việt còn cứng và mang cấu trúc dịch từng chữ. Dependency BL-P04 DONE. Goal: biên tập toàn bộ copy Tiếng Việt đang hiển thị trên frontend để người mới đọc tự nhiên, rõ ý và vẫn học đúng thuật ngữ English.

## Phạm vi và acceptance

- Rà shell, Home, hai roadmap, trang Learning/empty/error/404 và hai bài HTTP hiện có; sửa cả heading, mô tả, hướng dẫn, diagram/caption, câu hỏi, lựa chọn và feedback.
- Giữ nguyên các thuật ngữ kỹ thuật cần nhận diện như `request`, `response`, `client`, `server`, `endpoint`, `HTTP method`, `status code`, `header`, `body`, `API`, `database`, `validation`, `authentication`, `authorization`, `idempotency`. Giải nghĩa tự nhiên ở lần xuất hiện cần thiết; không nhét từ English vào động từ hoặc câu nối khi tiếng Việt đã rõ hơn.
- Không đổi nghĩa so với English, không đổi ID, đáp án, rubric, content version, URL hoặc state. Không sửa English chỉ để làm task xanh, trừ lỗi nghĩa được phát hiện và ghi rõ.
- Content validation, type/lint, build và browser regression liên quan đạt; review trực quan desktop/mobile và đọc độc lập bản Tiếng Việt, không chỉ so từng câu với English.

Ngoài scope: dịch curriculum chưa hiển thị, viết thêm bài học, đổi schema/backend/progress, account locale, dependency mới, commit/deploy.

Plan: lập glossary và tiêu chí giọng văn → rà copy theo từng màn → review ngữ nghĩa độc lập → validator/check/build/browser → visual review → checkpoint.

## Checkpoint — 15/10/2026

**Kết quả: DONE.** Toàn bộ copy Tiếng Việt đang hiển thị ở shell, Home, hai roadmap, Learning/empty/error/404, HTTP Fundamentals và Request lifecycle đã được đọc lại như văn bản độc lập. Các câu dịch từng chữ được thay bằng câu chủ động, động từ tiếng Việt và chủ ngữ rõ; thuật ngữ kỹ thuật English vẫn được giữ khi người học cần nhận diện chúng.

- Roadmap không còn các cụm như “chạy command”, “deploy application”, “configuration có thể lặp lại”; mô tả giờ dùng “chạy lệnh”, “triển khai ứng dụng”, “cấu hình có thể tái sử dụng”, đồng thời vẫn giữ `request`, `response`, `transaction`, `image`, `container`, `idempotency` và các term theo từng chủ đề.
- Hai bài HTTP đã biên tập theory, objective, diagram/caption, message annotation, table, ví dụ, self-check, feedback và hướng dẫn tương tác. English, ID, đáp án, rubric, content version, URL và learner state không đổi.
- Authoring standard bổ sung quy tắc: giữ term không đồng nghĩa giữ nguyên cấu trúc câu English; phải đọc lại bản Việt mà không nhìn bản English sau khi đối chiếu semantics.
- Test chỉ được cập nhật ở những expectation khóa nguyên copy cũ; các kiểm tra URL, state, score, đáp án, keyboard, reload và responsive vẫn giữ nguyên.

### Verification

- `./scripts/project.ps1 -Task content`: PASS, 8/8 content checks.
- `./scripts/project.ps1 -Task check`: PASS, locale/content validation, TypeScript và ESLint.
- `./scripts/project.ps1 -Task build`: PASS, 1,979 modules; output `index-skuKtweC.css` và `index-BcvSQ4FL.js`.
- Playwright trên Microsoft Edge: PASS, 16/16 tests trong khoảng 1 phút sau vòng sửa cuối.
- Visual review: PASS ở HTTP Fundamentals và Request lifecycle trên desktop 1440 px, mobile 390/320 px và dark mode; câu dài xuống dòng đúng, không tràn ngang.

Giới hạn: review này chỉ bao phủ copy đang có trên frontend. Curriculum chưa render và nội dung tương lai vẫn phải qua bilingual authoring review riêng; đây không phải bằng chứng toàn bộ curriculum đã được dịch.
