# Catalog bảo mật, Docker và vận hành — S08–S10

BL-D03 · bản thiết kế 1 · 23/09/2026. Tiếp nối [S00–S03](core-foundations.md) và [S04–S07](core-api-data.md). Dùng chung quy tắc TOP/CAP, năng lực A/B, prerequisite all-of và selector `.A`/`.B`. Domain mới: SEC, CTR, OPS. Tất cả topic ở đây là C, `contentStatus=planned`, `catalogVersion=1`. Đây là thiết kế mục học, chưa là bài đã xuất bản hoặc năng lực đã chứng minh.

Điều chỉnh BL-S04 ngày 28/09/2026: prerequisite shared đã chuyển sang nền độc lập ngôn ngữ ở [catalog DevOps](devops-catalog.md), catalogVersion=2 cho quan hệ; ID/nghĩa CAP giữ nguyên. Bằng chứng kiểm tra D03 cuối file là lịch sử, kiểm tra graph hiện hành gồm catalog DevOps theo checkpoint S04.

## 1. Phạm vi và quan hệ học

- S08 đưa tài khoản và resource ownership vào Task API. Chọn **session** làm luồng triển khai đầu tiên để người học tập trung vào cookie, trạng thái đăng nhập, CSRF và quyền. JWT/OAuth/OIDC được nhận diện trong cây, triển khai sâu ở [nhánh mở rộng](extension-index.md).
- S09 dạy Docker bằng một nguồn topic dùng chung giữa Java Backend và DevOps. Có thể học image/network/volume trước P5; mốc P6 của project tích hợp bản API đã có ownership. Không gắn prerequisite P5 vào kiến thức Docker thuần.
- S10 đi từ bản build có kiểm tra đến triển khai, quan sát và diễn tập phục hồi. Rollback ứng dụng và restore dữ liệu là hai hành động khác nhau; không hứa đảo ngược migration mất dữ liệu chỉ bằng đổi image.
- Prerequisite là hướng dẫn và bằng chứng cần có, không khóa người học khỏi việc khám phá. Tập readiness P5–P7 ở mục 4 khác với “đã áp dụng project”.
- Không yêu cầu mua cloud, vận hành Kubernetes hay có nhiều service để hoàn thành đường lõi. Công cụ và phiên bản thực hành chọn khi viết bài/lab.

## 2. Danh mục và prerequisite

### S08 — Danh tính, quyền và bảo vệ dữ liệu: 8 mục

| Topic ID | Mục học | K | Mức | Prerequisite topic | Mốc liên quan |
|---|---|---|---|---|---|
| TOP-SEC-001 | Trust boundary và rủi ro input | K08,K16 | C | TOP-HTTP-004,TOP-SPR-004 | P5 |
| TOP-SEC-002 | Tài khoản và password hash | K07,K16 | C | TOP-DATA-002,TOP-SQL-002,TOP-SPR-003 | P5 |
| TOP-SEC-003 | Spring Security và session đăng nhập | K09,K16 | C | TOP-SEC-002,TOP-HTTP-004,TOP-SPR-002 | P5 |
| TOP-SEC-004 | Quyền tài nguyên và ownership | K07,K09,K16 | C | TOP-SEC-003,TOP-DATA-003,TOP-SPR-004 | P5 |
| TOP-SEC-005 | Cookie, CSRF và CORS theo client | K02,K16 | C | TOP-SEC-003,TOP-HTTP-004 | P5 |
| TOP-SEC-006 | Secrets, hash, mã hóa và log an toàn | K16,K20 | C | TOP-ENV-002,TOP-HTTP-001,TOP-SQL-007.A | P5–P7 |
| TOP-SEC-007 | Abuse, rate limit và xử lý lỗi an toàn | K08,K16 | C | TOP-SEC-001,TOP-SEC-004,TOP-SPR-004 | P5 |
| TOP-SEC-008 | Security test và kiểm tra vượt quyền | K16,K17 | C | TOP-SEC-004,TOP-SEC-005,TOP-SEC-006,TOP-SEC-007,TOP-SPR-006,TOP-DATA-007 | P5 |

