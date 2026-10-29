# Backend Lab — Wireframe và phong cách giao diện

Phiên bản 1.0 · 19/09/2026 · T02 hoàn thành: wireframe, phong cách, bản xem trước và kiểm tra thiết kế. Căn cứ: [product-spec.md](../../delivery/history/product-spec-t01.md). Chưa triển khai frontend sản phẩm.

## 1. Hướng thiết kế

Chọn phong cách phòng làm việc yên tĩnh, gọn và có tính thực hành: nền dịu, bề mặt rõ, hình mô phỏng ở trung tâm và màu nhấn teal. Giọng giao diện thân thiện với người đã biết lập trình cơ bản, mới học Java/backend.

Home đưa người học vào một việc cụ thể. Theo điều chỉnh ngày 20/09/2026, Journey dùng sơ đồ phân nhánh tương tác, mở dần theo chặng; xem [journey-map.md](../f01-journey/journey-map.md), thay thế wireframe Journey cũ bên dưới. Lab tập trung vào hệ thống đang chạy và kết quả thao tác. Bản HTML T02 được giữ làm tư liệu thiết kế ban đầu.

Tiến bộ được thể hiện bằng việc hiểu và làm được một nhiệm vụ, không dựa vào streak, bảng điểm hay số phút ở lại app. Hình ảnh không che code, log hoặc dữ liệu mà người học cần đọc.

## 2. Đầu ra và phạm vi bản xem trước

- [design-preview.html](../../design-preview.html): nguồn bản xem trước tương tác, dạng HTML fragment dùng trong cuộc trò chuyện; không phải frontend ứng dụng.
- Wireframe đơn sắc và thiết kế màu dùng cùng cấu trúc, đổi qua tùy chọn của bản xem trước.
- Năm màn: Home, Journey, Lab, Challenge và Result; có các trạng thái mới/tiếp tục, chưa chạy/đang xem trace/lỗi/đã sửa, chưa đạt/đạt, lỗi lưu.
- Hai màu nhấn: teal mặc định, xanh lam phụ; theo theme thiết bị hoặc chọn sáng/tối.
- Chế độ ép chiều rộng mobile 390px để xem sự sắp xếp lại. Bản xem trước cũng tự thích ứng theo chiều rộng khung.
- Mọi tiến độ, log, outcome và thành tích trong preview là dữ liệu minh họa. Không có Java/Docker thật, không ghi dữ liệu học cá nhân.
- Widget có thể nhớ lựa chọn thiết kế cho lần xem sau; việc đó không phải chức năng lưu tiến độ của sản phẩm.

T02 minh họa hành vi đủ để đánh giá bố cục, thứ bậc nội dung và feedback. Bộ chấm C1–C5, storage và state machine đầy đủ thuộc T04–T09. Không dùng mã preview để coi các task đó đã hoàn thành.

## 3. Visual tokens

### Màu

| Token đề xuất | Sáng | Tối | Vai trò |
|---|---|---|---|
| background | #F4F5F1 | #141C20 | Nền không gian học |
| surface | #FFFFFF | #1C272D | Panel, request form, vùng nhiệm vụ |
| text | #192E36 | #E8F0EF | Tiêu đề và nội dung chính |
| text-muted | #53666D | #B1C0C6 | Chú thích, nhãn phụ; vẫn cần đọc rõ |
| border | #D6DFDC | #405258 | Phân tách bề mặt; không dùng để thể hiện lỗi một mình |
| surface-soft | #E7EEEA | #293A3E | Vùng phụ và nhãn trung tính |
| accent | #11675A | #8DD5C0 | CTA chính, trạng thái đang chọn |
| on-accent | #FFFFFF | #102D26 | Text trên CTA |
| accent-soft | #E3F1E9 | #223F36 | Highlight nhẹ cho bước hoặc validation |
| error | #A93225 | #FFB7AB | Trạng thái thất bại và dấu hiệu lỗi |
| error-soft | #FFF0EB | #442E2C | Nền thông báo lỗi |
| warning | #79540D | #ECD293 | Lỗi lưu, kết quả cũ hoặc việc cần chú ý |
| warning-soft | #FFF6DA | #3E3929 | Nền cảnh báo nhẹ |
| code-background | #16262E | #16262E | Vùng code/log tối ổn định |
| code-text | #E3EEE9 | #E3EEE9 | Code/log |

