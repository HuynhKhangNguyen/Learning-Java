# Collections — luyện tập, kiểm tra và áp dụng

BL-D06b · 29/09/2026 · contentVersion=1, rubricVersion=1 · **draft, chưa published**. Đi cùng [kiến thức/mô hình D06a](../../f02-learning-experience/collections/knowledge.md), [policy D05](../evidence-contract.md), [contract server S03](../../f11-learner-work/data-contract.md) và [template](../../f08-content/authoring/topic-template.md). Phạm vi REQ-04–08/10/12/16; F03–06/08/11; MD04/05/07/11. Java SE 21 là chuẩn ngữ nghĩa, không có Java runner trong mẫu này.

## 1. Hợp đồng chung và ma trận năng lực

ID CL-P-* (luyện), CL-A-* (assessment), CL-U-* (áp dụng) là ID nội dung ổn định để D08 đưa vào schema. Không tạo TOP hoặc progress tổng “Collections” mới. Các case cùng ID sửa nghĩa phải tăng version; biến thể mới có ID mới, không đổi nhãn ngẫu nhiên để giả case mới.

| CAP chính | Kiến thức | Luyện | Assessment thay thế nhau | Tiêu chí bắt buộc |
|---|---|---|---|---|
| CAP-COL-002-A | C1 | CL-P-L | CL-A-L1 hoặc CL-A-L2 | LA1 thêm/đọc/sửa; LA2 xóa vị trí/giá trị và giữ trùng |
| CAP-COL-002-B | C1/C4 | CL-P-L | CL-A-L1 hoặc CL-A-L2 | LB1 chọn List có lý do; LB2 rỗng/index/giữ state khi từ chối |
| CAP-COL-003-A | R3/C2 | CL-P-S | CL-A-S1 hoặc CL-A-S2 | SA1 kết quả loại trùng; SA2 equality và phản chứng hash |
| CAP-COL-003-B | C2/C4 | CL-P-S | CL-A-S1 hoặc CL-A-S2 | SB1 chọn implementation theo order; SB2 add lại/remove rồi add |
| CAP-COL-004-A | C3 | CL-P-M | CL-A-M1 hoặc CL-A-M2 | MA1 lookup/update/missing; MA2 phân biệt null bằng containsKey |
| CAP-COL-004-B | R3/C3 | CL-P-M | CL-A-M1 hoặc CL-A-M2 | MB1 key trùng/value trùng/size; MB2 khóa ổn định và biến/reference |
| CAP-COL-006-A | C4/C5 | CL-P-F, CL-P-G | CL-A-D1 hoặc CL-A-D2 | DA1 lựa chọn/trade-off; DA2 đếm; DA3 nhóm có List riêng |

Một CAP chỉ đạt khi **mọi tiêu chí của CAP đó đúng trong cùng submission độc lập**; không dùng điểm trung bình, không cộng mảnh từ hai lần fail. Sai LB2 không làm LA1/LA2 tự sai, sai MB2 không xóa MA. CAP-COL-006-B, CAP nền và P1 không được cấp bởi gói này. Với TOP-COL-006 luôn hiển thị B chưa được đánh giá, kể cả A đạt.

Evidence là `assessedResponse` cho dự đoán/lý do, hoặc `checkedSimulation` cho trace được server kiểm; đây là tên nghiệp vụ, D08 thống nhất enum. UI ghi “đạt trong mô hình Collections”, không ghi đã chạy Java. Chỉ backend tính tiêu chí từ đáp án/trace và case đã ghim, không nhận passed/correct của client. Tự viết code hoặc giải thích tự do là ghi chú, chưa có evaluator để cấp thêm CAP.

### Định dạng trả lời và quy tắc so sánh

- Miền String ID, tối đa 12 phần tử, giới hạn value và phân biệt hoa thường theo D06a. Không tự trim/lowercase ID. Null chỉ xuất hiện trong preset chỉ đọc được ghi rõ.
- List/LinkedHashSet: so chuỗi theo đúng thứ tự; HashSet: so tập, không so thứ tự trình bày. Map/counter/grouping: so key/value không theo thứ tự key; value List vẫn có thứ tự và giữ lần lặp.
- Mỗi câu có field: return value, state, size/existence nếu đề yêu cầu; phân biệt `void`, `null`, `false` và lỗi index. Không chấp nhận chuỗi chữ “null” thay null hoặc số đếm dạng text không hợp lệ. Form có lựa chọn giá trị đặc biệt để không bắt đoán cú pháp JSON.
- Câu lý do dùng chọn đáp án + dữ liệu phản chứng. Chọn thừa đáp án sai hoặc thiếu đáp án đúng thì tiêu chí đó chưa đạt. Không chấm văn tự do bằng regex/AI. Đề công khai số lựa chọn cần chọn, các lựa chọn và yêu cầu; đáp án đúng bên dưới là tài liệu tác giả, không nhúng trước submit.
- Trong assessment, action/trace do người học đề xuất hoặc dự đoán được lưu nhưng **không chạy để lộ kết quả đúng trước nộp**. Validation chỉ báo sai định dạng/ngoài miền, không báo đúng ngữ nghĩa. Người học muốn thử mô phỏng được chuyển sang luyện có hỗ trợ; server ghi exposure trước khi trả feedback như S03.
- Tất cả List trong bài là mô hình List sửa được kiểu ArrayList; `{}` chỉ là notation mapping, không là Java source. Lỗi index ở mô hình là từ chối action, giữ state; giải thích Java tương ứng ném IndexOutOfBoundsException, không giả đã ném lỗi JVM.

