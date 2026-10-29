# Backend Lab — Backlog thực thi

> Microservices v3 MS01–04: [backlog](docs/delivery-backlog.md)/[implementation plan](docs/architecture/implementation-plan.md)/[test-release](docs/architecture/test-release-plan.md) giữ tasks/stack/ops/DoD. R01a–d/R02a/R02b có nền build/ba bootstrap/DB/trust; R03 có [local credential/maintenance evidence](docs/identity-credential-verification.md), leaf kế tiếp R04a session/preauth/CSRF. R01 là parent rollup, R02 còn chờ c; danh sách T01–35 và mốc trạng thái dưới giữ lịch sử, không thực thi.

> Từ 26/09/2026, backlog hiện hành là [docs/delivery-backlog.md](docs/delivery-backlog.md). **Phạm vi v2: backend/nhiều user, roadmap DevOps; admin sau R1. BL-S01–S04 DONE; D09 DONE; BL-R00 DONE; tiếp theo BL-R01 sau chỉ dẫn runtime backend; D06/D06b và D07 DONE ở mức thiết kế/prototype, D08 DONE, D09 DONE.** Tài liệu này giữ lịch sử T01–T35 và bằng chứng cũ; không dùng danh sách TODO dưới đây để tự chọn việc mới. Cách giao một session/một đầu ra ở [execution-model.md](docs/execution-model.md).

> Điều chỉnh business được chốt: xem [business-design.md](docs/business-design.md). T01–T05 giữ nguyên lịch sử; T06–T35 là backlog cũ chờ đối chiếu. Chuỗi B01–B05 bên dưới là kế hoạch lịch sử; hiện trạng và task tiếp theo nằm trong [backlog hiện hành](docs/delivery-backlog.md). Chưa triển khai thêm code.

Trạng thái hiện tại: T01–T05 DONE; T06–T35 TODO. Kế hoạch nằm trong PROJECT_PLAN.md; quy trình kiểm thử/review ở [docs/quality-process.md](docs/quality-process.md). Schema, bài API 500 dạng draft và Journey nối catalog đã có; lesson player, mô phỏng và tiến độ chưa triển khai.

Quy ước: TODO → IN_PROGRESS → REVIEW → DONE; BLOCKED phải ghi nguyên nhân. P0 = cần cho mốc tương ứng; P1 = cải thiện sau khi luồng chính ổn định. Owner mặc định: người/agent thực hiện task được giao. Dependency ghi bằng ID và phải đạt tiêu chí trước khi bắt đầu phần phụ thuộc. Mọi task áp dụng cổng và bằng chứng trong [quy trình test/review](docs/quality-process.md); task UI kiểm tra accessibility trong lúc làm, không dồn sang T22.

## M1 — Prototype tương tác

### T01 — Chốt đặc tả trải nghiệm [P0, DONE]
- Phụ thuộc: không.
- Làm: cụ thể hóa persona, mục tiêu, màn hình và hành trình “Cứu API 500”; ghi rõ mô phỏng so với thực thi thật.
- Đầu ra: docs/product-spec.md, danh sách hành vi từng màn.
- Đạt khi: mỗi màn có điểm vào/ra, hành động chính, trạng thái lỗi/trống và tiêu chí hoàn thành bài.
- Hoàn thành: 19/09/2026. Đầu ra: [docs/product-spec.md](docs/product-spec.md).
- Bằng chứng: người dùng xác nhận biết lập trình cơ bản, mới học Java/backend; đặc tả có 9 màn với hành vi/điểm vào-ra/empty-error, kịch bản API 500, ma trận 6 input × 4 cách sửa, 5 điều kiện qua bài và 16 tiêu chí nghiệm thu triển khai.
- Kiểm tra: rà soát luồng và tính nhất quán của đặc tả; kiểm tra cấu trúc đủ 9 screen ID, 16 acceptance ID, 5 completion ID và không trùng screen ID; `git diff --check` đạt. Chưa chạy test ứng dụng hoặc thử UI với người dùng; các việc đó thuộc task triển khai và T10.

