# BL-D15 — Tổ chức tài liệu theo feature

Ngày04/10/2026. Người dùng yêu cầu phân bố doc theo feature lớn/nhỏ để làm việc dễ hơn. [Registry](../../delivery-backlog.md) giữ trạng thái.

## Goal và phạm vi

Một điểm vào cho doc dự án; source business/LLD/task/evidence nằm theo owner, có đường đọc ngắn cho từng phiên. Không đổi business/protocol/ID/dependency/trạng thái runtime hoặc nội dung evidence. Không code sản phẩm, agent/chat mới, commit/publish.

## Context và plan

Đọc AGENTS, backlog/checkpoint R04a, REQ-13 trong requirements, feature map/quality; rà inventory và các lệnh đang tham chiếu file. Inventory/hash baseline → phân nguồn theo feature/platform/shared → tách backlog registry/brief/checkpoint → rewrite links/alias cũ → source-content/DAG/protected-input/link review → bàn giao.

## Acceptance và Definition of Done

- D15-AC01: docs/README và feature index bao phủ F01–F12, mỗi feature nối goal/REQ/MD/source/task/DoD; subfeature có content thật mới tách.
- D15-AC02: source được chuyển/tách theo owner, không mất nội dung chuẩn hoặc evidence; tài liệu shared có nguồn duy nhất; old paths chỉ hướng dẫn.
- D15-AC03: registry giữ nguyên73 runtime IDs, trạng thái và169 dependency edges; full brief/AC/checkpoint đã có nằm đúng file task, latest R04a tìm được trực tiếp.
- D15-AC04: local links/anchors hợp lệ, không thay code/runtime/contract/lockfile/input; cập nhật cách đọc ở AGENTS/README/execution model. Tài liệu thuần không yêu cầu build/E2E/auth runtime suite.

## Checkpoint

04/10/2026 — DONE trong phạm vi tổ chức tài liệu. Registry giữ trạng thái chính thức của task.

| Acceptance | Kết quả và bằng chứng |
|---|---|
| D15-AC01 | PASS: một mục lục dự án; 6 nhóm lớn và 12 feature F01–F12. Mỗi feature có goal/scope, REQ/module, source, task, hiện trạng và DoD. Credentials/sessions/publication có phần riêng; không tạo hồ sơ task trống cho runtime chưa bắt đầu. |
| D15-AC02 | PASS: 41 đường dẫn nguồn được chuyển/tách theo owner; 106 phần nội dung gốc được đối chiếu đầy đủ. Nguồn dùng chung giữ một bản; đường dẫn cũ là trang chỉ dẫn tới nguồn mới. Business assessment/progress/storage tách theo trách nhiệm, giữ nguyên số mục và AP IDs. |
| D15-AC03 | PASS: giữ nguyên 73 dòng task runtime, trạng thái và 169 quan hệ dependency; DAG hợp lệ. Brief/checkpoint của R00/R01a–d/R02a–b/R03/R04a nằm ở file task theo owner. Backlog giảm từ 788 xuống 167 dòng. |
| D15-AC04 | PASS: 156 file Markdown, 1207 link nội bộ và 34 link tới heading được kiểm tra. 163 file source/config/runtime/ops/contracts được bảo vệ giữ nguyên SHA256. AGENTS, README, execution model, delivery contract và quality process dẫn theo cấu trúc mới. |

Kiểm tra: `python .tools/doc-reorganization/review.py` — exit0. Script đối chiếu baseline nội dung, SHA256 đầu vào ngoài doc, runtime rows/dependency DAG, file/heading links, UTF-8/fences và cấu trúc README feature. Baseline/manifest/report cục bộ nằm trong `.tools/doc-reorganization/`; đây là bằng chứng lần di chuyển, không phải test runtime hoặc điều kiện build sản phẩm.

Review phạm vi: chỉ tổ chức và điều chỉnh hướng dẫn đọc/cập nhật doc. Không đổi goal/requirement nghiệp vụ, security/data protocol, quyền CODE, source sản phẩm, hợp đồng API, lockfile hoặc trạng thái runtime. Không chạy build/E2E/auth suite vì không thay code. Bằng chứng cũ giữ nguyên kết quả và giới hạn; không suy DONE sản phẩm từ kiểm tra tài liệu.

Bàn giao: bắt đầu tại `docs/README.md` → feature/subfeature → task leaf → source/verification liên quan. R04a vẫn IN_PROGRESS; checkpoint sản phẩm và phần việc opt-in config/wiring/auth TLS/browser còn lại ở `docs/features/f10-identity-access/sessions/tasks/BL-R04a.md`. Không tự chuyển sang R04b/c/d hoặc R02c.