## 2. Bài luyện có hỗ trợ

Mỗi bài dưới cho dự đoán → thử → feedback → sửa lại. H1 là gợi ý hướng nghĩ, H2 chỉ vị trí cần theo dõi; mở đáp án hiển thị toàn phần expected. Mọi lượt vẫn là practice, không cấp mastery dù không mở hint.

### CL-P-L — Sửa danh sách quét

Input `[P7,P2,P7]`. Làm liên tiếp `append(P9), get(1), set(1,P4), removeValue(P7), removeAt(1)`.

| Bước | Return | State sau bước |
|---|---|---|
| append(P9) | true | `[P7,P2,P7,P9]` |
| get(1) | P2 | `[P7,P2,P7,P9]` |
| set(1,P4) | P2 | `[P7,P4,P7,P9]` |
| removeValue(P7) | true | `[P4,P7,P9]` |
| removeAt(1) | P7 | `[P4,P9]` |

Tiếp: trên state cuối get(2) bị từ chối, insert(2,P8) hợp lệ → `[P4,P9,P8]`, return void. Fixture riêng `[]`: get(0) bị từ chối, insert(0,P1) → `[P1]`. Chọn List cho lịch sử quét vì giữ thứ tự và lần lặp; Set mất số lần quét.

H1: “Index là vị trí hiện tại hay mã sản phẩm?” H2: “Đánh lại chỉ số sau mỗi lần xóa; removeValue chỉ tìm lần xuất hiện đầu.” Sai xóa cả hai P7 → feedback chỉ hai vị trí ban đầu và vị trí còn lại, ôn C1. Sai get(size) → nhắc phạm vi đọc 0…size−1; insert cho phép size. Đáp án là bảng và hai fixture trên; không thêm số điểm thưởng vì xem lại.

### CL-P-S — Mã duy nhất và thứ tự gặp

Input add vào LinkedHashSet rỗng: `P7,P2,P7,p7`. Return `true,true,false,true`; state `[P7,P2,p7]`. Add lại P2 trả false, giữ nguyên; remove P7 trả true rồi add P7 trả true → `[P2,p7,P7]`. HashSet chứa cùng ba mã nhưng không hứa thứ tự.

H1: “So nội dung ID chính xác, có phân biệt hoa thường.” H2: “Thêm phần tử đã có khác xóa rồi thêm.” Lời giải: P7 và p7 khác nhau; add trùng không thêm phần tử hay chuyển vị trí. Sai dùng hash bằng nhau để kết luận equal → phản chứng hai object khác ID được tác giả cho cùng hash, equality theo ID vẫn false; ôn R3. Sai cho HashSet luôn theo alphabet → chỉ rõ đó là cách vẽ UI, ôn C2.

### CL-P-M — Cập nhật bảng tên

Input `{P7:Pen,P2:Book}`. `put(P7,Pencil)` trả Pen → `{P7:Pencil,P2:Book}` size2; `put(P9,Book)` trả null → size3; `get(P0)` null, `containsKey(P0)` false; `remove(P2)` Book → `{P7:Pencil,P9:Book}`. Key không trùng, value Book được phép lặp trước khi xóa.

Preset riêng `{P0:null}`: get(P0) và get(P1) đều null, containsKey lần lượt true/false. H1: “Đang tìm theo key hay value?” H2: “Ghi key đã có có thêm hàng không? get null đã đủ biết thiếu key chưa?” Lời giải là các state trên. Sai tăng size khi cập nhật → đánh dấu cùng hàng bị thay; sai đồng nhất null với thiếu → hiện cột tồn tại; ôn C3. ID String ổn định phù hợp hơn index danh sách cho tra cứu sản phẩm.

### CL-P-F — Bài toán kinh điển: bảng tần suất

Đề: input `[RED,BLUE,RED,RED]`, trả mapping số lần xuất hiện; giữ phân biệt chữ hoa/thường, không cần thứ tự key, input rỗng hợp lệ. Chỉ dùng Map counter/step của D06a, không phải Java chạy thật.

Lời giải: với mỗi x, đọc count cũ (0 nếu chưa có), ghi cũ+1. Trace `{RED:1}` → `{RED:1,BLUE:1}` → `{RED:2,BLUE:1}` → `{RED:3,BLUE:1}`. Rỗng → `{}`; `[A,A,A]` → `{A:3}`; `[A,a]` → `{A:1,a:1}`.