### T02 — Thiết kế wireframe và phong cách [P0, DONE]
- Phụ thuộc: T01.
- Làm: wireframe Home, Journey, Lab, Result; định nghĩa màu, chữ, spacing, motion; bản lab mở rộng editor.
- Đầu ra: thiết kế cụ thể và docs/design.md.
- Đạt khi: có bố cục laptop/desktop/mobile đọc bài; giao diện chính không dùng roadmap graph; có reduced-motion và focus state.
- Hoàn thành: 19/09/2026. Đầu ra: [docs/design.md](docs/design.md), [bản thiết kế tương tác](docs/design-preview.html), ảnh và [báo cáo kiểm tra](docs/design/qa/report.json).
- Bằng chứng: 6 wireframe bố cục, 5 màn chuyển được, Home mới/tiếp tục, lab idle/trace/500/validation/201, code mở rộng, challenge đúng/sai, Result và lỗi lưu. Có theme sáng/tối, teal/xanh lam, mobile, wireframe đơn sắc, quy tắc focus và reduced motion.
- Kiểm tra: Chrome headless qua 30 tổ hợp màn/kích thước 320–1440px, không có lỗi JavaScript; 13 screenshot và review trực quan; 13 cặp màu chữ chính đạt tỷ lệ tối thiểu 4.5:1. Đây là kiểm tra mockup, chưa phải nghiệm thu app, learner test hoặc audit accessibility toàn diện.

### T03 — Khởi tạo dự án và quy trình chạy [P0, DONE]
- Phụ thuộc: T02.
- Làm: kiểm tra runtime, khởi tạo frontend, TypeScript, routing, scripts dev/build/check; lockfile và README.
- Đầu ra: app shell chạy local, hướng dẫn từ checkout mới.
- Đạt khi: cài dependency và build thành công; có URL preview; route chính điều hướng được và không lỗi console chưa giải thích.
- Hoàn thành: 20/09/2026. Đầu ra: app React/TypeScript/Vite trong src/, package.json + lockfile, README, helper Windows và [báo cáo xác minh](docs/setup-verification.md).
- Bằng chứng: dev server 127.0.0.1:5173; Home, Journey, Lab/Challenge/Result và đường phục hồi URL sai; Node 24 được helper chọn thay Node 16 trên máy mà không đổi cấu hình hệ thống.
- Kiểm tra: cài lại từ lockfile bằng npm ci --offline đạt; TypeScript/ESLint đạt; build đạt; 5/5 Playwright smoke tests đạt, không lỗi console app trong các route kiểm tra; review ảnh desktop/mobile/dark. Nội dung đang chuẩn bị vẫn được ghi rõ; không đánh dấu T04–T09 hoàn thành theo app shell.

### T04 — Schema nội dung và bài mẫu [P0, DONE]
- Phụ thuộc: T01, T03.
- Làm: định nghĩa Lesson/Step/Exercise/Progress; tạo dữ liệu bài API 500 và validator tối thiểu.
- Đầu ra: schema, fixture bài học, test dữ liệu hợp lệ/sai.
- Đạt khi: bài được nạp từ dữ liệu, không hardcode toàn bộ vào UI; báo lỗi id trùng hoặc tham chiếu không tồn tại.
- Hoàn thành: 22/09/2026. Đầu ra: schema Lesson/Step/Exercise/Progress, catalog, [tài liệu schema](docs/content-schema.md), fixture API 500 gồm 8 bước, 6 exercise và ma trận 6 input × 4 patch.
- Bằng chứng: Home/LearningPage đọc metadata và step từ catalog; validator chạy trong `check`, phát hiện ID trùng, reference thiếu, vòng prerequisite, đáp án/patch result sai và bài published thiếu điều kiện.
- Kiểm tra: 5 unit test dữ liệu hợp lệ/sai; TypeScript strict/ESLint; production build và toàn bộ browser smoke. Fixture vẫn `draft`; test chỉ xác minh hợp đồng dữ liệu, chưa chứng minh mô phỏng T07 hoạt động.

