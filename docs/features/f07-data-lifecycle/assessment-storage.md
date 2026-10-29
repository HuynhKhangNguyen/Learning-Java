# Lưu và chuyển dữ liệu assessment

Nguồn BL-D05, giữ số mục/ID khi phân tài liệu ngày04/10/2026. Xem [toàn bộ hợp đồng](../f04-assessment/evidence-contract.md).

## 6. Lưu phiên, nhập dữ liệu và nội dung dùng chung

**Điều chỉnh v2 ngày 26/09/2026:** R1 có backend theo [system overview](../../system-overview.md). Các nguyên tắc bên dưới áp dụng trong phạm vi từng user. Backend là nguồn kết quả chính thức và dữ liệu bền vững; bản local/nháp không được tự tạo server mastery. BL-S02/S03 bổ sung quyền, version/concurrency và import trước D08. DONE D05 cũ là bằng chứng lịch sử của business v1, không chứng minh contract multi-user đã đầy đủ.

- Một nguồn progress theo user + TOP/CAP stable ID. Hai lộ trình chỉ có vị trí/đường dẫn riêng, không sao chép attempt, evidence hay mẫu số. Một activity dùng nhiều nơi có một identity; server kiểm ownership, user A và B luôn có tiến độ riêng.
- Planned/draft vẫn cho xem đề cương. Chỉ nội dung published mới được ghi đã xem/assessment hiện hành; draft cũ đã từng published giữ lịch sử nhưng tạm không gợi ý như bài học dùng được. Không cấp CAP pass từ bài chưa xuất bản.
- Khi mạng/storage lỗi, có thể tiếp tục mô phỏng và giữ nháp tạm đúng owner; UI báo **“chưa lưu lên server”**. Chưa được backend chấm/xác nhận thì không cấp pass chính thức. Retry phải kiểm phiên/owner, chống nộp trùng và conflict theo revision; không phát lại event cấp mastery từ client. Logout không chuyển nháp sang user tiếp theo; dữ liệu server tốt vẫn được giữ.
- Import được kiểm tra cấu trúc/version/reference và preview tác động **trước khi ghi**. Import sai hoặc migration fail giữ nguyên snapshot tốt. Import hợp lệ không tự nâng `simulation`/`selfReport` thành `verifiedExecution`, không tạo CAP pass từ sự kiện thiếu rubric hoặc nguồn. Đụng ID evidence với payload khác là xung đột cần giải quyết hoặc hủy; không đoán bên nào đúng. Cùng event được dedupe, lịch sử cũ được bảo toàn.
- V2: schema hợp lệ chưa chứng minh nguồn đáng tin; file do client gửi không được ghi official pass chỉ vì có đủ trường rubric. [Contract server BL-S03](../f11-learner-work/data-contract.md) chỉ cho import merge thành nháp mới/lịch sử unverified, không replace và không ghi vào official evidence. Dedupe ID trong vùng import riêng, không sửa ID evidence server; backup server do operator phục hồi là luồng khác.
- Tiến độ khai trong file chỉ là lịch sử unverified, không dùng tính current mastery. Chỉ nguồn server đáng tin được rebuild theo quy tắc mục 5 và version hiện hành; giữ lịch sử và lý do thay đổi. S03 chốt JSON export, preview/atomic apply; D08 chốt schema và adapter v1, không để import hỏng phá dữ liệu tốt.
- Progress trước R1 từ `LessonProgress` schema v1 chỉ là kiểu và draft API 500; không tự chuyển một bước đã xem hoặc `evidence` cũ thành CAP mới. D08 quyết định adapter/migration có kiểm chứng, có đường đọc/xuất dữ liệu cũ khi không ánh xạ được.
- Input/answer chỉ lưu phần cần cho đánh giá/debug; không thu password/token/secret từ bài. Mô phỏng reset không xóa lịch sử, thao tác xóa dữ liệu học là luồng riêng có phạm vi rõ và xác nhận.

