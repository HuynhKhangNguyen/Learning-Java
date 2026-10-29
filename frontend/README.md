# Frontend — Backend Lab

React/TypeScript/Vite, đã chuyển từ root vào thư mục này trong BL-R00. Source, config, tests và package/lockfile thuộc frontend; không nâng dependency khi di chuyển.

Từ root repo, dùng `./scripts/project.ps1 -Task dev` hoặc `-Task content/check/build/smoke`. Helper chọn Node phù hợp và chuyển cwd vào frontend. Từ thư mục này, dùng npm scripts với Node phù hợp theo [hướng dẫn root](../README.md). Build ra `frontend/dist/`; Playwright report/ảnh ở `frontend/test-results/`. npm cache dùng `../.npm-cache` để giữ cache có sẵn.

- [Architecture source FE](../docs/architecture/frontend.md): phần hiện có, cấu trúc đích, dependency và test boundary.
- [HLD/LLD](../docs/design/README.md): hành vi sản phẩm.
- [Backlog](../docs/delivery-backlog.md): task hiện hành.

Hiện có shell/Journey/schema v1/API500 draft. Auth, player mới và progress server là thiết kế cho task sau; việc chuyển thư mục không triển khai các tính năng đó. Authoring private và expected assessment R1 không được import vào bundle FE.