H1: “Set giữ được số lần không?” H2: “Ở lượt RED thứ hai, số cũ là mấy?” Sai luôn put(x,1) → báo mất tích lũy từ lần lặp thứ hai. Sai tăng một lần cho cả batch → yêu cầu mỗi phần tử input tạo đúng một increment. Bài ứng dụng: số lần gọi endpoint hoặc quét mã; muốn lịch sử đầy đủ vẫn cần List riêng. Đây là trade-off dữ liệu, không chấm Big O.

### CL-P-G — Bài toán kinh điển: nhóm theo khóa

Đề: `[(P1,A),(P2,B),(P1,A),(P3,A)]`, trả Map category→List ID, giữ thứ tự trong nhóm và giữ trùng. Expected `{A:[P1,P1,P3],B:[P2]}`. Trace sau mỗi input: `{A:[P1]}` → `{A:[P1],B:[P2]}` → `{A:[P1,P1],B:[P2]}` → kết quả. Rỗng → `{}`; `[(X,A),(X,B)]` → `{A:[X],B:[X]}` với hai List khác nhau.

H1: “Một nhóm chứa một hay nhiều lần xuất hiện?” H2: “Tạo List ở lúc chưa có category, hay tạo lại mỗi vòng?” Lời giải: nếu chưa có nhóm, tạo List mới cho riêng nhóm đó; append ID vào List của category. Sai put(category,id) → chỉ giữ phần tử cuối; sai dùng Set → mất P1 lặp; sai cùng một List cho A/B → đánh dấu hai mũi tên vào cùng hộp và phần tử bị trộn. Ôn C5/R2.

Hai bài đếm/nhóm là phần kinh điển bắt buộc trong gói; Two Sum chỉ liên kết mở rộng, không đặt vào gate pass. Không tạo bài thuật toán giả cho mỗi topic để đủ số mục.

## 3. Ngân hàng kiểm tra — List

### CL-A-L1 — Biên tập lịch sử đặt chỗ (family CL-F-L1)

Đề công khai: cần giữ mọi lần đặt chỗ theo vị trí, kể cả cùng mã lặp. Input `[A2,B5,A2]`; dự đoán return/state **sau từng bước** dưới đây, chọn cấu trúc và giải thích bằng lựa chọn ở cuối. Mỗi bước dùng kết quả bước trước.

| Action | Return đúng | State đúng | Criterion |
|---|---|---|---|
| get(1) | B5 | `[A2,B5,A2]` | LA1 |
| set(0,C4) | A2 | `[C4,B5,A2]` | LA1 |
| insert(1,A2) | void | `[C4,A2,B5,A2]` | LA1 |
| removeValue(A2) | true | `[C4,B5,A2]` | LA2 |
| removeAt(2) | A2 | `[C4,B5]` | LA2 |

LB1 chọn một: (a) List vì giữ vị trí và lặp; (b) Set vì mọi lần đặt đều còn; (c) Map mã→một giá trị vì tự giữ mọi lần lặp. Đúng a; phản chứng bắt buộc: Set của input chỉ còn `{A2,B5}`, không biểu diễn đủ ba lần.

LB2 ba fixture độc lập: state cuối size2, get(2) bị từ chối/state giữ `[C4,B5]`; insert(2,D6) hợp lệ `[C4,B5,D6]`; trên `[]`, get(0) bị từ chối/state vẫn `[]`. Không chấm insertion từ state đã thay ở fixture khác.

### CL-A-L2 — Xây hàng chờ từ rỗng (family CL-F-L2)

Đề công khai: lịch sử hàng chờ cần giữ các lần vào theo thứ tự, cùng mã có thể vào lại. Bắt đầu `[]`, dự đoán bảng; sau đó phân tích lỗi đọc khi đã rút hết. Không dùng bài luyện để chạy chuỗi này trước nộp.

| Action | Return đúng | State đúng | Criterion |
|---|---|---|---|
| insert(0,X1) | void | `[X1]` | LA1 |
| append(Y2) | true | `[X1,Y2]` | LA1 |
| append(X1) | true | `[X1,Y2,X1]` | LA1 |
| removeAt(1) | Y2 | `[X1,X1]` | LA2 |
| set(1,Z3) | X1 | `[X1,Z3]` | LA1 |
| get(0) | X1 | `[X1,Z3]` | LA1 |
| removeValue(X1) | true | `[Z3]` | LA2 |

LB1 lựa chọn khác thứ tự: (a) Map vì key lặp sẽ thêm hàng; (b) List vì mỗi lần vào có vị trí và giữ lặp; (c) HashSet vì bảo đảm lịch sử. Đúng b; phản chứng Set cho ba lần vào `[X1,Y2,X1]` chỉ chứa `{X1,Y2}`.

LB2 từ `[Z3]`: removeValue(Q9) trả false/state giữ, removeAt(0) trả Z3 → `[]`; get(0) sau đó bị từ chối/state giữ; cách tiếp tục đúng là kiểm rỗng trước đọc, không đổi index sang −1. Fixture riêng `[]`: insert(1,Q9) bị từ chối, insert(0,Q9) hợp lệ. Các bước boundary thuộc LB2, không đòi đúng LB2 để cấp LA.

