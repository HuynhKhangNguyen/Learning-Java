# Backend Lab — Đặc tả trải nghiệm sản phẩm

Phiên bản: 1.0 — 19/09/2026. Đầu ra T01. Trạng thái: đặc tả sẵn sàng cho T02; chưa có UI hoặc mã ứng dụng để nghiệm thu.

## 1. Người học và mục tiêu

### Thông tin đã được người dùng xác nhận

- Biết lập trình cơ bản, mới học Java/backend.
- Muốn roadmap backend hoàn chỉnh, chi tiết, có Java và Docker/DevOps.
- Tham khảo độ rộng kiến thức của roadmap.sh. Cập nhật 20/09/2026: muốn Journey có sơ đồ cấu trúc phân nhánh nhưng sinh động hơn; dùng mở/thu gọn chặng, biểu tượng, màu nhóm và mục tiêu từng chủ đề. [Điều chỉnh mới](../../features/f01-journey/journey-map.md) thay thế mô tả Journey ban đầu khi có khác biệt.
- Muốn học trực quan, có thao tác và ít nhàm chán hơn app đầy chữ.

### Quyết định thiết kế

- Dùng tiếng Việt rõ nghĩa; thuật ngữ kỹ thuật giữ tiếng Anh, có giải thích tại chỗ. Nội dung app xưng “bạn”, tránh giọng chấm điểm hoặc trách móc.
- Mỗi phiên có một mục tiêu nhỏ, thời lượng dự kiến khoảng 10–20 phút, được ngắt và tiếp tục bất kỳ lúc nào. Thời lượng là ước tính biên tập, không đếm ngược ép người học.
- Trải nghiệm desktop là chính. Mobile đọc được, thao tác cơ bản qua bố cục xếp dọc; không bắt kéo thả chính xác để qua bài.
- Không cần tài khoản ở M1–M2. Dữ liệu học lưu trên trình duyệt đang dùng, không ngụ ý đồng bộ nhiều thiết bị.
- Không giả định biết Spring, HTTP, SQL hoặc Docker từ đầu. Mỗi bài nêu nền tảng cần có và cung cấp phần ôn ngắn nếu cần.

### Năng lực sản phẩm cần hỗ trợ

Người học xác định được nên học gì tiếp theo, hiểu cơ chế bằng tương tác, áp dụng vào tình huống mới và nhìn thấy bằng chứng tiến bộ. Toàn bộ khóa hướng tới tự xây, triển khai và vận hành backend; prototype chỉ kiểm chứng trải nghiệm của một bài.

## 2. Phạm vi theo mốc

| Phạm vi | M1 | M2 | M3 |
|---|---|---|---|
| Màn chính | Home, Journey, Lab, Challenge, Result | Thêm Library, Progress, My System, Settings đầy đủ | Màn lab thật và trạng thái runner |
| Bài học | Một bài API 500 tự chứa phần dẫn nhập | 15 bài trong ba cụm mẫu và mục lục toàn khóa | Lab Java/Docker thật và khóa học mở rộng |
| Thực thi | Mô phỏng trình duyệt, hành động định sẵn | Mô phỏng; chưa chạy Java tự do | Hướng dẫn terminal, sau đó runner giới hạn |
| Lưu dữ liệu | Bài/bước đang học, lựa chọn, checkpoint và preference giảm chuyển động | Notes/bookmarks, progress versioned, export/import | Evidence từ thực thi thật |
| Điều hướng | Chỉ hiển thị mục có màn hoạt động | Hiển thị các màn đã hoàn thiện | Bổ sung khi có chức năng |

M1 không có editor biên dịch Java, terminal thật, database thật, gọi API mẫu qua mạng hoặc AI tutor. Tab Code xem snippet và chọn cách sửa định sẵn. Hiển thị “Mô phỏng tương tác” tại lab, output và kết quả. Placeholder nội dung không có nút “Bắt đầu” giả.

## 3. Nguyên tắc trải nghiệm