### T05 — Xây shell, Home và Journey [P0, DONE]
- Phụ thuộc: T02, T03, T04.
- Làm: sidebar thu gọn, Journey dạng sơ đồ phân nhánh tương tác (điều chỉnh 20/09), card nhiệm vụ, breadcrumb, trạng thái bài chưa có nội dung.
- Đầu ra: điều hướng từ Home đến bài mẫu.
- Đạt khi: link và nút hoạt động; Back giữ ngữ cảnh; bài chưa xuất bản có nhãn rõ.
- Hoàn thành: 22/09/2026. Đầu ra: shell responsive, Home, breadcrumb, bản đồ Journey tương tác và kết nối chủ đề `Validation & error` với catalog.
- Bằng chứng: metadata bài đọc từ catalog; draft hiện tóm tắt/thời lượng/mục tiêu nhưng không có nút bắt đầu; trạng thái nhánh/chủ đề nằm trong URL và được phục hồi khi Back; sidebar chuyển thành nav ngang trên màn hẹp.
- Kiểm tra: `test:all` đạt gồm 5 content unit, TypeScript/ESLint, production build và 9/9 Playwright tests; browser test có direct/reload/Back, keyboard, reduced-motion, dark và không overflow 320–1440px. Chưa có lesson player nên bài vẫn chỉ xem đề cương.

### T06 — Xây phòng lab và bộ điều khiển bài [P0, TODO]
- Phụ thuộc: T04, T05.
- Làm: bố cục sân khấu, panel nhiệm vụ, tab code/log/giải thích, next/back, hints, pause/step/reset.
- Đầu ra: lesson player dùng chung.
- Đạt khi: đổi bước không mất câu trả lời trong phiên; có thể học bằng bàn phím và không phụ thuộc animation.

### T07 — Mô phỏng request và lỗi 500 [P0, TODO]
- Phụ thuộc: T06.
- Làm: client/controller/service/database, request hợp lệ/sai, log theo bước và sửa lỗi theo hành động định sẵn.
- Đầu ra: mô phỏng có state/action, giải thích giới hạn.
- Đạt khi: cùng input cho cùng kết quả; đổi input thay đổi luồng đúng kịch bản; reset/replay chính xác; gắn nhãn mô phỏng.

### T08 — Thử thách và màn hình kết quả [P0, TODO]
- Phụ thuộc: T07.
- Làm: câu hỏi dự đoán, debug challenge, hints ba mức và feedback theo sai lầm.
- Đầu ra: checkpoint và kết quả thể hiện kỹ năng đã đạt.
- Đạt khi: có trường hợp đúng/sai; không chỉ bấm Next là hoàn thành; có retry và hướng dẫn khi chưa đạt.

### T09 — Lưu phiên học tối thiểu [P0, TODO]
- Phụ thuộc: T08.
- Làm: storage adapter, vị trí bài đang học và trạng thái checkpoint.
- Đầu ra: Resume hoạt động sau reload.
- Đạt khi: refresh và đóng/mở trình duyệt giữ dữ liệu; storage lỗi có thông báo và vẫn học được trong phiên.

### T10 — Kiểm chứng prototype và sửa UX [P0, TODO]
- Phụ thuộc: T09.
- Làm: chạy toàn luồng, review giao diện, thử với người dùng, ghi các điểm vướng và sửa lỗi chặn học.
- Đầu ra: docs/prototype-review.md với phát hiện và quyết định.
- Đạt khi: người dùng vào đúng bài và thao tác được; báo rõ nếu chưa có phản hồi thực tế; build/check và smoke test đạt. Không tự đánh dấu phần thử người dùng đã xong nếu chưa thử.

## M2 — MVP dùng học được

### T11 — Mục lục toàn khóa và prerequisite [P0, TODO]
- Phụ thuộc: T04, T10.
- Làm: nhập các chương trong kế hoạch; chia chủ đề; xác định bắt buộc/nâng cao và node dùng chung Backend/DevOps.
- Đầu ra: catalog có id ổn định, roadmap và lesson references.
- Đạt khi: không có prerequisite vòng hoặc id thiếu; 15 bài mẫu có đường vào rõ; bài chưa viết mang trạng thái planned.

### T12 — Quy trình biên soạn và kiểm tra nội dung [P0, TODO]
- Phụ thuộc: T11.
- Làm: lesson template, schema version, sources/reviewedAt, validation trong build.
- Đầu ra: docs/content-authoring.md và lệnh kiểm tra nội dung.
- Đạt khi: thêm bài đơn giản không sửa lesson player; published lesson thiếu mục tiêu/tiêu chí sẽ bị phát hiện.

