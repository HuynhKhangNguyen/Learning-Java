# Backend Lab — Business design được chốt

Người dùng đã đồng ý hướng high-level design: lấy mục học và năng lực làm trung tâm; triển khai trọn một mục nền tảng trước khi mở rộng. Tài liệu này là hướng sản phẩm hiện hành, ưu tiên hơn các phần khác biệt trong kế hoạch T01–T35 ban đầu. Việc triển khai code đang tạm dừng để hoàn thiện thiết kế business.

## 1. Mục tiêu và người học

Giúp người đã biết lập trình cơ bản nhưng mới học Java/backend tự học đến mức xây dựng, kiểm thử, triển khai và vận hành một backend. Người học nhìn được toàn cảnh, hiểu cơ chế bằng tương tác, luyện tập có hỗ trợ, kiểm tra bằng tình huống mới và theo dõi năng lực đã chứng minh.

Điều chỉnh 26/09/2026: hệ thống nhiều người dùng, có backend, login/logout, lưu bài làm và tiến độ trên server ngay R1. Người dùng đã chọn admin để giai đoạn sau. Xem [system v2](system-overview.md) và REQ-14–18; quyết định này thay thế phạm vi local-only trước đây, chưa là chỉ dẫn triển khai code.

Hai lộ trình là Java Backend và DevOps. Docker thuộc roadmap DevOps; Java Backend nối vào phần vận hành cần thiết theo mốc project. Nội dung/tiến độ dùng chung theo từng user, không nhân bản bài Docker. DevOps có đường vào riêng từ hệ điều hành/mạng/Git, không phải đợi hoàn thành Java. Khung ở [roadmap DevOps](curriculum/devops-learning-path.md).

Refinement recovery 30/09/2026 (MS03, chưa code): case/hint/solution có thể hiện “đang chuẩn bị” tới khi dấu exposure durable ngoài snapshot; restore không làm case đã biết thành độc lập mới. Restore hệ thống tách khỏi learner import, giữ history/epoch/tombstone và buộc restored account cấp credential mới để mật khẩu cũ không hoạt động lại. Publish có “đang đồng bộ” khi Learning fenced, không current mastery từ policy cũ. Đây là xử lý lỗi để giữ cam kết học/owner, không mở rộng mục tiêu, admin hay runner; chi tiết [publication](features/f08-content/publication/protocol.md)/[recovery](architecture/lifecycle-recovery-protocol.md).

## 2. Cấu trúc chương trình

Quyết định14/10/2026: nội dung học là giá trị trọng tâm, được biên soạn English–Vietnamese theo [chuẩn song ngữ](features/f08-content/authoring/bilingual-standard.md). Giữ thuật ngữ English chuẩn, lý thuyết đủ và minh họa có ý nghĩa; hai ngôn ngữ dùng chung năng lực/tiến độ. Bài mẫu thử trước khi mở rộng R1; chưa coi toàn curriculum đã song ngữ.

Quyết định14/10/2026 bổ sung: language là preference toàn frontend, switch giữa Tiếng Việt và English áp dụng cả shell, roadmap, trạng thái và bài học. Mode Tiếng Việt dùng câu giải thích tự nhiên nhưng giữ thuật ngữ kỹ thuật English như request, response, HTTP method, header, body, status code, authentication, authorization và idempotency. Đổi language không tạo bài/CAP/progress mới hoặc reset state đang làm. Prototype lưu preference local; đồng bộ theo account nếu cần thuộc task profile sau.

| Cấp | Vai trò | Ví dụ |
|---|---|---|
| Lộ trình | Mục tiêu học tổng thể | Java Backend, DevOps |
| Chặng | Nhóm năng lực lớn | Java Core, Database, Docker |
| Mục học | Đơn vị chính trên bản đồ, có năng lực và tiến độ | Collections, Transaction, Volume |
| Bài học | Phần kiến thức vừa một phiên học | Chọn List/Set/Map |
| Hoạt động | Thao tác giúp học hoặc chứng minh năng lực | Mô phỏng, code, debug, kiểm tra |
| Dự án | Áp dụng nhiều mục học | Task API, Order Management |

Năng lực là kết quả quan sát được, ví dụ “chọn đúng cấu trúc để loại trùng và giải thích lựa chọn”. Một mục có nhiều năng lực; bài học và hoạt động tham chiếu đến năng lực tương ứng. Dự án có thể liên kết nhiều mục, không nằm cố định dưới một bài.

## 3. Hợp đồng của một mục học

Mẫu biên soạn chi tiết nằm ở [topic-template.md](features/f08-content/authoring/topic-template.md): các phần bắt buộc/tùy chọn, ma trận năng lực và điều kiện draft/published. Hợp đồng về điều kiện ghi nhận assessment/evidence/progress nằm ở [assessment-progress-design.md](features/f04-assessment/evidence-contract.md); template không thay thế rubric/case cụ thể của từng mục.