1. Mỗi màn có một hành động chính nổi bật và lý do rõ để thực hiện nó.
2. Trong vòng thao tác đầu tiên, người học được gửi request hoặc chạy ví dụ, không phải đọc toàn bộ chương.
3. Mô phỏng thay đổi theo input; animation hiển thị trạng thái có ý nghĩa, không chạy nền liên tục.
4. Đặt giải thích cạnh dữ liệu/kết quả liên quan; chỉ mở rộng phần dài khi người học yêu cầu.
5. Sai là cơ hội thử lại. Không trừ điểm, không khóa bài vì sai nhiều lần, không mất tiến độ vì bỏ ngày.
6. Hints đi từ định hướng đến lời giải; dùng hint vẫn được hoàn thành, nhưng lưu mức hỗ trợ để gợi ý ôn.
7. Điểm kết thúc bài nói rõ đã làm được gì và bước tiếp theo hiện có.
8. Mỗi khái niệm dùng chung có một lesson ID và một tiến độ xuyên roadmap.
9. Sơ đồ kiến thức là công cụ tra cứu/phụ thuộc; trang học chính dùng nhiệm vụ và chương.

## 4. Kiến trúc thông tin và điều hướng

| Screen ID | Tên | Route dự kiến | Có từ |
|---|---|---|---|
| S01 | Hôm nay | `/` | M1 |
| S02 | Hành trình | `/journey/:roadmapId` | M1 |
| S03 | Phòng lab | `/learn/:lessonId/:stepId` | M1 |
| S04 | Thử thách | `/learn/:lessonId/challenge` | M1 |
| S05 | Kết quả | `/learn/:lessonId/result` | M1 |
| S06 | Hệ thống của tôi | `/system` | M2 |
| S07 | Thư viện | `/library` | M2 |
| S08 | Tiến độ | `/progress` | M2 |
| S09 | Cài đặt | `/settings` | M2 |

Route là đề xuất định danh chức năng để T03 triển khai; không khóa lựa chọn router. Back của trình duyệt và breadcrumb hoạt động, giữ bộ lọc/vị trí Journey. Chuyển bước không tạo hàng chục entry trong history; rời Lab rồi Back trở lại đúng bài và bước.

M1 đặt lựa chọn giảm chuyển động trong điều khiển lab, theo hệ điều hành mặc định. Không cần hiện Settings/Library trống chỉ để đủ thanh điều hướng.

## 5. Hợp đồng hành vi từng màn

### S01 — Hôm nay

- Vào: mở app hoặc bấm Home; ra: S02, S03; các liên kết S06–S08 chỉ xuất hiện từ M2.
- Hành động chính: “Tiếp tục bài” khi có phiên dở; “Thử lab đầu tiên” khi chưa có tiến độ.
- Nội dung: tên nhiệm vụ, mục tiêu, vị trí đang dở, nhãn mô phỏng và thời lượng ước tính. Phần phụ hiển thị năng lực vừa đạt và đường sang Journey.
- Người mới ở M1 thấy lời dẫn “Làm quen cách học qua một lab API”; không gọi đây là bài đầu tiên bắt buộc của khóa Java.
- Trống: chưa học gì thì giải thích ngắn và dẫn đến bài mẫu, không hiển thị thành tích giả.
- Lỗi: không đọc được storage thì vẫn mở bài với thông báo “Tiến độ phiên này có thể chưa được lưu”; không tự xóa bản cũ.
- Hoàn thành thao tác: vào đúng bài/bước. Home không tự cấp trạng thái hoàn thành bài.

### S02 — Hành trình

- Vào: sidebar, Home, breadcrumb; ra: S03 hoặc Home.
- Hành động chính: bắt đầu/tiếp tục một bài đã xuất bản.
- Nội dung: chương dạng danh sách/thẻ có thu gọn; mục tiêu, prerequisite, trạng thái học, bắt buộc/nâng cao. Java Backend và DevOps là hai góc nhìn trên kho bài chung.
- Bài planned/draft công khai dưới dạng kế hoạch: xem tóm tắt được, không mở một bài rỗng. Trong M1 chỉ một bài playable.
- Thiếu nền tảng: hiện “Nên biết trước…”; vẫn có “Xem thử”, không khóa cứng.
- Trống: bộ lọc không có bài thì cho xóa bộ lọc. Lỗi catalog thì cho thử lại và trở về Home.
- Hoàn thành thao tác: mở lesson đúng ID; không tính xong bài từ click card. Trở lại giữ chapter và vị trí.