Phương án xanh lam thay accent thành #315B9B / #A8C7FF; on-accent #FFFFFF / #162D4A; accent-soft #E7EEFB / #26364D. Các màu semantic lỗi/cảnh báo giữ nguyên giữa hai phương án.

Không dùng màu sắc một mình: lỗi có mã status, chữ và dấu chỉ vị trí. 400 do validation dùng nhãn mô tả “Input bị từ chối”, không hàm ý API đã tạo task thành công.

### Typography

- Giao diện: Segoe UI, Arial, sans-serif. Hỗ trợ dấu tiếng Việt, không phụ thuộc tải web font.
- Code: Consolas, Courier New, monospace; giữ chữ dễ đọc, không dùng làm font nội dung dài.
- Heading màn desktop: 34px / 1.2, weight 600; mobile 28px. Hero mission 30px; heading panel 22px; heading nhỏ 16px.
- Nội dung chính: 15–16px / 1.55; supporting text 12–13px; nhãn phụ 11px tối thiểu. Nhãn 10px chỉ dành metadata trang trí; khi triển khai vẫn ưu tiên 11–12px nếu cần đọc để thao tác.
- Input edit được: tối thiểu 16px. Code 12px trong panel nhỏ, 14px khi mở rộng; cung cấp zoom từ browser, không khóa text scaling.
- Dòng giải thích dài nên nằm trong khoảng 55–70 ký tự. Tránh cả trang viết hoa; eyebrow ngắn mới dùng uppercase.

### Khoảng cách và hình khối

- Thang khoảng cách: 4, 8, 12, 16, 24, 32, 48px; ưu tiên 24px giữa các nhóm lớn.
- Page padding desktop/laptop: 28–32px; tablet 22px; mobile 16px.
- Panel radius 16px; control 8px; tag 5px. Wireframe đơn sắc dùng góc 3px để tập trung vào cấu trúc.
- Button cao 42px ở desktop, ít nhất 44px ở mobile. Chỉ một CTA nổi bật trong một nhóm tác vụ.
- Dùng đường phân cách và độ tương phản bề mặt. Không dùng bóng đổ nặng, nền kính hoặc gradient trang trí rộng.
- Icon dùng bộ Lucide trong lúc triển khai, kích thước 16–20px, luôn có tên thao tác rõ; icon đơn lẻ cần accessible name.

## 4. Cấu trúc bố cục theo chiều rộng

Breakpoint dựa trên chiều rộng vùng ứng dụng, không chỉ cửa sổ trình duyệt. Preview dùng container query để khung mobile/inline cũng sắp xếp đúng.

| Chiều rộng | Navigation | Nội dung | Lab |
|---|---|---|---|
| Trên 1100px | Sidebar 176px | Page padding 28px, hero hai cột | Sân khấu + bàn thao tác bên trái, nhiệm vụ 210px bên phải |
| 801–1100px | Sidebar 158px | Hai cột nếu còn đủ chỗ | Nhiệm vụ xuống dưới, sân khấu giữ chiều rộng |
| 561–800px | Header và nav ngang | Padding 22px | Một cột chính; panel nhiệm vụ có thể chia hai phần |
| 320–560px | Brand rồi nav ngang có nhãn | Padding 16px; hero/panel xếp dọc | Sơ đồ 2×2, trace có thứ tự rõ; form và nhiệm vụ xếp dọc |

Mobile không thu nhỏ nguyên giao diện desktop. Text, controls và request vẫn có thể đọc. Sơ đồ 2×2 phải đánh số bước để tránh hiểu sai đường đi; khi triển khai có danh sách trace thay thế tương đương. Không vẽ đường đi trái/ngược gây nhầm lẫn.

Màn hình rộng không kéo dòng text dài vô hạn. Trong app thật, nội dung dashboard nên có max-width khoảng 1280–1360px; lab được tận dụng chiều rộng để đọc code. Không khóa toàn trang bằng chiều cao cố định.

## 5. Wireframe màn chính

Các khối dưới đây mô tả thứ tự và tỷ trọng. Bản xem trước có chế độ đơn sắc để xem trực tiếp cùng bố cục.

### W01 — Home mới và tiếp tục

