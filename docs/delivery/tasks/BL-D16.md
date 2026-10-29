# BL-D16 — Review cấu trúc dự án

04/10/2026. Người dùng yêu cầu review lại cấu trúc repo sau BL-D15. [Registry](../../delivery-backlog.md) giữ trạng thái; đầu ra chính là [báo cáo cấu trúc](../../architecture/project-structure-review.md).

## Goal, context và phạm vi

Đối chiếu cấu trúc thực tế với source architecture FE/BE, service boundaries, cách chia feature và quy trình chất lượng; chỉ ra vấn đề có bằng chứng, đề xuất mức chia phù hợp và owner xử lý.

REQ-13; nền MD06/MD09/MD10 và feature map. Dependency: cấu trúc R00/R01 và phân doc D15 đã có; review không phụ thuộc R04a DONE. Đọc docs/README, registry, D15, checkpoint R04a, source architecture, package/POM/helper/ops/contracts và quality process theo phạm vi, không đọc lại toàn bộ business/nội dung học.

Ngoài phạm vi: chuyển package/file sản phẩm, sửa dependency/runtime/auth, cài framework/tooling, thêm service, qualification R04a, commit/publish hoặc tạo chat/agent. R04a đang có phiên thực thi riêng; review chỉ đọc snapshot, không sửa file mà task đó đang sở hữu.

## Plan

Inventory source/config → đối chiếu build/import/data/test boundaries → phân biệt lỗi hiện có với phần còn PLANNED → ghi findings kèm file/owner/thời điểm xử lý → kiểm file/heading links, task/dependency và cấu trúc thực tế → ghi checkpoint/registry.

## Acceptance và Definition of Done

- D16-AC01: có cây hiện có và cây tổ chức đề xuất, phân biệt hiện có/dự kiến; giữ FE/BE cùng repo và ba service theo MS01.
- D16-AC02: mỗi finding có bằng chứng, tác động, đề xuất và owner; không coi feature/Compose/CI còn PLANNED là lỗi triển khai hiện tại.
- D16-AC03: kiểm parent/child modules, direct runtime dependencies và main Java imports giữa service, layout DB/roles, task/dependency và link tài liệu. Không gọi kiểm tra tĩnh là build/runtime/security PASS.
- D16-AC04: bàn giao thứ tự xử lý phù hợp; chỉ ghi doc review và registry của D16, không chuyển trạng thái runtime hoặc sửa source.

## Checkpoint

04/10/2026 — DONE trong phạm vi review tài liệu/cấu trúc; findings còn cần xử lý theo owner, không được coi là đã sửa.

| Acceptance | Kết quả |
|---|---|
| D16-AC01 | PASS: báo cáo có cây hiện có, ranh giới ownership và convention feature/package đề xuất; phân biệt hiện có/PLANNED. Giữ cùng repo, ba service/DB; không tạo source tree hoặc topology mới. |
| D16-AC02 | PASS: STR-01–05 có bằng chứng, tác động, hướng xử lý và owner. STR-01 Git baseline, STR-02 Python ignore, STR-03 browser prerequisite, STR-04 BE convention và STR-05 test-fixture coupling được phân biệt với thiếu feature chưa tới task. |
| D16-AC03 | PASS kiểm tra tĩnh: ba parent/child POM đúng; đọc 41 main Java files, không thấy cross-service main imports/direct shared runtime dependencies; ba DB/chín roles riêng trong layout. 73 runtime IDs/169 dependency edges và DAG hợp lệ; file/heading links, UTF-8/fences đã kiểm. Git chưa có HEAD/0 tracked files và hai bytecode chưa ignore được xác nhận bằng lệnh Git. Không build/DB/browser/security PASS từ review này. |
| D16-AC04 | PASS: chỉ tạo report/task doc, link ở architecture index và D16 registry row. Không sửa package/POM/helper/test/runtime/qualification manifest, không commit/chat/agent. Báo cáo là snapshot khi R04a đang active ở phiên khác; không dùng thay checkpoint hoặc kết quả kiểm thử của task đó. |

Kiểm tra: `python .tools/project-structure-review/audit.py` — exit0. Script/report cục bộ tại `.tools/project-structure-review/` ghi snapshot module/import/role/dependency/link/Git. Đây là công cụ kiểm của lượt review, chưa là CMD/CI gate của sản phẩm. Không dùng baseline hash D15 để kết luận source không đổi toàn repo khi phiên R04a đang có quyền sửa source.

Kết quả chính: `docs/architecture/project-structure-review.md`; bàn giao review qua architecture index. Thứ tự: R04a đủ AC và prerequisite docs → hygiene/Git baseline trước workflow bị ảnh hưởng → convention BE áp dụng ở leaf mở rộng → FE feature tree theo R14–24 và dependency thực. Findings chưa sửa không ngăn đóng task review, không tự cấp quyền commit/refactor hoặc chuyển runtime DONE.