### S03 — Phòng lab

- Vào: Home, Journey, deep link hoặc resume; ra: bước khác, S04, S02/Home.
- Hành động chính tùy bước: “Gửi request”, “Chạy từng bước”, “Kiểm tra cách sửa” hoặc “Tiếp tục”.
- Nội dung: mục tiêu ngắn, sân khấu mô phỏng, tab Thao tác/Code/Log/Giải thích, breadcrumb và bước hiện tại. Hiển thị trạng thái đang chạy/dừng/kết thúc bằng chữ và biểu tượng.
- Code ở M1 là snippet chỉ đọc kèm lựa chọn patch; không tạo cảm giác có thể viết/chạy Java tùy ý.
- Step tiến từng sự kiện; Pause dừng diễn tiến; Back trong trace chỉ đổi góc xem snapshot, không ghi dữ liệu lần nữa. “Chạy lại” tạo lần chạy mới từ input hiện tại.
- Trống: trước lần chạy đầu, output ghi “Gửi request để xem kết quả”. Tab log không hiển thị log giả có sẵn như thể vừa chạy.
- Lỗi mô phỏng dự kiến (400/500) là nội dung học. Lỗi engine/UI là lỗi ứng dụng riêng: cho khởi tạo lại mô phỏng, giữ câu trả lời và tiến độ.
- Sửa input/patch sau khi chạy: đánh dấu kết quả cũ không còn khớp, yêu cầu chạy lại trước khi chấm; dữ liệu input của lần chạy đang diễn ra là snapshot.
- Hoàn thành bước: theo bảng bước ở mục 7. Có thể xem trước bước khác, nhưng checklist evidence vẫn thiếu cho đến khi thực hiện yêu cầu.

### S04 — Thử thách

- Vào: cuối S03 hoặc mở lại từ Result; ra: S03 để ôn, S05 khi chấm hoặc kết thúc một lần thử.
- Hành động chính: “Kiểm tra lời giải”.
- Nội dung: tình huống mới, lựa chọn sửa, dự đoán kết quả, bộ kiểm tra và hints ba cấp. Giữ đề bài bên cạnh thao tác.
- Chưa trả lời: chỉ ra mục còn thiếu; không tính là một lần thử sai.
- Sai: feedback chỉ rõ hành vi nào chưa đúng; giữ lựa chọn và cho sửa/chạy lại. Không hiện đáp án ngay nếu chưa yêu cầu hint cuối.
- Lỗi engine: báo chưa chấm được, không ghi fail cho người học. Gửi lặp cùng attempt không nhân đôi tiến độ.
- Hoàn thành bài: phải đạt toàn bộ tiêu chí C1–C5 ở mục 9, không dựa vào số lần nhấn Next.

### S05 — Kết quả

- Vào: sau chấm hoặc xem lại bài; ra: ôn bước liên quan, làm lại challenge, Home/Journey hoặc bài published tiếp theo.
- Hành động chính: chưa đạt → “Thử lại”; đạt → “Về hành trình”. Chỉ đề xuất bài tiếp khi bài đó thực sự có nội dung.
- Nội dung: từng kỹ năng đạt/chưa đạt, evidence quan sát được, gợi ý ôn và mức hint đã dùng. Nhãn “Đã qua bài tập mô phỏng”.
- M1 hoàn thành bài cuối: “Bạn đã hoàn thành lab mẫu. Có thể thử lại với dữ liệu khác”; không có nút Next dẫn tới bài chưa viết.
- Trống: vào URL Result khi chưa thử thì dẫn về bước hiện tại/challenge, không tạo kết quả.
- Lỗi lưu: kết quả vẫn hiện, kèm thông báo chưa lưu và nút thử lưu lại. Không hiển thị “Đã lưu” khi persistence thất bại.
- Replay giữ evidence đã đạt, lưu lần thử mới riêng; không nâng thành “đã áp dụng dự án”.

