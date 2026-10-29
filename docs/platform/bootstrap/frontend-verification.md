# T03 — Xác minh khởi tạo dự án

Ngày hoàn thành: 20/09/2026 (Asia/Bangkok).

## Đầu ra

- React/TypeScript/Vite app tại src/, tách biệt với bản thiết kế HTML ở docs/.
- Route tree, navigation, breadcrumb, focus khi chuyển trang, trang phục hồi URL sai.
- Design tokens sáng/tối theo thiết bị, trang chủ nền theo T02 và bố cục responsive.
- Journey, Lab, Challenge và Result có trạng thái đang chuẩn bị/chưa có dữ liệu; chưa giả lập hoàn thành bài.
- Lệnh dev/check/build/preview/test:smoke, package-lock.json, runtime guard và helper PowerShell.
- README hướng dẫn cài từ checkout mới, chọn Node, chạy app và kiểm tra browser.

## Môi trường đã kiểm tra

| Thành phần | Phiên bản |
|---|---|
| Node dùng để build/test | 24.19.0 |
| npm | 8.5.5 |
| React / React DOM | 19.3.0 |
| React Router | 8.4.0 |
| TypeScript | 6.0.3 |
| Vite | 8.3.0 |
| ESLint | 10.11.0 |
| Playwright | 1.63.0 |
| Browser | Google Chrome được cài trên máy Windows |

Máy mặc định có Node 16.15.0. Runtime guard đã từ chối Node 16 với thông báo rõ. Helper tự chọn Node 24 có sẵn, chỉ thay PATH trong quá trình chạy rồi khôi phục, không cài hoặc đổi Node toàn hệ thống.

Engines của project là `^22.22.0 || >=24.0.0` để khớp mức tối thiểu của React Router hiện tại; chỉ Node 24.19.0 được chạy kiểm chứng trong task này. Không tuyên bố đã thử mọi phiên bản được khai báo hỗ trợ.

## Kết quả kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Cài dependency và pin version trực tiếp | Đạt, lockfile được tạo |
| Cài lại bằng npm ci --offline từ cache đã tải | Đạt: 167 package được cài lại |
| Kiểm tra runtime / TypeScript strict / ESLint | Đạt, không lỗi hoặc warning lint |
| Build production | Đạt |
| Main routes + reload trực tiếp | Đạt, không pageerror hoặc console.error trong các route kiểm tra |
| Điều hướng và browser Back | Đạt, active navigation đúng |
| Resource không tồn tại và step sai | Đạt, có đường phục hồi |
| Keyboard Enter + reduced motion | Đạt |
| Home responsive ở 1440/1280/390/320px | Đạt, không tràn ngang |
| Review ảnh Home desktop/mobile/dark | Đã xem; không thấy clipping hoặc che mất hành động chính |
| Dev server | Khởi động thành công tại http://127.0.0.1:5173/ |

Smoke suite: 5/5 tests pass trong 16.3 giây. Test tự chạy preview build tại port 4174 rồi dừng. Ảnh và trace phát sinh ở test-results/, được gitignore vì có thể tái tạo bằng lệnh test.

Build output đã ghi nhận: CSS 8.90 kB (gzip 2.38 kB), JS 273.64 kB (gzip 87.37 kB). Đây là kích thước bundle của app shell hiện tại, không phải phép đo hiệu năng người dùng hoặc mục tiêu cho bản hoàn chỉnh.

## Các lỗi môi trường đã xử lý

- Sandbox không truy cập được npm registry: đã tải package bằng lệnh được cho phép ngoài sandbox; không đổi registry hoặc proxy của người dùng.
- Vite và Chrome cần tạo process con trên Windows, bị sandbox chặn với EPERM: đã chạy build/browser check ngoài sandbox theo cơ chế quyền của môi trường.
- ESLint 9 báo hết hỗ trợ: chuyển sang ESLint 10 sau khi xác minh peer dependency của plugin, sau đó chạy lại check.
- Tối thiểu Node ban đầu chưa khớp React Router: đồng bộ package.json, lockfile, runtime guard, helper và README về 22.22/24.
- Playwright in cảnh báo NO_COLOR/FORCE_COLOR của môi trường terminal; không phải lỗi console của app và không ảnh hưởng kết quả test.

## Giới hạn và bàn giao

- Không có lesson schema/fixture, mô phỏng, chấm điểm, lưu progress, API server, Java hoặc Docker runtime trong T03.
- Trang Home là nền tảng để T05 nối dữ liệu; Journey hiện là khung chưa có chapter catalog.
- Không xem đây là nghiệm thu toàn bộ M1 hoặc kiểm chứng mức hứng thú người học. T10 vẫn cần thử luồng học thật.
- Chưa publish website hoặc tạo remote/commit. Lockfile và mã nguồn sẵn sàng để quản lý bằng Git.
- Bước tiếp: T04 tạo schema Lesson/Step/Exercise/Progress và dữ liệu bài API 500; giữ các quyết định trong product-spec.md.
