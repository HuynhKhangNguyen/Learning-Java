# Backlog cũ và checkpoint tổ chức — lịch sử

Không tự thực thi baseline BL-I/TASKS cũ. [Registry hiện hành](../../delivery-backlog.md).

### Baseline BL-I01–14 cũ — chỉ để đối chiếu, đã bị thay thế

Các trạng thái PLANNED trong bảng dưới là snapshot kế hoạch local-only trước thay đổi v2, không phải danh sách thực thi hiện hành. Không tiếp tục I01–I14 theo dependency cũ. D08 phải lập lại chuỗi backend/auth/data trước khi cho task code READY.

Các task dưới là khung phân rã triển khai, chưa READY. BL-D08 phải bổ sung hợp đồng và tách task nào vẫn quá lớn. Mỗi task code thêm test phù hợp với acceptance; không để toàn bộ kiểm thử đến I14.

| Task / trạng thái | Goal và đầu ra chính | Feature / module | Phụ thuộc | Nghiệm thu cốt lõi / kiểm tra |
|---|---|---|---|---|
| BL-I01 · PLANNED | Catalog mới + adapter fixture cũ | F08 / MD01 | D09 + cho phép code | Topic/competency/reference/version validated; fixture sai bị chặn; API 500 giữ draft; unit validator + check/build |
| BL-I02 · PLANNED | Journey → trang mục Collections | F01 / MD01,02,09 | I01 | K/S/topic nối đúng; prerequisite/ôn và planned/published rõ; deep-link/Back/keyboard; E2E luồng bị đổi |
| BL-I03 · PLANNED | Player đọc dữ liệu mẫu, điều khiển bước | F02 / MD03,09 | I02 | Next/Back/reset đúng phạm vi; input trong phiên không mất; text alternative; transition + UI test |
| BL-I04 · PLANNED | Mô phỏng Collections tích hợp player | F02 / MD03 | I03 | Thêm/xóa/tìm, trùng/equality/order đúng mô hình; đổi input thấy hệ quả; deterministic/replay và label simulation |
| BL-I05 · PLANNED | Luyện tập và feedback/hint | F03 / MD04,09 | I04 | Đúng/sai/retry/hint theo dữ liệu; ghi support; xem lời giải không tự cấp mastery; unit + E2E |
| BL-I06 · PLANNED | Assessment Collections → kết quả theo năng lực | F04 / MD04,09 | I05 | Tình huống mới, rubric pass/fail và evidence versioned; không pass bằng Next; invariant + E2E |
| BL-I07 · PLANNED | Progress chiếu từ evidence trong phiên | F05 / MD05 | I06 | Ba chiều, dedupe, shared/version/cần ôn theo D05; không cấp toàn nhóm K; unit ma trận |
| BL-I08 · PLANNED | Lưu learner/session state và resume an toàn | F05,F07 / MD06 | I07 | Reload khôi phục; dữ liệu hỏng/quota/save fail không ghi đè bản tốt; memory fallback có thông báo; unit failure + E2E |
| BL-I09 · PLANNED | Export/import và migration | F07 / MD06,09 | I08 | Validate trước ghi; lỗi không mất dữ liệu; version không hỗ trợ xử lý rõ; roundtrip/negative/atomicity tests |
| BL-I10 · PLANNED | Home tiếp tục học/ôn và progress view | F05 / MD02,05,09 | I08 | Next chỉ published có đường vào; giải thích cần ôn; resume đúng; không trộn coverage với progress; E2E |
| BL-I11 · PLANNED | Áp dụng nhỏ và readiness P1 | F06 / MD07,05,09 | I07,I08 | Mốc đủ bắt đầu khác đã hoàn thành; evidence tự khai/mô phỏng ghi đúng loại; không giả đã chạy Java; case + UI |
| BL-I12 · PLANNED | Review và xuất bản mục mẫu | F08 / MD01 | I04–I11 | Nội dung/runtime/đáp án đồng nhất, nguồn/version/review thật; published không thiếu đường vào; validator + technical review |
| BL-I13 · PLANNED | Audit toàn luồng và khả dụng | F01–F08 / MD09 | I12 | Keyboard/focus, 320px/desktop, theme/reduced motion, failure states; baseline tải có môi trường; full check/build/E2E và visual review |
| BL-I14 · PLANNED | Learner test, sửa finding và nghiệm thu R1 | F01–F08 / toàn mẫu | I13 | Người học thử thật; ghi điểm vướng, xử lý lỗi chặn; regression theo sửa đổi; không tự đánh dấu learner test xong nếu chưa có phản hồi |

Phạm vi loại trừ local-only của BL-I01–14 đã hết hiệu lực. R1 v2 bao gồm backend/login/logout và lưu server; vẫn loại trừ admin, AI tutor, runner thật và toàn bộ ngân hàng bài. Nội dung published tối thiểu do D06/D07 xác định. Hosting/deploy phục vụ web/API/DB được thiết kế ở D08, chưa chọn nhà cung cấp.

## Đối chiếu lịch sử

| Cũ | Hiện hành |
|---|---|
| B01 | Business design đã lưu, giữ nguyên |
| B02 | Khung 23 nhóm/11 chặng đã có; D01–03 hoàn thiện catalog cấp mục |
| B03 | D04–06 template + assessment/progress + mẫu |
| B04 | D07 trải nghiệm và nghiệm thu |
| B05 | D08–09 tác động kỹ thuật, phân rã và review |
| T01–T05 DONE | Tài sản/spec/schema/UI hiện có; không đánh dấu lại DONE cho business mới |
| T06–T23 cũ | Thay hướng thực thi bằng BL-I sau R0; chức năng ngoài mẫu chuyển batch sau |
| T24–T35 cũ | Đối chiếu trong BL-E, chưa tự chuyển thành task READY |

## Checkpoint tổ chức dự án — 23/09/2026

- Hoàn tất yêu cầu thiết kế cách chia việc: thêm AGENTS, requirements có ID, feature/module map, execution model và backlog này; không thực hiện D01 trong cùng đợt lập kế hoạch.
- Kiểm tra ngày 23/09/2026: script rà 10 tài liệu đạt link local/whitespace; đủ 13 requirement, 9 feature, 9 module, 9 task thiết kế, 14 task triển khai và 4 epic; dependency task tồn tại, không có vòng; chỉ D01 READY. Đã rà nguồn điều phối, ranh giới scope và đối chiếu legacy. Không chạy test app vì không sửa code; phép kiểm tra cấu trúc không thay thế review nội dung/learner test.
- Việc kế tiếp chính xác: BL-D01; sản phẩm chưa thay đổi code trong đợt này.