### S06 — Hệ thống của tôi [M2]

- Vào: Home/sidebar; ra: bài liên quan qua S03 hoặc Journey.
- Hành động chính: khám phá một thành phần, tiếp tục bài để hiểu nó.
- Nội dung: client/API/database/container… xuất hiện theo evidence, mỗi thành phần có nhiệm vụ và bài liên quan. Nêu rõ mô hình học tập.
- Trống: hiển thị khung hệ thống khởi đầu và bài giúp xây thành phần đầu tiên. Lỗi progress thì không bịa trạng thái đã xây.
- Hoàn thành thao tác: đi đến bài; mở thành phần không tự cấp thành tích. Bài mô phỏng không tạo huy hiệu “đã deploy”.

### S07 — Thư viện [M2]

- Vào: sidebar hoặc tìm kiếm; ra: bài/bước, glossary hay Journey.
- Hành động chính: tìm khái niệm, bookmark hoặc ghi chú.
- Nội dung: tìm có/không dấu, thuật ngữ Anh; filter theo track và trạng thái nội dung.
- Trống: gợi ý từ khóa hoặc xóa filter. Lỗi đọc notes: catalog vẫn dùng được, thông báo lỗi phần cá nhân.
- Hoàn thành thao tác: mở đúng kết quả; bài planned dẫn đến tóm tắt ở Journey, không mở lab trống.

### S08 — Tiến độ [M2]

- Vào: Home/sidebar; ra: bài ôn hoặc Journey.
- Hành động chính: tiếp tục kỹ năng chưa đạt hoặc ôn bài đã cần gợi ý.
- Nội dung: bằng chứng đã làm được, bài dở, bài cần ôn; phân biệt đã xem/qua bài tập/áp dụng. Tỷ lệ nếu có phải ghi mẫu số các bài published; không cộng bài dùng chung hai lần.
- Trống: lời dẫn và bài bắt đầu; lỗi storage cho thử lại và không tự ghi đè bằng tiến độ rỗng.
- Hoàn thành thao tác: dẫn đến hoạt động học. Tiến độ chỉ thay đổi từ evidence học, không từ việc mở dashboard.

### S09 — Cài đặt [M2]

- Vào: sidebar; ra: màn trước hoặc Home.
- Hành động chính: đổi preference, export/import dữ liệu.
- Nội dung: theme, giảm chuyển động, nơi lưu dữ liệu, sao lưu, phục hồi và reset.
- Trống: dùng preference mặc định. Lỗi import phải chỉ lý do, giữ nguyên bản đang lưu; không hỗ trợ version mới thì từ chối có giải thích.
- Reset dữ liệu cá nhân: xác nhận nêu rõ phạm vi trước khi xóa; không dùng chung nút reset simulator.
- Hoàn thành thao tác: preference áp dụng; chỉ báo import/save thành công sau khi ghi thành công. Không có hành vi hoàn thành bài ở màn này.

## 6. Luồng sử dụng chính và đường hồi phục

### Lần đầu trong M1

S01 → S03 phần dẫn nhập ngắn → gửi request lỗi → xem trace/log → chẩn đoán → chọn cách sửa → chạy kiểm tra → S04 tình huống mới → S05 → S02 hoặc S01.

Phần dẫn nhập giải thích client, request, JSON, response, status code và null bằng ví dụ; có “Tôi đã biết” để bỏ qua. Bỏ qua dẫn nhập không cấp evidence của bài.

### Tiếp tục buổi học

Mở Home → hiện bài/bước đang dở → resume input và lựa chọn đã lưu → mô phỏng ở trạng thái paused/idle, không tự chạy → tiếp tục. Nếu phiên cũ đang chạy lúc thoát, khởi động lại trace tại đầu với cùng input và nhãn “Chạy lại để xem luồng”; không tự ghi một lần chạy hoàn tất.

### Đi sai đường hoặc thiếu nội dung

