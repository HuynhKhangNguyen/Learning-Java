# Backend Lab — Kế hoạch triển khai

> Điều phối hiện hành MS01–04 v3: [implementation plan](docs/architecture/implementation-plan.md), [test-release](docs/architecture/test-release-plan.md) và [backlog](docs/delivery-backlog.md) chốt73 task IDs/58 runtime leaves/14 parent, stack/auth/data/ops/test/REL gates. R01a–d/R02a/R02b có nền build/ba bootstrap/DB/trust; R03 có [local credential/maintenance evidence](docs/identity-credential-verification.md), tiếp R04a session/preauth/CSRF. Canonical admission/owner/nghiệp vụ học còn sau. Các con trỏ R01/T01–35 và mốc trạng thái dưới giữ lịch sử, không là quyền runtime hoặc backlog hiện hành.

> Điều phối mới ngày 26/09/2026: [execution model](docs/execution-model.md) → [requirements](docs/product-requirements.md) → [feature/module](docs/feature-module-map.md) → [backlog hiện hành](docs/delivery-backlog.md). R0–R3 là mốc bàn giao mới; M1–M3/T01–T35 dưới đây là kế hoạch lịch sử để đối chiếu. BL-D01–D05 và D06a đã hoàn thành catalog, template, hợp đồng business assessment/progress và kiến thức/mô hình Collections; phạm vi v2 thêm backend/nhiều user và roadmap DevOps, admin sau R1. BL-S01–S04 DONE; D09 DONE; BL-R00 DONE; task kế tiếp BL-R01 sau chỉ dẫn runtime backend; chưa chuyển sang code sản phẩm.

> Hướng sản phẩm: [Business design](docs/business-design.md) đã được người dùng đồng ý. Đợt này đang hoàn thiện thiết kế R0 trước khi viết code. Nội dung T01–T35 và kế hoạch B02–B05 dưới đây là lịch sử để đối chiếu; không tự tiếp tục T06 theo schema cũ. Hiện trạng/task kế tiếp theo [backlog hiện hành](docs/delivery-backlog.md); mẫu đầu tiên là Collections cơ bản.

Ngày lập: 19/09/2026. Mốc cập nhật lịch sử: 23/09/2026. T01–T05 đã hoàn thành đặc tả, thiết kế, nền tảng chạy, [schema nội dung](docs/content-schema.md) và Journey nối catalog. Tại mốc đó, công việc đang làm B02: [đường học cốt lõi 11 chặng](docs/java-backend-learning-path.md) trên khung 23 nhóm. Trạng thái hiện tại: BL-D01–D09 DONE ở mức thiết kế/prototype, nội dung Collections vẫn draft; D09 DONE; BL-R00 DONE; tiếp theo BL-R01 sau chỉ dẫn runtime backend theo [backlog hiện hành](docs/delivery-backlog.md). T06 vẫn tạm dừng; bài API 500 còn `draft`, lesson player, mô phỏng và tiến độ chưa triển khai. Quy trình kiểm tra/review xuyên suốt ở [docs/quality-process.md](docs/quality-process.md).

## 1. Mục tiêu và quyết định sản phẩm

App tự học Java Backend và Docker bằng nhiệm vụ, mô phỏng, thực hành và điều tra lỗi. Theo yêu cầu bổ sung ngày 20/09/2026, Journey dùng sơ đồ phân nhánh tương tác, mở dần từng chặng để khám phá cấu trúc kiến thức. Home và Lab vẫn tập trung vào nhiệm vụ và mô phỏng. Xem [điều chỉnh Journey](docs/journey-map.md).

- Người dùng đầu tiên: một người tự học, dùng trên máy cá nhân; đã xác nhận biết lập trình cơ bản và mới học Java/backend. Giao diện tiếng Việt, giữ thuật ngữ kỹ thuật tiếng Anh kèm giải nghĩa.
- Desktop là trải nghiệm chính. Tablet vẫn dùng được; mobile ưu tiên đọc, ôn và xem tiến độ.
- Trang chủ đưa người học trở lại đúng bài và bước đang dở.
- Hai lộ trình Java Backend và DevOps dùng chung bài học, prerequisite và tiến độ.
- Kiến thức vận hành thiết yếu xuất hiện đúng lúc trong lộ trình Backend. Kubernetes và hạ tầng chuyên sâu thuộc nhánh mở rộng.
- Cho phép khám phá bài nâng cao; prerequisite là hướng dẫn, không phải khóa cứng.
- Tách rõ bài mô phỏng với lab chạy thật. Không trình bày phản hồi dựng sẵn như kết quả thực thi Java/Docker.
- Animation giải thích cơ chế; có pause, step, reset và chế độ giảm chuyển động.

