# Backend Lab

Tài liệu làm việc: [mục lục dự án](docs/README.md) → [feature lớn/nhỏ](docs/features/README.md) → [registry task](docs/delivery-backlog.md). Brief và checkpoint nằm cạnh feature; tài liệu dùng chung ở architecture, curriculum, platform và delivery.

> Architecture v3: MS01–04 thiết kế ba service/auth/data và [implementation plan](docs/delivery/implementation-plan.md)/[test-release](docs/delivery/test-release-plan.md). Review [HLD/LLD](docs/design/README.md), [service boundaries](docs/architecture/service-boundaries.md) và [ADR-03](docs/architecture/microservices-review.md). R01/R02 foundation, R03 credentials, R04 session/admission/revoke/reauth trong owned-lab và R05 catalog schema2 đã có evidence theo leaf; [registry](docs/delivery-backlog.md) là nguồn trạng thái hiện hành. FE session UI, learner business flow, production edge và publication runtime còn theo backlog.

Review bổ sung01/10/2026: [D14 — khoảng trống và cách xử lý](docs/architecture/readiness-review.md), gồm test environment trước FE, edge trust/privacy, backup interlock, wire API chung và hai checkpoint review UX sớm. Các quyết định đã nối vào nguồn/backlog; receiver/common-wire layer R02b đã chạy; full wire/edge/backup/UX của D14 còn pending; bootstrap/DB lab có evidence riêng theo leaf.

Nền tảng tự học Java Backend và DevOps bằng nhiệm vụ, mô phỏng và thực hành. Docker là một chặng DevOps. Thiết kế đích có backend, tài khoản nhiều user và lưu bài làm/tiến độ server; admin ở giai đoạn sau. Giao diện tiếng Việt; [system v2](docs/system-overview.md) là nguồn kiến trúc hiện hành, [docs/design.md](docs/features/f02-learning-experience/visual-design.md) giữ thiết kế UI lịch sử.

## Trạng thái

Hướng sản phẩm mới đã chốt ở [business-design.md](docs/business-design.md): mục học + năng lực + kiến thức/luyện tập/kiểm tra/áp dụng/progress. Thiết kế business R0 đã có; mẫu đầu tiên là Collections cơ bản. BL-R00 đã chuyển cấu trúc FE vào frontend/; backend có ba bootstrap, DB lab và transport trust kỹ thuật; nghiệp vụ người học chưa triển khai. Các mô tả triển khai bên dưới phản ánh code hiện có, chưa phải thiết kế mới đã triển khai.

T01–T05 là nền React/schema/Journey; T06–35/BL-I/D08 một-app chỉ còn là lịch sử. FE hiện có hai bài HTTP trải nghiệm và locale EN–VI nhưng chưa nối learner API. Backend có ba service, ba DB/trust/delivery foundation và Identity canonical auth owned-lab qua R04d; R05 đã chốt catalog 95 TOP/190 CAP ở schema2. Publication, bài làm/chấm/progress server, FE auth và production edge vẫn `PLANNED`; xem [hướng dẫn backend](backend/README.md) và [backlog](docs/delivery-backlog.md) thay vì suy trạng thái từ đoạn mô tả này.

Quy định làm việc ở [AGENTS.md](AGENTS.md); cách chia session ở [execution-model](docs/execution-model.md). Phạm vi có ID ở [product-requirements](docs/product-requirements.md), ranh giới feature/module ở [feature-module-map](docs/feature-module-map.md).

Bản thiết kế T02 ở docs/design-preview.html là tài liệu tham chiếu độc lập, không phải frontend chạy thật và không được nhúng vào ứng dụng.

## Yêu cầu

- Node 24 khuyến nghị cho project này. Engines hỗ trợ nhánh Node 22 từ 22.22 và Node 24 trở lên; phiên bản thực tế dùng để xác minh được ghi trong docs/setup-verification.md.
- npm từ 8.5 trở lên. Dùng npm để giữ thống nhất package-lock.json; không tạo thêm pnpm/yarn lockfile.
- FE preview chưa cần Java/Docker/database. Nền backend R01a dùng JDK21/Maven Wrapper; xem [setup backend](backend/README.md). Ba bootstrap/DB lab theo hướng dẫn backend; Docker/container runtime ở R31.
- Trình duyệt hiện đại có hỗ trợ CSS light-dark; theme tự theo thiết bị.

