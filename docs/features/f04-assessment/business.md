# Rubric, hỗ trợ và nghiệm thu

Nguồn BL-D05, giữ số mục/ID khi phân tài liệu ngày04/10/2026. Xem [toàn bộ hợp đồng](evidence-contract.md).

Các phần dùng chung: [mục1/4/5 — ba chiều tiến độ, trạng thái và phiên bản](../f05-progress/business.md); [mục6 — lưu/nhập dữ liệu](../f07-data-lifecycle/assessment-storage.md). Số mục và AP IDs được giữ để đối chiếu thiết kế cũ.

## 2. Rubric và loại bằng chứng

Mỗi assessment được biên soạn có ID ổn định, content/rubric version và ít nhất một tình huống mới so với luyện tập. Rubric khai báo CAP nào được đánh giá, các tiêu chí bắt buộc, bằng chứng quan sát được, loại nguồn được chấp nhận, phần hỗ trợ được phép, feedback cho từng tiêu chí và điều kiện làm lại. D06 điền nội dung cụ thể; không áp một mốc điểm 70%/80% chung cho mọi năng lực.

| Loại evidence | Có thể chứng minh | Giới hạn bắt buộc |
|---|---|---|
| Trả lời/giải thích được đánh giá | Hiểu và chọn đúng trong tình huống mới khi rubric cho phép | Không chứng minh đã chạy Java/Docker hay áp dụng project |
| Quan sát mô phỏng có kiểm tra | Dự đoán/truy vết cơ chế nằm trong phạm vi mô hình | Ghi `simulation`; không nâng lên thực thi thật |
| Thực thi được xác minh | Code/test hoặc lab có kết quả, môi trường và tiêu chí được kiểm tra | Chỉ ghi nếu có output/observation đáng tin trong phạm vi lab; log text tự chép không đủ |
| Tự khai hoặc bằng chứng ngoài app chưa kiểm chứng | Ghi nhận người học đã tự làm, phục vụ ghi chú và xem lại | Hiện rõ `tự ghi`; không tự cấp đạt CAP yêu cầu thực thi xác minh |

Một CAP có thể chấp nhận nhiều loại evidence nếu rubric nói rõ chúng chứng minh **cùng hành vi**. CAP-CTR-005-A có thể được kiểm tra qua sơ đồ/giải thích. CAP-CTR-005-B yêu cầu đối chứng dữ liệu sau recreate trên lab thật: xem mô phỏng hoặc đọc transcript chỉ cho phép trạng thái đã xem/luyện, chưa cấp đạt B. Ở giai đoạn app chưa có runner, hướng dẫn terminal và tự khai vẫn hữu ích nhưng không được gọi là thực thi “đã xác minh”. Nếu một CAP chỉ có nguồn tự khai trong sản phẩm hiện tại, hiển thị chưa xác minh và tách khỏi current mastery được chứng minh.

Mỗi attempt lưu đủ để review kết luận: TOP/CAP, assessment/case ID và version, input/answer cần thiết sau khi loại secret, observation, tiêu chí pass/fail, hỗ trợ đã dùng, loại evidence, nguồn, thời điểm, trạng thái và lý do. Thời gian là metadata, không tự làm bài đạt. Một assessment nhiều CAP trả kết quả **riêng từng CAP**; CAP thiếu một tiêu chí bắt buộc không pass chỉ vì tổng điểm cao. D08 quy định schema/ID và giới hạn dữ liệu chính xác.

## 3. Chính sách hint, lời giải và lần thử

