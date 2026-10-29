# Collections — kiến thức và mô hình trực quan

Điều chỉnh system 29/09/2026: [system v2](../../../system-overview.md) chuyển official assessment/progress lên backend theo tài khoản. Nội dung/mô hình D06a và bài đánh giá D06b dùng [contract bài nộp BL-S03](../../f11-learner-work/data-contract.md). Mọi chỉ dẫn giữ nháp/lưu trong spec theo owner/server authority mới, không tự cấp pass từ client; D06 hoàn thành thiết kế trên giấy, chưa triển khai.

BL-D06a · spec v1 · 26/09/2026. Trạng thái bản biên soạn: **draft**; catalog các TOP vẫn planned, chưa xuất bản vào app. Phạm vi F02/F08, MD01/03, REQ-02/03/10/12. Chuẩn kỹ thuật tham chiếu: Java SE 21, dùng API cơ bản, không yêu cầu preview feature. Đây là phiên bản tài liệu được chọn để review mẫu, không chọn JDK mới nhất hay thay runtime của app.

Nguồn business: [catalog nền tảng](../../../curriculum/core-foundations.md), [template](../../f08-content/authoring/topic-template.md), [assessment/progress](../../f04-assessment/evidence-contract.md), [backlog](../../../delivery-backlog.md). D06a giữ kiến thức/mô hình; [D06b — bài luyện và đánh giá](../../f04-assessment/collections/cases.md) đã bổ sung ngày 29/09/2026 với rubric, biến thể và bài áp dụng. Gói vẫn draft, hoàn thành thiết kế không đồng nghĩa đã chạy trong app.

## 1. Phạm vi và đầu ra

Tên gói hiển thị: **Cùng một dữ liệu, chọn đúng Collections**. Tình huống xuyên suốt: một quầy hàng cần giữ danh sách thao tác, danh sách mã duy nhất và bảng tra cứu sản phẩm. Cùng mã P7 xuất hiện hai lần sẽ tạo kết quả khác nhau tùy yêu cầu.

Không tạo TOP “Collections” mới. Các TOP dưới giữ K04/S02/C; TOP-COL-006 có thêm K05. ID K/S là phân loại/vị trí học, không làm khóa progress mới.

| Topic | CAP chính dự kiến kiểm tra ở D06b | Kiến thức trong bản này | Đầu ra quan sát cần hướng tới |
|---|---|---|---|
| TOP-COL-002 | CAP-COL-002-A | C1 | Thêm/đọc/sửa/xóa List, giữ trùng, phân biệt vị trí và giá trị |
| TOP-COL-002 | CAP-COL-002-B | C1/C4 | Chọn List theo vị trí/thứ tự; xử lý rỗng/index sai |
| TOP-COL-003 | CAP-COL-003-A | R3/C2 | Loại trùng theo equality và giải thích lựa chọn |
| TOP-COL-003 | CAP-COL-003-B | C2/C4 | Chọn đặc tính thứ tự; phân biệt HashSet và LinkedHashSet |
| TOP-COL-004 | CAP-COL-004-A | C3 | Tra/cập nhật khóa và phân biệt khóa thiếu theo contract |
| TOP-COL-004 | CAP-COL-004-B | R3/C3 | Giải thích thay giá trị ở khóa trùng và chọn String ID ổn định |
| TOP-COL-006 | CAP-COL-006-A | C4/C5 | Chọn cấu trúc cho tra cứu/loại trùng/đếm/nhóm và giải thích đánh đổi |

CAP-COL-006-B chỉ **hỗ trợ** qua C6; không cấp đạt Big O từ vài ví dụ chọn cấu trúc. TOP-COL-001, TOP-JAVA-004/005/006/010 là nền được ôn một phần, không tự cấp tất cả CAP của chúng. TOP-COL-005 (duyệt sửa/sorting/view), COL-007 (logic CLI/test) ngoài phạm vi mẫu. Chưa có code learner chạy thật hoặc cơ chế chấm thực thi.

## 2. Đường vào và ôn ngay trong gói