L2 khác L1 về xây từ rỗng, dịch index sau xóa, xóa thiếu và quay về rỗng; không chỉ đổi mã. Hint L1/L2: H1 “Vẽ index sau mỗi bước”; H2 “Phân biệt miền insert với get và lần xuất hiện đầu của removeValue”. Mở hint làm attempt có hỗ trợ. Feedback LA1: chỉ bước read/write sai; LA2: chỉ vị trí bị xóa; LB1: thông tin mất khi dùng Set/Map; LB2: miền index và state được giữ. Đường ôn CL-P-L/C1.

## 4. Ngân hàng kiểm tra — Set

### CL-A-S1 — Danh sách khách duy nhất (family CL-F-S1)

Đề: cần mã khách duy nhất theo lần gặp đầu, String equality chính xác. Từ rỗng add `B2,A1,B2,b2`; tiếp remove B2 rồi add B2. Trả từng boolean, tập cuối và thứ tự nếu dùng implementation bảo đảm yêu cầu.

Expected SA1: add `true,true,false,true`, sau add `[B2,A1,b2]`; remove/add B2 đều true, cuối `[A1,b2,B2]`. `B2` khác `b2`. SA2 chọn một: (a) equal khi nội dung String ID bằng nhau; (b) equal chỉ khi cùng object; (c) equal nếu hash giống nhau. Đúng a. Preset phân tích: hai object U/V có hash được cho cùng là 7, ID khác và equals theo ID → **không equal**, Set không loại chỉ do collision. Đây là giả thiết object, không khẳng định hashCode của B2/b2.

SB1 chọn LinkedHashSet cho yêu cầu giữ thứ tự; HashSet không hứa, List đơn thuần chưa loại trùng. Nếu yêu cầu chỉ membership không order, cả HashSet và LinkedHashSet đều thỏa; không chấm sai LinkedHashSet chỉ vì đáp án tác giả thích HashSet.

SB2 fixture độc lập `[B2,A1,b2]`: add(A1) trả false/giữ order; remove(B2)+add(B2) cho `[A1,b2,B2]`. Không nhận `[B2,A1,b2]` ở nhánh remove/add.

### CL-A-S2 — Sửa đề xuất loại trùng sai (family CL-F-S2)

Đề: log vào cửa `[M1,M1,N2,m1,N2]`. Đề xuất của bạn học trả `[M1,N2]` vì “không phân biệt hoa thường”; báo cáo yêu cầu giữ thứ tự gặp đầu. Hãy sửa kết quả và chọn lý do; sau đó xét hai thao tác tiếp nối dưới đây.

Expected SA1: `[M1,N2,m1]`, boolean add lần lượt `true,false,true,true,false`. SA2 chọn một: (a) mọi khác biệt hoa thường bị bỏ; (b) ID so chính xác nên M1 khác m1; (c) hai lần M1 luôn khác vì hai thẻ. Đúng b. Phản chứng bắt buộc chọn “chưa đủ kết luận equal” khi chỉ biết hash bằng nhau; với preset đã cho ID khác/equals theo ID thì không equal.

SB1: sửa đề xuất “HashSet luôn xuất theo thứ tự log” bằng LinkedHashSet cho báo cáo; nếu chỉ hỏi membership thì cả hai Set đều hợp lệ. SB2 bắt đầu `[M1,N2,m1]`: remove(Q0) false/giữ; add(M1) false/giữ; remove(N2) true → `[M1,m1]`; add(N2) true → `[M1,m1,N2]`. Dự đoán đủ boolean/state.

S2 yêu cầu tìm lỗi normalization và phân biệt remove thiếu/add trùng với remove/add thật, khác tình huống S1. H1: “Contract có cho lowercase không?” H2: “Một phần tử đang có bị thêm lại có rời vị trí không?” Feedback SA1/SA2 chỉ cặp ID và lỗi equality; SB1 nêu yêu cầu order chưa được bảo đảm; SB2 chỉ bước thực sự đổi membership. Ôn R3/C2/CL-P-S.

## 5. Ngân hàng kiểm tra — Map

### CL-A-M1 — Tra trạng thái thiết bị (family CL-F-M1)

Đề: mapping `{D1:Ready,D2:Busy}`, không cho null value; dự đoán các bước liên tiếp.

| Action | Return đúng | State đúng | Criterion |
|---|---|---|---|
| get(D1) | Ready | `{D1:Ready,D2:Busy}` | MA1 |
| put(D2,Ready) | Busy | `{D1:Ready,D2:Ready}` | MA1/MB1 |
| put(D3,Ready) | null | `{D1:Ready,D2:Ready,D3:Ready}` | MA1/MB1 |
| get(D9) | null | `{D1:Ready,D2:Ready,D3:Ready}` | MA1 |
| containsKey(D9) | false | `{D1:Ready,D2:Ready,D3:Ready}` | MA1 |
| remove(D1) | Ready | `{D2:Ready,D3:Ready}` | MA1 |