```text
┌─ App navigation ─┬─ Breadcrumb / khu vực học ─────────────────┐
│ Brand            │ Lời chào theo ngữ cảnh                     │
│ Hôm nay          │ Mục tiêu ngắn                             │
│ Hành trình       ├─────────────────────┬──────────────────────┤
│ Phòng lab        │ Nhiệm vụ hôm nay    │ Cảnh request mẫu     │
│                  │ Tên lab + bối cảnh  │ Client → API 500     │
│                  │ Thời lượng dự kiến  │ Manh mối trong log   │
│                  │ [Bắt đầu/Tiếp tục]  │ Nhãn mô phỏng        │
│                  ├─────────────────────┼──────────────────────┤
│                  │ Sẽ làm được gì      │ Hành trình của bạn   │
│                  │ 3 năng lực cụ thể   │ [Xem các chặng]      │
└──────────────────┴─────────────────────┴──────────────────────┘
```

- Home mới: không có % hoặc thành tích giả; CTA “Thử lab đầu tiên”, giải thích đây là lab trải nghiệm, không phải chương bắt buộc đầu tiên của Java.
- Home tiếp tục: cùng vị trí CTA nhưng đổi “Tiếp tục bài”; nêu bước đang dở. Thời lượng là ước tính, không có timer gây áp lực.
- Hero dùng hình hệ thống để gợi vấn đề, không dùng minh họa thuần trang trí chiếm hết chỗ.
- Mobile: intro → nhiệm vụ/CTA → cảnh minh họa → mục tiêu → hành trình. CTA xuất hiện trước cảnh.

### W02 — Journey

```text
Tiêu đề và mục tiêu hành trình
[Java Backend] [DevOps]
┌ Chương 01 · Java nền tảng                        [+] ┐
│ Chương 02 · HTTP & API                           [−] │
│   Lab: Cứu API 500   · Có phần dẫn nhập     [Mở lab] │
│ Chương 03 · Dữ liệu & nghiệp vụ                   [+] │
│ Chương 04 · Đóng gói & triển khai                 [+] │
└─────────────────────────────────────────────────────┘
```

- Chapters thu gọn bằng disclosure; màu nhấn dành cho bài khả dụng/đang học.
- Planned chapter có mô tả và nhãn “Đang biên soạn”, không gắn nút Start bị vô hiệu không có giải thích.
- T02 dùng bốn cụm tổng quan để thử tỷ trọng; T11 sẽ nhập đầy đủ catalog 12 chặng của kế hoạch. Không xóa hoặc gộp mất kiến thức trong dữ liệu thật.
- M1 DevOps hiện mục lục tóm tắt; giải thích bài Docker sau này được chia sẻ với Backend, chưa có nút mở lab Docker.
- Khi triển khai, Back giữ chapter/filter/scroll. Preview chỉ minh họa điều hướng cục bộ.

### W03 — Lab mặc định

```text
Breadcrumb  /  tên bài                     [Mô phỏng]
01 Thử request → 02 Tìm lỗi → 03 Sửa & kiểm tra → 04 Thử thách
┌─ Sân khấu ────────────────────────────┬─ Nhiệm vụ ─────┐
│ Client → Controller → Service → DB   │ 1. Tái hiện lỗi │
│ Trạng thái / dấu hiệu đang quan sát   │ 2. Tìm dấu hiệu │
│ [Bước tiếp] [Reset] [Giảm chuyển động]│ 3. Sửa & thử lại│
├─ Thao tác | Code | Log | Giải thích ──┤ [Gợi ý]         │
│ POST /tasks                          │ [Mới học API?]  │
│ Preset request  /  Cách xử lý         └─────────────────┤
│ Input hoặc code/log                                    │
│                                     [Gửi request]     │
└───────────────────────────────────────────────────────┘
                                 [Đến thử thách]
```

- Cùng một chỗ hiển thị trạng thái idle/running/failed/repaired, tránh người học phải tìm feedback ở nơi mới.
- Sidebar toàn app nhẹ; tiến trình bài nằm trong lab, không tạo thêm menu dài ở cột trái.
- Giải thích nền tảng mở tại chỗ, không chuyển sang bài unpublished.
- Repository có thể gộp trên sân khấu nhỏ; log phải ghi rõ bước Repository trước Database. Mobile hiển thị nhãn thứ tự để không ngụ ý các node chạy song song.
- Empty output: “Gửi request để xem kết quả”. Mã lỗi bài học nằm trong sân khấu, khác với banner lỗi lưu/engine.