Prerequisite giữ nguyên catalog: List cần COL-001 và JAVA-003; Set/Map cần COL-001 và JAVA-010; COL-006 cần COL-002/003/004. Toàn bộ prerequisite bắc cầu vẫn xem được trong catalog. Đây là hướng dẫn, không khóa người học theo số bài đã xem.

R1 phải có các thẻ ôn R0–R3 dưới đây ngay trong gói; khi bài nền còn planned, nút ôn mở thẻ tại chỗ và giải thích giới hạn, không dẫn vào lesson trống. Khám phá mẫu được tiếp tục; người còn thiếu nền sâu được xem đúng đề cương topic liên quan. Câu hỏi ôn dùng để nhận biết cần hỗ trợ, không là bài xác nhận toàn bộ prerequisite.

| Thẻ | Nội dung dạy ngắn và câu hỏi | Đáp án/đường hỗ trợ |
|---|---|---|
| R0 — Đọc thao tác | Biến giữ dữ liệu; method nhận input/trả kết quả. Duyệt `[P7,P2,P7]` xử lý ba lần, kể cả mã trùng. Duyệt danh sách rỗng xử lý không lần nào. Hỏi số phần tử có đổi khi chỉ đọc không? | Không. Minh họa con trỏ lần lượt 0,1,2 rồi dừng; rỗng dừng ngay. Thiếu nền: JAVA-002/003 |
| R1 — Kiểu và hợp đồng | `List<String>` cho biết kiểu phần tử; `Map<String,String>` có kiểu khóa/giá trị. `List<String> ids = new ArrayList<>();` dùng interface bên trái, implementation bên phải. Hỏi số nguyên có phải String ID không? | Không tự đổi kiểu; trong ví dụ phải dùng chuỗi. Giải nghĩa từng phần khai báo, không dạy wildcard/erasure. Ôn COL-001/JAVA-006 |
| R2 — Biến và object | Hai mũi tên cùng trỏ một hộp dữ liệu có thể thấy cùng thay đổi. Đổi nhãn biến không tự tạo bản sao hộp. `null` là không có reference, khác danh sách rỗng. | Cho chọn sơ đồ hai biến chung hộp hay hai hộp khác nhau, rồi giải thích. Chưa chấm CAP reference; ôn JAVA-004/005 |
| R3 — Equality và khóa | Hai thẻ khác identity cùng String ID có thể equal. Với String ID trong mẫu, so nội dung chính xác, có phân biệt hoa thường. Equal ⇒ cùng hashCode; cùng hashCode không đủ kết luận equal. | Hỏi hai thẻ P7 có thành hai mã duy nhất không? Không. `P7` khác `p7`. Không thay field dùng cho equality/hash khi object đang làm khóa/phần tử hash. Ôn JAVA-010 |

Nền R1 dùng [List API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html) và [Map API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html); R3 theo [Object.equals/hashCode](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html), [String](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html) và [Set về mutable element](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html). R2 là sơ đồ ôn, không mô phỏng heap/GC JVM.

## 3. Nội dung bài nhỏ

### C1 — List: một phần tử ở vị trí nào?

Đặt ba thẻ `[P7,P2,P7]` dưới chỉ số `0,1,2`. Hai thẻ P7 vẫn là hai lần xuất hiện. Người học thử đọc ô 1, thay ô 1 bằng P9, rồi xóa theo vị trí hoặc theo giá trị ở hai lượt độc lập. So sánh trạng thái trước/sau và giá trị trả về, không chỉ animation.

Ví dụ tác giả, mỗi dòng sau tiếp nối dòng trước:

| Thao tác | Giá trị trả về | List sau thao tác |
|---|---|---|
| `get(1)` | P2 | `[P7,P2,P7]` |
| `set(1,"P9")` | P2 | `[P7,P9,P7]` |
| `add(1,"P2")` | void | `[P7,P2,P9,P7]` |
| `remove("P7")` | true | `[P2,P9,P7]` |
| `remove(1)` | P9 | `[P2,P7]` |