Lesson không tồn tại: hiện thông báo và về Journey. Step không tồn tại nhưng lesson hợp lệ: về bước đầu/step lưu hợp lệ, giữ evidence. Prerequisite chưa published: có giải thích tóm tắt trong bài mẫu, không dẫn vào ngõ cụt.

## 7. Kịch bản chuẩn “Cứu API trả lỗi 500”

Lesson ID: `api-debug-500`. Version ban đầu: `1`. Nhóm: HTTP/API. Thời lượng biên tập: khoảng 15–20 phút. Mọi request, log, task row và thời gian trong lab là dữ liệu mô phỏng, không phải phép đo hệ thống thật.

### Mục tiêu học

- O1: đọc method, path, JSON và status của một request/response.
- O2: lần theo trace để tìm bước thất bại thay vì suy ra nguyên nhân chỉ từ status 500.
- O3: phát hiện title null đi tới xử lý chuỗi trước khi được kiểm tra.
- O4: chọn kiểm tra dữ liệu tại biên API và giữ lỗi nội bộ là lỗi server.
- O5: kiểm tra cả input hợp lệ, input không hợp lệ và lỗi nội bộ sau khi sửa.

### Tình huống và giới hạn

Ứng dụng quản lý task nhận `POST /tasks`. Code minh họa gọi `request.title().strip()` trong service. Khi JSON thiếu `title` hoặc title bằng null, mô hình binding chuyển thành null; gọi method trên giá trị này gây NullPointerException và trả lỗi 500 chung. Database chưa được gọi.

Đây là một nguyên nhân cụ thể của 500. Bài không dạy rằng mọi lỗi 500 đều do null hay mọi validation đều phải dùng status 400. Lab chọn contract 400 cho title không hợp lệ; API khác có thể quy định khác.

Luồng mô hình: Client → Controller (binding/validation) → Service (normalize) → Repository → Database → Response. Repository có thể gộp trong giao diện tổng quan, nhưng trace phải phân biệt bước ghi dữ liệu.

### Các bước

| Step ID | Người học làm | Phản hồi nhìn thấy | Evidence/điều kiện |
|---|---|---|---|
| intro | Xem hoặc bỏ qua phần nền tảng | Ví dụ request/response có chú giải | Không tính thành tích |
| reproduce | Gửi preset `{}` ở bản lỗi | 500, task không được tạo | Một run lỗi hoàn tất |
| trace | Step qua luồng, chọn service/log liên quan | title=null, điểm lỗi, DB chưa chạy | Xác định đúng điểm lỗi và dấu hiệu |
| diagnose | Chọn nguyên nhân từ ba phương án | Feedback tại lựa chọn | Chọn thiếu validation trước thao tác trên null |
| repair | Chọn cách sửa và chạy lại | Thay đổi code minh họa và luồng response | Chọn patch, chưa đủ để pass |
| verify | Chạy bốn case nền: thiếu field, null, chuỗi hợp lệ, lỗi ghi nội bộ | Bảng expected/observed, record có/không | Bốn case đúng contract; để case rỗng/blank cho challenge |
| challenge | Giải quyết input title rỗng/chỉ có dấu cách, rồi chạy đủ bộ sáu case | Validation bắt được blank, hợp lệ vẫn chạy | Tiêu chí C1–C5 |
| result | Xem kỹ năng và quay lại hành trình | Evidence, mức hint và gợi ý ôn | Không tự tạo evidence mới |

### Input và hành động được hỗ trợ

- M1 cho sửa trường title bằng form và xem JSON tương ứng; có preset thiếu field, null, rỗng, dấu cách và chuỗi hợp lệ. Không giả vờ hỗ trợ JSON tự do/parser chưa có.
- Method/path cố định `POST /tasks`; các thuộc tính JSON và kiểu dữ liệu ngoài phạm vi không hiện như input có thể chạy.
- Bộ kiểm tra có một tình huống lỗi ghi dữ liệu nội bộ được cài sẵn, chỉ nhằm kiểm tra phân loại lỗi; không mô phỏng quản trị database.
- Mỗi lần chạy có fixture dữ liệu riêng và bắt đầu từ trạng thái ban đầu. Record tạo trong lần trước không tích lũy sang lần sau; nhãn giải thích điều này. Snapshot replay không thực hiện ghi lần hai.

