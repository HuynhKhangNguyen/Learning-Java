# BL-P04 — Locale English/Tiếng Việt toàn frontend

14/10/2026. User yêu cầu switch English/Tiếng Việt áp dụng toàn hệ thống; mode Tiếng Việt giữ thuật ngữ kỹ thuật English. Dependency P03 DONE. Goal: một locale source ở root cho toàn bộ route/UI hiện có, không làm mất trạng thái học khi đổi ngôn ngữ. REQ-02/10/11/13/19, MD01/03/09.

AC: global switch luôn truy cập được; chỉ `vi|en`, mặc định Việt, persist qua navigation/reload/deep link và fallback Việt khi preference sai; cập nhật `html lang`, document title và accessible labels. Mọi copy user-facing hiện có ở shell, Home, hai roadmap, Learning/empty/error/404 và hai bài HTTP có EN/VI. Mode Việt giữ các term `request`, `response`, `client`, `server`, `endpoint`, `HTTP method`, `status code`, `header`, `body`, `API`, `database`, `validation`, `authentication`, `authorization`, `idempotency`, giải nghĩa khi cần. Đổi locale giữa section/simulation/quiz không reset state/score/URL. Pair/ID/rubric/content version dùng chung; check/content/build/browser/keyboard/mobile/visual và doc links đạt.

Ngoài scope: account/profile preference trên server, backend/API/content publication schema, dịch curriculum chưa có UI, official progress/assessment, auth/DB/microservice, dependency mới, chat mới/commit/deploy. LocalStorage chỉ lưu locale preference, không lưu bài làm.

Plan: brief → root locale/provider/switch → shell/pages/data/lessons → validation/tests/mobile/visual → docs/evidence.

## Checkpoint — 15/10/2026

**Kết quả: DONE.** Frontend hiện có một locale source ở root với hai giá trị hợp lệ `vi|en`; mặc định Tiếng Việt, persist qua reload/navigation/deep link và tự fallback về Tiếng Việt khi preference hỏng. Switch English/Tiếng Việt nằm trong topbar, hỗ trợ keyboard/mobile và không remount route nên state section, simulation, quiz, score và URL đang mở được giữ nguyên.

- Shell, Home, Learning/empty/error/404, hai roadmap và hai bài HTTP đang hiển thị đều có copy EN/VI. Mode Tiếng Việt giữ thuật ngữ English theo authoring standard; topic ID, lesson ID, CAP, rubric, progress và content version vẫn dùng chung.
- `html lang`, meta description, document title, breadcrumb, navigation và accessible label đổi theo locale. Storage event đồng bộ preference giữa các tab cùng origin.
- Locale catalog có validation bắt pair thiếu EN/VI và lesson step thiếu bản dịch; dữ liệu roadmap được kiểm cùng content command.
- Không đổi backend hay business progress. Preference hiện chỉ lưu cục bộ trong browser; đồng bộ theo account vẫn thuộc task sau. Curriculum chưa render chưa được dịch hàng loạt.

### Verification

- `./scripts/project.ps1 -Task content`: PASS, 8/8 content checks.
- `./scripts/project.ps1 -Task check`: PASS, content validation, TypeScript và ESLint.
- `./scripts/project.ps1 -Task build`: PASS, 1,979 modules; output `index-skuKtweC.css` và `index-BfF6FGka.js`.
- Playwright trên Microsoft Edge: PASS, 16/16 tests trong 39.3 giây; gồm chuyển locale ở mọi route, reload/deep link, preference sai, keyboard, 320 px mobile và bảo toàn state/URL.
- Visual review: PASS ở bài HTTP VI/EN và roadmap Java Backend trên viewport mobile; switch không che nội dung và thuật ngữ English còn nguyên trong mode Việt.
- Relative links trong tài liệu thay đổi: PASS.

Review đã phát hiện và sửa vùng click của segmented control từng bị text che hidden radio, một nhãn roadmap cũ chưa theo locale và hai expectation browser còn dùng copy trước migration. Candidate cuối đã chạy lại toàn bộ suite sau các sửa này.
