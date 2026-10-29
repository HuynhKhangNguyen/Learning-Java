# BL-R00 — brief và checkpoint

Trạng thái/dependency chuẩn ở [registry](../../../delivery-backlog.md). File này thuộc backlog, giữ toàn bộ brief/acceptance/checkpoint của task; cập nhật checkpoint mới tại đây.

## Brief thực thi BL-R00 — 30/09/2026

- Chỉ dẫn: người dùng yêu cầu triển khai FE vào frontend/, BE vào backend/, cùng repo và kiến trúc source riêng. Cho phép di chuyển/config/test/docs cho R00; không suy thành yêu cầu thực thi toàn R01–34.
- Goal: FE chạy/test từ frontend qua helper root; backend có nơi riêng và hướng kiến trúc source rõ, chưa có backend source để di chuyển.
- Context: AGENTS, ADR-02/D08, LLD-07, package/config/helper/tests hiện tại, quality-process. Không nâng framework/dependency hoặc tạo service chỉ để lấp thư mục.
- Plan: inventory/hash → kiểm process/path → di chuyển explicit source/config/test và dependency local → sửa cwd/docs → content/check/build/smoke/dev/preview → scope/hash review/checkpoint.
- R00-AC01: FE thuộc frontend/, helper root hoạt động, lockfile/package không đổi dependency; kiểm hash và layout.
- R00-AC02: content/check/build/smoke đạt với Node phù hợp; dev/preview/deep link hoạt động; kiểm UI ảnh/keyboard/responsive từ suite hiện có.
- R00-AC03: kiến trúc FE/BE riêng ghi file hiện có vs dự kiến, module/dependency/source/test boundaries; backend README không giả có runtime.
- R00-AC04: đường dẫn/config/ignore/docs hiện hành đúng; docs lịch sử giữ bằng chứng, thêm chú thích relocation; không commit/deploy. DoD chung DONE-01–05.

### Checkpoint BL-R00 — hoàn thành 30/09/2026

- Quyền: yêu cầu triển khai frontend/backend riêng thư mục và architecture source; chỉ cấu trúc/config/test/docs R00, chưa dựng Java service.
- AC01 đạt: source/public/tests/package/lockfile/config/runtime scripts FE ở frontend; helper root chuyển cwd frontend; npm cache root giữ và frontend/.npmrc trỏ ../.npm-cache. Dependency local và output cũng chuyển theo path đã kiểm trong workspace. Không npm install/nâng dependency; package/lock direct dependency maps khớp. Hash SHA256 hiện tại package.json `1325BF98729E8ED2F5E1132EA6E3E32699E95434171A26877528035DCD6C5AC8`, lockfile `086DCFE75DD85EC84B8CD8479DE3F57F40E38DBB0FB35B1241925F9D279F71D4`. Inventory hash trước di chuyển không còn trong bộ nhớ sau đổi model; không tuyên bố so hash trước/sau độc lập.
- AC02 đạt: helper root/bundled Node24, `-Task content` PASS5/5; `-Task check` PASS runtime/content/TypeScript/ESLint; `-Task build` PASS1968modules/output frontend/dist. Build sandbox EPERM spawn, chạy ngoài sandbox được duyệt rồi PASS. Smoke Chrome `PLAYWRIGHT_CHANNEL=chrome`, `-Task smoke`: PASS9/9 route/reload/Back/keyboard/reduced-motion/320px/themes. Lần duyệt smoke trước lỗi hạn mức reviewer chưa thực thi; lần chạy lại được duyệt và PASS.
- Dev helper tại127.0.0.1:5173, GET /journey/java-backend HTTP200 có dev client/entry. Preview helper tại127.0.0.1:4173, GET /journey/devops HTTP200 có built asset không dev client. Server do phiên tạo đã dừng bằng Ctrl+C; exit1 do chủ động ngắt không là lỗi startup/test.
- Review ảnh journey1440/home390 từ frontend/test-results: bố cục/chữ/nút đúng, không thấy overflow trong ảnh; suite kiểm thêm320px/themes. Không learner test.
- AC03 đạt: [architecture FE](../../../architecture/frontend.md), [architecture BE](../../../architecture/backend.md), [index](../../../architecture/README.md) và README từng phần; trách nhiệm/dependency/state/API/test, source hiện có khác cây đích. Backend chỉ README, chưa pom/wrapper/Java source.
- AC04 đạt: root README/system/LLD/module map/D08/schema source links đồng bộ; AGENTS ghi quyền R00, ignore artifacts đúng. Kiểm291 link local trong48 tài liệu PASS trước checkpoint. Source/package FE không còn ở root. Repo untracked từ trước nên git diff không chứng minh baseline; scope review từ inventory/path/current files. Không commit/deploy.
- Bàn giao R01: dependency R00 DONE, runtime/nghiệp vụ backend vẫn PLANNED tới khi được yêu cầu. Không tự thực thi backlog lịch sử.