## 8. Cách sửa và kết quả chuẩn

| Patch ID | Hành vi | Bài học |
|---|---|---|
| original | Không validate, xử lý chuỗi rồi ghi | Bản lỗi dùng để tái hiện |
| null-only | Từ chối missing/null trước service; vẫn nhận chuỗi rỗng | Sửa một phần, chưa đủ contract |
| catch-all-400 | Giữ xử lý cũ nhưng đổi mọi exception thành 400 | Che mất phân biệt lỗi input và lỗi nội bộ |
| validate-title | Từ chối missing/null/blank tại controller; normalize rồi ghi khi hợp lệ | Cách sửa đạt yêu cầu bài |

Trong fixture, database không áp constraint nonblank để kiểm tra việc bỏ sót validation có thể tạo record rỗng. Đây là quyết định của bài mẫu, không phải khuyến nghị schema production. Patch sửa chỉ thay cơ chế validation/error mapping, không đổi fixture.

| Input / điều kiện | original | null-only | catch-all-400 | validate-title |
|---|---|---|---|---|
| `{}` | 500, không ghi | 400, không ghi | 400, không ghi | 400, không ghi |
| title=null | 500, không ghi | 400, không ghi | 400, không ghi | 400, không ghi |
| title="" | 201, ghi rỗng | 201, ghi rỗng | 201, ghi rỗng | 400, không ghi |
| title="   " | 201, ghi rỗng | 201, ghi rỗng | 201, ghi rỗng | 400, không ghi |
| title="  Học Java  " | 201, ghi "Học Java" | 201, ghi "Học Java" | 201, ghi "Học Java" | 201, ghi "Học Java" |
| Title hợp lệ + lỗi ghi nội bộ định sẵn | 500, không ghi | 500, không ghi | 400, không ghi — sai contract | 500, không ghi |

Response mô phỏng chuẩn: 201 có task id/title; 400 có mã `INVALID_TITLE` và giải thích field title; 500 có mã `INTERNAL_ERROR` chung. Với patch catch-all, 400 dùng lỗi chung `BAD_REQUEST`, không giả là validation có cấu trúc. Log nội bộ mới hiển thị dấu hiệu exception; response gửi client không chứa stack trace.

Trace cho missing/null + original: request → bind title=null → service normalize lỗi → error handler → response 500; repository/database hiện “chưa được gọi”. Với validate-title: request → validation từ chối → response 400; service/repository/database không chạy. Với input hợp lệ: đi đủ các lớp và tạo đúng một record trong fixture lần chạy.

## 9. Chấm bài, hints và tiến độ

### Điều kiện hoàn thành

- C1: tái hiện run 500 của preset thiếu title ở bản original.
- C2: xác định service là điểm exception, title=null là bằng chứng; không chấm theo việc chỉ mở tab log.
- C3: lựa chọn sửa cuối cùng đạt bộ kiểm tra sáu trường hợp ở mục 8. Nếu thay patch/input sau chấm, kết quả hiển thị cũ được đánh dấu để chạy lại; không dùng output cũ để chứng minh patch mới.
- C4: hoàn thành challenge title blank và trả lời đúng vì sao chỉ kiểm tra null chưa đủ.
- C5: trả lời đúng rằng lỗi ghi nội bộ không nên bị chuyển hàng loạt thành lỗi input 400.

Hiển thị checklist còn thiếu khi người học xem trước challenge hoặc deep link vào Result. Các lần thử trước được giữ; replay không xóa lần pass trước. Tiêu chí là hành vi quan sát được trong mô phỏng, không đủ để gắn “đã viết backend thật”.

### Gợi ý

1. Định hướng: “Tìm bước cuối cùng chạy được trước khi response 500 xuất hiện.”
2. Chỉ dấu hiệu: “Quan sát title tại service. Giá trị này có thể nhận lời gọi strip() không?”
3. Giải thích lời giải: “Kiểm tra title thiếu/null/blank trước xử lý; giữ lỗi nội bộ riêng. Chạy cả input hợp lệ và không hợp lệ để kiểm chứng.”

