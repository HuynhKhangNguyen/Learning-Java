# Cách chia việc và vận hành session

Ngày 23/09/2026. Phạm vi: tổ chức thực thi thiết kế và dự án; không khởi tạo session/automation hay mục tiêu tự chạy.

## 1. Mỗi lớp giải quyết một câu hỏi

| Lớp | Ý nghĩa | Ví dụ | Quy mô |
|---|---|---|---|
| Product goal | Người học đạt kết quả gì? | Tự xây, test, deploy một backend qua học và thực hành | Xuyên dự án |
| Release | Bàn giao được trải nghiệm gì? | R1 học Collections trọn luồng và giữ kết quả sau reload | Nhiều feature/task |
| Feature | Người dùng làm được gì? | Kiểm tra năng lực và biết thiếu tiêu chí nào | Có thể đi qua nhiều module |
| Module | Phần phần mềm chịu trách nhiệm gì? | Assessment đánh giá attempt theo rubric | Sống lâu qua nhiều release |
| Task | Thay đổi cụ thể cần nghiệm thu | Chấm một attempt Collections và trả evidence có version | Một đầu ra chính |
| Session | Phiên làm việc thực tế | Hoàn thành task đó, kiểm tra và bàn giao | Thường một task; không là đơn vị kiến trúc |

Không có quan hệ “một module = một session”. Không chia session theo màn hình khi hành vi cần data, logic và UI cùng hoạt động. Ưu tiên một lát cắt nhỏ đi qua các phần liên quan, ví dụ chọn đáp án → nhận feedback → lưu attempt; các hợp đồng dùng chung có thể là task nền riêng.

## 2. Khuyến nghị mặc định

**Một session = một mục tiêu kiểm chứng được, thường = một task đã READY.**

- Goal của session viết bằng kết quả, không phải “làm tiếp app” hoặc “làm module Progress”.
- Goal của release có thể cần nhiều session; khi chưa hoàn thành thì tiếp tục hoặc bàn giao, không sửa định nghĩa để báo DONE.
- Task dự kiến vài giờ làm tập trung hoặc một phiên là hướng dẫn chia nhỏ, không phải cam kết thời gian. Tách nếu có nhiều đầu ra độc lập, nhiều hợp đồng chưa rõ hoặc không thể nêu acceptance ngắn gọn.
- Có thể gom chỉnh sửa nhỏ cùng một hành vi và cùng cách nghiệm thu. Không gom vì các file nằm chung thư mục.
- Task nội dung lớn chia theo mục học/nhóm năng lực có đường vào rõ; không giao “viết toàn bộ Java” hay “làm 100 bài”.
- Khi task kéo qua phiên, giữ ID và acceptance, ghi checkpoint. Tạo session mới khi đổi mục tiêu độc lập hoặc cần ngữ cảnh gọn; không bắt mở chat mới cho mỗi bước.
- Chỉ tạo task hội thoại riêng khi người dùng yêu cầu; backlog trong repo không tự sinh task trong sidebar. Quy tắc này áp dụng cho chat người dùng nhìn thấy; subagent nội bộ trong một task đã được giao tuân theo mục 10 bên dưới. Không dùng lịch tự chạy hay budget token khi chưa có yêu cầu.

## 3. Các tài liệu thực sự cần

| Thành phần | Quyết định hiện tại | Nguồn chuẩn |
|---|---|---|
| Quy định agent | Có, ngắn và áp dụng toàn repo | AGENTS.md |
| Product goal/phạm vi | Có sẵn; dẫn chiếu, không viết thêm bản cạnh tranh | business-design.md |
| Requirements có ID | Bổ sung để nối business → task → nghiệm thu | product-requirements.md |
| Feature/module | Bổ sung trách nhiệm và ranh giới | feature-module-map.md |
| Backlog và checkpoint | Registry giữ trạng thái/dependency; file task theo feature/platform giữ brief/AC/checkpoint | delivery-backlog.md → file task được link |
| Template mục học/rubric | Đã có template/D05 và mẫu Collections D06 ở mức thiết kế | topic-template.md, assessment-progress-design.md; runtime chưa triển khai |
| Custom skill | Chưa cần tạo | Quy trình chưa được kiểm chứng qua một mục mẫu |
| ADR | Viết khi có lựa chọn kiến trúc quan trọng | ADR-01 trong docs/implementation-design.md; tách file khi có nhiều quyết định, chưa tạo thư mục rỗng |
| Active goal của công cụ | Không bắt buộc cho mỗi task | Chỉ đặt khi người dùng yêu cầu theo đuổi một goal tự động |