Với `List<Integer>`, xóa `1` và xóa `Integer.valueOf(1)` chọn hai overload khác nhau. Phần ôn phụ cho thấy `[1,9,1]` thành `[1,1]` khi xóa index 1, nhưng thành `[9,1]` khi xóa giá trị 1. Không đưa số nguyên vào input String của mô hình chính. Nguồn thao tác: [List methods](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html); implementation sửa được chọn là [ArrayList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html).

Thông điệp sau thí nghiệm: index là vị trí hiện tại, không là ID lâu dài. Sau khi xóa một ô, các ô sau dịch vị trí. Muốn lấy ô đầu phải xét có phần tử; chưa có ô nào thì không đọc index 0. ArrayList dùng ở đây cho dữ liệu có vị trí; hình thẻ không thể hiện capacity/bộ nhớ.

### C2 — Set: thế nào là một mã duy nhất?

Đưa lần lượt `P7,P2,P7` qua cổng equality. Hai lần đầu làm tăng số mã; lần cuối trả false vì P7 đã có. Nêu lý do “cùng nội dung ID” bên cạnh thẻ bị bỏ qua. Thay bằng `p7` làm xuất hiện mã khác theo contract phân biệt hoa thường.

Đặt hai khung so sánh: HashSet hiển thị thành cụm **không đánh số**, LinkedHashSet thành chuỗi thứ tự thêm. Thêm lại P7 đang tồn tại không chuyển nó xuống cuối; xóa P7 rồi thêm lại thì thứ tự LinkedHashSet trở thành `[P2,P7]`. Thứ tự sắp thẻ HashSet trên UI chỉ là cách trình bày, không phải output iteration của JVM. Nguồn: [HashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html), [LinkedHashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html).

Đối chứng equality: “hai object có cùng hash” chưa đủ để bỏ một object. Khi minh họa khóa mutable, chỉ chỉ ra hợp đồng đã bị vi phạm; không dựng kết quả chắc chắn rằng Java luôn trả false hoặc luôn ném exception. Không mở rộng sang comparator/TreeSet trong mẫu. Nguồn: [Object contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html), [Set contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html).

### C3 — Map: tìm đúng giá trị bằng khóa

Tạo bảng `P7 → Pen`, `P2 → Book`. Đọc P7 thấy Pen; ghi `P7 → Pencil` thay giá trị ở khóa đó, số khóa vẫn là 2. Hai khóa khác nhau được phép mang cùng giá trị. Mô hình chính dùng String ID, không dùng vị trí danh sách làm ID sản phẩm.

Hai kết quả cần đọc riêng: khóa có tồn tại không và giá trị trả về là gì. Trong contract mẫu cấm giá trị null, đọc mã thiếu trả null cùng nhãn “không có khóa”. Một preset đối chứng chỉ đọc chứa `P0 → null`: `get(P0)` và `get(P9)` đều null, nhưng `containsKey` lần lượt true/false. Đây là lý do không suy khóa thiếu chỉ từ null khi contract cho phép null. Tham chiếu [Map get/containsKey/put](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html), [HashMap null và order](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).

Khi người học thử “đổi ID”, yêu cầu phân biệt đổi biến String và sửa object dùng làm khóa. String bất biến; gán biến sang ID khác không thay khóa đã lưu. Muốn chuyển bản ghi sang khóa mới cần một thao tác nghiệp vụ có xử lý trùng; chưa coi đó là `put` tự đổi tên khóa. Nguồn: [String](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html), [Map mutable key](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html).

### C4 — Chọn theo yêu cầu

| Yêu cầu quầy hàng | Lựa chọn minh họa | Lý do và điều mất đi nếu chọn sai |
|---|---|---|
| Giữ từng lần quét theo vị trí, kể cả trùng | List/ArrayList | Set làm mất các lần quét lặp |
| Chỉ hỏi đã gặp mã chưa | Set/HashSet | Một List phải tự tìm trùng; Set không tự lưu số lần xuất hiện |
| Loại trùng nhưng giữ thứ tự gặp đầu tiên | Set/LinkedHashSet | HashSet không hứa thứ tự này |
| Tra tên mới nhất theo ID | Map/HashMap | `put` thay giá trị ở cùng khóa; cần lịch sử thì phải thiết kế thêm |
| Đếm mỗi mã hoặc nhóm ID theo loại | Map với value số đếm hoặc List | Map một chuỗi value không giữ được nhiều phần tử cùng nhóm |