- **Luyện tập** cho phép hint, lời giải và retry bao nhiêu lần phù hợp bài; ghi mức hỗ trợ và feedback để hướng dẫn học. Luyện đúng không tự thành assessment độc lập.
- **Assessment** được nộp trước khi xem feedback/đáp án của tình huống đó. Hint hoặc lộ lời giải **trước khi nộp** làm attempt thành `có hỗ trợ`; không cấp “đạt độc lập” từ attempt này. Vẫn ghi kết quả từng tiêu chí, kiến thức đã chứng minh có hỗ trợ và nội dung cần ôn.
- Xem lời giải **sau một lần nộp độc lập** không đổi ngược kết quả đã lưu của lần đó. Tình huống đã lộ đáp án không được dùng tiếp để cấp một pass độc lập mới.
- Sau fail/hint/lời giải, người học được luyện và làm lại bằng **tình huống đủ khác** do tác giả định nghĩa. Đổi số/ngữ cảnh hời hợt mà giữ đáp án mẫu có thể đoán ngay không được coi là tình huống mới. D06 phải có biến thể hợp lệ trước khi publish assessment có retry độc lập.
- Mỗi lần nộp hợp lệ là một attempt riêng; reload giữa lần chưa nộp khôi phục attempt dở hoặc bắt đầu lại có giải thích. Nhấn Submit trùng/replay không tạo attempt/evidence thứ hai. Nếu engine chấm lỗi, giữ câu trả lời và trạng thái chưa đánh giá, không ghi fail giả.
- Một CAP đạt trong một assessment không làm các CAP khác của cùng activity đạt. Rubric có thể yêu cầu nhiều evidence khác loại; tất cả tiêu chí bắt buộc phải đủ theo contract được công bố. Không ghép các mảnh của nhiều attempt fail thành pass nếu rubric không cho phép.
- Điểm đã đạt từ một attempt trước được giữ trong lịch sử khi fail sau đó. Current mastery chỉ thay đổi qua policy cần ôn/phiên bản ở mục 5, không bị xóa chỉ vì thử lại.

Không đặt hạn chế số lần làm sai, giảm điểm vì học chậm hay dùng hint trong luyện tập. Sự khác biệt là nhãn và giá trị bằng chứng của lần thử, không là hình phạt ẩn. Đường tự đánh giá dùng tình huống mới để tránh học thuộc đáp án.

## 7. Ma trận case nghiệm thu business

“Expected” là kết quả quan sát cần giữ khi cài đặt, chưa là test tự động. D08 phân rã task R1 v2 và chuyển các case rủi ro thành unit/integration/E2E phù hợp; BL-I cũ là baseline lịch sử.

