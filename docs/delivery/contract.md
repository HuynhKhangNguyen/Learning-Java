# Goal, context và Definition of Done theo cấp

> V3 MS04: [implementation plan](implementation-plan.md)/[test-release](test-release-plan.md) và backlog parent/leaf thay topology/test một-app. R00 giữ DONE; MS01–04 là evidence thiết kế, chưa CODE/runtime. DoD business giữ nghĩa, thêm REL-01–08 và SEC/DATA/DB/storage/provider/compatibility reports khi nghiệm thu R1.

BL-D11 · 30/09/2026. Đây là hợp đồng thực thi, không phải báo cáo các tính năng đã hoàn thành. Nguồn goal/phạm vi vẫn ở [business](../business-design.md), kế hoạch/trạng thái ở [backlog](../delivery-backlog.md), quy định chung ở [AGENTS](../../AGENTS.md). [Bảng truy vết](traceability.md) nối mục tiêu với nghiệm thu; [mẫu task](task-brief-template.md) chuẩn bị context cho từng phiên.

## 1. Mỗi cấp phải có gì?

| Cấp | Goal và plan | Context và rule | DoD và nơi ghi bằng chứng |
|---|---|---|---|
| Project | Kết quả người học trong business; kế hoạch R0–R3 | Requirements/HLD, AGENTS và quyết định hiện hành | Chỉ nghiệm thu theo phạm vi release đã chốt; không gọi toàn project DONE khi mới xong mẫu |
| Release | Trải nghiệm bàn giao, tập REQ bắt buộc và task | System, module, LLD, giới hạn mốc | Tất cả acceptance của mốc đạt cùng nhau; report liên kết kết quả thực tế |
| Feature/module | Hành vi người dùng hoặc trách nhiệm phần mềm | Module map + LLD liên quan; kế thừa rule chung | Contract và hành trình liên quan tích hợp đạt trong release; không chỉ đủ file/class |
| Task | Một đầu ra chính, dependency, các bước, ngoài phạm vi | Brief dẫn file cần đọc, rule đặc thù, checkpoint trước | Acceptance riêng + DoD chung; bằng chứng dưới task backlog |
| Mục học | TOP/CAP mục tiêu, prerequisite, kế hoạch biên soạn | Catalog, topic template, policy chấm, nguồn kỹ thuật | Design-complete khác published; checklist nội dung/runtime phù hợp |
| Session | Mục tiêu cụ thể của task hoặc phần còn lại | Brief + checkpoint; không phải toàn bộ lịch sử chat | Kết thúc có checkpoint; chỉ task đạt acceptance mới DONE |

Không cần tạo sáu bản rule giống nhau. Rule cấp nhỏ kế thừa AGENTS/quality/business; chỉ viết invariant bổ sung. “Goal” trong tài liệu không tự tạo goal tự chạy của công cụ.

## 2. Definition of Ready của task

- Goal, một đầu ra, phạm vi loại trừ và dependency đã rõ; dependency đã đạt hoặc được giải thích hợp lệ.
- Có REQ/module, nguồn cần đọc và fixture/ví dụ đầu vào phù hợp; không còn quyết định sản phẩm chặn đầu ra.
- Acceptance quan sát được, mỗi acceptance có cách kiểm và nơi ghi evidence; không dùng “code sạch” hoặc phần trăm chủ quan làm nghiệm thu.
- Brief theo mẫu đã điền trước IN_PROGRESS; chỉ định phần chưa biết phải khám phá trong task, không giả có sẵn command/file.
- Nếu là code, có chỉ dẫn CODE thực tế trong backlog. Thiết kế DONE hoặc brief đầy đủ không thay chỉ dẫn này.

Registry chỉ giữ trạng thái, dependency và link. Brief, acceptance, bảng evidence và checkpoint duy nhất nằm trong file task được registry liên kết; không chép checkpoint trở lại registry hoặc file tổng hợp khác.

D14 bổ sung: fixture/command/môi trường bắt buộc phải có từ dependency hoặc được tạo rõ trong task. DAG hợp lệ về ID chưa đủ Ready; R31a edge/TLS/base CMD-07 có trước R14/R24a. Schema-only hoặc mock không thay lớp bằng chứng endpoint/DB/browser được acceptance yêu cầu.

## 3. DoD chung cho mọi task