Các dependency trực tiếp được pin version; package-lock.json khóa cả cây dependency. Ghi nhận cả package.json và package-lock.json khi đưa repo vào Git.

## Chạy từ checkout mới

Chạy npm trong `frontend/`. Từ root repo, sau khi chọn đúng Node:

```powershell
cd frontend
node --version
npm --version
npm ci
npm run dev
```

Mở http://127.0.0.1:5173. Dev server chỉ lắng nghe trên máy local. Dừng bằng Ctrl+C trong terminal chạy server.

Helper được chạy **từ root repo**, chọn runtime phù hợp và tự chuyển cwd sang frontend:

```powershell
.\scripts\project.ps1 -Task install
.\scripts\project.ps1 -Task dev
```

Helper chọn Node phù hợp trên PATH; nếu không có, thử runtime Node đã có sẵn của Codex trên máy. Helper không tải runtime hoặc thay PATH toàn hệ thống. Có thể chỉ định thư mục node.exe của bạn:

```powershell
.\scripts\project.ps1 -Task dev -NodeDirectory 'C:\path\to\node'
```

Để dùng trực tiếp `java`, `node`, `npm` trong terminal dự án bằng các runtime đã có, xem [môi trường không cần Administrator](docs/platform/environment/development.md). Trong PowerShell 7.4+, chạy `. .\scripts\enter-dev-shell.ps1` từ root; cấu hình chỉ áp dụng cho terminal đó. Tài liệu này cũng ghi trở ngại WSL/Docker đã kiểm trên máy hiện tại.

Nếu PowerShell trên máy không cho chạy script, dùng các lệnh npm ở trên với Node 24 trong terminal; không cần thay execution policy toàn hệ thống.

## Kiểm tra và build

Các lệnh npm trong bảng chạy tại `frontend/`; helper `scripts/project.ps1` chạy tại root. Không có package npm ở root.

| Lệnh | Mục đích |
|---|---|
| npm run dev | Server phát triển tại 127.0.0.1:5173 |
| npm run validate:content | Kiểm tra catalog, ID và các tham chiếu nội dung |
| npm run test:content | Unit test validator với dữ liệu hợp lệ/sai |
| npm run check | Runtime, content validation, TypeScript strict và ESLint |
| npm run build | Kiểm tra TypeScript, tạo build trong frontend/dist/ |
| npm run preview | Xem build đã tạo tại 127.0.0.1:4173 |
| npm run test:smoke | Playwright kiểm tra route, reload, Back, keyboard và responsive |
| npm run test:all | Content unit → check → production build → toàn bộ browser test |

Helper Windows tương ứng: `project.ps1 -Task content`, `-Task check`, `-Task build`, `-Task preview`, `-Task smoke`, `-Task all`.

Smoke test cần build trước. Nếu có Google Chrome cài trên máy:

```powershell
npm run build
$env:PLAYWRIGHT_CHANNEL = 'chrome'
npm run test:smoke
```

Hoặc cài browser riêng của Playwright rồi chạy mặc định:

```powershell
npx playwright install chromium
npm run build
npm run test:smoke
```

Trên máy đang dùng helper Node, chạy `project.ps1 -Task build`, đặt PLAYWRIGHT_CHANNEL, rồi `project.ps1 -Task smoke`. Smoke test tự khởi động và dừng preview server riêng trên 127.0.0.1:4174; không tái sử dụng một server có thể đang chạy code cũ. Ảnh/trace kiểm tra nằm trong frontend/test-results/ và được loại khỏi Git.

## Các route có sẵn