| Thành phần | Yêu cầu |
|---|---|
| Tổng quan | Vấn đề giải quyết, ứng dụng và đầu ra |
| Prerequisite | Nền tảng cần biết, liên kết và ôn nhanh; hướng dẫn thay vì khóa cứng |
| Kiến thức | Khái niệm, cơ chế, ví dụ đúng/sai, giới hạn và lỗi thường gặp |
| Trực quan | Hình thức phù hợp: sơ đồ, mô phỏng hoặc thay input để thấy hệ quả |
| Luyện tập | Từ cơ bản đến áp dụng/debug; có feedback, hint và lời giải |
| Bài toán kinh điển | Nếu phù hợp; bao gồm thuật toán và tình huống backend tiêu biểu |
| Kiểm tra | Tình huống mới, có tiêu chí và bằng chứng đạt năng lực |
| Tổng kết | Điều làm được, điểm yếu và cách áp dụng |
| Progress/ôn tập | Kiến thức đã xem, năng lực đã đạt, áp dụng và lịch sử lần thử |
| Tham khảo | Nguồn, phiên bản công nghệ và ngày review thực tế |

Không ép mọi mục có cùng loại hoạt động. Nội dung bắt buộc, nâng cao và tùy chọn phải được phân biệt. Bài toán kinh điển có đề, ràng buộc, ví dụ, gợi ý, lời giải, đánh đổi, biến thể và ứng dụng.

## 4. Học, luyện tập và kiểm tra

- Học kiến thức: hướng dẫn và khám phá; xem hết chỉ ghi nhận đã xem.
- Luyện tập: thử nhiều lần, xem gợi ý/lời giải và làm lại; không tự động cấp chứng nhận năng lực.
- Kiểm tra: dùng tình huống mới để xác nhận tiêu chí năng lực. Ghi nhận mức hỗ trợ; khả năng tự làm cần bằng chứng tương ứng.
- Áp dụng: bài tổng hợp hoặc dự án có rubric/bằng chứng riêng. Mô phỏng, tự khai và thực thi được xác minh phải phân biệt.

Tiêu chí đạt là đầu vào thiết kế của từng mục, không dùng mặc định một mức điểm chung cho tất cả. Hợp đồng business về chấm theo CAP, mức hỗ trợ và bằng chứng đã được chốt trong [assessment-progress-design.md](features/f04-assessment/evidence-contract.md); task D06 cụ thể hóa bằng case và đáp án Collections trước khi triển khai.

## 5. Progress và ôn tập

Ba chiều tiến độ độc lập là kiến thức đã xem, năng lực đã chứng minh theo từng CAP và áp dụng vào dự án. Không gộp thành một phần trăm duy nhất khiến người học hiểu sai; chi tiết điều kiện ghi nhận và lịch ôn nằm trong [assessment-progress-design.md](features/f04-assessment/evidence-contract.md).

Chuỗi “chưa học → đang học → đã học kiến thức → đạt kiểm tra → đã áp dụng” chỉ là nhãn tóm tắt có thể bỏ qua trạng thái; ba chiều cùng bằng chứng riêng mới là nguồn sự thật. Nhãn “cần ôn” bổ sung vào trạng thái hiện có, không xóa lịch sử đã đạt.

Quy tắc:

- Một mục/bài dùng chung có một tiến độ xuyên lộ trình.
- Bài chưa xuất bản không nằm trong mẫu số nội dung hiện có để học.
- Độ bao phủ chương trình của app tách khỏi tiến độ người học.
- Lưu attempt, version, input/câu trả lời, hint và evidence, không chỉ điểm cuối.
- Mô phỏng không được ghi nhận thành đã chạy Java/Docker thật.
- Nội dung thay đổi đáng kể có thể yêu cầu kiểm tra lại; giữ lịch sử cũ và lý do cần ôn.
- Reset mô phỏng không xóa tiến độ; reset dữ liệu học là hành động riêng.

Backend là nguồn tiến độ đã lưu; client chỉ cung cấp câu trả lời/observation và nháp tạm. Nộp chưa tới server không được hiện như pass chính thức đã lưu. Quyền với draft/attempt/history/export được kiểm theo tài khoản ở server. Bài nộp ghim phiên bản đề/rubric, không mất lịch sử khi đề thay đổi. Admin tương lai xem/nhận xét theo quyền; đánh giá lại nếu có phải giữ evidence cũ.

## 6. Trải nghiệm và màn hình

Luồng: chọn mục → hiểu vấn đề → khám phá → luyện tập → kiểm tra → áp dụng → ôn lại.