SEC-001/002 có thể học song song từ nền API/DB. SEC-005/006/007 được đào sâu song song sau nền riêng; SEC-008 hợp nhất kiểm tra. SEC-006 chỉ cần năng lực quyền DB (SQL-007.A), không buộc diễn tập restore chỉ để học secrets. Lỗ hổng upload/webhook/SSRF nâng cao thuộc nhánh tích hợp khi chức năng tương ứng xuất hiện. CORS chỉ là chính sách browser; không thay kiểm tra quyền server.

### S09 — Docker và chạy lại được: 8 mục

| Topic ID | Mục học | K | Mức | Prerequisite topic | Mốc liên quan |
|---|---|---|---|---|---|
| TOP-CTR-001 | Image/container và khác biệt với VM | K01,K18 | C | TOP-ENV-002.B | P6 |
| TOP-CTR-002 | Dockerfile, build context và image | K18 | C | TOP-CTR-001,TOP-ENV-004.A | P6 |
| TOP-CTR-003 | Lifecycle, logs và chẩn đoán container | K18,K20 | C | TOP-CTR-001,TOP-ENV-002.B | P6 |
| TOP-CTR-004 | Port, network và service name | K02,K18 | C | TOP-CTR-003,TOP-HTTP-001 | P6 |
| TOP-CTR-005 | Bind mount, volume và dữ liệu bền vững | K18 | C | TOP-CTR-003,TOP-ENV-002.A | P6 |
| TOP-CTR-006 | Compose: app và database | K18,K19 | C | TOP-CTR-002,TOP-CTR-004,TOP-CTR-005,TOP-OPS-009 | P6 |
| TOP-CTR-007 | Health, readiness và shutdown | K18,K20 | C | TOP-CTR-006 | P6 |
| TOP-CTR-008 | Cấu hình runtime, non-root và giới hạn tài nguyên | K16,K18,K19 | C | TOP-CTR-002,TOP-SEC-006 | P6 |

CTR-004/005 có thể học song song; CTR-002 và CTR-003 cũng vậy sau khái niệm image. CTR-006 dùng app/DB mẫu có hợp đồng vận hành OPS-009, không cần tự viết Spring/JPA hoặc chứng nhận P4. Biến thể Task API tự viết cần DATA-007/SPR-007 ở setup lab; không thêm chúng vào prerequisite topic dùng chung. CTR-008 cần SEC-006 với nền độc lập Java; học image/network/volume trước rồi quay lại hardening. Testcontainers không là điều kiện DATA-007; sau S09 chỉ là cách cấp DB test cô lập, cùng progress DATA-007.

### S10 — Delivery, quan sát và phục hồi: 8 mục

| Topic ID | Mục học | K | Mức | Prerequisite topic | Mốc liên quan |
|---|---|---|---|---|---|
| TOP-OPS-001 | CI, artifact và release có dấu vết | K03,K17,K19 | C | TOP-ENV-003,TOP-ENV-004,TOP-CTR-002,TOP-ENV-006 | P7 |
| TOP-OPS-002 | Environment, secret, domain và TLS | K02,K16,K19 | C | TOP-SEC-006,TOP-CTR-008,TOP-NET-002 | P7 |
| TOP-OPS-003 | Triển khai và kiểm tra sau phát hành | K19 | C | TOP-OPS-001,TOP-OPS-002,TOP-CTR-007,TOP-OPS-009 | P7 |
| TOP-OPS-004 | Migration tương thích và rollback ứng dụng | K10,K19 | C | TOP-OPS-003,TOP-SQL-005,TOP-OPS-009 | P7 |
| TOP-OPS-005 | Log, metrics, trace cơ bản và health | K20 | C | TOP-OPS-009,TOP-CTR-007 | P7 |
| TOP-OPS-006 | Alert, sự cố và runbook | K20,K21 | C | TOP-OPS-005 | P7 |
| TOP-OPS-007 | Backup drill và restore DB | K10,K20 | C | TOP-SQL-007,TOP-OPS-004 | P7 |
| TOP-OPS-008 | Smoke test, tải nhỏ và giới hạn hệ thống | K17,K20,K21 | C | TOP-OPS-003,TOP-OPS-005,TOP-OPS-009 | P7 |