Đây là lựa chọn theo contract ví dụ, không khẳng định mọi yêu cầu chỉ có một cách cài đặt. Tên interface nêu hành vi; implementation quyết định thêm đặc tính thứ tự/chi phí. Các API nguồn ở C1–C3 hỗ trợ cơ chế; tình huống và đánh đổi của bảng do tác giả thiết kế.

### C5 — Từ lookup/update tới đếm và nhóm

**Đếm tần suất:** đầu vào `[BOOK,PEN,BOOK]`. State ban đầu `{}`; đọc từng mã, lấy số cũ (0 khi khóa chưa có), ghi số cũ + 1. Ba bước: `{BOOK:1}`, `{BOOK:1,PEN:1}`, `{BOOK:2,PEN:1}`. Rỗng trả `{}`. Set chỉ lưu hai mã và không trả được số lần BOOK xuất hiện. Đây là thuật toán minh họa do tác giả thiết kế từ thao tác Map, không là lời gọi thư viện “đếm” đã chạy.

**Nhóm dữ liệu:** đầu vào `[(P7,STATIONERY),(P2,BOOK),(P9,STATIONERY)]`. Với mỗi loại, tạo List riêng nếu chưa có rồi thêm ID. Kết quả `STATIONERY:[P7,P9], BOOK:[P2]`; thứ tự bên trong từng List theo input, không yêu cầu thứ tự các key. Rỗng trả `{}`. Nếu hai loại cùng dùng một List mutable, phần tử sẽ bị trộn nhóm; sơ đồ phải hiện hai hộp List khác nhau. `put(loại,id)` đơn thuần chỉ giữ ID cuối của loại nên không đáp ứng yêu cầu.

D06b đã có CL-P-F/G và CL-A-D1/D2 cho hai cơ chế với đề, hint, lời giải và tình huống khác; ví dụ đã trình bày này không được tái dùng làm assessment độc lập. Two Sum là mở rộng tùy chọn, không cần để chứng minh 7 CAP chính.

### C6 — Chi phí: giới thiệu có giới hạn

Với ArrayList, đọc theo index là O(1), thêm cuối có chi phí O(1) khấu hao; tìm theo giá trị/xóa giữa có thể đi qua hoặc dịch nhiều phần tử. HashSet/HashMap có thao tác cơ bản nhanh dưới giả định phân bố hash phù hợp; không suy mọi lần lookup luôn mất một bước hay mọi implementation đều như nhau. Tham chiếu [ArrayList performance](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html), [HashSet performance](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html), [HashMap performance](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).

UI so sánh số phần tử cần xem trong một linear scan do tác giả định nghĩa, không hiển thị millisecond hoặc đếm bước hash giả thành benchmark Java. Mẫu chỉ gợi mở CAP-COL-006-B; bài chi phí đầy đủ cùng assessment sẽ được biên soạn sau.

## 4. Cách trực quan hóa

Một tình huống ngắn → người học dự đoán → thực hiện một action → đối chiếu state trước/sau → đọc lý do. Mỗi lần chỉ giải thích sự thay đổi vừa xảy ra; không đổ toàn bộ chương lý thuyết trước bàn thao tác.

| Bài | Hình chính | Quan sát bằng chữ tương đương |
|---|---|---|
| C1 | Thẻ có index, đánh dấu ô đọc/thay/xóa | Bảng index/value trước/sau, input và return value |
| C2 | Cổng so ID, cụm không thứ tự hoặc hàng có thứ tự | `add(P7): false, size=2; không có phần tử mới` |
| C3 | Hai cột key/value, đường nối khi lookup | Key, tồn tại?, giá trị cũ/mới, size |
| C4 | Cùng bộ dữ liệu đi qua ba lựa chọn | Yêu cầu nào giữ được, thông tin nào bị mất |
| C5 | Con trỏ input và Map số đếm/nhóm sau mỗi bước | Dòng đang xử lý, giá trị trước/sau, phần input còn lại |
| C6 | Dãy ô được kiểm tra của linear scan | Số phép so trong mô hình, ghi rõ không là thời gian JVM |