## 2. Ba mốc bàn giao

### M1 — Prototype tương tác

Luồng hoàn chỉnh: trang chủ → chọn nhiệm vụ → học trong lab → giải thử thách → xem kết quả → quay lại học tiếp.

Nội dung mẫu: “Cứu API trả lỗi 500”. Người học đổi request, chạy từng bước, xem log mô phỏng và sửa một lựa chọn xử lý lỗi. Đây là bài minh họa có giải thích nền tảng, không ép người mới bắt đầu học theo thứ tự này.

Điều kiện đạt: điều hướng thật, tương tác có tác động đến kết quả, có reset, không có nút trang trí giả chức năng. Thử cùng người dùng và ghi nhận điểm khó hiểu trước khi mở rộng.

### M2 — MVP tự học cá nhân

- Toàn bộ mục lục các chặng chính; đánh dấu chính xác bài nào chưa có nội dung.
- 15 bài hoàn thiện trong ba cụm mẫu: Java object/reference, HTTP/API, Docker cơ bản.
- Ba mô phỏng dùng lại được: object/reference, luồng request và container/network.
- Bài tập, checkpoint, gợi ý theo cấp độ, ghi chú, bookmark và tìm kiếm.
- Tiến độ bền vững trên trình duyệt; export/import để sao lưu và chuyển máy thủ công.
- Hành trình có hướng dẫn, tránh đề xuất bước tiếp theo là bài chưa xuất bản.

MVP kiểm chứng nền tảng học tương tác, chưa phải khóa học đầy đủ từ đầu đến cuối. Các cụm mẫu có prerequisite và phần hướng dẫn vào bài rõ ràng.

### M3 — Lab thật và mở rộng khóa học

- Task API mẫu chạy bằng Java/Spring, có database, validation, error handling và test.
- Bài thực hành qua terminal do người học chạy trước; app hiển thị hướng dẫn và tiêu chí kiểm tra.
- Bộ chạy lab cục bộ tích hợp sau, chỉ hỗ trợ các hành động được định nghĩa trước.
- Chạy Docker/Compose, quan sát output thật và có quy trình reset dữ liệu lab.
- Hoàn thiện khóa học theo từng chặng, ưu tiên lấp khoảng trống nền tảng trước các bài nâng cao.

## 3. Các màn hình

| Màn hình | Nội dung và hành vi chính |
|---|---|
| Hôm nay | Tiếp tục bài, nhiệm vụ tiếp theo, ôn bài, hệ thống đang xây |
| Hành trình | Các chương theo thứ tự, checklist kỹ năng, prerequisite, bộ lọc bắt buộc/nâng cao |
| Phòng lab | Sân khấu tương tác; tab thao tác/code/log/giải thích; nhiệm vụ và gợi ý |
| Thử thách | Dự đoán, điều tra lỗi hoặc áp dụng kiến thức vào tình huống mới |
| Hệ thống của tôi | Các thành phần mở ra theo bài thực hành đã hoàn thành |
| Thư viện | Tìm theo khái niệm, thuật ngữ, lỗi thường gặp và bài học |
| Tiến độ | Bằng chứng đã làm được, bài còn yếu, ghi chú, bookmark |
| Cài đặt | Theme, giảm chuyển động, export/import và reset có xác nhận |

## 4. Kiến trúc đề xuất

M1–M2 dùng web app chạy local, không yêu cầu Java hoặc Docker để xem bài mô phỏng. M3 mới thêm runtime cho lab thật.

| Phần | Đề xuất | Lý do |
|---|---|---|
| Giao diện | React + TypeScript + Vite | Dễ chia component tương tác, phát triển và build ứng dụng web |
| Thiết kế | CSS tokens và component dùng chung | Kiểm soát bố cục, typography, theme và accessibility |
| Sơ đồ | SVG cho mô hình đơn giản; React Flow khi cần node/edge tương tác | Tránh dùng công cụ graph nặng cho mọi bài |
| Nội dung | Metadata có schema + nội dung bài tách khỏi UI | Thêm bài và sửa thứ tự mà không viết lại màn hình |
| Lưu cá nhân | IndexedDB qua một storage adapter | Lưu tiến độ, ghi chú, đáp án và phiên bản dữ liệu |
| Bài mô phỏng | State machine/reducer thuần, UI render theo trạng thái | Kết quả nhất quán khi step, reset hoặc replay |
| Kiểm thử | Unit cho logic học/mô phỏng; E2E cho hành trình quan trọng | Kiểm tra hành vi có rủi ro thay vì chụp mọi component |
| Lab Java | Project Spring riêng trong labs/ | Phân biệt ứng dụng học với phần backend dùng để thực hành |
| Lab Docker | Dockerfile/Compose có cấu hình và dữ liệu riêng cho lab | Dễ chạy, debug, tái lập và khôi phục |