OPS-005 bắt đầu từ app mẫu/Compose, không cần chờ triển khai; OPS-004 cần release và SQL transaction. OPS-006/007/008 đi theo nền riêng. Diễn tập alert/rollback/restore cần môi trường lab rõ. Bộ test/ownership/migration được cung cấp trong OPS-009; với Task API tự viết, DATA-002/005/007, SPR-006/007 và SEC-008 là nền triển khai project, không buộc người học DevOps tự xây backend. CI fail thì không phát hành; smoke fail thì xử lý theo runbook. Không dùng benchmark lab như bảo đảm hiệu năng thực tế.

## 3. Năng lực A/B quan sát được

Mỗi dòng khai báo hai competency ID theo quy tắc D01, ví dụ TOP-SEC-004 → CAP-SEC-004-A và CAP-SEC-004-B. Bằng chứng chỉ ghi đúng loại: mô phỏng, lời tự khai hay chạy/test được xác minh. Rubric chi tiết thuộc BL-D05; bài/lab cụ thể thuộc đợt biên soạn.

| Topic ID | A — năng lực/bằng chứng | B — năng lực/bằng chứng |
|---|---|---|
| TOP-SEC-001 | Vẽ luồng input qua client/API/service/DB, xác định trust boundary và điểm cần validate | Tạo ca input bất thường và phân biệt lỗi input, lỗi auth, lỗi server; không log/trả thông tin nội bộ không cần thiết |
| TOP-SEC-002 | Tạo/tìm tài khoản có định danh duy nhất và lưu password bằng hàm hash dành cho password theo cấu hình được chọn | Phân biệt password hash với hash thông thường, encryption và signature; không lưu/trả raw password và xử lý trùng tài khoản |
| TOP-SEC-003 | Thiết lập đăng nhập/đăng xuất bằng session với cookie và tái hiện trạng thái hết hạn/chưa đăng nhập | Phân biệt authentication với authorization; request không có phiên không được coi là người đã đăng nhập |
| TOP-SEC-004 | Kiểm tra quyền trên từng task theo owner ở service/query phù hợp, không tin owner ID do client tự nhận | Chứng minh người dùng A không đọc/sửa/xóa task của B; phân biệt 401/403/404 theo policy công bố mà không lộ tài nguyên |
| TOP-SEC-005 | Giải thích cookie và CSRF trong luồng session, chứng minh request thay đổi trạng thái được bảo vệ theo client đã chọn | Cấu hình CORS phù hợp origin/credential; chứng minh origin policy không cấp quyền đọc/sửa dữ liệu của người khác |
| TOP-SEC-006 | Đặt secret ngoài source/artifact, không log password/token; chỉ định quyền tối thiểu cho DB và runtime | Phân biệt hash/password hash/encryption/TLS theo mục đích; mô tả cách thay secret trong lab và kiểm tra giá trị cũ không bị in ra |
| TOP-SEC-007 | Giới hạn yêu cầu gây lạm dụng theo bài lab và xử lý lỗi không lộ stack/chi tiết nội bộ cho client | Thử input sai, yêu cầu quá nhiều và trạng thái lỗi; log đủ để chẩn đoán mà không tiết lộ dữ liệu nhạy cảm |
| TOP-SEC-008 | Chạy test âm cho anonymous, wrong user, logout/expired session và truy cập trực tiếp ID tài nguyên | Kiểm tra CSRF/CORS theo client, quyền qua API thật và dữ liệu cuối sau request bị từ chối; không chấm pass chỉ bằng UI ẩn nút |
| TOP-CTR-001 | Giải thích image, container, runtime process và khác biệt cơ bản với VM trên sơ đồ/lab | Tạo container từ image và chỉ ra thứ được lưu trong image, trạng thái container và thứ ở host |
| TOP-CTR-002 | Tạo image app từ context có Dockerfile/layer phù hợp và chạy bằng config bên ngoài | Giải thích cache build, tag/digest và chọn runtime image thích hợp; không đưa secret vào layer |
| TOP-CTR-003 | Start/stop/recreate, xem log/exit code và phân biệt lifecycle container với process trong container | Chẩn đoán container không chạy do command/config và chứng minh reset/restart khác remove/recreate |
| TOP-CTR-004 | Phân biệt host port, container port, localhost theo vị trí và service name trong network Compose | Sửa case app không nối DB do sai host/port/network bằng dấu hiệu từ log/connection test |
| TOP-CTR-005 | Chọn bind mount/volume cho dữ liệu và chỉ ra dữ liệu còn/mất sau recreate | Thử có/không volume trên DB lab, phân biệt recreate với xóa volume; không xóa dữ liệu ngoài lab |
| TOP-CTR-006 | Dựng app + DB bằng Compose trên môi trường lab sạch với config riêng, kiểm tra hai service giao tiếp | Chẩn đoán DB chưa sẵn sàng và restart service mà vẫn giữ dữ liệu; không dựa vào thứ tự start là bảo đảm readiness |
| TOP-CTR-007 | Dùng health/readiness phản ánh khả năng phục vụ thực, phân biệt với process chỉ đang sống | Thử DB lỗi và shutdown, quan sát tín hiệu/trạng thái; xử lý request/connection đang dở theo phạm vi lab |
| TOP-CTR-008 | Chạy runtime không dùng quyền cao không cần thiết, đưa secret/config đúng kênh và đặt giới hạn tài nguyên | Chứng minh cấu hình khác nhau không đòi rebuild image; phát hiện image/log chứa secret hoặc lỗi thiếu tài nguyên trong lab |
| TOP-OPS-001 | Pipeline chạy build/test/package, lưu artifact có version/digest và truy dấu commit tạo bản | Tạo ca test fail ngăn bước phát hành; chứng minh deploy cùng artifact đã kiểm tra thay vì rebuild tùy tiện |
| TOP-OPS-002 | Cấu hình domain/TLS, runtime secret và quyền cần thiết cho một môi trường lab triển khai | Chẩn đoán sai config/cert/secret và không in bí mật trong log, artifact hoặc bản báo cáo |
| TOP-OPS-003 | Phát hành bản có artifact xác định, kiểm tra migration/order và thực hiện smoke trên môi trường đích | Thử bản lỗi trong lab, chỉ ra trạng thái đang triển khai/đã phục vụ/thất bại và đường khôi phục |
| TOP-OPS-004 | Phân loại migration tương thích/không tương thích khi code cũ và mới cùng tồn tại | Diễn tập rollback ứng dụng theo kế hoạch, xác định trường hợp cần forward fix/restore dữ liệu riêng; không hứa rollback image đảo migration |
| TOP-OPS-005 | Theo một request qua structured log/correlation ID và chỉ ra một metric, health và trace có ý nghĩa | Phân biệt tín hiệu “đang chạy” với lỗi người dùng gặp; tạo trường hợp lỗi có thể phát hiện mà không lộ dữ liệu nhạy cảm |
| TOP-OPS-006 | Đặt điều kiện cảnh báo có hành động cho một sự cố lab và viết bước xử lý/leo thang trong runbook | Đi qua ca cảnh báo giả lập, đối chiếu log/metric với nguyên nhân rồi ghi sự cố và việc theo dõi; không tuyên bố SLO đo thật nếu chưa đo |
| TOP-OPS-007 | Backup DB theo quy trình lab và restore vào môi trường riêng, kiểm tra record/constraint sau phục hồi | Ghi thời điểm backup, phần dữ liệu có thể mất và quan hệ với rollback app; không dùng file backup tồn tại làm bằng chứng đã restore được |
| TOP-OPS-008 | Chạy smoke sau deploy cho API/ownership và tải nhỏ trên môi trường ghi rõ, báo latency/throughput theo phép đo | Nhận diện bottleneck/giới hạn và phân biệt số đo lab với mục tiêu sản phẩm; regression test fail chặn release |