Màu/animation bổ sung cho nhãn chữ. Step/replay/pause nếu có phát tự động; reduced motion hiển thị trực tiếp trạng thái cuối của bước. Bố cục màn, kích thước, focus cụ thể thuộc D07.

## 5. Contract mô hình

Đây là contract hành vi để MD03 triển khai sau; không phải schema TypeScript. Tên action dưới là định danh trong spec, D08 quyết định ID activity bền vững và adapter.

**Miền dữ liệu và giới hạn:** tối đa 12 phần tử/bản ghi trong bảng thao tác để đọc được. String ID khớp `[A-Za-z0-9_-]{1,16}`, so chính xác và không tự trim/lowercase. Giá trị tên là chuỗi ASCII chữ/số/dấu cách dài 1–32, có ít nhất một ký tự không phải dấu cách. Giới hạn này thuộc UI bài học, không phải giới hạn của Java. Input sai bị từ chối trước mutation, có lý do, giữ state tốt. Main model không nhận null, object tùy ý hoặc Java source. Preset null/equality là đối chứng tác giả soạn, có nhãn riêng.

**Fixture mặc định:** List `[P7,P2,P7]`; Set rỗng để thêm từng mã; Map `{P7:Pen,P2:Book}`. Counter dùng input C5 và Map rỗng/cursor 0; Grouping dùng input C5 và Map rỗng/cursor 0. Cho chọn fixture rỗng ở mỗi mô hình. Mỗi lượt nhóm giữ mọi lần xuất hiện của ID trong input, kể cả trùng; muốn loại trùng trong nhóm là một yêu cầu khác. Counter/Grouping cũng giới hạn input 12 bản ghi và kiểm tra toàn input trước khi bắt đầu. Các state lỗi chỉ để đọc trong preset riêng, không làm hỏng state chính.

| Model/state | Action | Điều kiện và observation |
|---|---|---|
| List: dãy String có trùng | append(value), insert(index,value) | Append cuối; insert nhận 0…size. Khi đủ 12 phần tử báo giới hạn trước khi thêm; insert trả void, append true |
| List | get(index), set(index,value), removeAt(index) | Nhận index nguyên 0…size−1; get trả phần tử; set trả giá trị cũ; removeAt trả phần tử xóa và dịch vị trí |
| List | contains(value), removeValue(value) | Contains không đổi state; removeValue xóa lần xuất hiện đầu và trả boolean; thiếu trả false |
| Set: tập String; chế độ hash hoặc linked | add(value), contains(value), remove(value) | Kết quả boolean; add trùng không đổi state/size/thứ tự linked; remove thiếu false. Giới hạn 12 chỉ chặn thêm phần tử mới, không chặn add trùng |
| Map: khóa String duy nhất → tên String | put(key,value), get(key), containsKey(key), remove(key) | Put trả giá trị cũ hoặc null khi mới; get/remove trả value/null; containsKey boolean. Ghi đè không tăng size, vẫn cho phép khi đủ 12 khóa |
| Counter: input ID[], cursor, Map<ID,count> | step | Đọc input[cursor], tăng count đúng một lần và cursor +1; tới cuối không làm gì thêm |
| Grouping: input {id,category}[], cursor, Map<category,List<ID>> | step | Category dùng miền ID, mỗi nhóm có List riêng; append ID theo thứ tự input; cursor +1; tới cuối không đổi |

Set hash/Map hiển thị khóa theo thứ tự ký tự ổn định để dễ đọc nhưng ghi **“thứ tự trình bày, không phải thứ tự Java bảo đảm”**; nghiệm thu so tập/mapping, không so thứ tự màn hình. LinkedHashSet có thứ tự theo add/remove trong mô hình này; không mô phỏng addFirst/addLast. Không dùng equality theo reference của object JavaScript để thay hợp đồng String đang dạy.