Skill là quy trình tái sử dụng; rule là ràng buộc ổn định; requirement là hành vi phải đạt. Không chép cùng một quy định vào cả ba nơi. Sau mẫu Collections, nếu biên soạn/review lặp lại ổn định, có thể đóng gói skill content-authoring; nếu pipeline review đã đủ trong helper và quality-process thì không cần skill riêng.

## 4. Trạng thái và điều kiện bắt đầu/kết thúc

PLANNED → READY → IN_PROGRESS → REVIEW → DONE. BLOCKED ghi điều kiện chặn và cách gỡ. PLANNED nghĩa là đầu ra đã dự kiến nhưng còn phụ thuộc/phân rã; không phải đang làm. REVIEW là chờ hoàn tất review, không nhất thiết là chờ người dùng phê duyệt.

Trong thuật ngữ chung, `PLANNED` tương ứng Backlog và `REVIEW` tương ứng In Review. Không thêm alias vào registry để tránh hai lifecycle cạnh tranh.

**Ready:** đầu ra cụ thể; phạm vi loại trừ; requirements và dependency rõ; dữ liệu/ví dụ đầu vào đủ; tiêu chí đo được; biết cách xác minh; không còn quyết định sản phẩm ngăn việc thực hiện. Task triển khai còn cần chỉ dẫn cho phép chuyển sang code.

**Done:** đúng acceptance; kiểm tra phù hợp pass; review diff/nội dung/UI theo phạm vi; không còn lỗi nghiêm trọng trong phần bàn giao; cập nhật docs và checkpoint. Feedback người dùng chỉ là điều kiện khi task ghi rõ cần learner test; không bắt mỗi task kỹ thuật phải chờ duyệt lại.

Task không đủ nhỏ lúc bắt đầu phải tách thành ID con, giữ task cha PLANNED/IN_PROGRESS và ghi liên kết. Không để acceptance bị mất khi tách.

## 5. Mẫu giao việc cho session

> Thực hiện BL-D01 theo docs/delivery-backlog.md. Mục tiêu là catalog mục học S00–S03 có ID ổn định, năng lực đầu ra và prerequisite đủ để đi vào Collections. Đọc AGENTS.md và tài liệu task dẫn chiếu. Hoàn thành acceptance và kiểm tra tương ứng; chỉ sửa tài liệu trong phạm vi. Cập nhật trạng thái/bằng chứng/checkpoint trước khi kết thúc. Nếu cần tách task, ghi rõ phần đã làm và ID phần còn lại.

Mẫu checkpoint ghi trong file task được registry dẫn chiếu, cạnh feature/subfeature hoặc platform sở hữu task. Registry chỉ giữ trạng thái/dependency và link, không chép lại nhật ký:

```text
Session goal:
Task / trạng thái:
Execution role / mode / write scope:
Đã thay đổi / file:
Acceptance đã đạt / còn thiếu:
Kiểm tra: lệnh hoặc cách review, kết quả, giới hạn:
Kết quả tích hợp/review của phần được giao (nếu có):
Quyết định mới / nguồn:
Trở ngại và dữ liệu cần thêm (nếu có):
Bước tiếp theo chính xác:
```

