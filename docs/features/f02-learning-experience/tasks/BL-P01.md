# BL-P01 — Bài học mẫu HTTP cho người học trải nghiệm

14/10/2026. User cho phép triển khai bài mẫu trước khi tiếp tục R1, lý thuyết đầy đủ kèm hình minh họa. Goal: một bài học chạy trong FE từ lý thuyết đến mô phỏng, kiểm tra và feedback. REQ02/03/04/11/12/13; MD03/09, F02. Dependency: FE hiện có; đây là prototype độc lập, không đóng các leaf R1.

AC: bài HTTP có mục tiêu/prerequisite/lý thuyết/diagram, mô phỏng step/reset với input hợp lệ và lỗi, kiểm tra có lời giải và retry, kết quả chỉ sau nộp; keyboard/mobile và kiểm tra FE. Không backend/login/server progress, không giả JVM/network thật. Không persistence để tránh nhầm owner; rời/reload bài sẽ bắt đầu lại.

Plan: nội dung và SVG → player → điểm vào → check/build/browser → review/checkpoint. Brief chốt trước source edits.

## Checkpoint 14/10/2026

DONE trong phạm vi prototype: `/learn/http-foundations`, entry trang chủ/roadmap, 3 phần lý thuyết + SVG có text thay thế, 3 tình huống step/reset, 5 câu tự kiểm tra có explanation/retry và kết quả RAM sau submit. Source `frontend/src/pages/HttpLessonPage.tsx`, CSS cạnh page, App/Home/Journey/AppShell; browser test `frontend/tests/http-lesson.spec.ts`. F02 README nối prototype; không đổi runtime/backend hoặc state R1.

Verification: root `scripts/project.ps1 -Task check` exit0 (schema legacy, TypeScript, ESLint); `-Task build` exit0 trên source cuối; browser Edge qua pinned Node `node node_modules/@playwright/test/cli.js test` toàn 11 cases PASS/32s trên final build. Test kiểm 201/400/500/no premature result/5-of-5 và sai4câu/retry/reload; keyboard, responsive320/390/1440, dark/reduced-motion, existing content/routes/Back. Đã xem screenshot1440/390; diagram mobile scroll có focus và lời mô tả đầy đủ. Screenshots trong frontend/test-results (generated/ignored); không learner acceptance thực trước khi user thử.

Giữ thất bại: browser restricted sandbox treo startup, đã dừng rồi chạy Edge ngoài sandbox cùng user; không claim fix sandbox. Regression full lần đầu9pass/2fail do link Xem phòng lab bị thay; khôi phục link cạnh CTA bài mẫu, rebuild và full11pass. Không sửa test để bỏ assertion cũ.

Nguồn tham khảo đã mở14/10: RFC9110 HTTP Semantics và Java21 String API, link cuối bài. Lý thuyết là bài nhập môn có phạm vi rõ, không toàn bộ HTTP/Java. Không Java/DB/network thật, server progress, login hoặc official mastery. Rời/reload mất kết quả được giải thích trước học. Dev server local5173 đã mở để user trải nghiệm; bước tiếp nhận feedback bài mẫu trước mở rộng nội dung/R1, không tự tạo chat/agent/commit/deploy.