**Điều khiển và phục hồi:** một click action tạo tối đa một transition; action chỉ đọc vẫn có observation. Trong một transition, state mới và observation phải cùng một bước. Khi phát tự động thì input/action sửa dữ liệu khóa tạm đến pause. Back/replay xem hoặc dựng lại các bước từ fixture/action hợp lệ, không tạo evidence mới; sửa input sau Back tạo nhánh mới và bỏ tương lai của thí nghiệm đó. Đổi fixture/chế độ dựng state riêng, không biến lịch sử mô phỏng thành lịch sử học. Reset đưa về fixture đầu, giữ attempts/progress. Reload lấy checkpoint đúng version theo D08; nếu không khôi phục được, báo rõ và cho chạy lại, không giả đã nộp bài.

**Ngoài mô hình:** không buckets/collision implementation, GC, memory address, concurrency, custom equals/comparator, capacity growth hoặc chạy JDK. Không đo hiệu năng thật. Java exceptions chỉ được giải thích qua input tương ứng; UI validation error không là bằng chứng JVM vừa ném lỗi. Main Map null bị chặn không có nghĩa HashMap Java cấm null.

## 6. Ma trận expected của mô hình

Mỗi hàng độc lập, trừ khi ghi chuỗi action. Đây là review contract bằng tay, chưa là test app/Java đã chạy. D06b không được dùng nguyên các hàng đã lộ đáp án này để cấp pass độc lập.

| ID | State/input và action | Expected |
|---|---|---|
| CM-01 | List `[P7,P2,P7]`; get(1) | P2; state/size không đổi |
| CM-02 | List `[P7,P2,P7]`; set(1,P9) | Trả P2; `[P7,P9,P7]`, size 3 |
| CM-03 | List `[P7,P2,P7]`; removeValue(P7) | true; `[P2,P7]`, chỉ xóa một lần |
| CM-04 | List `[P7,P2,P7]`; removeAt(1) | Trả P2; `[P7,P7]` |
| CM-05 | List `[]`; insert(0,P7), rồi get(0) | `[P7]`; insert void, get P7 |
| CM-06 | List `[]`; get(0) hoặc removeAt(0) | Input index sai, state giữ; giải thích nếu gọi Java get/remove index này sẽ lỗi |
| CM-07 | List size 3; insert(3,P9) so với get(3) trên fixture gốc | Insert được, size 4; get trên fixture gốc sai index |
| CM-08 | List `[P7]`; contains(P9), removeValue(P9) | Cả hai false; `[P7]` |
| CM-09 | HashSet rỗng; add P7, P2, P7 | true,true,false; tập {P7,P2}, size 2; không kiểm order |
| CM-10 | LinkedHashSet `[P7,P2]`; add(P7), remove(P7), add(P7) | false,true,true; cuối `[P2,P7]`; lần add trùng không đổi order |
| CM-11 | Set {P7}; add(p7), remove(P9) | true,false; tập {P7,p7} |
| CM-12 | Map `{P7:Pen}`; put(P7,Pencil) | Trả Pen; `{P7:Pencil}`, size 1 |
| CM-13 | Map `{P7:Pen}`; put(P2,Pen) | Trả null; hai key cùng value Pen, size 2 |
| CM-14 | Map `{P7:Pen}`; get(P9), containsKey(P9), remove(P9) | null,false,null; map giữ nguyên |
| CM-15 | Map `{P7:Pen,P2:Book}`; remove(P7) | Trả Pen; `{P2:Book}` |
| CM-16 | Preset null `{P0:null}`; query P0 và P9 | Get đều null; containsKey P0 true, P9 false; preset không cho sửa |
| CM-17 | String ID sai format/null hoặc index không nguyên | UI từ chối trước mutation; không biến thành chuỗi `null` hoặc tự sửa input |
| CM-18 | Đủ 12 phần tử/key; thử thêm mới và cập nhật/trùng | Thêm mới báo giới hạn mô hình; List.set, Set.add trùng và Map.put khóa cũ vẫn hợp lệ |
| CM-19 | Counter `[BOOK,PEN,BOOK]`; step đến cuối, thêm step | BOOK:2/PEN:1, cursor 3; step thêm không đếm lại |
| CM-20 | Counter/grouping input `[]` | Map rỗng, cursor 0; step không đổi |
| CM-21 | Grouping `(P7,S),(P2,B),(P9,S)` | S:[P7,P9], B:[P2]; hai List riêng, thứ tự key không chấm |
| CM-22 | HashSet/Map đổi vị trí thẻ hiển thị, mapping không đổi | Kết quả logic không đổi; không tạo failed case chỉ vì order |
| CM-23 | Replay/Back/reset sau khi đã có assessment pass trước đó | Chỉ thay thí nghiệm, không xóa/nhân attempt hoặc CAP progress |
| CM-24 | Cùng hash nhưng khác equality; hoặc sửa field key | Cùng hash chưa đủ kết luận trùng; key mutable chỉ giải thích rủi ro/ngoài mô hình, không phát output JVM giả |
| CM-25 | Grouping `(P7,S),(P7,S)` | S:[P7,P7], giữ hai lần xuất hiện; không tự đổi List của nhóm thành Set |