### T13 — Mô phỏng object/reference [P0, TODO]
- Phụ thuộc: T06, T10, T12.
- Làm: mô hình biến/object/reference, gán giá trị và truyền tham số qua các ví dụ định sẵn.
- Đầu ra: simulator dùng lại qua cấu hình.
- Đạt khi: minh họa đúng việc Java truyền giá trị, phân biệt sửa object và gán lại reference; có test các chuyển trạng thái chính.

### T14 — Năm bài Java hoàn chỉnh [P0, TODO]
- Phụ thuộc: T13.
- Làm: class/object; primitive/reference; truyền tham số; ==/equals; debug object.
- Đầu ra: năm bài, ví dụ, gợi ý, bài áp dụng và nguồn tham chiếu.
- Đạt khi: hoàn thành từng bài không cần bài chưa xuất bản; kiểm tra đúng cơ chế Java và các đáp án sai có feedback.

### T15 — Năm bài HTTP/API hoàn chỉnh [P0, TODO]
- Phụ thuộc: T07, T12.
- Làm: request/response; method/status; JSON/validation; luồng request; debug lỗi 500. Hoàn thiện bài mẫu hiện có, không tạo bản sao.
- Đầu ra: năm bài với request thay đổi được.
- Đạt khi: phân biệt lỗi client/server trong các ví dụ; mô phỏng và code minh họa thống nhất; checkpoint có tình huống mới.

### T16 — Mô phỏng container/network/volume [P0, TODO]
- Phụ thuộc: T06, T10, T12.
- Làm: image/container, start/stop/recreate, port host/container, network và volume.
- Đầu ra: simulator Docker giới hạn theo kịch bản.
- Đạt khi: stop/start khác remove/recreate; lưu dữ liệu có/không volume đúng mô hình; phân biệt localhost và service name theo ngữ cảnh.

### T17 — Năm bài Docker hoàn chỉnh [P0, TODO]
- Phụ thuộc: T16.
- Làm: image/container; lifecycle; port; network/service name; volume.
- Đầu ra: năm bài, thử thách và sơ đồ.
- Đạt khi: không yêu cầu cài Docker để học mô phỏng; từng bài có nhãn, phạm vi và lệnh thực tế tham chiếu rõ ràng.

### T18 — Tiến độ, tiếp tục học và gợi ý bài tiếp theo [P0, TODO]
- Phụ thuộc: T09, T11, T12.
- Làm: lưu evidence/attempt/version; dùng chung progress giữa roadmap; next lesson theo prerequisite và trạng thái xuất bản.
- Đầu ra: dashboard tiến độ và resume.
- Đạt khi: bài dùng chung không bị tính hai lần; không đề xuất bài planned; sửa đáp án hoặc replay không tự tăng cấp thành thạo.

### T19 — Ghi chú, bookmark và tìm kiếm [P1, TODO]
- Phụ thuộc: T11, T18.
- Làm: ghi chú theo bài/bước, bookmark, tìm tiếng Việt có/không dấu và thuật ngữ Anh.
- Đầu ra: thư viện cá nhân và tìm kiếm nội dung.
- Đạt khi: reload giữ ghi chú; kết quả dẫn đúng bài/bước; trạng thái không có kết quả rõ ràng.

### T20 — Sao lưu, phục hồi và migration dữ liệu [P0, TODO]
- Phụ thuộc: T18.
- Làm: export/import có schemaVersion, kiểm tra file trước ghi, xử lý version và xác nhận reset.
- Đầu ra: màn settings và storage migration.
- Đạt khi: dữ liệu sai không phá bản đang lưu; export→import giữ tiến độ/notes; lỗi ghi giữa chừng không để dữ liệu nửa chừng.

### T21 — Hệ thống đang xây và chuyển kiểu học [P1, TODO]
- Phụ thuộc: T14, T15, T17, T18.
- Làm: hệ thống của tôi theo evidence; chọn khám phá/xây dựng/debug ở các bài hỗ trợ.
- Đầu ra: màn hệ thống liên kết đến các bài liên quan.
- Đạt khi: trạng thái thành phần phản ánh tiến độ thật; không tuyên bố đã deploy khi chỉ hoàn thành mô phỏng.