| Màn | Trách nhiệm |
|---|---|
| Hôm nay | Đề xuất việc học/ôn tiếp theo và tiếp tục phiên dở |
| Bản đồ | Toàn cảnh, quan hệ giữa mục học và vị trí hiện tại |
| Trang mục học | Năng lực, prerequisite, kiến thức, bài tập, kiểm tra, progress |
| Không gian học | Nội dung và hoạt động trực quan |
| Khu luyện tập | Thử, feedback, hint, lời giải, retry |
| Kiểm tra | Tình huống mới, rubric và kết quả theo năng lực |
| Dự án | Kết hợp nhiều mục và bằng chứng áp dụng |
| Tiến độ/ôn tập | Lịch sử, điểm cần cải thiện, ôn lại |
| Đăng nhập/đăng xuất | Phiên, hết hạn, nháp chưa lưu và đổi tài khoản an toàn |
| Bài làm của tôi | Nháp, lần nộp, kết quả từng năng lực và phiên bản bài |
| Admin — sau R1 | Học viên/tiến độ/bài nộp/nhận xét theo phạm vi quyền được thiết kế riêng |

Node trên bản đồ dẫn đến trang mục học đầy đủ. Mobile có bố cục đọc/thao tác phù hợp; keyboard và reduced-motion thuộc tiêu chí mỗi màn.

## 7. Quy trình nội dung

Đề cương → biên soạn → bài tập/đáp án → review kỹ thuật → review trải nghiệm → xuất bản → cập nhật.

Mỗi mục có ID ổn định, version, trạng thái nội dung, prerequisite, phân loại mức cần thiết, rubric, feedback, nguồn và thông tin review. Validator kiểm tra cấu trúc; review kỹ thuật kiểm tra sự đúng; thử với người học kiểm tra khả dụng. Không dùng một lớp kiểm tra thay cho hai lớp còn lại.

## 8. Mẫu đầu tiên và phạm vi

Mẫu được chọn: **Collections cơ bản — chọn và sử dụng List, Set, Map**.

- Kiến thức: cấu trúc, phần tử trùng, thứ tự, equality và thao tác thường dùng.
- Trực quan: thêm/xóa/tìm trên các cấu trúc với cùng dữ liệu.
- Luyện tập: danh sách sản phẩm, loại mã trùng, tra cứu theo mã.
- Bài toán: đếm tần suất, nhóm dữ liệu; Two Sum là phần mở rộng phù hợp.
- Kiểm tra: lựa chọn cấu trúc và xử lý dữ liệu trong tình huống mới.
- Áp dụng: chức năng tra cứu/nhóm sản phẩm trong bài tổng hợp nhỏ.

Mẫu phải có đường vào cho người mới: rà prerequisite và cung cấp phần ôn đủ dùng trước khi thử nghiệm. Không mặc định người học đã biết toàn bộ OOP hoặc thuật toán.

Phần API 500, UI và schema hiện có được giữ làm tài sản. Schema và backlog triển khai sẽ được đối chiếu với business mới; chưa coi chúng đã hỗ trợ đầy đủ Topic/Competency/Assessment/Progress.

## 9. Thứ tự tiếp theo

Ngày 23/09/2026: B02–B05 bên dưới đã được phân rã thành BL-D01–09 trong [delivery-backlog.md](delivery-backlog.md); đây là nguồn trạng thái và thứ tự hiện hành. Danh sách B01–B05 bên dưới là kế hoạch lịch sử. [Product requirements](product-requirements.md) và [feature/module map](feature-module-map.md) cụ thể hóa cách truy vết, không thay đổi mục tiêu học đã thống nhất.

Project song song đã được người dùng đồng ý: bài tập nhỏ trước, project chính mở khi đủ năng lực và phát triển theo milestone; có mini-project tùy chọn. Mốc P0–P7 và điều kiện nằm trong backend-knowledge-outline.md. Đây là hướng business, chưa triển khai engine chấm hoặc progress.

Ưu tiên được người dùng làm rõ: chốt toàn bộ cây kiến thức ở [backend-knowledge-outline.md](curriculum/backend-knowledge-outline.md) trước; chỉ hoàn thiện từng mục sau khi review độ bao phủ. Collections vẫn là mẫu dự kiến của bước sau.

Ngày 23/09/2026: sau khi xem bộ khung, người dùng đồng ý bước xác định đường học. [Đường học Java Backend v1](curriculum/java-backend-learning-path.md) cụ thể hóa 11 chặng S00–S10, HTTP/SQL song song, các luồng xuyên suốt và project P0–P7. Đây là lớp thứ tự học trên 23 nhóm, chưa thay thế catalog mục học/năng lực chi tiết.

1. B01: lưu business design đã được đồng ý — hoàn thành bằng tài liệu này.
2. B02: thiết kế catalog mục học toàn chương trình, prerequisite, bắt buộc/nâng cao và năng lực đầu ra.
3. B03: đặc tả mục Collections đầy đủ, rubric kiểm tra, progress và ôn tập.
4. B04: luồng màn hình/wireframe và ma trận nghiệm thu cho mẫu.
5. B05: rà tác động lên code/schema hiện có, thay backlog triển khai và trình bản thiết kế tổng hợp để người dùng xem trước khi code.
6. Sau bước review business: xây trọn mẫu → thử với người học → sửa → mở rộng Java, HTTP/API, Docker; lab thật và DevOps sâu theo mốc riêng.