## 7. Bàn giao phần luyện, kiểm tra và áp dụng

[collections-assessment.md](../../f04-assessment/collections/cases.md) là nguồn bài luyện/assessment/rubric/áp dụng đã hoàn thiện trên giấy ngày 29/09/2026. Bảng dưới giữ các yêu cầu bàn giao D06a; ma trận cụ thể bảy CAP và ID bài nằm ở mục 1 của D06b.

| CAP chính | Kiến thức/ca mẫu để thiết kế luyện | Yêu cầu đã được D06b cụ thể hóa |
|---|---|---|
| CAP-COL-002-A | C1, CM-01–04 | Luyện thao tác đủ bốn loại; assessment mới phân biệt vị trí/giá trị |
| CAP-COL-002-B | C1/C4, CM-05–08 | Chọn List và xử lý rỗng/index, không chỉ chọn tên cấu trúc |
| CAP-COL-003-A | R3/C2, CM-09/11/24 | Loại trùng + giải thích equality, không suy hash bằng nhau là equal |
| CAP-COL-003-B | C2/C4, CM-10/22 | Tình huống yêu cầu/thả lỏng thứ tự, chỉ rõ implementation |
| CAP-COL-004-A | C3, CM-12–16 | Tra/cập nhật/thiếu khóa theo contract, preset null đối chứng |
| CAP-COL-004-B | R3/C3, CM-12/24 | Trùng key/khóa ổn định; giải thích vì sao không dùng index hoặc field mutable |
| CAP-COL-006-A | C4/C5, CM-19–21 | Chọn/đếm/nhóm có trade-off; bài thuật toán và bài áp dụng nhỏ |

D06b phải có câu hỏi/input riêng, tiêu chí quan sát, đáp án, feedback từng lỗi, hint tăng dần và biến thể đủ khác sau khi lộ lời giải. Cách chấm câu giải thích phải được thiết kế cụ thể (ví dụ chọn lý do và phản chứng có đáp án); không mặc định cần AI chấm văn bản tự do.

Bài áp dụng nhỏ có thể tra/nhóm sản phẩm; không gọi là đã hoàn thành P1. P1 thật cần readiness TOP-COL-007 và prerequisite theo catalog, gồm validation/unit test chưa được chứng minh bởi gói này. Phần mô phỏng cấp evidence đúng loại khi rubric D06b đủ, không xác minh người học đã chạy Java.

## 8. Progress, khả dụng và trạng thái xuất bản

Điều khiển bước chỉ ghi kiến thức đã xem của đúng TOP/version. D06b chốt rubric cho 7 CAP chính; việc xem thẻ ôn không cấp CAP nền, C6 không cấp CAP-COL-006-B. Kết quả gói là tổng hợp các TOP, không có tiến độ “Collections” riêng. Gợi ý sau mẫu dựa năng lực còn thiếu, không mặc định bắt đầu P1 khi vừa xem xong.