| ID | Tình huống | Expected |
|---|---|---|
| AP-01 | Bấm Next, xem hết hoặc replay mô phỏng | Chỉ thay dấu đã xem/vị trí; không có CAP pass/applied |
| AP-02 | Luyện đúng, dùng hint và xem lời giải | Có attempt luyện + mức hỗ trợ, feedback; current mastery không tăng |
| AP-03 | Kiểm tra Map bằng case mới, không hint, đủ tiêu chí A nhưng thiếu B | CAP-COL-004-A đạt; B còn thiếu, TOP chưa đủ CAP |
| AP-04 | Mở hint trước khi nộp kiểm tra rồi trả lời đúng | Ghi `có hỗ trợ`, không cấp pass độc lập; cho luyện/biến thể khác |
| AP-05 | Xem lời giải sau lần nộp pass sạch | Giữ pass đã lưu; lần nộp lại cùng case không tạo pass độc lập mới |
| AP-06 | Fail rồi làm biến thể khác pass sạch | Giữ cả hai attempt, pass CAP đủ rubric; không nhân đôi khi bấm Submit hai lần |
| AP-07 | Reload khi đang làm, chưa nộp | Tiếp tục attempt dở hoặc hướng dẫn bắt đầu lại; không có pass/fail giả |
| AP-08 | Chấm bài lỗi giữa chừng | Giữ câu trả lời và trạng thái chưa đánh giá; không ghi fail hay pass |
| AP-09 | CAP từng pass nhưng kiểm tra ôn sau đó fail | Pass lịch sử còn; current hiển thị cần ôn, có tiêu chí và đường luyện |
| AP-10 | Bài đổi chữ/ảnh, CAP/rubric không đổi | Giữ current mastery, chỉ xem lại bài nếu tác giả chỉ định |
| AP-11 | Rubric/đáp án CAP đổi đáng kể hoặc có CAP mới | CAP liên quan cần xác minh hoặc chưa đạt; pass cũ còn history; không reset CAP không ảnh hưởng |
| AP-12 | Topic dùng chung hai lộ trình | Một TOP/CAP/attempt; đổi lộ trình không tăng progress/mẫu số |
| AP-13 | Bài planned/draft hoặc bài vừa rút khỏi published | Không gợi ý như bài khả dụng/không vào mẫu số; draft không sinh pass; lịch sử cũ giữ |
| AP-14 | Volume hiểu sơ đồ nhưng chưa có Docker/lab thật | Có thể đạt A nếu rubric cho phép; B chưa xác minh, không ghi đã chạy container |
| AP-15 | Người học tự khai đã deploy/hoàn thành project | Hiện `tự ghi`; readiness/applied verified không được tự tăng |
| AP-16 | Đủ CAP readiness P1 rồi mở project; chỉ mới mở đề | Có thể bắt đầu P1, applied P1 vẫn trống |
| AP-17 | Mất mạng/lỗi server khi nộp hoặc lưu attempt | Tra receipt đúng owner để phân biệt chưa xác nhận/đã nhận pending; T1/T2 atomic, retry không trùng, lỗi chấm không phải fail; xem LD-03–13 |
| AP-18 | Import file sai/version không hỗ trợ hoặc migration lỗi | Không ghi đè dữ liệu tốt; preview và apply atomic, khác owner bị từ chối; dữ liệu hợp lệ chỉ thành draft/unverified, xem LD-21/22/24 |
| AP-19 | Import cùng sourceId hoặc cùng ID khác payload | Dedupe trong vùng import theo owner/nguồn; khác payload hoặc preview stale cần giải quyết/preview lại; không sửa official evidence, xem LD-23 |
| AP-20 | Reset mô phỏng rồi reset dữ liệu học riêng | Reset mô phỏng giữ attempt/evidence; reset dữ liệu học cần luồng xác nhận và chỉ xóa phạm vi đã nêu |
| AP-21 | Đổi version published khi assessment mới chưa sẵn sàng | Lịch sử cũ giữ, current mastery ghi cần xác minh; bài không được gợi ý đánh giá bằng case lỗi thời |
| AP-22 | Bài đã pass CAP khi chưa xem mọi bước | CAP pass có thể hiện, chiều “đã xem” vẫn phản ánh phần thực đã xem |

## 8. Ranh giới bàn giao

| Task | Việc nhận từ thiết kế này |
|---|---|
| BL-D06 | Case/rubric cụ thể cho CAP Collections, biến thể độc lập, feedback/hint, chứng cứ ứng dụng nhỏ |
| BL-D07 | UI ba chiều tiến độ, trạng thái hỗ trợ/chưa lưu/cần ôn, resume và lỗi nhập/lưu dễ hiểu |
| BL-D08 | Hợp đồng Topic/CAP/assessment/event, version impact/migration, dedupe/atomic storage, adapter schema v1, test mapping |
| Task R1 v2 do D08 phân rã | Chấm theo CAP và hỗ trợ, chiếu progress từ evidence, lưu/reload/import không mất dữ liệu; không thực thi BL-I06–I09 cũ |

V2 có [contract dữ liệu server](../f11-learner-work/data-contract.md): nguồn evidence/loại chấm ở mục 3, JSON export/import unverified ở mục 7, reset toàn dữ liệu học theo epoch và retention ở mục 8. AP-01–22 kết hợp IA-01–24 và LD-01–30 cho multi-user/server failure. D08 còn schema/API/config và test triển khai; chưa có runner kiểm chứng Java/Docker.