### W04 — Lab mở rộng code

```text
Tên bài + breadcrumb + nhãn mô phỏng
Các bước của bài
┌─ Thao tác | Code | Log | Giải thích ─────── [Thu gọn] ┐
│ Tên snippet                                    Chỉ đọc│
│                                                     │
│       Code minh họa / diff cách sửa                  │
│                                                     │
│ [Thử thêm validation]                               │
└─────────────────────────────────────────────────────┘
```

- Ẩn sân khấu và panel nhiệm vụ tạm thời để tăng vùng đọc code, giữ tiêu đề/breadcrumb/tab.
- Không fullscreen hệ điều hành, không chiếm quyền điều hướng; Thu gọn luôn nhìn thấy.
- Code chỉ đọc ở M1, lựa chọn patch là thao tác riêng. Chưa có nút “Compile” hay terminal giả.
- Khi quay lại, giữ tab, input, patch, trace và evidence. Preview minh họa việc mở/thu, chưa có evidence engine.

### W05 — Challenge

```text
Tình huống mới: title chỉ có dấu cách
┌─ Bài toán ───────────────────┬─ Kết quả kiểm tra ──────┐
│ Request mẫu                  │ Thiếu title          —  │
│ ○ Chỉ kiểm tra null          │ Title blank          —  │
│ ○ Kiểm tra null và blank     │ Title hợp lệ         —  │
│ ○ Đổi mọi exception thành 400│ Lỗi ghi dữ liệu      —  │
│ [Kiểm tra lời giải]          │ Feedback tại chỗ         │
│ [Gợi ý theo cấp]             │ [Xem kết quả khi đạt]   │
└──────────────────────────────┴────────────────────────┘
```

- Chưa chọn: báo cần chọn; không tăng số lần sai.
- Chưa đạt: bảng nói input nào sai và vì sao; giữ lựa chọn, CTA kiểm tra vẫn ở chỗ cũ.
- Đạt: màu teal, dấu check và lời giải thích; có đường xem Result.
- Mobile: đề → lựa chọn → nút kiểm tra → bảng feedback. Feedback quan trọng có live announcement, không chỉ ở dưới màn hình.
- Preview hiển thị bốn nhóm kết quả gọn cho mục đích thiết kế; T08 phải có đầy đủ sáu case và C1–C5 của product-spec.

### W06 — Result đạt, chưa đạt và lỗi lưu

```text
Dấu trạng thái
Tiêu đề: đã làm được gì / cần thử thêm gì
Nhãn: kết quả bài tập mô phỏng
┌ Evidence theo từng kỹ năng ──────────────────────────┐
│ ✓ Đọc luồng request                                   │
│ ✓/· Kiểm tra input                                    │
│ ✓/· Phân biệt lỗi input và lỗi nội bộ                  │
└──────────────────────────────────────────────────────┘
[Banner lỗi lưu nếu có] [Thử lưu lại]
[Về hành trình / Thử lại] [Ôn lại / Thử cách khác]
```

- Không dùng confetti, XP hoặc popup che kết quả.
- Lỗi lưu không đổi trạng thái bài thành fail; kết quả và nút Retry vẫn đọc được.
- Khi hết lab published, CTA về Journey/replay, không dẫn tới bài chưa viết.
- Tình trạng lưu chỉ báo thành công sau khi ghi thực tế. Preview gắn nhãn dữ liệu minh họa kể cả khi tắt trạng thái lỗi lưu mẫu.

## 6. Danh mục trạng thái cần triển khai