| ID | Điều kiện | Bằng chứng tối thiểu |
|---|---|---|
| DONE-01 | Đầu ra đạt từng acceptance, không lấn ngoài phạm vi | Acceptance → file/hành vi → kết quả kiểm |
| DONE-02 | Kiểm tra phù hợp loại thay đổi đạt | Lệnh/cách review, môi trường, kết quả thật và giới hạn |
| DONE-03 | Review logic biên, quyền/dữ liệu/UI khi liên quan; không còn P0/P1 trong scope | Finding và cách xử lý; lỗi còn lại có mức độ/owner |
| DONE-04 | Requirements/contracts/docs thống nhất với kết quả | File quyết định sửa; không giữ hai hướng trái nhau |
| DONE-05 | Handoff đủ để phiên khác tiếp tục | Checkpoint, phần còn lại, bước tiếp theo chính xác |

DONE-02 chọn theo [quality process](../quality-process.md); docs thuần không cần app E2E. Không có test chạy được thì ghi chưa kiểm chứng, không PASS thay. Điều kiện không áp dụng ghi lý do cụ thể. Không tự miễn acceptance bắt buộc hoặc learner test để đóng task.

## 4. DoD release và project

| Mốc | Điều kiện nghiệm thu |
|---|---|
| R0 | Catalog/policy/mẫu/UX/thiết kế kỹ thuật được review; findings chặn thiết kế xử lý; nêu rõ chưa runtime. Không cần code sản phẩm để R0 thiết kế DONE |
| R1 | REQ-01–16/18 đúng phạm vi mẫu/hai roadmap outline và REL-01–08: ba artifacts/DB/trust/auth/owner/T1/T2/pub/journal/recovery/UX/learner evidence; Collections publish kiểm soát, không required skip/P0/P1. REQ-17 ngoài mốc; R31d pilot chưa là R1 accepted trước R34 |
| R2 | Nghiệm thu theo từng batch nội dung/nhánh admin đã chốt, không hứa toàn95mục chi tiết chỉ từ outline; trước READY mỗi batch phải có tập TOP/CAP, rubric và acceptance riêng |
| R3 | Contract runner/lab được duyệt phạm vi; kiểm cô lập/quyền/lifecycle và output thật theo môi trường; chỉ mở khi task thiết kế tương ứng đủ đầu vào |

Project là sản phẩm tiếp tục phát triển, chưa có ngày kết thúc và phạm vi đóng toàn bộ. Chỉ tuyên bố project hoàn tất khi người dùng chốt phạm vi cuối và các release bắt buộc trong đó có nghiệm thu; không tự đặt một DoD “xong tất cả backend/devops”. R2/R3 hiện là cổng nguyên tắc, chưa READY triển khai.

[Review D14](../architecture/readiness-review.md) bổ sung REL-02 WIRE-01–06, REL-03/05 actual edge cert/proxy spill controls và REL-07 checkpoint interlock/crash/verified resume. R15/R20 demo review sớm không thay REL-08 learner thật hoặc thêm requirement runtime đã đạt; các gates có owner/expected ở test-release.

## 5. DoD từng module trong phạm vi release

Mọi dòng kế thừa DONE-01–05. Đây là tiêu chí cần đạt, **không phải trạng thái đã đạt**; số MD nối [module map](../feature-module-map.md).