MB1 size sau hai put là 2 rồi 3; nhiều key cùng Ready không gộp lại. Các tiêu chí dùng field độc lập: MA1 xét return/state, MB1 xét size và lựa chọn lý do “key duy nhất, value được trùng”.

MA2 preset riêng có null `{D0:null}`: get(D0)/get(D8) đều null, containsKey(D0)=true/containsKey(D8)=false; chọn containsKey để phân biệt. Preset không được đưa null vào form Map chính.

MB2 lựa chọn khóa: (a) index trong danh sách thiết bị có thể đổi; (b) String ID không đổi trong vòng đời thiết bị; (c) object có field equals/hash đang bị sửa. Đúng b, lý do “lookup gắn identity nghiệp vụ ổn định, không gắn vị trí hoặc field thay đổi”. Với biến k ban đầu D2 đã put vào map, gán k=D8 chỉ đổi biến; key D2 vẫn còn, không tự thành D8. Với key object mutable, đáp án đúng là “vi phạm điều kiện ổn định, không suy chắc get sẽ trả gì”, không nhận “luôn ném lỗi”.

### CL-A-M2 — Bảng tên khu vực và đổi biến khóa (family CL-F-M2)

Đề: từ `{R1:North,R2:South}`, biến k là R1. Dự đoán put(k,East), gán k=R3, get(k), put(k,East), remove(R2), put(R1,West).

| Action | Return đúng | State đúng | Criterion |
|---|---|---|---|
| put(k,East), k=R1 | North | `{R1:East,R2:South}` | MA1/MB1 |
| gán k=R3 | Không phải thao tác Map | `{R1:East,R2:South}` | MB2 |
| get(k), k=R3 | null | `{R1:East,R2:South}` | MA1 |
| put(k,East), k=R3 | null | `{R1:East,R2:South,R3:East}` | MA1/MB1 |
| remove(R2) | South | `{R1:East,R3:East}` | MA1 |
| put(R1,West) | East | `{R1:West,R3:East}` | MA1/MB1 |

MA1 thêm containsKey(R3) trước/sau put = false/true. MB1 size sau mỗi put lần lượt 2,3,2 (đã remove trước put cuối), key trùng thay value, value East trùng vẫn được. MA2 preset `{R0:null}` với R9 thiếu: get đều null, containsKey R0=true/R9=false.

MB2 chọn String ID ổn định thay index hoặc object đổi field equals/hash; gán biến không đổi key đã lưu; chỉnh field mutable không có kết quả lookup bảo đảm. Các lựa chọn gồm ba phương án như M1, đổi thứ tự hiển thị nhưng không coi việc đổi thứ tự là một biến thể mới. M2 khác M1 vì phải lần theo biến khóa và hai key cùng value qua xóa/ghi đè.

H1: “Mỗi thao tác dùng key nào lúc đó?” H2: “Theo dõi riêng existence, value và size.” Feedback MA1 chỉ key/return sai; MA2 chỉ sự khác biệt tồn tại/null; MB1 nêu ghi đè không thêm key; MB2 cho sơ đồ biến và key tách nhau. Ôn R3/C3/CL-P-M. Không yêu cầu viết Java hoặc mô phỏng hash bucket.

## 6. Ngân hàng kiểm tra — chọn cấu trúc, đếm và nhóm

### CL-A-D1 — Tổng hợp sự kiện giao hàng (family CL-F-D1)

Đề có ba phần đều bắt buộc cho CAP-COL-006-A:

**DA1:** chọn cấu trúc và lý do cho bốn view. Lựa chọn mỗi câu: List, Set, Map ID→value, Map category→List. (1) Giữ mọi lần quét theo thứ tự → List, vì Set mất lặp. (2) Chỉ membership mã đã gặp → Set, không tự có số lần. (3) Trạng thái mới nhất theo ID → Map ID→value, put thay state cũ nên không giữ lịch sử. (4) Các lần quét theo vùng, giữ trùng/thứ tự trong vùng → Map category→List, không phải value đơn. Mỗi câu phải chọn đúng cấu trúc và đúng trade-off; chọn theo hợp đồng interface, không bắt một implementation duy nhất nếu không đòi order key.

**DA2:** counter ban đầu `{OK:2}`, batch `[FAIL,OK,FAIL]`. Expected sau từng bước `{OK:2,FAIL:1}` → `{OK:3,FAIL:1}` → `{OK:3,FAIL:2}`. Chọn update “đọc cũ hoặc 0, cộng1 mỗi event”, không chọn “gán1” hoặc “cộng tổng độ dài batch cho mọi key”. Batch rỗng giữ `{OK:2}`, không trả `{}` vì có state trước.

