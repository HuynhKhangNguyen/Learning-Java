# Mẫu chuẩn bị task và session

> MS04 v3: ví dụ R01a là task leaf đầu tiên theo [implementation plan](implementation-plan.md); R01 là parent ba app, không thực thi nguyên parent. [Backlog](../delivery-backlog.md) giữ trạng thái và CODE permission, mẫu này không tự cho phép code.

BL-D11 · 30/09/2026. Điền khi task sắp bắt đầu, không tự tạo session hoặc goal công cụ. [Backlog](../delivery-backlog.md) là nguồn trạng thái; [delivery contract](contract.md) định Ready/Done. Không cần copy toàn bộ rule chung vào brief.

Trạng thái bàn giao: implement/fix đã validation và commit candidate chờ Control Center review thì
`NEED_REVIEW`; còn việc thì `IN_PROGRESS`. Review yêu cầu sửa quay về `IN_PROGRESS`, nghiệm thu đủ
DoD mới `DONE`. NEED_REVIEW thay tên REVIEW trong lifecycle hiện hành.

## Mẫu dùng cho code, thiết kế và nội dung

```text
Task ID / release / feature / module:
Goal: Sau task, ... có thể ...; chứng minh bằng ...
Đầu ra chính / nơi lưu:
Phạm vi / ngoài phạm vi:
Requirement IDs / TOP-CAP nếu là nội dung:
Dependency và bằng chứng đã đạt:
Quyền thực thi: docs-only hoặc chỉ dẫn CODE đã ghi ở đâu:
Loại phiên: Control Center | implementation | review
Execution role / owner:
Execution mode: single executor | native subagent | manual chat
Write scope được phép sửa / file integrator sở hữu:
Kế hoạch delegation và thứ tự tích hợp, hoặc lý do không cần delegation:

Context cần đọc (file + phần cụ thể + lý do):
1. AGENTS và task/checkpoint gần nhất
2. REQ/LLD/policy liên quan
3. Code/schema/fixture hiện có; điểm chưa có cần tạo
Rule kế thừa: AGENTS + quality-process + policy được dẫn
Rule đặc thù / invariant không được phá:
Giả định / quyết định mở / owner và lúc cần giải quyết:

Plan:
1. Kiểm đầu vào và hiện trạng
2. Thực hiện lát cắt trong scope
3. Kiểm ca đúng/sai/biên/lỗi tương ứng
4. Review, cập nhật hợp đồng và checkpoint

Nếu chạy song song: mỗi subtask ghi input, expected output, write scope không giao nhau, validation và điều kiện escalation. Một file chỉ có một writer tại một thời điểm; Control Center/Integrator giữ task file, registry và quyết định tích hợp.

Acceptance:
AC-01: hành vi/đầu ra quan sát được
AC-02: ...
Ma trận AC → cách kiểm → expected → nơi ghi evidence:
Lệnh kiểm tra: command đã xác minh tồn tại, cwd/runtime/env/fixture
Nếu command chưa có: ghi việc tạo/chọn command thuộc bước nào, chưa gọi đã chạy
DoD: AC ở trên + DONE-01–05; điều kiện bổ sung nếu có

Handoff:
- Task/status và role/mode/write scope đã dùng
- Việc hoàn thành và file thực sự thay đổi
- Quyết định triển khai quan trọng
- AC đạt/còn thiếu; command/review và kết quả thật
- Finding, giới hạn, blocker và cách gỡ
- Dependency/follow-up; commit/branch/PR nếu thực sự có
- Context tối thiểu và bước tiếp theo chính xác cho phiên sau
```

Brief và checkpoint trong file task là nguồn handoff bền. Báo cáo của subagent hoặc nội dung chat chỉ là đầu vào cho integrator cho tới khi đã được review và ghi vào task/evidence; không tạo thêm file handoff cạnh tranh.

## Ví dụ đã điền: BL-R01a — toolchain và build contract