Số cấp hint đã mở được lưu với attempt. Xem lời giải vẫn có thể qua bài sau khi làm đủ kiểm tra; Result ghi “Hoàn thành với gợi ý”, khuyến khích thử tình huống khác. Không tự chấm câu trả lời tự do bằng keyword. M1 dùng lựa chọn có lý do rõ ràng; câu phản tỉnh tự do nếu có chỉ là ghi chú, không phải bằng chứng được máy xác minh.

### Trạng thái và persistence

- Chưa học → đang học khi vào hoạt động đầu tiên → đã xem khi duyệt hết phần giải thích → đã qua bài tập khi C1–C5 đạt.
- “Đã áp dụng” cần evidence riêng từ project/lab thật, không có trong M1.
- Lưu lessonId/version, stepId, input, patchId, đáp án, hints, run evidence và attempt hoàn tất; giảm chuyển động là preference riêng.
- Evidence gắn phiên bản bài và cấu hình run. M1 dùng dữ liệu mô phỏng cục bộ; không hứa cơ chế chống gian lận.
- Reset mô phỏng xóa trace/output của lần hiện tại; giữ bài, đáp án, patch và input. Nút “Khôi phục ví dụ ban đầu” đưa input/patch về preset ban đầu và nói rõ phạm vi, vẫn giữ lịch sử học.
- Replay challenge bắt đầu attempt mới; xóa toàn bộ dữ liệu cá nhân chỉ có ở Settings M2.
- Nếu storage lỗi: tiếp tục trong bộ nhớ, luôn báo chưa lưu và có Retry; không báo mất toàn bộ dữ liệu khi chưa kiểm tra được.
- Nếu version thay đổi về sau: giữ lịch sử cũ, chỉ dùng evidence tương thích và hiện bài cần ôn; migration chi tiết thuộc T18/T20.

## 10. Accessibility và trạng thái toàn cục

- Mọi thao tác cốt lõi có button/control dùng bàn phím; focus dễ thấy; tab panel có tên và trạng thái.
- Không dùng màu đơn độc để thể hiện lỗi/đúng; có status text, icon và danh sách trace tương đương sơ đồ.
- Reduced motion bỏ di chuyển liên tục, giữ snapshot, step và nội dung đầy đủ; kết quả không phụ thuộc thời gian animation.
- Không tự phát âm thanh hoặc nhấp nháy; không thêm đồng hồ đếm ngược cho challenge.
- Khi chấm xong hoặc gặp lỗi, thông báo có thể đọc được nhưng không giật focus khỏi input tùy tiện.
- Trên màn hẹp, nhiệm vụ → sân khấu → panel xếp dọc; code có cuộn riêng, nội dung chính không tràn ngang toàn trang.
- Loading phải có trạng thái; nếu tải thất bại phải có đường thử lại và quay về. Không giữ spinner vô hạn không có lời giải thích.

## 11. Tiêu chí nghiệm thu cho các task triển khai

Các tiêu chí sau là yêu cầu tương lai, chưa được chạy ở T01.

