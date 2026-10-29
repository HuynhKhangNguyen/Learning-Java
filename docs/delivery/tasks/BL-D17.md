# BL-D17 — Hoàn thiện cấu trúc sau review D16

05/10/2026. Sau review cấu trúc, người dùng yêu cầu “tiếp tục”: thực hiện doc/convention/repo hygiene trong chat này. [Registry](../../delivery-backlog.md) giữ trạng thái; [findings D16](../../architecture/project-structure-review.md) là input.

## Goal, context và phạm vi

Đóng STR-02/03/04: ignore cache Python, hướng dẫn prerequisite browser qualification khớp launcher đã có, thống nhất source architecture BE theo feature. Một đầu ra chính: quy ước cấu trúc và hướng dẫn làm việc có thể dùng ngay.

REQ-13, nền MD06/MD09/MD10; đọc D16, source architecture FE/BE, F10/session README/checkpoint, quality process và launcher/helper hiện có. D16 DONE; R04a DONE là evidence lịch sử, không cập nhật hash hoặc chạy lại qualification trong task này. Registry R04b và các leaf sau thuộc luồng thực thi sản phẩm riêng.

Ngoài phạm vi: commit/Git baseline STR-01, chuyển class/test fixture STR-05, refactor source/package, dependency/lockfile/helper/launcher/runtime/API/migrations, code sibling hoặc tạo chat/agent. STR-01/05 phải còn owner và bước xử lý rõ, không đóng findings chỉ từ doc.

## Plan

Đọc launcher/input-hash boundaries → brief và IN_PROGRESS → ignore Python outputs → source architecture feature convention → một nguồn prerequisite browser và link entrypoints → cập nhật disposition findings → kiểm ignore/source discovery/Node modules/current qualified input hashes/doc links/DAG → checkpoint và DONE.

## Acceptance và Definition of Done

- D17-AC01: `__pycache__/` và `*.py[cod]` được ignore ở mọi cấp; `.py/.mjs` helper source vẫn được Git phát hiện; không xóa cache/source.
- D17-AC02: browser prerequisite có một source, được link từ backend/service/feature entrypoint; ghi đúng Node path hardcoded, lockfile dependencies và installed Edge; không hướng dẫn như thể launcher nhận mọi Node trên PATH hoặc qualification đã portable.
- D17-AC03: Identity/Content/Learning dùng cùng convention feature ở cấp đầu, layers chỉ thêm khi cần; tách source thực tế và cây đích, giữ topology/transaction/service boundaries và không scaffold/refactor.
- D17-AC04: có disposition STR-01–05 và giới hạn; file/heading links/73 task/169 dependency edges hợp lệ; retained R04a qualified input hashes không đổi bởi task; không dùng kiểm tra tĩnh làm runtime PASS.

## Checkpoint

06/10/2026 — DONE trong phạm vi convention/doc/repo hygiene sau closing review. Phần chỉnh sửa đã có ngày05/10; closing khi đó bị gián đoạn bởi shared-input guard phát hiện thay đổi đồng thời. STR-02/03/04 đóng đúng phần được xử lý; STR-01/05 vẫn OPEN/DEFERRED theo disposition D16, không coi mọi finding đã sửa.

| Acceptance | Kết quả và bằng chứng |
|---|---|
| D17-AC01 | PASS: `git check-ignore` kiểm 5 cache/bytecode paths, gồm cache thật trong scripts và nested service; 3 source `.py/.mjs` không bị ignore. Không xóa cache hoặc sửa helper. |
| D17-AC02 | PASS: source prerequisite tại `docs/platform/environment/identity-browser-qualification.md`; backend/service/feature/platform entrypoints dẫn tới nó. Đối chiếu launcher: Node hardcoded ở user cache, Playwright/Ajv từ FE lockfile, channel msedge. Read-only preflight thực tế có Node24.19.0, hai module resolve thành file trong frontend/node_modules và installed Edge154.0.4258.53. Không chạy browser/qualification từ kiểm prerequisite này. |
| D17-AC03 | PASS: architecture BE §2/2.1 thay layer ở cấp đầu bằng feature ở cả ba service, feature nhỏ giữ phẳng; bootstrap api/transport/configuration giữ scope hiện có. AGENTS nối nguồn hiện hành; D16 giữ snapshot cũ và có disposition ngày05/10. Không tạo folder/source/package/DB/service mới. |
| D17-AC04 | PASS đúng phạm vi doc: audit ngày06/10 đọc46 main Java files, không cross-service import/direct shared runtime dependency; ba modules/DB/chín roles,73runtime IDs/169dependency edges và DAG hợp lệ. 162Markdown/1264local links/40heading links hợp lệ. 64 stable protected inputs gồm Identity/locked inputs giữ nguyên; hai shared harness paths được quan sát riêng, `services.ps1` đã đổi trong luồng R04b. Hai retained qualification input-hash files giữ nguyên; write paths D17 không giao qualified input paths. Không claim stationary whole tree/current runtime qualification. |

Lệnh kiểm cuối06/10: `python .tools/project-structure-followup/audit.py` và `python .tools/project-structure-followup/verify_followup.py` — exit0 ngoài restricted sandbox. Audit PASS tĩnh; follow-up trả `PASS_DOC_SCOPE_WITH_SHARED_INPUT_DRIFT`, không runtime PASS. Kiểm read-only Node/module/installed Edge và `git check-ignore -v`/source discovery đã chạy. Script/report/baseline cục bộ trong `.tools/project-structure-followup/`, không CMD/CI gate hoặc runtime evidence mới.

Giữ thất bại thật: ngày05/10 whole-input guard ban đầu exit1 vì `scripts/services.ps1` đổi trong lúc phiên R04b active; `.tools/project-structure-followup/concurrent-hash-failure.json` giữ kết quả. Kiểm cuối tách64 stable inputs khỏi hai mutable shared harness paths, vẫn assert input manifests/disjoint write paths/ignore/prerequisites, ghi drift explicit thay vì cập nhật baseline để giả qualifier PASS. Ngày06/10 chạy trong sandbox cả hai script bị WinError5 tại `Path.resolve`; chạy lại ngoài sandbox cùng user chỉ kiểm tĩnh và ghi report trong repo đã đạt, không coi sandbox failure là lỗi runtime hoặc claim đã sửa sandbox.

Giới hạn qualification: retained final R04a manifests có58 DB/64 contract inputs. Tại lúc đọc, `scripts/services.ps1` và `scripts/db_lab.py` đã khác hashes trong cả hai snapshot cũ; chúng đang phục vụ luồng thực thi tiếp theo và không được D17 sửa. Lượt này xác nhận doc write paths không giao input sets, giữ nguyên manifests và kiểm current protected hashes; không tuyên bố toàn source hiện tại vẫn khớp qualification R04a hoặc sửa hashes để qua gate. Khi qualifier cần dùng current shared harness, owner phải thu regression evidence cho candidate phù hợp.

Bàn giao: đọc source BE convention khi leaf mở rộng feature; đọc prerequisite browser trước CMD04 Identity trên checkout mới. Git baseline còn cần lượt được giao commit; fixture cleanup cần task có suite/POM/evidence và qualification riêng. Không tự thực thi R04b/c/R02c/R04d, commit/publish, hoặc tạo chat/agent. Task sản phẩm tiếp theo giữ owner/checkpoint/DAG hiện hành.