**Goal:** có toolchain/wrapper/parent build tái lập và contract helper cho ba app, làm đầu vào bootstrap từng service. Brief này được dùng cho R01a; [backlog](../delivery-backlog.md) giữ trạng thái thực và chỉ dẫn CODE ngày 01/10/2026. Không tạo app/auth/DB hoặc tuyên bố app tests pass khi mới validate parent.

| Trường | Nội dung |
|---|---|
| Release/module/REQ | R1 nền MD06, REQ-13; hỗ trợ technical foundation cho REQ-09/14/15/16, không nghiệm thu toàn REQ |
| Đầu ra | backend/pom.xml/wrapper, build/helper command contract và deps manifest — paths dự kiến; không ba Java entrypoints |
| Dependency | D09/R00/MS01–04 và D14 đạt trong scope; người dùng cho phép bước tiếp theo 01/10/2026 sau handoff R01a; chỉ dẫn thực ghi ở backlog |
| Context | AGENTS; R01a/backlog và checkpoint MS04; implementation-plan §2/3/5; architecture BE §2; test-release CMD-02; helper/package/lockfile hiện có |
| Rule | Giữ FE/framework/lockfile; Maven parent conventions không executable/shared domain; không nonexistent child module refs; không auth/DB/app code/commit/deploy |
| Quyết định cần xử lý | JDK patch/distribution/platform/hash, wrapper checksum/advisory + parent/BOM resolved metadata; owner R01a, actual dependency tree bổ sung lúc R01b–d |

Plan: inventory toolchain/helper/FE hashes → chọn/pin verified JDK/Maven/Boot và record nguồn/advisory → tạo parent/wrapper/build command contract → wrapper version/parent validate → scope/hash/checkpoint → bàn giao R01b bootstrap Identity. Máy hiện có Java17, thiếu Maven; chọn JDK21 portable trong cache repo, wrapper tải Maven có checksum, không đổi Java/PATH hệ thống. Tạo thư mục/file khi có nội dung, parent thêm module từng child được tạo.

| AC | Cách kiểm / expected | Evidence |
|---|---|---|
| R01a-AC01 | Runtime/parent/BOM/wrapper versions/checksums xác định, no latest; advisory checkedAt | Version output + deps manifest/primary sources |
| R01a-AC02 | Wrapper `--version`, parent `-N validate` thành công, dùng runtime đã ghi | Command/exit/log; không claim bootstrap/app-test pass |
| R01a-AC03 | Selected-service command/helper contract rõ; missing module/suite không false PASS, parent không executable/shared domain | Build config/runner contract review; nonexistent modules chưa cần tạo |
| R01a-AC04 | FE lockfile/framework/hành vi không đổi; ngoài scope không app/auth/DB, đủ DONE-01–05 | Input hashes/scope/handoff R01b |

Command R01a: wrapper `./mvnw.cmd --version`, `./mvnw.cmd -N validate` cwd backend Windows; root helper `scripts/services.ps1 -Task toolchain`/`-Task validate`. Linux `./mvnw` có source, chưa qualified. [Bằng chứng nền build](../platform/toolchain/verification.md) ghi kết quả thực/JAVA_HOME/network, backlog giữ trạng thái. Selected child `-pl services/identity-service -am clean verify` và bootstrap tests thuộc R01b sau tạo app; missing module hiện phải nonzero. Build bị chặn thì task chưa DONE, không thay bằng FE PASS.

## Áp dụng cho task biên soạn mục học

Thay context code bằng catalog TOP/CAP + [topic template](../features/f08-content/authoring/topic-template.md) + rubric/progress policy + nguồn kỹ thuật. Acceptance phải ghi rõ đầu ra là design-complete hay published. Bổ sung CAP→lesson→practice→assessment→apply, input/expected và giới hạn mô hình. Không dùng goal “viết xong topic” nếu chưa chỉ ra phạm vi và bằng chứng độ đúng.