| ID | Tình huống | Kết quả cần đạt | Task chính |
|---|---|---|---|
| AC01 | Người mới mở Home | Một CTA rõ, có nhãn mô phỏng và dẫn nhập tùy chọn | T05 |
| AC02 | Mở bài planned | Xem tóm tắt, không vào lab trống | T05 |
| AC03 | Gửi `{}` bản original | 500, service có lỗi, DB chưa gọi | T07 |
| AC04 | Step/Pause/Replay | Trace đúng thứ tự; replay không ghi thêm | T06–T07 |
| AC05 | Chạy sáu case qua bốn patch | Kết quả đúng bảng mục 8 | T07–T08 |
| AC06 | Sửa patch sau khi có output | Kết quả cũ được đánh dấu; chạy lại mới chấm | T07–T08 |
| AC07 | Bấm Next hoặc mở Result trực tiếp | Không tự pass nếu thiếu C1–C5 | T08 |
| AC08 | Sai hoặc dùng hints | Retry được, giữ đáp án, lưu mức hỗ trợ | T08 |
| AC09 | Đóng/reload giữa bài | Resume đúng bước; không tự chạy hoặc chấm run dở | T09 |
| AC10 | Storage không ghi được | Học tiếp, báo chưa lưu, Retry không nhân đôi evidence | T09 |
| AC11 | Reset mô phỏng | Không mất tiến độ/đáp án/evidence đã đạt | T07–T09 |
| AC12 | Xong bài duy nhất ở M1 | Quay về hoặc replay; không dẫn tới bài chưa viết | T08 |
| AC13 | Bàn phím và reduced motion | Qua được luồng chính, đọc được trace | T06, T10 |
| AC14 | Route bài/bước sai | Có lối về hợp lệ, giữ dữ liệu cũ | T05–T06 |
| AC15 | Mở bài dùng chung ở hai track | Một tiến độ, không cộng thành tích hai lần | T18 |
| AC16 | Import dữ liệu không hợp lệ | Giữ bản đang lưu và báo lý do | T20 |

## 12. Kiểm chứng hứng thú và khả năng sử dụng ở T10

Thử với chính người dùng trước; không suy rộng kết quả của một người thành hiệu quả giáo dục chung. Bài test: tìm nơi bắt đầu, gây lỗi, tìm nguyên nhân, sửa, vượt challenge, rời bài và tiếp tục.

Ghi nhận tại máy hoặc ghi chú trực tiếp, chưa cần analytics cloud: nơi phải hỏi cách dùng, nhãn khó hiểu, thao tác bị bỏ sót, phần giải thích quá dài và việc có tự diễn giải được cơ chế sau bài. Thời gian chỉ để tìm điểm vướng, không làm điểm số người học.

Đạt về khả dụng khi người dùng tự hoàn thành các thao tác cốt lõi và tìm lại bài dở; phần chưa làm được trở thành issue cụ thể. Hứng thú cần hỏi sau trải nghiệm thật: đoạn nào muốn thử tiếp, đoạn nào chán, có muốn học bài kế tiếp không. Không coi một animation chạy đúng là bằng chứng người học thấy hấp dẫn.

## 13. Đầu ra T01 và bàn giao T02

- Persona và mức nền tảng đã xác nhận ở mục 1.
- Phạm vi M1/M2/M3 ở mục 2; chín màn có điểm vào/ra, hành động, empty/error và điều kiện hoàn thành ở mục 5.
- Bài mẫu có step, dữ liệu, patch, response, expected matrix và tiêu chí chấm ở mục 7–9.
- Hành vi resume/reset/accessibility và tiêu chí triển khai ở mục 9–11.
- T02 cần thiết kế Home mới/có tiến độ, Journey, Lab ở các trạng thái idle/running/failed/repaired, Challenge chưa đạt/đạt, Result và lỗi lưu; desktop/laptop cùng bản đọc mobile.
- T02 quyết định palette, typography, icon, spacing và bố cục cụ thể. T01 không yêu cầu chọn màu hoặc công nghệ mới để tiếp tục.
- Chưa có input bắt buộc còn thiếu. Có thể điều chỉnh theme và nhịp tương tác sau khi xem wireframe/prototype; không chặn T02.

## 14. Tham chiếu và giới hạn kỹ thuật

[RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html) là nguồn cho ý nghĩa HTTP status: 201 ở §15.3.2, 400 ở §15.5.1 và 500 ở §15.6.1. Contract validation dùng 400, cấu trúc response, fixture và các patch trong tài liệu là thiết kế bài học của dự án; không phải quy định bắt buộc của RFC hay hành vi mặc định của mọi ứng dụng Spring.

Các snippet Java hoàn chỉnh, binding/validation framework và log thực tế cần kiểm chứng khi biên soạn/triển khai T04/T07 và lab thật T25. T01 chỉ xác định mô hình và output mong đợi, không tuyên bố đã chạy Java hay kết nối database.