### T22 — Accessibility, responsive và tải ban đầu [P0, TODO]
- Phụ thuộc: T19, T20, T21.
- Làm: keyboard/focus, contrast, reduced motion, text tương đương sơ đồ; lazy-load simulator; kiểm tra các kích thước màn hình.
- Đầu ra: sửa lỗi và checklist QA.
- Đạt khi: luồng chính dùng được khi giảm chuyển động; màn mobile có nội dung đọc được; ghi nhận baseline hiệu năng trên môi trường thử cụ thể.

### T23 — Kiểm thử và bàn giao MVP [P0, TODO]
- Phụ thuộc: T14, T15, T17, T22.
- Làm: E2E resume, checkpoint, shared progress, export/import; review độ đúng 15 bài và hướng dẫn khởi chạy.
- Đầu ra: bản M2 và docs/mvp-review.md.
- Đạt khi: build/check đạt; các luồng cốt lõi pass; không còn lỗi chặn học/mất dữ liệu đã biết; báo rõ giới hạn mô phỏng và nội dung chưa hoàn thiện.

## M3 — Chạy Java/Docker thật

### T24 — Thiết kế hợp đồng lab và kiểm tra môi trường [P0, TODO]
- Phụ thuộc: T23.
- Làm: xác định JDK/build tool/Docker cần thiết, input/output, task hợp lệ, thư mục lab, timeout, cancel và reset.
- Đầu ra: docs/local-labs.md với các tình huống lỗi và ranh giới thực thi.
- Đạt khi: tách rõ chế độ hướng dẫn terminal và tích hợp runner; không có API shell tùy ý hay ghi ngoài workspace lab.

### T25 — Task API mẫu có kiểm thử [P0, TODO]
- Phụ thuộc: T24.
- Làm: Spring API, validation, error response, database, migration và test nghiệp vụ/API.
- Đầu ra: project mẫu trong labs/task-api cùng README.
- Đạt khi: chạy theo README trên môi trường đã ghi; test đúng/sai pass; có seed và reset dữ liệu lab.

### T26 — Đóng gói lab Docker/Compose [P0, TODO]
- Phụ thuộc: T25.
- Làm: Dockerfile, Compose app/database, health check, cấu hình, port và volume riêng.
- Đầu ra: bộ lab container có hướng dẫn build/run/debug/reset.
- Đạt khi: API truy cập được; dữ liệu còn sau recreate khi dùng volume; có bài lỗi network và cách xác minh nguyên nhân.

### T27 — Bài thực hành terminal có hướng dẫn [P0, TODO]
- Phụ thuộc: T26.
- Làm: nối bài học với code mẫu, lệnh chạy và đầu ra mong đợi; tự kiểm tra có chỉ dẫn.
- Đầu ra: hành trình app → terminal → kiểm chứng.
- Đạt khi: người học biết mình đang chạy ở đâu; tự khai hoàn thành được phân biệt với kết quả app đã xác minh.

### T28 — Runner cục bộ giới hạn hành động [P0, TODO]
- Phụ thuộc: T24, T27.
- Làm: build/test/start/stop/status qua task định sẵn, bind localhost, kiểm tra origin/token phiên, giới hạn thời gian và cleanup process.
- Đầu ra: runner cùng kiểm tra positive/negative.
- Đạt khi: từ chối task không hợp lệ và path vượt thư mục lab; không lộ secrets trong output; cancel/timeout không để process mồ côi; yêu cầu nguồn khác không thể chạy tác vụ.

### T29 — Kết nối UI với kết quả thực thi thật [P0, TODO]
- Phụ thuộc: T28.
- Làm: trạng thái runner, streaming output có giới hạn, exit code, bằng chứng kiểm thử, xử lý mất kết nối.
- Đầu ra: real-lab player.
- Đạt khi: không nhầm pending/failed/success; runner offline có hướng dẫn khôi phục hoặc fallback terminal; không chấm pass từ chuỗi log giả định.

### T30 — CI và bản đóng gói có thể tái lập [P0, TODO]
- Phụ thuộc: T29.
- Làm: pipeline frontend/content/tests; pipeline lab Java; tài liệu cấu hình và hướng dẫn cài từ checkout mới.
- Đầu ra: pipeline trong repo, hướng dẫn dùng local và phương án preview.
- Đạt khi: chạy các bước tương đương CI thành công tại môi trường có đủ dependency; nếu chưa có remote thì ghi rõ pipeline chưa được xác minh trên remote. Publish chỉ khi được yêu cầu trong bước triển khai liên quan.