| Module | Điều kiện đặc thù cần chứng minh | Task/bằng chứng đích |
|---|---|---|
| MD01 Catalog | ID/version/reference/DAG/shared đúng; STAGE/FENCE/canonical local COMMIT/RELEASE có barrier, private tách; current policy không stale | R05/R06a–c/R07/30, DATA-01–07/SEC-16/artifact report |
| MD02 Journey/guidance | Placement đúng; prerequisite thật; gợi ý có lý do và nội dung khả dụng | R14/22, deep-link và recommendation tests |
| MD03 Learning runtime | State/action/biên/reset đúng mô hình; không giả thực thi hoặc cấp pass | R16–18, CM và kiểm UI |
| MD04 Assessment | Rubric từng CAP, support/known-case đúng, grader xác định | R10/12/13/18–20, CA/AP và fixtures |
| MD05 Progress | Ba chiều riêng, evidence/version hợp lệ, rebuild/shared không nhân đôi | R13/21/22, projection/version/race tests |
| MD06 Persistence | Owner/local FK/CAS/T1/T2 đúng, no cross-DB access; journal before effect/disclosure, epochs/tombstones/retention; real provider/isolated restore không resurrect | R02a–c/R09/11/13a/b/25–29/32a/b, DATA/LD/qualification/drill |
| MD07 Project track | Readiness khác applied; CL-U-01 đúng nhãn mô hình, không P1 giả | R22/23, U1–U5/CA-12 |
| MD08 Execution | Sau R1; chưa có DoD triển khai đủ chi tiết, cần contract runner trước | E04/R3; không tính hoàn thành từ transcript tự khai |
| MD09 Experience | Trọn hành trình, keyboard/320px/errors/resume, đổi user không lộ cache | R14–24/33/34, UX/E2E/learner report |
| MD10 Identity | Invite/session/CSRF/expiry/owner, online admission+mandatory CNT/LRN gates/ACK, cookie reorder, mTLS trust/lifecycle/credential recovery | R03/R04a–d/R24a–c/R29b/R32b, IA/SEC/DB/browser/recovery |
| MD11 Learner work | Nháp sửa có revision; submission bất biến; retry/dedupe/history đúng owner | R08–11/13/19–21, LD và API/E2E |
| MD12 Admin | Sau R1; S05 chốt role/action/audit và acceptance trước triển khai | S05/E06, hiện chưa READY |

Một feature đi qua nhiều module chỉ hoàn tất khi hành trình kết nối đã kiểm, không cộng số module DONE để suy feature DONE.

## 6. Hợp đồng cho từng mục học

Mỗi TOP hoặc gói mục học khi được chọn biên soạn phải có hồ sơ theo [topic template](../features/f08-content/authoring/topic-template.md): ID/version, goal bằng CAP quan sát được, CAP chỉ hỗ trợ, persona/prerequisite, các phần kiến thức/hoạt động, invariant kỹ thuật, input/expected/rubric, nguồn và kế hoạch review. Dùng task brief cho công việc biên soạn; không tạo task hội thoại riêng cho mọi TOP.

**Design-complete:** các phần bắt buộc/điều kiện áp dụng có nội dung; ma trận CAP→học→luyện→kiểm tra→áp dụng đầy đủ; review đáp án/nguồn/biên và phạm vi bằng chứng; phần runtime chưa có được ghi rõ. Đây là DONE của task thiết kế, không published.

**Published:** đạt toàn bộ checklist publish của template và kiểm runtime liên quan, không ngõ cụt prerequisite, assessment có case mới hợp lệ, không lộ private answers, feedback/progress/version đúng và không còn lỗi chặn học. Publish report/version là evidence, không phải chỉ checkbox đã tick.

Collections hiện có hồ sơ thiết kế D06/D07; runtime còn ở BL-R. Các TOP còn lại chủ yếu catalog/planned, phải điền hồ sơ khi tới batch. Không tạo 95 hồ sơ rỗng rồi coi là đủ chi tiết.

## 7. Session và context bàn giao

Mở phiên: đọc AGENTS → task/brief/checkpoint → REQ/LLD/nguồn có liên quan → code hiện có. Ghi mục tiêu phiên bằng kết quả cần chứng minh. Kết phiên: ghi trạng thái, file, acceptance đã đạt/còn thiếu, kiểm tra/giới hạn, quyết định mới và bước tiếp theo. Nếu chưa xong, giữ IN_PROGRESS; session hết không tạo bằng chứng DONE.


## 8. Vị trí tài liệu từ04/10/2026

Quy ước lưu brief/checkpoint tại mục2 được thay bằng cách tổ chức theo feature: backlog gồm registry và các file task mà registry dẫn chiếu. Registry giữ trạng thái/dependency; mỗi file task trong `features/.../tasks/` hoặc `platform/.../tasks/` giữ brief, acceptance và checkpoint duy nhất, kể cả task ngắn. Các lời dẫn “dưới task backlog” trong hợp đồng này chỉ phần chi tiết ở file task; không chép nhật ký trở lại registry.

Đọc [mục lục tài liệu](../README.md) và [feature index](../features/README.md) để tìm source theo owner. Thay đổi này chỉ đổi nơi đọc/cập nhật doc; goal, Ready/Done, ID, dependency, quyền CODE và release gates giữ nguyên.