## 4. Readiness P5–P7 và bằng chứng project

Tập readiness gốc bên dưới dùng A/B của từng topic và prerequisite bắc cầu; việc xem bài hoặc làm mô phỏng không tự thành bằng chứng chạy ứng dụng thật. Topic học có ví dụ nhỏ, milestone kết hợp thành hệ thống.

| Mốc | Readiness gốc | Bằng chứng hoàn thành milestone | Chưa bắt buộc |
|---|---|---|---|
| P5 bắt đầu | TOP-SEC-008 | Task API có hai tài khoản A/B, session login/logout, ownership cho đọc/sửa/xóa, test anonymous, quyền sai và CSRF theo client; dữ liệu không lộ qua ID trực tiếp | JWT/OAuth/OIDC/MFA/SSO, multi-tenant |
| P6 bắt đầu | TOP-CTR-007,TOP-CTR-008,TOP-SEC-008 | Compose app + DB từ trạng thái sạch; volume giữ dữ liệu, port/service name đúng, health khi DB lỗi, restart/shutdown, runtime config/secret an toàn | Kubernetes, cloud, Testcontainers làm điều kiện |
| P7 bắt đầu | TOP-OPS-004,TOP-OPS-006,TOP-OPS-007,TOP-OPS-008,TOP-SEC-008 | Pipeline từ cùng artifact, phát hành và smoke; quan sát lỗi, cảnh báo/runbook, rollback ứng dụng và restore DB riêng trong diễn tập có báo mất dữ liệu | Nhiều vùng/cloud, microservices, SLO/HA thương mại |