### T31 — Nghiệm thu lab thật [P0, TODO]
- Phụ thuộc: T30.
- Làm: thử thiếu Java/Docker, port bận, test fail, timeout, hủy tác vụ, reset và restore lab.
- Đầu ra: docs/lab-review.md và bản M3.
- Đạt khi: có bằng chứng cho luồng chuẩn và lỗi quan trọng; dữ liệu ngoài lab không bị tác động; hướng dẫn recovery dùng được.

## Sau M3 — Mở rộng theo đợt

### T32 — Hoàn thiện đường học nền tảng liên tục [P0, TODO]
- Phụ thuộc: T31.
- Làm: lấp các bài Java nền tảng, OOP, collections, exceptions, build và testing còn thiếu.
- Đạt khi: người mới có đường học liên tục đến Task API; mỗi chương có bài ứng dụng và review kỹ thuật.

### T33 — SQL, Spring và persistence chuyên sâu [P0, TODO]
- Phụ thuộc: T32.
- Làm: chia tiếp thành task theo chương; ưu tiên DI, SQL join, transaction và N+1 với mô phỏng tương ứng.
- Đạt khi: nội dung, lab, sources và checkpoint của từng chương hoàn chỉnh trước khi xuất bản.

### T34 — Security, nghiệp vụ và hiệu năng [P1, TODO]
- Phụ thuộc: T33.
- Làm: Order/Inventory, ownership, concurrency, idempotency, cache, queue và đo tải.
- Đạt khi: test quyền truy cập và xung đột nghiệp vụ; số liệu hiệu năng ghi rõ môi trường, không dùng số minh họa như phép đo thật.

### T35 — DevOps sâu và system design [P1, TODO]
- Phụ thuộc: T34.
- Làm: chia đợt Linux/network, CI/CD, observability, cloud/IaC, orchestration và reliability theo nhu cầu học.
- Đạt khi: từng đợt có mục tiêu, lab và tiêu chí riêng; không mở rộng vô hạn trong một task.

## Mẫu giao việc

“Thực hiện Txx trong TASKS.md. Đọc PROJECT_PLAN.md và quy định repo trước khi sửa. Kiểm tra dependency; hoàn thành đầu ra và tiêu chí của task. Chạy kiểm tra phù hợp, cập nhật trạng thái và ghi bằng chứng. Không tự đánh dấu việc chưa kiểm chứng là DONE.”

## Checkpoint trước khi chuyển sang backlog mới

B02 — IN_PROGRESS. Khung 23 nhóm đã được người dùng xem và đồng ý chuyển sang bước đường học. Đầu ra mới ngày 23/09/2026: [đường học cốt lõi v1](docs/java-backend-learning-path.md), 11 chặng S00–S10, điều kiện/đầu ra/bằng chứng, HTTP–SQL song song, luồng xuyên suốt và ánh xạ đủ 23 nhóm tới lõi hoặc nhánh mở rộng. Project P0–P7 giữ ý nghĩa đã thống nhất. Bản xem theo chặng phục vụ review, chưa triển khai vào app.

Phần tiếp theo của B02: phân rã chặng thành catalog mục học/năng lực có ID ổn định và prerequisite cấp mục; kiểm tra đường vào Collections. Sau đó B03 chuẩn hóa template/rubric/progress và mẫu Collections → B04 → B05. Không đánh dấu toàn bộ B02 DONE từ bản đồ cấp chặng. T06 cũ tạm dừng; chỉ tiếp tục triển khai sau khi review thiết kế business tổng hợp.

Việc này đã được chia cụ thể thành BL-D01–09 trong delivery-backlog.md; trạng thái mới chỉ cập nhật tại đó.

Xác minh đường học: đủ 11 chặng/23 nhóm, prerequisite cấp chặng không có vòng; bản xem qua kiểm tra tương tác/bàn phím/state và responsive 800/390/320px, không lỗi JavaScript. Chưa kiểm chứng với người học; chưa thay đổi source app.

R02b technical trust foundation và bằng chứng kiểm hiện hành ở [tài liệu review](docs/service-trust-verification.md); trạng thái/evidence cuối ở backlog. Chưa login/session/owner/business/edge deployment.