| URL | Hiện tại |
|---|---|
| / | Home theo style T02, không có tiến độ giả |
| /journey/java-backend | Khung hành trình Java |
| /journey/devops | Khung hành trình DevOps |
| /learn/http-foundations | Bài mẫu HTTP Fundamentals song ngữ |
| /learn/request-lifecycle | Bài mẫu vòng đời request song ngữ |
| /learn/api-debug-500/intro | Lab đang chuẩn bị |
| /learn/api-debug-500/challenge | Thử thách đang chuẩn bị |
| /learn/api-debug-500/result | Chưa có kết quả; không tự cấp hoàn thành |

Các step dự kiến khác được giữ chỗ trong frontend/src/routes.ts. Bài/roadmap không tồn tại mở trang phục hồi; step không hợp lệ của bài mẫu trở về intro. Không có network API hay runtime Java phía sau các route này.

BrowserRouter dùng History API. Dev/preview Vite hỗ trợ tải lại URL trực tiếp. Khi triển khai static hosting ở bước sau, cần rewrite route ứng dụng về index.html; vite preview không phải production server.

## Cấu trúc

```text
AGENTS.md / README.md   Rule và hướng dẫn toàn repo
docs/                  Business, HLD/LLD, architecture, backlog và DoD
scripts/project.ps1    Helper root: chọn Node và chạy package FE
frontend/
  package.json / package-lock.json
  src/                 React entry, routes, content, components, pages, styles
  public/              Assets public
  scripts/             Kiểm runtime và validate content
  tests/               Unit nội dung và Playwright
  dist/                Output build, ignore trong Git
backend/
  README.md            Hướng dẫn ba Spring Boot service, build và lab backend
```

## Xử lý lỗi thường gặp

- Node quá cũ: kiểm tra `node --version`; chọn Node 24 hoặc helper. Không hạ version Vite chỉ để khớp Node 16.
- Port đang bận: server dùng strictPort và báo lỗi; dừng server cũ của bạn hoặc chạy `npm run dev -- --port 5175`. Không tự đổi port âm thầm.
- Chưa có frontend/dist/: chạy build trước preview/test:smoke.
- Browser Playwright chưa cài: dùng PLAYWRIGHT_CHANNEL=chrome nếu Chrome có sẵn, hoặc cài Chromium như trên.
- Network registry bị hạn chế: npm ci cần các package trong cache hoặc quyền truy cập registry; npm cache của project ở .npm-cache/.
- Trang bài học chỉ hiện “đang chuẩn bị”: đây là trạng thái đúng của T03. Player/chấm/progress mới thuộc BL-R trong docs/delivery-backlog.md; TASKS.md chỉ giữ lịch sử.

## Tài liệu

- [Kế hoạch](PROJECT_PLAN.md), [backlog hiện hành](docs/delivery-backlog.md), [lịch sử task](TASKS.md), [đặc tả](docs/delivery/history/product-spec-t01.md), [thiết kế](docs/features/f02-learning-experience/visual-design.md).
- Tham chiếu cấu hình: [Vite](https://vite.dev/guide/), [React Router](https://reactrouter.com/start/declarative/installation), [typescript-eslint](https://typescript-eslint.io/getting-started/).

## Bộ tài liệu để review

Đọc [HLD và LLD theo từng phần](docs/design/README.md): tổng thể sản phẩm trước, sau đó tài khoản, nội dung, bài làm, tiến độ, giao diện, dữ liệu và vận hành. Đây là thiết kế hành vi R1; R00 đã thực hiện cấu trúc repo, các tính năng mới vẫn thuộc task sau.

Cấu trúc FE/BE cùng repo được chốt ở [ADR-02](docs/delivery/history/implementation-v2.md#11-adr-02-một-repository-frontend-và-backend-riêng-thư-mục). FE ở `frontend/`; backend có parent build và ba service độc lập. Kiến trúc source riêng: [FE](docs/architecture/frontend.md) và [BE](docs/architecture/backend.md).

Bằng chứng foundation/auth/catalog nằm trong task và verification của từng owner; [backlog](docs/delivery-backlog.md) giữ trạng thái cuối. Không suy learner business hoặc production readiness từ các leaf kỹ thuật đã `DONE`.