**DA3:** input `[(J1,EAST),(J2,WEST),(J1,EAST),(J3,WEST)]`. Expected `{EAST:[J1,J1],WEST:[J2,J3]}`; cần hai List riêng. Câu phản chứng `put(region,id)` chỉ còn `{EAST:J1,WEST:J3}`, mất lần xuất hiện; dùng Set cho value mất J1 lặp. Input rỗng/state ban đầu rỗng trả `{}`. Người học phải chọn cách “tạo List khi key chưa có rồi append”, không tạo lại List mỗi lượt.

### CL-A-D2 — Sửa báo cáo đặt hàng bị mất dữ liệu (family CL-F-D2)

**DA1:** hệ thống cần (1) lịch sử click gồm lặp; (2) danh sách ID duy nhất không order; (3) tên mới nhất theo ID; (4) danh sách ID theo loại giữ lặp/thứ tự. Chọn từ cùng bốn loại ở D1 và gắn lý do: List/Set/Map ID→value/Map category→List tương ứng. Phản chứng lần lượt: Set mất click lặp; List không có bước dedupe vẫn giữ trùng; Set chỉ chứa ID không cung cấp tên; value đơn làm mất các lần cùng loại. List có thể cài membership nhưng đề yêu cầu chọn abstraction trực tiếp cho mã duy nhất, không tuyên bố List không thể làm được.

**DA2:** code giả luôn gán count[x]=1 trên `[NEW,DONE,NEW,DONE,NEW]` cho `{NEW:1,DONE:1}`. Sửa bằng tăng từ giá trị cũ, trả trace `{NEW:1}` → `{NEW:1,DONE:1}` → `{NEW:2,DONE:1}` → `{NEW:2,DONE:2}` → `{NEW:3,DONE:2}`. Fixture `[new,NEW]` trả `{new:1,NEW:1}`, rỗng trả `{}`.

**DA3:** input `[(T1,A),(T2,B),(T3,A),(T1,A)]`, hai key A/B đang bị trỏ tới cùng một List. Chọn sơ đồ sửa mỗi key một List và thuật toán create-if-absent/append; expected `{A:[T1,T3,T1],B:[T2]}`. Nếu cùng dùng shared List, cả A/B đều nhìn `[T1,T2,T3,T1]`; nếu put(category,id) thì `{A:T1,B:T2}`; nếu value Set thì A mất T1 lặp. Đáp án phải chỉ đúng cả hai lỗi “shared List” và “value không giữ nhiều lần” theo fixture tương ứng, không gộp hai fixture thành một execution.

DA1 dùng bank lý do đóng, mỗi view chọn đúng một: R1 giữ thứ tự và lần lặp; R2 membership duy nhất, không giữ tần suất; R3 lookup theo key và thay value, không giữ lịch sử; R4 mỗi key có List riêng giữ nhiều lần theo thứ tự; distractor R5 mọi Set giữ thứ tự; R6 put cùng key luôn tăng size; R7 một value đơn giữ mọi lần; R8 hash giống nhau chứng minh equal; R9 Map lưu số đếm tích lũy theo ID. Đáp án bốn view là R1/R2/R3/R4. Phản chứng D2 chọn một trong: mất lần lặp; vẫn còn trùng; thiếu value gắn key; chỉ còn phần tử cuối của nhóm, ghép theo thứ tự bốn tình huống. Phản chứng D1 theo thứ tự bốn view: Set mất lặp; Set không giữ số lần; put mới nhất mất lịch sử; value đơn mất các lần cùng vùng. U1 dùng R1/R2/R3/R9/R4 theo năm view; view mã duy nhất theo lần gặp đầu phải chọn thêm LinkedHashSet và lý do insertion order.

D1 là incremental batch từ state có sẵn; D2 là debug thuật toán overwrite/aliasing với output sai cho trước, không chỉ thay nhãn input. H1: “Phần thông tin nào cần giữ?” H2: “Ở phần tử lặp, bạn cập nhật số cũ hay thay hết List?” Feedback DA1 nêu yêu cầu bị mất, DA2 chỉ lượt lặp đầu tiên sai, DA3 chỉ nhóm/alias sai. Ôn C4/C5/CL-P-F/CL-P-G. Không cấp Big O vì không có bài phân tích độ phức tạp đầy đủ.

## 7. Retry, hỗ trợ và kết quả

Trong mỗi nhóm L/S/M/D, server cấp một case chưa dùng/chưa lộ; không cho client tự khai case sạch. Hai family được tác giả cho phép thay thế cùng rubric vì có hành vi suy luận khác ở mục 3–6. Renaming input/shuffle lựa chọn không tạo family mới. Exposure trước submit của hint, đáp án, chạy thử đúng case hoặc áp dụng có tiết lộ case đều làm mất tính độc lập; practice không dùng bất kỳ fixture assessment nào.

Sau nộp mới trả feedback theo tiêu chí và đáp án case đó. Fail rồi retry độc lập dùng case còn lại chưa lộ. Hết cả hai case thì **luyện không giới hạn**, nhưng hiển thị “Chưa có tình huống mới để xác minh độc lập”; giữ progress cũ và gợi ý bài ôn, không tự reset exposure theo ngày/logout/reset học. Batch nội dung sau do E01 bổ sung case, cần review đủ khác. Đây là ngân hàng hữu hạn của R1, không hứa kiểm tra độc lập vô hạn.

