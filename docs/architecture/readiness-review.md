# Review độ đầy đủ sau MS04

BL-D14 · 01/10/2026 · Review thiết kế/kế hoạch theo yêu cầu người dùng. [Backlog](../delivery-backlog.md) giữ trạng thái; [implementation](../delivery/implementation-plan.md), [test-release](../delivery/test-release-plan.md), [catalog](service-boundaries.md), [security](security-design.md) và [lifecycle/recovery](lifecycle-recovery-protocol.md) giữ contract hiện hành. Chưa code hoặc kiểm chứng runtime.

Review tìm5 khoảng trống (3P1/2P2) và đã bổ sung kế hoạch xử lý:6dependency edges, base TLS E2E trước FE, edge trust/privacy, backup maintenance interlock, common wire/WIRE fixtures và review UX sớm. Chưa cần thêm service hoặc input business mới; task runtime đầu vẫn R01a khi có CODE. Kiểm cuối/bàn giao ở checkpoint D14, không coi tài liệu là chứng minh backend đã hoạt động.

## Phạm vi và tiêu chí review

Goal: tìm khoảng trống làm task không thể nghiệm thu thật hoặc gây lệch quyền/dữ liệu/trải nghiệm; bổ sung vào nguồn đã có, không thiết kế lại toàn dự án. REQ-09–16/18, MD01/02/06/09/10/11. Input: checkpoint MS04, requirements/module/quality, protocol và backlog v3. Rule kế thừa AGENTS; docs-only, một task, không agent/chat/commit/deploy.

| Acceptance | Cách kiểm / expected |
|---|---|
| D14-AC01 | Walk-through anonymous→login→learn→draft/support→T1/T2→progress; revoke/publication/reset/delete/backup/restore và task/test dependencies |
| D14-AC02 | Mỗi finding có trigger, ảnh hưởng, mức độ, nguồn, owner, cách xử lý và expected; không coi kế hoạch là test runtime |
| D14-AC03 | Sửa nguồn hiện hành/dependency/test owners, giữ business và MS02/MS03 invariants; không tự thêm service hoặc mở scope CODE |
| D14-AC04 | Local links/ID/DAG/trace/test owners/scope kiểm được; checkpoint ghi kết quả, giới hạn và bước tiếp theo |

## Findings

| ID / mức | Khoảng trống và ảnh hưởng | Owner / quyết định |
|---|---|---|
| RV-F01 / P1 | R14/R24a cần CMD-07 backend-linked/TLS nhưng chưa phụ thuộc edge R31a; R03/R06a cần trust/contract R02b, R12 cần Learning bootstrap R01d, R30 cần scan R31c nhưng DAG chưa ghi. R05 dùng CMD-04 trước runner/common harness mà chưa giao tạo catalog layer. Các task có thể không đủ môi trường để nghiệm thu thật. | Thêm6 dependency edges: R14/R24a→R31a, R03/R06a→R02b, R12→R01d, R30→R31c; R05 tự tạo standalone catalog validator/branch. R31a tạo base TLS E2E, R24 thêm auth; SEC-14 receiver R02b không full edge PASS trước R31a. |
| RV-F02 / P1 | CTL-01 yêu cầu upstream TLS/edge identity nhưng topology chưa ghi rõ protocol của public listener8080/cert edge. No-store API cũng chưa quy định request/response temp files tại proxy. Default config không là evidence riêng tư hoặc trust. Nguồn: implementation §3, security CTL-01/12 và test SEC-14/28/29. | R02b tạo receiver public HTTPS/mTLS chỉ edge và private mTLS chỉ service/operator đúng action; R31a pin proxy verification/route/buffering policy và kiểm actual cert/headers/spill. Không thêm edge quyền admission/owner. |
| RV-F03 / P1 | Backup nói quiesce/drain nhưng chưa chốt cách ngăn replica/app tự restart hoặc coordinator crash khiến ghi trở lại giữa ba dumps/journal heads. Edge503 một mình không dừng worker/relay/maintenance writers. Nguồn: lifecycle §5, R32a và FX-08. | R32a checkpoint R1 dừng tất cả app/writer dưới maintenance interlock bền vững, verify không còn DB writer trước capture; interrupted set incomplete, không auto-resume. R32b drill crash/restart/partial-stop, không mở traffic từ liveness. |
| RV-F04 / P2 | Wire chung còn mô tả schema có version nhưng thiếu encoding counter bigint, duplicate JSON keys, error/status/pagination/retry và quy tắc additive cụ thể. DTO provider/FE có thể diễn giải cùng revision khác nhau hoặc retry mutation sai. Nguồn: catalog §4, D08 baseline và CMD-04. | R02b tạo schema/examples/error fixtures chung theo catalog §4.1; R04/07/09/21a và FE client áp dụng khi endpoint được tạo. Counter decimal string chính xác, cursor opaque không là quyền, unknown response field additive khác privileged input. |
| RV-F05 / P2 | Thử learner chính thức ở R34 sau gần toàn release. Nếu chỉ test kỹ thuật, vấn đề app dễ chán/khó hiểu có thể xuất hiện muộn. Prototype D07 và UX fixtures chưa là feedback learner của app backend-linked. | Thêm checkpoint demo/review trải nghiệm vào R15 và R20 khi slice đủ; ghi rõ demo/report hoặc feedback thật, không giả feedback nếu chưa có learner. R34 vẫn nghiệm thu learner cuối; không thêm yêu cầu input chặn local foundation. |

