# BL-P03 — HTTP Fundamentals làm bài chuẩn và các khối nội dung dùng chung

14/10/2026. User yêu cầu thực hiện bước tiếp sau review content/system. Dependency P02 DONE. Goal: bài HTTP nhập môn rõ như reference, song ngữ và renderer tái sử dụng. REQ02/03/04/10/11/12/13, MD01/03/09. Reference Downloads/http-lesson.html chỉ tham khảo cách trình bày; không sửa file ngoài repo.

AC: 7 phần HTTP→exchange→request→response→methods→status→practice; mỗi method có input/output hoặc before/after, pitfalls/idempotency; message có annotation, diagram/caption EN/VI, status table, bài tập có feedback; block schema typed dùng chung/ID ổn định; lý thuyết tách data/renderer; giữ bài lifecycle đã có tại route riêng; language không mất đáp án. Check/lint/build/browser/keyboard/mobile/ảnh thật; nguồn và mapping objective→exercise; links docs và task evidence rõ.

Ngoài scope: backend/API/schema2/publication/official-progress, dịch toàn khóa, CMS/upload assets/runtime HTML tùy ý, runner thật, agent/chat/commit/deploy. Hệ thống giữ service ownership; block schema trong FE prototype chưa là schema Content R05.

Plan: brief → block contract/data/renderer → HTTP content & lifecycle route → validation/browser/visual → DoD/checkpoint. Brief chốt trước implementation; trạng thái hiện hành ở registry.

## Checkpoint 14/10/2026 — kết quả và review

Đạt AC/DoD trong phạm vi FE/content prototype. Bài mới gồm 7 sections, diagram client/server, request/response annotation, bảng path/query/header/body, 5 method examples cùng contract sản phẩm, before/after, safe/idempotent, status classes/codes và 8 câu tự kiểm tra (3 inline + 5 áp dụng). EN/VI/EN+VI dùng cùng block IDs/đáp án/state; đổi section/ngôn ngữ không xóa bài làm, reload xóa RAM. Thời gian học chưa đo. Bài lifecycle cũ giữ mô phỏng201/400/500 và quiz, chuyển sang `/learn/request-lifecycle`; home/journey/link cuối bài dẫn rõ hai bài.

Source: `frontend/src/content/lesson-blocks.ts`, `httpLesson.ts`, `validate-block-lesson.ts`; `frontend/src/components/LessonReader.tsx`, `pages/HttpLessonPage.tsx`, `RequestLifecyclePage.tsx` và CSS. App/routes/shell/home/journey cập nhật entry. Bài cũ `httpFoundations.ts` giữ data song ngữ, Pair dùng chung. Renderer không thực thi HTML/JS và không gửi request. [Contract authoring và objective→exercise mapping](../../f08-content/authoring/lesson-blocks.md) là source hướng dẫn; README/LLD/template liên kết tới đó.

Review nội dung: RFC9110 HTTP semantics, RFC5789 PATCH, RFC7396 JSON Merge Patch và RFC6585 429. Các source RFC chính thức đã đọc; từng section có link nguồn. PUT có thể create/replace; full representation theo API mẫu cần name/price. PATCH dùng merge-patch+json, null là remove và API có thể reject xóa required field; PATCH không mặc định idempotent. Idempotency xét intended effect, response có thể khác. 204/HEAD không có response content; 401/403 khác nhau. HTTP text excerpts lược framing headers và được ghi rõ là minh họa. Hai ngôn ngữ được rà theo nội dung/feedback, không coi test xanh là learner acceptance; user chưa review bản cuối.

Verification trên candidate cuối:

- `scripts/project.ps1 -Task content`: exit0, 7 tests PASS gồm valid/malformed blocks và legacy catalog. Validator được gọi trong `-Task check`, kiểm EN/VI/IDs/answers/table/diagram/source references; không là validator API payload.
- `scripts/project.ps1 -Task check`: exit0, content validation + TypeScript + ESLint. Chạy lại sau sửa test reload và đạt.
- `scripts/project.ps1 -Task build`: exit0; final assets `index-O6YHL_fZ.css` / `index-BS6_l_Vn.js`.
- Edge ngoài sandbox, `PLAYWRIGHT_CHANNEL=msedge`, `node node_modules/@playwright/test/cli.js test`: **14 PASS / 47.4s**. Luồng câu sai→feedback→retry đúng, 5 bài áp dụng, state qua đổi section/language, reload sau 6 câu đã nộp, link lifecycle, pageerror và regressions catalog/journey/shell. Keyboard và mọi section ở EN/VI/both ×320/390/1440, dark/reduced-motion.
- Lượt full đầu13PASS/1FAIL phát hiện grid min-content làm phần request tràn mobile. Sửa `.standard-lesson .lesson-body` với minmax(0,1fr) và child min-width0 để bảng cuộn trong vùng riêng. Rebuild, targeted2PASS/47.7s; full cuối đạt như trên. Không che lỗi bằng overflow hidden toàn trang hoặc bỏ assertion.
- Xem ảnh thật intro desktop, request mobile, methods desktop và dark mobile; không thấy clipping/tràn ngang sau sửa. Screenshots nằm trong `frontend/test-results/http-fundamentals-all-sect-23ee9-ns-keyboard-and-screenshots/` của run cuối, là output test có thể bị thay khi rerun.
- PowerShell kiểm relative links trong 7 tài liệu mới/sửa: PASS. Comment giải thích state/renderer/validator/grid/test reload đúng cơ chế đang có.
- Read-only HTTP local ngoài sandbox: `/learn/http-foundations` trả200, source `/src/content/httpLesson.ts` có tiêu đề bài mới. Sandbox TCP probe trảfalse; thử helper dev ngoài sandbox báo port5173 đã dùng, không tạo server thứ hai. Server hiện có phục vụ đúng candidate source.

Giới hạn: local prototype, chưa lưu progress theo user, chưa Content API/publication/assessment chính thức, chưa dịch toàn khóa hoặc chạy Java/API thật. Quiz đáp án công khai chỉ formative practice. Không đổi backend/service ownership/DB/auth, không commit/deploy hoặc tạo agent/chat. Bước sau khi review bài mẫu: chọn topic tiếp theo, biên soạn bằng cùng chuẩn; tích hợp publication và owner progress theo leaf R1 riêng.