Case draft chưa submit được resume đúng owner/revision. Submission đã nhận pending không cho sửa đáp án; grader lỗi giữ pending/retry kỹ thuật như S03, không chuyển case mới để né lỗi. Cùng key retry không tạo evidence đôi; hai tab hint/submit dùng thứ tự server. Hỗ trợ chỉ ảnh hưởng submission liên quan trước nộp, không làm bẩn pass cũ khi xem solution sau đó.

| Kết quả | Hiển thị và bước tiếp |
|---|---|
| L1 đúng LA, sai LB | A đạt trong mô hình, B thiếu; CL-P-L rỗng/index rồi L2 nếu chưa lộ |
| M1 đúng MA, sai MB | A đạt, B ôn key/identity rồi M2; không xóa A |
| Đúng hết nhưng mở hint trước nộp | Có hỗ trợ, không cấp mastery mới; có thể thử family còn sạch |
| D1 thiếu một trong DA1/2/3 | CAP-COL-006-A chưa đạt; không bù bằng tổng điểm L/S/M |
| Đã đạt rồi fail bài ôn | Giữ lịch sử, cờ cần ôn theo D05, không xóa evidence |
| Chỉ Next/replay/reset mô hình | Chỉ ghi đã xem/vị trí theo TOP, không pass/applied |
| Offline/expired | Chưa xác nhận/chưa lưu hoặc khóa nháp owner; tra receipt sau login, không ghi pass client |
| Import bài làm/kết quả | Nháp/lịch sử unverified; không official mastery dù đủ đáp án |

## 8. CL-U-01 — Bàn tổng hợp hàng mượn (áp dụng nhỏ)

Bắt đầu khi đã luyện List/Set/Map và đếm/nhóm, biết đọc input R0/R1; không khóa bằng pass bảy CAP và không bắt hoàn thành project từ bài đầu. Bài tổng hợp vận dụng trong mô hình, không phải P1 CLI hoặc chạy Java.

Đề: danh mục `{B1:Book,B2:Pen}`; cập nhật B2→Pencil, thêm B3→Book. Log mượn `[(B1,A),(B2,B),(B1,A),(B3,B)]`. Nộp lựa chọn cấu trúc, thứ tự xử lý và output cho: toàn lịch sử ID, mã duy nhất theo lần gặp đầu, tên mới nhất, số lần mỗi ID, nhóm ID theo khu; thêm kiểm tra mã không có/rỗng.

Expected: lịch sử `[B1,B2,B1,B3]` (List); mã duy nhất `[B1,B2,B3]` (LinkedHashSet); danh mục `{B1:Book,B2:Pencil,B3:Book}`; đếm `{B1:2,B2:1,B3:1}`; nhóm `{A:[B1,B1],B:[B2,B3]}`. get(B9)=null/containsKey(B9)=false. Log rỗng giữ danh mục đã cập nhật, bốn view lịch sử/mã duy nhất/đếm/nhóm lần lượt `[]`, `[]`, `{}`, `{}`.

| Tiêu chí áp dụng | Điều kiện |
|---|---|
| U1 | Chọn cấu trúc cho đủ năm view và lý do giữ/mất thông tin như C4 |
| U2 | Danh mục sau update/insert đúng, lookup missing phân biệt tồn tại |
| U3 | History và mã duy nhất đúng, không làm mất lần mượn lặp ở history |
| U4 | Counter và grouping đúng, mỗi nhóm List riêng; key order không chấm |
| U5 | Fixture log rỗng không xóa danh mục; không nhầm thiếu key với lỗi server |

Tất cả U1–U5 đúng trong một submission thì ghi **“đã áp dụng trong mô hình — CL-U-01”** với nguồn checkedSimulation/assessedResponse, không cấp CAP mới từ bài này, không ghi P1. Hint H1 “Mỗi view cần giữ thông tin gì?”, H2 “Tách danh mục khỏi các view của log”; solution là output trên. Nếu dùng hint/solution trước nộp thì ghi “áp dụng có hỗ trợ”, chưa tính applied độc lập. Làm lại sau khi đã lộ lời giải là luyện; hiện mẫu chỉ có một case áp dụng, chưa có case mới để chứng minh độc lập lại.

Sai view nào feedback chỉ view đó và dẫn CL-P-L/S/M/F/G tương ứng; giữ cả output đúng. Xuất code tự viết hay ảnh terminal chỉ là selfReport chưa xác minh. P1 thật còn COL-007/validation/unit test theo [catalog](../../../curriculum/core-foundations.md), có rubric riêng.

## 9. Checklist evaluator và review hành trình

Đây là expected cho D08/task triển khai R1 v2, chưa là test app đã chạy.