Mức P1 ở đây là lỗ hổng kế hoạch/contract có thể làm sai nghiệm thu hoặc dữ liệu; không tuyên bố đã phát hiện exploit/runtime incident. Quyết định trên nằm trong phạm vi review và giữ ba service, owner server, viewed/proven/applied, journal trước disclosure và admin sau R1. Nguồn đã bổ sung bên dưới; bằng chứng cuối giữ ở checkpoint D14.

## Thay đổi để xử lý findings ở kế hoạch

| Finding | Nguồn đã cập nhật | Evidence runtime còn phải thu |
|---|---|---|
| RV-F01 | Backlog6 deps bổ sung và R05 catalog runner owner; implementation §5, test FX-05/09/CMD-04/07/SEC-14, source FE/BE | R05 standalone validator; R02b trust/common trước R03/R06a; R01d build trước R12; R31a TLS trước FE; R31c scan trước R30; targeted harness không full flow PASS |
| RV-F02 | Implementation §3, security CTL-01/09/14, test SEC-14/28, LLD-07; giữ `/api/v1` qua edge | Receiver cert/R31a actual proxy+sentinel/load/errors/rotation; không config hoặc security suite từ docs |
| RV-F03 | Lifecycle §5.1, R32a AC, FX-08/DATA-24/30/REL-07 và LLD-07 | Actual provider/dump/interlock/coordinator-replica restart/NOLOGIN/drain/stale resume/drill reports R32a/b |
| RV-F04 | Catalog §4.1, R02b AC, WIRE-01–06/REL-02, source FE/BE/LLD-07 | Schema rồi provider/consumer/FE/DB assertions từ owner; cursor key/rotation R21a, không claim bigint import v1 đã migrate |
| RV-F05 | Backlog R15/R20 AC, implementation §5, quality process và LLD-07 | Hai slice review/report khi UI có thật; feedback learner ghi đúng actor/date nếu thử, R34 acceptance vẫn bắt buộc |

5 findings đã có bổ sung thiết kế/owner/expected, không đồng nghĩa controls đạt runtime. D14 không thay MS04 checkpoint lịch sử: runtime graph vẫn73IDs/58leaves, thêm6 dependency edges (163→169); WIRE6 bổ sung SEC30/DATA30 thay vì sửa số test cũ. Mức Ready của code còn theo brief/dependencies/CODE thật.

## Expected khi triển khai