Khi tiếp tục task dài, đọc bảng DoD/evidence hiện hành và checkpoint mới nhất thay vì đọc lại toàn bộ nhật ký. Áp dụng [kiểm tra theo ảnh hưởng và chốt candidate](quality-process.md#kiểm-tra-theo-ảnh-hưởng-và-chốt-candidate): giữ bằng chứng còn hợp lệ, chỉ vô hiệu hóa gate bị ảnh hưởng và ghi bước tái hiện nhỏ nhất khi có lỗi. Không thay acceptance để đóng session hoặc vượt giới hạn Goal.

## 6. Các mốc

- R0 v2: thêm BL-S01–04 cho backend/nhiều user, quyền, bài nộp/progress server và catalog DevOps, rồi hoàn thiện mẫu/UX/thiết kế kỹ thuật trước code.
- R1 v2: nhiều user login/logout, dữ liệu cá nhân server, hai roadmap đề cương và Collections trọn luồng, export/import theo quyền. Chưa có admin.
- R2: nội dung Java Backend/DevOps theo batch và admin ở nhánh sau R1 theo lựa chọn người dùng; không buộc đợi toàn khóa mới làm admin. Không tuyên bố đủ bài chỉ vì có đề cương.
- R3: lab thật hỗ trợ tự động — chỉ sau thiết kế execution boundary riêng; hướng dẫn terminal thủ công có thể xuất hiện trước R3.

R là mốc phần mềm; P là mốc project của người học; không trộn hai loại. R0–R3 thay cách điều phối hiện tại, không xóa lịch sử M1–M3/T01–T35.

## 7. Hợp đồng thực thi và DoD theo cấp

Dùng [delivery contract](delivery/contract.md) cho Ready/Done theo cấp; [traceability](delivery/traceability.md) nối goal/REQ/module/task/evidence. MS04 parent rollup không là đơn vị session; chọn một leaf trong backlog, điền [brief](delivery/task-brief-template.md) trước IN_PROGRESS. Ví dụ BL-R01a toolchain đã có, chưa CODE; không chuyển trạng thái từ ví dụ hoặc thiết kế DONE. R00 migration/checkpoint cũ giữ nguyên.

## 8. Đường đọc ngắn theo feature

[Mục lục doc](README.md) → [feature index](features/README.md) → README của feature/subfeature → registry và file task → source business/LLD/verification liên quan. F10 tách credentials/sessions; F08 tách authoring/publication. Foundation nằm ở platform; contracts dùng chung ở architecture; kiến thức người học nằm ở curriculum. Chỉ tách file/thư mục khi có nội dung thật, không chia theo số trang hoặc tạo file trống cho mọi task PLANNED.

## 9. Control Center

Control Center là **vai trò điều phối**, không phải service, automation hoặc một chat có khả năng tự giám sát các chat khác. Trạng thái bền nằm trong registry và file task; chat chỉ là nơi thực hiện một lượt công việc. Một chat planning có thể giữ góc nhìn toàn dự án, còn chat implementation vẫn theo một leaf task cụ thể.

Với mỗi yêu cầu đáng kể, Control Center thực hiện theo thứ tự:

1. Đọc registry, task/checkpoint và source liên quan để xác định trạng thái thật; không suy từ trí nhớ chat.
2. Chuyển yêu cầu thành objective và acceptance quan sát được. Nếu chưa có task phù hợp, tạo hoặc tách leaf brief trong đúng tracker hiện hành.
3. Xác định feature/module/service, contract, security/data invariant và file có thể bị ảnh hưởng; ghi rõ ngoài phạm vi.
4. Kiểm dependency, fixture, môi trường và quyền thực thi. Task chưa đủ `READY` thì hoàn thiện brief hoặc ghi blocker trước khi code.
5. Tách phần việc chỉ khi mỗi phần có input/output và validation độc lập; xác định phần nào bắt buộc tuần tự và phần nào có thể song song.
6. Chọn execution role và mode nhỏ nhất đủ làm việc. Control Center chỉ trực tiếp implement khi task nhỏ, một owner và delegation không đem lại lợi ích rõ.
7. Giao từng phần với context tối thiểu, expected output, write scope, validation, escalation và integrator rõ ràng.
8. Theo dõi tiến độ bằng output/evidence thực trong task; commentary hoặc báo cáo của agent không tự trở thành trạng thái hay bằng chứng nghiệm thu.
9. Thu và review kết quả, giải quyết xung đột về nghĩa, chạy cổng tích hợp bị ảnh hưởng rồi đối chiếu từng acceptance.
10. Cập nhật checkpoint/knowledge source và chỉ đổi trạng thái registry khi điều kiện chuyển trạng thái đạt. Không tự mở task kế tiếp.

## 10. Execution role và chế độ phối hợp

Role được chọn theo đầu ra của task, không tạo agent riêng chỉ vì repo có một thư mục hoặc công nghệ. Một người/agent có thể giữ nhiều role trong task nhỏ; task lớn chỉ tách role khi ranh giới đầu ra và kiểm tra rõ.

| Role | Context/input bắt buộc | Tool/skill phù hợp | Output, validation và lúc escalation |
|---|---|---|---|
| Control Center / Integrator | Registry, leaf brief/checkpoint, source ownership và output của executors | `rg`, task/delivery docs và native orchestration tools khi runtime có; không cần skill riêng | Chốt scope/dependency, phân write scope, review và tích hợp evidence, cập nhật tracker. Escalate khi thiếu quyết định sản phẩm, quyền external write hoặc dependency không đạt |
| Product / Architecture | Business/REQ, feature/module map, HLD/LLD và affected contracts | Markdown/Mermaid và source architecture hiện có; không tạo runtime config | Decision/impact map có rationale và invariant; review traceability. Escalate khi có hai hướng business/architecture hợp lệ cần người dùng chọn; design không là runtime evidence |
| Content / Learning design | TOP/CAP, authoring template, bilingual standard, rubric, learner persona và nguồn | Content schema/validator, browser review và authoring sources; skill chỉ tạo sau khi workflow lặp đã ổn định | Lesson/rubric/review findings với source và EN–VI tương đương; validate content/schema. Escalate khi thuật ngữ, đáp án hoặc product learning outcome chưa chốt |
| Frontend | FE architecture, route/state/API contract, design tokens, accessibility và relevant fixtures | `scripts/project.ps1`, npm, TypeScript/ESLint/Vite/Playwright và browser inspection | React/TypeScript/UI changes cùng check/build/browser evidence. Escalate khi backend contract, canonical authorization/data policy hoặc learner behavior chưa rõ |
| Backend / Platform | Service/data owner, protocol, migration/transport contract, security gates và command fixture | `scripts/services.ps1`, Maven/JUnit, PostgreSQL/trust/fault harness và ops runbooks | Java/SQL/ops changes cùng DB/contract/fault evidence phù hợp. Escalate khi đổi service boundary, schema ownership, secret/provider hoặc môi trường ngoài scope |
| Verification / Reviewer | Task AC/DoD, candidate/input, changed files và expected evidence | Required command contracts, reports/logs/screenshots và review tooling có sẵn | Findings P0–P3, missing evidence và validation report; không tự sửa registry. Escalate P0/P1, required suite thiếu/skip hoặc claim vượt lớp bằng chứng |

Role dùng tool sẵn có của runtime và skill chỉ khi task thực sự khớp trigger của skill đó. Bảng này là nhãn phân công; nó không tạo agent, sandbox hay permission riêng. Mỗi executor chỉ đọc context được brief dẫn và trả output theo định dạng task yêu cầu.

### Mode A — native subagent

- Dùng khi môi trường thật sự hỗ trợ và các phần có thể làm độc lập. Integrator tạo subtask trong cùng leaf task, không biến mỗi subtask thành một backlog ID nếu nó không có đầu ra độc lập.
- Mỗi subtask phải ghi role, input, expected output, file được phép sửa, validation và khi nào phải escalation. Integrator sở hữu task file, registry và các file tích hợp dùng chung.
- Chỉ chạy write song song khi các tập file không giao nhau. Một file có một writer tại một thời điểm; thay đổi phụ thuộc nhau làm tuần tự. Read-only discovery/review có thể chạy song song.
- Agent trả kết quả không đồng nghĩa đã merge hoặc PASS. Integrator phải kiểm thay đổi, giải quyết xung đột về nghĩa và chạy cổng tích hợp bị ảnh hưởng.
- Chỉ dùng branch/worktree khi repository có Git baseline đã review và task cho phép, theo [Git workflow](platform/repository/git-workflow.md). Khi chưa có baseline, không dựa vào worktree/diff để cô lập; giới hạn write vào các file rời nhau và giữ một integrator.

### Mode B — manual chat coordination

- Dùng khi không có native subagent hoặc người dùng muốn mở chat riêng. Control Center tạo prompt task-specific từ brief; người dùng là người tạo chat và chuyển prompt.
- Chat mới đọc cùng registry/task/checkpoint và không giả có live memory, live state hoặc quyền điều khiển chat khác.
- Kết quả chỉ được chuyển về qua file task/evidence hoặc handoff người dùng cung cấp. Control Center đọc lại output thực tế trước khi cập nhật trạng thái.

### Git isolation và integration

Git chỉ là cơ chế cô lập/integration sau khi repository có baseline hợp lệ; không thay source ownership, task acceptance hoặc checkpoint. Branch/worktree/commit cần quyền riêng ghi trong task; subagent mặc định không commit và integrator sở hữu integration. Khi chưa có `HEAD`, scope review dùng write scope, inventory/checksum và validation thật. Toàn bộ rule về initial baseline, branch naming, staged review, conflict, handoff, remote và thao tác nguy hiểm nằm tại [Git workflow](platform/repository/git-workflow.md), không lặp lại ở đây.

## 11. Handoff bền qua session

File task là gói handoff duy nhất cho một leaf: brief/acceptance ở đầu, bảng DoD/evidence hiện hành và checkpoint mới nhất. Registry chỉ giữ trạng thái, dependency và link. Không tạo bản handoff cạnh tranh trong một chat hoặc file tổng hợp khác.

Khi kết phiên, executor ghi các trường ở mẫu checkpoint mục 5, gồm role/mode/write scope, file thực sự đổi, lệnh và kết quả thật, giới hạn, finding chưa xử lý và bước tiếp theo. Integrator kiểm output trước khi đổi `REVIEW` hoặc `DONE`. Nếu chưa đạt, giữ `IN_PROGRESS`; nếu có điều kiện chặn cụ thể thì ghi `BLOCKED` cùng cách gỡ. Task `DONE` không tự cấp quyền hoặc tự bắt đầu task kế tiếp.

Khi mở phiên mới, đi theo đường `registry → task file → checkpoint mới nhất → source được brief dẫn`; không yêu cầu người dùng chép lại lịch sử chat. Commit/branch/PR chỉ ghi khi thực sự tồn tại, không là điều kiện bắt buộc của handoff. Nếu có Git evidence, checkpoint phải gắn branch/base/commit/working-tree status và validation với revision thật theo [Git workflow](platform/repository/git-workflow.md).

## 12. Ba prompt dùng ngay

### Bắt đầu hoặc tiếp tục Control Center

```text
Đóng vai Control Center của Backend Lab. Đọc AGENTS.md, docs/README.md, docs/delivery-backlog.md và chỉ các source liên quan đến yêu cầu hiện tại. Xác định task/trạng thái/dependency thật, chỉ ra goal, acceptance, scope và execution mode phù hợp. Nếu task chưa READY, hoàn thiện hoặc đề xuất tách brief; nếu đã READY và yêu cầu đã cho phép thực thi, điều phối công việc đến checkpoint. Không tự mở task kế tiếp và không dùng trí nhớ chat thay nguồn trong repo.
```

### Bắt đầu một task session

```text
Thực hiện <TASK-ID> theo brief/checkpoint được registry link. Mục tiêu phiên là <KẾT-QUẢ-CẦN-CHỨNG-MINH>. Chỉ sửa <WRITE-SCOPE>; ngoài phạm vi: <OUT-OF-SCOPE>. Chọn role/mode nhỏ nhất phù hợp, triển khai và chạy các kiểm tra gắn với acceptance. Cập nhật evidence/checkpoint trước khi kết thúc; giữ IN_PROGRESS nếu chưa đủ DoD và không chuyển sang task khác.
```

### Kết thúc và bàn giao task

```text
Kết thúc lượt làm <TASK-ID>. Đối chiếu từng acceptance với file/hành vi và kết quả kiểm tra thật; review scope, finding và giới hạn còn lại. Cập nhật checkpoint trong file task và chỉ đổi registry sang REVIEW/DONE khi điều kiện tương ứng đạt. Báo ngắn: trạng thái, file đổi, quyết định chính, lệnh/kết quả, rủi ro hoặc blocker, bước tiếp theo chính xác và prompt để phiên mới tiếp tục. Không triển khai bước tiếp theo trong lượt này.
```