Chốt phiên bản dependency tương thích khi khởi tạo, ghi runtime yêu cầu và commit lockfile. Không cần backend lưu tài khoản ở M2; đồng bộ nhiều thiết bị là tính năng sau.

Tham chiếu kỹ thuật: [Vite](https://vite.dev/guide/), [React Flow](https://reactflow.dev/learn), [IndexedDB](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API).

## 5. Hợp đồng dữ liệu

- Roadmap: id, title, chapterIds; tham chiếu bài dùng chung.
- Chapter: id, title, learningOutcomes, lessonIds, checkpointId.
- Lesson: id ổn định, version, title, summary, prerequisites, difficulty, estimatedMinutes, contentStatus, objectives, steps, references, reviewedAt.
- Step: id, loại hoạt động, hướng dẫn, nội dung/mô phỏng, tiêu chí chuyển bước và gợi ý.
- Simulation: loại, cấu hình, initialState, action hợp lệ, kết quả mong đợi và giới hạn của mô hình.
- Exercise: đề, dữ liệu đầu vào, tiêu chí chấm, feedback và hints. Nêu rõ kiểm tra mô phỏng hay thực thi thật.
- Progress: lessonId, lessonVersion, currentStepId, trạng thái, attempts và evidence.
- Note/Bookmark: lessonId, stepId tùy chọn, nội dung và thời điểm cập nhật.

Trạng thái nội dung: planned/draft/published. Trạng thái học: chưa học/đang học/đã xem/đã qua bài tập/đã áp dụng/cần ôn. Không tự suy ra “đã thành thạo” từ số lần mở trang.

Kiểm tra nội dung khi build: id trùng, tham chiếu thiếu, prerequisite có chu trình, bài published thiếu mục tiêu hoặc checkpoint không có tiêu chí. Bài dùng chung có một tiến độ duy nhất ở tất cả roadmap.

## 6. Bản đồ chương đầy đủ

1. Công cụ và nền tảng: terminal, Git, debugging, client/server, process, port, DNS.
2. Java cơ bản: biến, điều kiện, vòng lặp, method, array, String, class, object, reference.
3. Java có cấu trúc: OOP, generics, collections, exceptions, lambda, streams, I/O, datetime.
4. Build và chất lượng: Maven, dependency, unit test, mock, logging, refactoring.
5. Web và API: HTTP, JSON, REST, validation, pagination, idempotency, API contract.
6. Spring: DI, bean, controller/service/repository, configuration, request lifecycle.
7. Database: SQL, modeling, joins, indexes, transactions, isolation, migrations.
8. Persistence: JDBC, JPA, entity lifecycle, fetching, N+1, locking, integration tests.
9. Security: authentication, authorization, sessions/tokens, ownership, secrets, security tests.
10. Backend thực tế: cache, jobs, concurrency, queue, retry, duplicate events, file storage.
11. Triển khai: Docker, Compose, CI/CD, reverse proxy, TLS, observability, rollback/restore.
12. Nâng cao: JVM/performance, modular monolith, system design, distributed systems.

DevOps chuyên sâu: Linux/network → delivery → cloud/IaC → orchestration → reliability. Các chương này được mở rộng sau, không chặn việc hoàn thành lộ trình Backend thực hành.

15 bài mẫu M2: class/object; primitive/reference; truyền tham số; ==/equals; object debug challenge; request/response; method/status; JSON/validation; request qua các lớp; API 500 challenge; image/container; lifecycle; port mapping; network/service name; volume và mất dữ liệu.

## 7. Quy tắc nội dung để học không chán

- Mở bài bằng tình huống cụ thể, một mục tiêu chính mỗi bài.
- Xen kẽ khám phá, xây dựng và điều tra lỗi; không bắt mọi bài dùng chung một kịch bản.
- Ưu tiên thay đổi đầu vào và quan sát hệ quả; giải thích xuất hiện gần kết quả.
- Có ví dụ đúng, ví dụ lỗi và bài áp dụng mới; quiz nhớ định nghĩa không đủ làm checkpoint.
- Mỗi mô phỏng ghi rõ cơ chế được đơn giản hóa và các trường hợp không mô hình hóa.
- Bài kỹ thuật có nguồn chính thức, phiên bản áp dụng nếu cần và ngày rà soát.
- Cho dừng giữa bài, quay lại đúng bước; không ép streak hoặc trừng phạt bỏ ngày học.

## 8. Kiểm tra trước khi bàn giao

Áp dụng năm cổng: chốt hành vi → kiểm tra nhanh → tích hợp/build → hành trình trình duyệt → review nội dung/UX. Cách chọn test, mức độ finding và bằng chứng cho từng mốc được chốt trong [quy trình test và review](docs/quality-process.md). Accessibility được kiểm tra trong mọi task UI; T22 là audit tổng, không phải điểm bắt đầu.

- Luồng chính: mở app → tiếp tục bài → thao tác → làm bài tập → reload → giữ đúng tiến độ.
- Reset mô phỏng không xóa tiến độ/ghi chú; reset dữ liệu cá nhân là thao tác riêng có xác nhận.
- Import dữ liệu sai không ghi đè dữ liệu đang có; export/import có schemaVersion.
- Nội dung unpublished không xuất hiện như bài có thể hoàn thành.
- Dùng bàn phím được; focus rõ, text thay thế cho sơ đồ, lỗi không chỉ phân biệt bằng màu.
- Bật giảm chuyển động vẫn học và xem từng bước được.
- Kiểm tra desktop 1440px, laptop 1280px và khả năng đọc trên mobile 390px.
- Review screenshot các màn chính, kiểm tra build, kiểm tra E2E quan trọng; sửa lỗi trước khi mở rộng.
- Với M3: kiểm tra cài đặt thiếu, timeout, hủy tác vụ, output lỗi, reset và phân biệt kết quả thật/mô phỏng.

## 9. Lịch làm và cách giao task

Task chi tiết ở TASKS.md. Ước lượng chỉ để sắp xếp công việc, giả định một người triển khai tập trung và có phản hồi giao diện kịp thời; không phải cam kết ngày bàn giao.

- M1: khoảng 5–8 ngày công.
- M2: thêm khoảng 12–20 ngày công, gồm biên soạn và kiểm tra 15 bài.
- M3: thêm khoảng 10–18 ngày công cho nền tảng lab thật; nội dung toàn khóa tiếp tục chia đợt.
- Chốt lại ước lượng sau prototype, vì độ sâu mô phỏng và nội dung là phần biến động lớn nhất.

Mỗi lần giao việc dùng task ID, phạm vi, dependency và tiêu chí nghiệm thu. Chỉ đánh dấu DONE khi có bằng chứng. Không tự mở nhiều nhánh công việc vào cùng các file khi chưa thống nhất cách chia.

## 10. Rủi ro và phạm vi để sau

- Rủi ro lớn nhất là chất lượng nội dung và tính đúng của mô phỏng: xây mẫu bài và kiểm tra trước khi nhân rộng.
- Browser storage có thể bị xóa; có export/import, thông báo rõ nơi lưu và không hứa đồng bộ cloud.
- Không đặt mục tiêu mô phỏng JVM hoặc Docker đầy đủ; mỗi mô hình có phạm vi rõ.
- Bộ chạy lệnh là một hạng mục riêng: giới hạn task, thư mục, timeout, origin và không cung cấp shell tùy ý. Không mount Docker socket vào frontend/container web.
- Để sau: tài khoản/cloud sync, chatbot AI, cộng đồng, leaderboard, mobile app riêng, Kubernetes lab và microservices cho bản thân ứng dụng học.

## 11. Điểm bắt đầu

T01–T05 đã hoàn thành đặc tả, thiết kế, khởi tạo frontend, schema/bài mẫu và đường khám phá Journey. Thực hiện T06 → T07 → T08 → T09 → T10 để bàn giao M1. Sau khi thử prototype, cập nhật quyết định UX rồi bắt đầu M2.

R02b technical trust foundation và bằng chứng kiểm hiện hành ở [tài liệu review](docs/service-trust-verification.md); trạng thái/evidence cuối ở backlog. Chưa login/session/owner/business/edge deployment.