- RV-F01: DAG có bootstrap/trust/scan/edge trước task tiêu thụ và R05 tạo standalone catalog branch; CMD-07 dùng services/edge/cert thật, không thay bằng FE smoke. Missing fixture/suite vẫn fail.
- RV-F02: edge sai cert, upstream sai SAN/CA hoặc browser giả forwarded identity bị reject; edge certificate không gọi INT/OPS. Payload sentinel tại chunked/near-limit/slow upload/export/error không xuất hiện trong proxy temp/cache/log hoặc public artifact.
- RV-F03: coordinator crash, replica restart, writer không dừng, dump/head đổi hoặc key thiếu không tạo complete backup. Maintenance giữ đóng tới verified resume; drain timeout không là success. Chỉ generation/backup ID hiện hành được resume.
- RV-F04: 64-bit revision round-trip exact qua Java/JSON/TypeScript, duplicate key/privileged field/bad UTF-8/invalid counter/cursor bị reject; same-key khác intent conflict, pending không completed, provider N/N-1 tương thích theo policy đã ghi.
- RV-F05: hai demo có checklist hiểu mục tiêu/next step/feedback/progress và trạng thái lỗi; report phân biệt review kỹ thuật với learner thực, finding có owner và regression khi sửa.

## Nguồn và giới hạn

Đọc primary 01/10/2026: [RFC7493](https://www.rfc-editor.org/rfc/rfc7493.html) nêu giới hạn số nguyên chính xác của JSON receiver phổ biến và khuyến nghị string cho số lớn; [Nginx proxy](https://nginx.org/en/docs/http/ngx_http_proxy_module.html) mô tả upstream certificate verification và request/response buffering; [PostgreSQL dump](https://www.postgresql.org/docs/18/backup-dump.html) mô tả snapshot theo từng DB; [role attributes](https://www.postgresql.org/docs/18/role-attributes.html)/[server signaling](https://www.postgresql.org/docs/18/functions-admin.html#FUNCTIONS-ADMIN-SIGNAL) đối chiếu NOLOGIN/confirmed disconnect. Chọn counter string, trust/spill policy và maintenance interlock là quyết định app, không bảo đảm tự có từ nguồn.

Review này không xác nhận actual provider, cipher/cert/config, DB locks, restore, tải, accessible UI hoặc feedback learner đã đạt. Host/provider/key/recovery channel cần ở task tương ứng; không cần chốt chúng để làm R01a local foundation. Phiên bản runtime/advisory vẫn phải kiểm lúc pin/cài, không rà CVE toàn stack chưa tồn tại bằng tài liệu.

## Phạm vi đã đủ và quyết định để đúng giai đoạn

Khung business đã có mục học/CAP→kiến thức/tương tác/luyện tập/kiểm tra/bài toán áp dụng/progress; outline Java Backend/DevOps và shared IDs, Collections là slice chi tiết R1. Service/data ownership, auth/revoke, T1/T2/publication và lifecycle/recovery đã có source. Không cần thêm một service, framework hoặc bộ rule lặp để xử lý5findings này.

| Phần còn cần evidence/đầu vào | Lúc cần / owner |
|---|---|
| JDK patch/checksum/resolved dependencies/advisory, runtime build | R01a rồi bootstrap R01b–d; chưa cần cloud |
| Người học có hiểu và thấy hấp dẫn, UI/accessibility thật | Checkpoint R15/R20 và R34; bản prototype không thay feedback của app |
| Tải/tài nguyên, pause backup và delay auth/journal thực | R31b/R32a/b đo trên host đã ghi; chưa SLA/capacity proof |
| Provider/namespace/keys/verified credential recovery/host-deploy scope | R28b/R32b/R31d, chuẩn bị khi tới task; thiếu không fake PASS |
| Bài chi tiết toàn curriculum, admin, runner Java/Docker | Batch R2/admin sau R1/runner R3; chưa bổ sung vào R1 hoặc dựng95hồ sơ rỗng |

Đây là phần việc đã có owner ở backlog hoặc cổng batch, không đầu vào business mới phải hỏi ngay. R01a vẫn bước runtime đầu tiên khi có CODE; review docs không thay permission hoặc chứng minh chương trình đã chạy.
