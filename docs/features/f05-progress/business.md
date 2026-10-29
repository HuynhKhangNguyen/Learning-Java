# Tiến độ và phiên bản

Nguồn BL-D05, giữ số mục/ID khi phân tài liệu ngày04/10/2026. Xem [toàn bộ hợp đồng](../f04-assessment/evidence-contract.md).

Các phần dùng chung: [mục2/3 — rubric, evidence và hỗ trợ](../f04-assessment/business.md); [mục6 — lưu/nhập dữ liệu](../f07-data-lifecycle/assessment-storage.md); [mục7 — ma trận AP nghiệm thu](../f04-assessment/business.md#7-ma-trận-case-nghiệm-thu-business). Mỗi phần có một source, không sao chép hợp đồng sang nhiều feature.

## 1. Ba chiều nguồn sự thật

| Chiều | Ghi nhận ở cấp nào | Khi nào thay đổi | Không suy ra |
|---|---|---|---|
| Kiến thức đã xem | Bài/bước bắt buộc của TOP và version | Người học thực sự mở/xem đến điểm kết thúc do bài định nghĩa; xem lại không nhân thêm số bài | Đã hiểu, đã đạt CAP, đã chạy lab |
| Năng lực đã chứng minh | Từng CAP, rubric version, evidence hợp lệ | Kết quả kiểm tra **tình huống mới** thỏa tiêu chí CAP với loại evidence và mức hỗ trợ được rubric chấp nhận | Đã áp dụng project hoặc đạt các CAP khác trong cùng bài |
| Đã áp dụng | Bài tổng hợp/milestone và tiêu chí riêng | Có submission/observation đạt rubric áp dụng; gắn TOP/CAP liên quan | Hoàn thành mọi CAP của TOP hoặc mọi mốc project |

Lưu ba chiều riêng. Chuỗi “chưa học → đang học → đã học kiến thức → đạt kiểm tra → đã áp dụng” ở business design là **nhãn tóm tắt**, có thể bỏ qua một nhãn: người biết sẵn được kiểm tra đầu vào và đạt CAP mà chưa xem mọi bài. Màn tiến độ luôn cho xem từng chiều và nguồn bằng chứng, không chỉ một nhãn hoặc phần trăm tổng. “Cần ôn” là nhãn phủ lên trạng thái hiện tại, không xóa lịch sử đã đạt.

Một TOP có thể có CAP-A đạt và CAP-B chưa đạt. Chỉ hiện “đạt các năng lực mục này” khi mọi CAP mục tiêu được công bố cho version hiện tại đã đạt theo rubric; vẫn hiển thị CAP hỗ trợ/chưa đánh giá riêng. Gói Collections tổng hợp từ TOP-COL-002/003/004/006 không có một mastery thứ hai. Bài dùng chung Java Backend/DevOps cũng chỉ có một TOP/CAP/version và một tiến độ.

**Điều kiện sẵn sàng** học mốc project lấy từ CAP prerequisite, còn **đã áp dụng** cần bằng chứng của chính milestone. Đủ điều kiện không tự ghi project hoàn thành; hoàn thành project không tự cấp các CAP nằm ngoài phần thực sự được đánh giá.

## 4. Chuyển trạng thái và cách hiển thị

| Sự kiện | Kiến thức | Năng lực | Áp dụng / nhãn |
|---|---|---|---|
| Mở TOP/bắt đầu hoạt động | `đang học`, nhớ vị trí | Không đổi | Không đổi |
| Xem đủ các phần bắt buộc | `đã xem` trên version đó | Không đổi | Không đổi |
| Làm luyện tập, kể cả đúng/hint/lời giải | Ghi phần đã xem/luyện, mức hỗ trợ | Không đổi current mastery | Có thể gợi ý assessment |
| Nộp assessment pass một CAP độc lập, đúng loại evidence | Không tự đánh dấu đã xem nếu chưa xem | CAP đó `đạt` với evidence/version | Tính lại readiness từ CAP, chưa ghi áp dụng |
| Nộp assessment pass có hỗ trợ hoặc sai một tiêu chí | Giữ dấu đã xem | Không cấp mới; ghi kết quả/hỗ trợ, CAP thiếu cần ôn | Đưa đúng bài ôn/biến thể tiếp |
| Nộp assessment nhiều CAP, chỉ một CAP đạt | Không tự thay đổi | Chỉ CAP đạt được ghi đạt | TOP còn thiếu CAP khác |
| Áp dụng project đạt rubric | Không tự đánh dấu đã xem | CAP chỉ đổi nếu rubric project tuyên bố và chứng minh riêng | Ghi milestone/applied theo loại evidence |
| Bỏ qua nhờ kiểm tra đầu vào mới | Giữ `chưa xem` nếu chưa xem | CAP đủ rubric được đạt | Gợi ý bước kế tiếp phù hợp |
| Reset mô phỏng / xem lại | Giữ lịch sử đã xem | Giữ attempt/evidence | Chỉ reset trạng thái thí nghiệm |
| Fail sau một pass cũ | Giữ lịch sử | Pass cũ còn xem được; thêm cờ `cần ôn` có lý do | Gợi ý ôn, không xóa áp dụng cũ |

Nhãn tổng hợp được tính từ ba chiều và trạng thái bài đang available. Nếu một CAP cần ôn, màn không được in “thành thạo hiện tại” mà không kèm cảnh báo; lịch sử “đã đạt trước đây” vẫn xem được. Không có một phần trăm toàn lộ trình trộn planned content, bài xem và project vào cùng mẫu số.

## 5. Phiên bản nội dung và lịch ôn

Tác giả xuất bản thay đổi cùng `contentVersion`, `rubricVersion` và tác động trên từng CAP: **không ảnh hưởng năng lực**, **cần xem lại bài**, hoặc **cần kiểm tra lại CAP**. Ngày/nguồn review kỹ thuật phải là thật. Đổi câu chữ/giao diện thuần giữ current mastery; đổi đáp án, rubric, phạm vi CAP hoặc lỗi kỹ thuật ảnh hưởng kết luận khiến CAP liên quan thành `cần xác minh lại`. Không đặt lại hàng loạt theo số version. Nếu thêm CAP mới, CAP đó bắt đầu chưa đạt; các CAP cũ giữ bằng chứng còn hợp lệ. ID bị thay/tách cần quan hệ di trú và lịch sử ở D08, không tái sử dụng ID cũ cho nghĩa khác.

Để người học biết khi nào ôn, mỗi CAP có `reviewReason` và `reviewDue` theo sự kiện:

| Sự kiện | Hẹn ôn hiển thị |
|---|---|
| Attempt fail/hint/lời giải trước khi chứng minh độc lập | Có thể ôn ngay, gợi ý đúng tiêu chí thiếu và biến thể mới; không spam lại cùng case |
| Sau khi vừa đạt CAP | Đề xuất một lần nhắc ở **phiên học kế tiếp** có bài published phù hợp; khi đã ôn thành công hoặc bỏ qua chủ động, không hiện lặp trong cùng phiên |
| Kiểm tra ôn pass độc lập | Đóng nhắc hiện tại; các nhắc định kỳ chỉ theo lịch của mục nếu tác giả đã định nghĩa và review, không tự đặt khoảng ngày chung |
| Kiểm tra ôn fail | Giữ pass lịch sử, đặt `cần ôn` với tiêu chí thiếu và đường luyện, hẹn ôn lại khi có biến thể mới |
| Nội dung đổi có tác động cần kiểm tra | `cần xác minh lại` ngay khi version published và có assessment mới; nếu chưa có đường đánh giá, báo đang biên soạn |
| Người học đánh dấu ôn sau | Chỉ hoãn nhắc, không biến CAP chưa đạt thành đạt |

Nhắc ôn được đề xuất từ **nội dung published và đường prerequisite dùng được**; draft/planned không xuất hiện trong “bài nên học tiếp” hoặc mẫu số nội dung hiện có. Lịch định kỳ theo ngày, nếu cần, được cấu hình khi có bài/biến thể cụ thể và kiểm chứng với người học; bản này không gán interval tùy tiện cho mọi kỹ năng. Readiness có thể hiển thị kiến thức từng đạt nhưng đang cần xác minh lại; quy tắc sử dụng nó để mở project được giải thích rõ và không âm thầm coi đó là current mastery sạch.