| ID | Ca nghiệm thu | Expected |
|---|---|---|
| CA-01 | Đáp án đúng toàn bộ một case L/S/M/D | Pass đúng CAP rubric, không cấp CAP hỗ trợ/P1 |
| CA-02 | Chỉ LA đúng hoặc chỉ MA đúng | Kết quả riêng A/B, không pass theo tổng điểm |
| CA-03 | HashSet/Map đổi thứ tự key trình bày | Vẫn đúng; List hoặc list con đổi thứ tự thì sai |
| CA-04 | Bỏ phần tử trùng ở List/nhóm; gộp ID khác case | Sai criterion tương ứng, ôn đúng bài |
| CA-05 | Counter luôn1, grouping last-value hoặc shared List | DA2/DA3 không pass dù chọn tên Map đúng |
| CA-06 | Chọn đúng loại nhưng lý do sai/phản chứng sai | Criterion lựa chọn chưa đạt |
| CA-07 | Hint/solution/try trước nộp ở tab khác | Có hỗ trợ theo S03; response client không override |
| CA-08 | Solution sau pass; logout/reset rồi retry case cũ | Pass cũ giữ; không dùng lại case làm độc lập |
| CA-09 | Hết hai biến thể hoặc case withdrawn | Không giả sinh case mới, gợi ý practice và báo thiếu case |
| CA-10 | Server lỗi/submit trùng/nháp conflict/đổi user | Theo LD/IA: không pass giả, không mất snapshot hay đổi owner |
| CA-11 | Reset/replay/Next hoặc import official result giả | Không cấp mastery/applied |
| CA-12 | CL-U-01 đạt/hỗ trợ hoặc nộp code text | Chỉ applied mô hình đúng nhãn; không P1/verifiedExecution |
| CA-13 | Input malformed, quá12, null ngoài preset | Validation trước ghi/chấm, giữ nháp tốt; không đánh đồng lỗi nhập với kết quả kiến thức sai |
| CA-14 | Lý do tự do hoặc lựa chọn thừa | Text không tự chấm; tập lựa chọn so đúng/đủ, không partial CAP |

Walk-through tác giả: người mới thiếu generic vào R1, luyện L rồi assessment; người nhầm Set/order vào C2; Map A đạt/B thiếu nhận ôn key; người biết trước kiểm tra mà chưa xem đủ vẫn chỉ tăng CAP; người hết case còn practice; người mở bài áp dụng hiểu đây là mô hình chứ chưa project Java. D07 phải biến thành luồng keyboard/text alternative, focus lỗi/feedback, resume và pending; không dùng kéo thả làm cách trả lời duy nhất.

## 10. Đối chiếu template và nguồn

| Phần | Nơi đã đặc tả / giới hạn |
|---|---|
| T01 | D06a mục1 và tài liệu này mục1: TOP/CAP/ID/version/draft |
| T02 | D06a tình huống, mục1 ma trận bảy CAP |
| T03 | D06a R0–R3 và prerequisite catalog; chưa cấp CAP nền |
| T04 | D06a C1–C6 |
| T05 | D06a hình và text alternative |
| T06 | D06a model/CM-01–25; mục1 khóa feedback assessment trước submit |
| T07 | Mục2: năm bài luyện, hints/solution/feedback |
| T08 | CL-P-F/G: tần suất và nhóm; Two Sum ngoài scope |
| T09 | Mục3–7: tám case, bảy CAP, rubric/lý do/biến thể/exposure |
| T10 | Mục8: CL-U-01 và rubric U1–U5, không P1 |
| T11 | Mục7/D05: kết quả từng CAP và ôn theo lỗi |
| T12 | Mục1/7/9 và D06a; wireframe/keyboard/runtime nghiệm thu ở D07 và task code sau |
| T13 | Không áp dụng: không có lab Java thật hoặc runner |
| T14 | Nguồn dưới đây; ngày đối chiếu thực 29/09/2026 |
| T15 | Big O, Two Sum, COL-005/007 ngoài scope, liên kết catalog |

Nguồn Java SE 21 được đọc lại ngày 29/09/2026: [List](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html) cho positional access/add/set/remove; [LinkedHashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html) cho insertion order/reinsert; [Map](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html) cho key/value/put/null/containsKey và mutable key; [Object](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html) cho equals/hash. Nguồn bổ trợ HashSet/String/ArrayList đã gắn ở D06a. Bộ input, expected, counter/grouping và phản chứng ở đây là bài tác giả thiết kế dựa trên contract, không sao chép ngân hàng đề hay giả đã chạy JVM.

D06 hoàn thành thiết kế trên giấy khi coverage/expected/policy/template đạt; không đồng nghĩa published. D07 còn trải nghiệm màn; D08 còn schema/evaluator/API/test fixtures; task R1 v2 còn triển khai và nghiệm thu. Nội dung private của assessment phải được tách khỏi payload/browser bundle; tài liệu tác giả chứa đáp án không được phục vụ nguyên văn cho learner. E01 bổ sung ngân hàng case sau mẫu; không buộc có AI chấm hoặc runner để thực hiện phạm vi này.