| ID | Màn/trạng thái | Dấu hiệu trên UI | Hành động khôi phục/chuyển tiếp |
|---|---|---|---|
| D01 | Home mới | Chưa có progress, lab mẫu | Thử lab |
| D02 | Home tiếp tục | Bài và bước đang dở | Tiếp tục đúng bước |
| D03 | Journey planned | Tóm tắt, đang biên soạn | Thu gọn hoặc mở bài published khác |
| D04 | Lab idle | Node chưa chạy, output trống | Gửi request |
| D05 | Lab chạy/paused | Highlight một node, status text | Pause/resume/step; bản T02 hiển thị snapshot |
| D06 | Lab lỗi bài học | 500 tại service, DB chưa gọi | Xem log/code, sửa |
| D07 | Lab đã thêm validation | 400 tại controller, service không chạy | Thử input hợp lệ/blank |
| D08 | Lab hợp lệ | 201, ghi đúng một task mẫu | Thử trường hợp khác |
| D09 | Lab nhận nhầm blank | 201 nhưng title rỗng, cảnh báo nghiệp vụ | Sửa validation |
| D10 | Code mở rộng | Vùng code lớn, nhãn chỉ đọc | Thu gọn, chọn patch |
| D11 | Challenge chưa trả lời | Bảng chưa chạy | Chọn lời giải |
| D12 | Challenge chưa đạt | Case sai và giải thích | Thử lại hoặc mở hint |
| D13 | Challenge đạt | Case đúng, feedback | Xem Result |
| D14 | Result chưa đạt | Kỹ năng cần làm tiếp | Thử lại/ôn |
| D15 | Result đạt | Evidence và gợi ý ôn | Về Journey/replay |
| D16 | Lỗi lưu | Banner amber, kết quả còn nguyên | Thử lưu lại |
| D17 | Kết quả cũ sau sửa input | Nhãn cần chạy lại | Gửi lại request |
| D18 | Engine lỗi thật | Banner lỗi ứng dụng riêng | Khởi tạo lại mô phỏng, giữ câu trả lời |

D01–D17 có biểu diễn hoặc thao tác tương ứng trong preview; D05 là snapshot và D17 chuyển output về trạng thái cần chạy, chưa phải state machine cuối. D18 được đặc tả cho triển khai; T02 chưa giả lập engine crash.

## 7. Component và hành vi bàn giao

| Component | Trách nhiệm | Không nên chứa |
|---|---|---|
| AppShell | Navigation, breadcrumb, vùng nội dung | Logic chấm bài |
| MissionHero | Một nhiệm vụ và CTA rõ | Danh sách nhiều nhiệm vụ ngang hàng |
| ChapterDisclosure / LessonRow | Nhóm chương, metadata, trạng thái published | Bản sao nội dung bài |
| LessonSteps | Vị trí trong bài, điều hướng | Quyết định pass theo click |
| SimulationStage | State hiện tại, node, trace summary | Dữ liệu progress độc lập |
| WorkbenchTabs | Thao tác/code/log/giải thích | Tự giả lập kết quả compiler |
| TaskPanel / HintDisclosure | Mục tiêu và gợi ý có cấp độ | Sidebar dài không liên quan |
| TestCaseResults | Expected/observed và feedback | Thành tích suy đoán |
| EvidenceSummary | Điều đã đạt, việc còn thiếu | XP/đếm thời gian thay thế năng lực |
| PersistenceNotice | Chưa lưu/Retry | Tuyên bố đã đồng bộ cloud |

Các component nhận dữ liệu và callback, không nhúng bài API 500 cố định. T04 quy định schema; T06–T09 chịu trách nhiệm state và persistence. Nhãn/empty/error cần dùng chung giữa các màn.

## 8. Motion, focus và accessibility

- Mặc định trạng thái mới xuất hiện trực tiếp; chuyển panel 120–180ms nếu cần. Di chuyển packet chỉ khi người học yêu cầu run, không animation vòng lặp trang trí.
- Pause và step vẫn cho cùng thông tin với autoplay. Reduced motion giữ snapshot và văn bản, không bỏ bước cần học.
- Focus native được giữ trong preview. Khi xây app: bổ sung focus-visible ít nhất 2px có độ tương phản phù hợp và không bị clipping; không vô hiệu outline.
- Sau thao tác tab/radio, focus cần ở control vừa dùng; không tạo lại toàn bộ DOM như preview. Sau điều hướng màn, đặt focus có chủ đích vào tiêu đề hoặc vùng chính theo chiến lược accessibility của app.
- Tabs triển khai theo keyboard semantics chuẩn; preview dùng nhóm button thông thường, không giả khai ARIA tablist khi chưa có arrow-key behavior.
- Live region thông báo kết quả thao tác quan trọng; không đọc từng frame. Không ép focus nhảy xuống bảng test khi người học còn chọn đáp án.
- Mã status luôn đi cùng ý nghĩa. Lỗi lưu, lỗi engine và lỗi mô phỏng có giọng/nhãn phân biệt.
- Không phụ thuộc icon hoặc hover để hiểu CTA. Có bản mô tả trace bằng text.
- Khi zoom 200%, các panel phải reflow; cần kiểm tra trong app thật ở T22. Kiểm tra layout T02 không thay cho audit screen reader.