Người đã đủ năng lực Docker nhưng chưa hoàn thành P5 vẫn học CTR-001–008 được; mốc P6 của **project chính** cần phần quyền theo Task API. Người học không bị khóa UI, nhưng chỉ đánh dấu “đủ nền tảng P6” khi tập readiness đã đạt. BL-S04 giữ SEC-008 trực tiếp trong readiness P7 sau khi tháo prerequisite Java khỏi OPS-008: P7 vẫn kế thừa nền P6/P5, không dùng trạng thái “đã hoàn thành P6” làm prerequisite topic. DP của DevOps không yêu cầu SEC-008 tự triển khai auth.

## 5. Rà các luồng dễ nhầm

| Ca | Hành vi/năng lực cần giữ |
|---|---|
| A gọi URL trực tiếp tới task của B | Kiểm tra owner trên server/API và dữ liệu cuối; ẩn nút trên frontend không đủ |
| Client gửi owner ID khác sau đăng nhập | Không tin ID do client khai; dùng danh tính đã xác thực và policy tài nguyên |
| Session bị hết hạn hoặc logout | Request sau đó bị từ chối đúng contract; không coi cookie tồn tại là còn quyền |
| CORS cho phép một origin | Vẫn phải check authentication, CSRF và ownership; CORS không thay security control khác |
| Người học chỉ mới mô phỏng Docker | Ghi evidence mô phỏng, không ghi đã build/chạy container thật |
| Recreate container DB | Volume giữ dữ liệu; xóa volume là tác vụ riêng với hậu quả dữ liệu rõ |
| Process chạy nhưng DB chưa sẵn sàng | Readiness không báo phục vụ được; startup order không thay health check |
| Rollback image sau migration phá dữ liệu | Không hứa dữ liệu tự quay lại; dùng kế hoạch migration tương thích/restore/forward fix |
| Backup tạo file nhưng chưa thử restore | Chưa cấp năng lực phục hồi; phải kiểm tra record trong DB đích |
| Pipeline test fail hoặc smoke sau deploy fail | Không báo release thành công; có đường dừng/khôi phục và bằng chứng lỗi |

Kiểm tra 23/09/2026 sau khi ghép [extension index](extension-index.md): script đọc trực tiếp ba catalog và chỉ mục xác nhận 78 TOP/156 CAP, S00–S10, đủ 23/23 K và P0–P7; 51 tham chiếu TOP trong nhánh mở rộng đều tồn tại, selector/link hợp lệ, graph prerequisite không vòng. Closure readiness P5 ⊆ P6 ⊆ P7; các mục Docker đầu không kéo theo auth, P3 không kéo SQL/security/Docker. Đã rà mười ca ở trên và phân biệt rollback ứng dụng với restore DB. Đây là kiểm tra thiết kế, chưa thử learner hoặc code Java/Docker. Rubric, lệnh và nguồn phiên bản thuộc các task biên soạn sau.