Mọi thao tác phải dùng keyboard được; kéo thẻ có nút/form thay thế. Input có label/error, feedback state trước/sau được đọc bằng chữ; order không chỉ biểu đạt bằng màu. Pause giữ con trỏ, reduced motion không làm thiếu thông tin. Lỗi lưu hiển thị “chưa lưu” theo D05 và cho tiếp tục trong phiên; tải lại không được khẳng định khôi phục phần chưa lưu. D07 chốt màn/focus; D08 chốt persistence/checkpoint.

Không có lab Java thật trong D06a; T13 không áp dụng cho mẫu mô phỏng. Khi bổ sung lab phải có môi trường/setup/reset/output đã thử riêng. Nguồn kỹ thuật đã review không đồng nghĩa runtime đã được kiểm chứng.

## 9. Đối chiếu template và review

| Phần template | Phần tài liệu / trạng thái |
|---|---|
| T01/T02 | Mục 1: TOP/CAP/version/phạm vi và tình huống |
| T03 | Mục 2: prerequisite và thẻ ôn R0–R3, không ngõ cụt planned |
| T04 | Mục 3: C1–C6, chính/hỗ trợ rõ |
| T05/T06 | Mục 4–6: trực quan, contract, 25 case expected |
| T07/T08/T09/T10 | D06b mục 2–8: 5 bài luyện, đếm/nhóm, 8 assessment, CL-U-01 và rubric |
| T11 | Mục 8/D05 và D06b mục 7: kết quả từng CAP, ôn theo lỗi, hết biến thể |
| T12 | Mục 8 có yêu cầu hành vi; bố cục/luồng chi tiết D07 |
| T13 | Không áp dụng: chưa có đường thực thi Java thật |
| T14 | Mục 10: nguồn/phiên bản/ngày review |
| T15 | Two Sum tùy chọn, Big O/Collections nâng cao liên kết catalog |

Walk-through tác giả trên giấy: người mới gặp khai báo generic → R1; nhầm identity/equality → R3; tiếp tục List bằng thẻ có index; thử rỗng thấy lý do; Set thấy phần tử trùng; Map thấy ghi đè; C4/C5 liên kết nhu cầu thật. Người biết trước được khám phá trực tiếp và sẽ kiểm tra bằng đề mới ở D06b. Bài nền planned không được giả thành đã học; chưa có learner test.

Review 26/09/2026: đối chiếu 7 CAP chính với catalog, rà C1/C2/C5 và CM-01–25 bằng tay; nguồn chính thức xác nhận return/index/order/null/equality và giả định chi phí. Findings đã xử lý trong spec: thứ tự HashSet chỉ là trình bày; thêm trùng/ghi đè vẫn được khi chạm giới hạn 12; reset không xóa progress; nhóm dùng List riêng và giữ trùng; không ép lookup null thành missing trong mọi Map. Kiểm tra link/ID theo checkpoint backlog. Chưa chạy Java hoặc app test, chưa có runtime để nghiệm thu.

## 10. Nguồn kỹ thuật đã đọc

Review bởi Codex ngày 26/09/2026, tài liệu Java SE 21. Dẫn nguồn cho semantics, không coi nguồn là chứng nhận các ví dụ tác giả đã chạy. Khi đổi version hoặc bổ sung API phải review phần bị ảnh hưởng.

| Nguồn chính thức | Phần dùng |
|---|---|
| [List](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html) | Generics/API và index, overload/remove, return values ở C1/CM-01–08 |
| [ArrayList](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html) | Implementation sửa được, chi phí C6 |
| [Set](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Set.html) | Equality/duplicate, mutable element |
| [HashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashSet.html) | Không bảo đảm thứ tự, giả định hash |
| [LinkedHashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html) | Thứ tự add và thêm lại phần tử đang có |
| [Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html) | Get/containsKey/put/remove, khóa mutable và null |
| [HashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html) | Cho phép null, không bảo đảm thứ tự, giả định chi phí |
| [Object](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html) | Equals/hashCode và identity mặc định |
| [String](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html) | String bất biến; ID dạng chuỗi |