## 9. Kiểm tra và bằng chứng

Helper [design/inspect-preview.cjs](../../design/inspect-preview.cjs) dùng Playwright để chụp các màn và kiểm tra hành vi mẫu. Không thêm Playwright vào package ứng dụng trong T02; helper dùng runtime có sẵn qua BL_PLAYWRIGHT_MODULE.

Đã chạy thành công trên Chrome headless 153.0.8010.48. Kết quả tại [design/qa/report.json](../../design/qa/report.json): 30 tổ hợp màn/chiều rộng, không tràn ngang trong phạm vi đã kiểm tra và không có lỗi JavaScript. Đã lưu 13 ảnh, rà trực quan các màn Home, Journey, Lab lỗi/đã sửa/mở code, Challenge, Result và các bố cục mobile/dark/wireframe.

Phạm vi đã kiểm tra: 1440, 1280, 1024, 736, 390 và 320px; điều hướng năm màn; mở rộng code; phản hồi 500/400/201; challenge sai/đúng; lỗi lưu; bàn phím Enter; theme/màu nhấn và reduced motion. Icon từ host là enhancement; helper chặn script mạng để kiểm tra nhãn và bố cục không phụ thuộc network.

Đã tính độ tương phản cho 13 cặp màu chữ/bề mặt chính: tất cả đạt ít nhất 4.5:1, thấp nhất 5.5:1; chi tiết ở [design/qa/contrast.json](../../design/qa/contrast.json). Đây là kiểm tra token, không phải chứng nhận WCAG toàn ứng dụng.

Trong review đã sửa nhãn thứ tự node trên mobile, đồng bộ feedback khi đổi trạng thái trong bảng tùy chỉnh và chụp lại ảnh đủ chiều cao để không mất phần dưới màn hình.

Ảnh tham chiếu:

- [Home desktop](../../design/qa/01-home-desktop.png).
- [Lab gặp lỗi](../../design/qa/02-lab-error-desktop.png).
- [Code mở rộng](../../design/qa/03-code-expanded-desktop.png).
- [Journey](../../design/qa/07-journey-desktop.png).
- [Wireframe đơn sắc](../../design/qa/08-home-wireframe.png).
- [Lab laptop](../../design/qa/09-lab-laptop.png).
- [Home mobile](../../design/qa/10-home-mobile.png).
- [Lab mobile](../../design/qa/11-lab-mobile.png).
- [Lab dark mode](../../design/qa/13-lab-dark.png).

T02 chưa kiểm chứng cảm nhận hứng thú của người học và chưa audit công nghệ hỗ trợ. T10 mới là bước thử prototype học thật, T22 hoàn thiện accessibility toàn app.

## 10. Các quyết định đã chốt để T03 bắt đầu

1. Layout Home có một mission hero, một nhóm năng lực và một đường đến Journey.
2. Navigation M1 chỉ có Hôm nay, Hành trình và Phòng lab; không dựng menu chức năng chưa có.
3. Journey dùng chapter disclosure, không làm graph roadmap làm màn chính.
4. Lab ưu tiên sân khấu, panel code/log có thể mở rộng; nhiệm vụ chuyển xuống dưới khi thiếu chiều rộng.
5. Theme mặc định theo thiết bị; teal là accent mặc định. Biến thể xanh lam là phương án so sánh, chưa cần thêm tùy chọn màu accent vào sản phẩm M1.
6. Nội dung bằng tiếng Việt, thuật ngữ kỹ thuật giữ đúng tên, giải thích tại chỗ.
7. Chỉ ra trạng thái mô phỏng trên toàn luồng; code chỉ đọc ở M1.
8. Chưa cần thêm input bắt buộc để làm T03. Có thể sửa visual tokens sau feedback mà không đổi cấu trúc bài học.

T03 khởi tạo frontend theo kế hoạch, đưa tokens và shell thành component. Bản T02 là tham chiếu thiết kế; không coi chuyển màn trong preview là ứng dụng đã hoàn thiện.
