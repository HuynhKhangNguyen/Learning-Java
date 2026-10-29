# Tài khoản, phiên và quyền — R1

> Hướng microservices 30/09/2026: Identity sở hữu phiên, receiver kiểm quyền dữ liệu của mình. Cam kết logout/revocation vẫn giữ; cơ chế shared DB cũ được thay bằng [session LLD v3 MS02](sessions/protocol.md) và [security design](../../architecture/security-design.md): online admission + local monotonic fences + hai participant static ACK trước completed. Đây là thiết kế, chưa runtime.

> [Service catalog](../../architecture/service-boundaries.md) giữ caller/data/API owners, gồm Content tự kiểm quyền body login. MS02 thêm reauth/operation-status và sửa cookie cleanup response trễ; session table không sao chép sang Learning/Content. Các review/evidence S02 phía dưới giữ lịch sử.

BL-S02 · v1 · 28/09/2026. Contract thiết kế cho REQ-14/15, F10, MD10/09; liên quan REQ-09/16, MD06/11. Đầu vào: [system v2](../../system-overview.md), [requirements](../../product-requirements.md), [business](../../business-design.md). Không có backend/auth được triển khai hoặc kiểm thử trong task này.

## 1. Quyết định cho bản đầu

R1 phục vụ nhóm thử nghiệm nhiều người bằng **lời mời**, chưa mở đăng ký công khai. Đây là quyết định cục bộ để có đường cấp tài khoản cụ thể khi admin được để sau; nếu mở công khai cần thiết kế thêm xác minh địa chỉ và chống lạm dụng. Không đổi mục tiêu nhiều user/nhiều thiết bị.

- Mỗi người có `userId` bất biến, `loginName` riêng và mật khẩu. Backend sinh userId; client không tự chọn owner.
- loginName gồm 3–32 ký tự ASCII chữ thường/số/dấu chấm/gạch dưới/gạch ngang, bắt đầu bằng chữ hoặc số. Form trim hai đầu và chuyển ASCII sang chữ thường trước validation; backend làm cùng quy tắc và kiểm unique. Tên hiển thị tách riêng, không dùng làm danh tính hoặc quyền.
- LoginName chưa đổi trong R1. Không dùng email làm định danh đăng nhập nên chưa có vấn đề alias/email normalization. Contact cấp lời mời thuộc quy trình vận hành hạn chế truy cập.
- Một tài khoản có thể đăng nhập nhiều thiết bị. Nút “Đăng xuất” thu hồi phiên hiện tại; “Đăng xuất mọi thiết bị” và đổi/reset mật khẩu thu hồi tất cả phiên của tài khoản.
- Session server, cookie HttpOnly/Secure/SameSite=Lax trên HTTPS cùng origin. Không lưu session credential trong localStorage hay URL. Dev local cần cấu hình riêng và test; không mang ngoại lệ cookie/TLS local vào production.
- Admin UI/role thao tác học viên chưa bật trong R1. SSO, MFA, tổ chức/lớp học và cấp quyền giáo viên nằm ngoài task.

## 2. Cấp và khôi phục tài khoản không cần admin UI

**Operator** là người vận hành deployment qua cơ chế bảo trì được kiểm soát, không là quyền mà learner có thể nhận qua API. D08 thiết kế lệnh bảo trì và quyền vận hành; không cung cấp public endpoint tự cấp role hoặc CLI chạy từ trình duyệt.

| Bước | Hành vi | Điều kiện hoàn tất |
|---|---|---|
| Tạo lời mời | Operator chọn loginName, gắn contact đã thống nhất cho người tham gia, cấp token kích hoạt | Token ngẫu nhiên mạnh, chỉ lưu digest; gắn user/purpose/expiry, không ghi raw token vào log |
| Giao lời mời | Operator chuyển token qua kênh riêng đã thống nhất với đúng người tham gia | Không đưa token vào repo, URL analytics hay danh sách user công khai; chưa cần provider email |
| Kích hoạt | Người nhận nhập token và đặt mật khẩu trên form | Token hợp lệ được consume cùng cập nhật mật khẩu/trạng thái; hai lần dùng đồng thời chỉ một thành công |
| Token lỗi/hết hạn | Form giải thích cần lời mời mới, không tiết lộ contact/account khác | Token cũ không kích hoạt; cấp lại làm token trước mất hiệu lực |
| Quên mật khẩu | UI hướng dẫn liên hệ operator qua kênh gốc của nhóm thử nghiệm | Không khóa tài khoản chỉ vì ai đó yêu cầu reset; không dùng câu hỏi bí mật |
| Cấp reset | Operator đối chiếu người nhận với contact đã xác lập lúc mời; chỉ giao mã qua kênh gốc | Không chấp nhận contact mới do người yêu cầu tự khai. Nếu không xác minh được thì dừng phục hồi, không tự chuyển dữ liệu sang account khác |
| Consume reset | Token một lần cho đúng purpose/user, mật khẩu mới hợp lệ | Đổi mật khẩu, consume token, vô hiệu các reset token và mọi phiên cũ trong cùng thay đổi nhất quán; yêu cầu login lại |

R1 không hứa tự phục hồi qua email. Người vận hành không biết mật khẩu mới. Mã kích hoạt/reset được nhập trong form và gửi body HTTPS, không là mật khẩu tạm dùng để login. Lời mời hết hạn sau 24 giờ, reset token sau 15 phút — cấu hình sản phẩm ban đầu, không phải hạn mặc định của framework. Hết hạn không xóa tài khoản/bài làm.

Trạng thái account: `invited → active`; `active → disabled` khi operator có thao tác rõ và audit; kích hoạt lại không khôi phục các session/token đã thu hồi. Disabled không login, không dùng phiên cũ, nhưng dữ liệu học không bị xóa. Việc xóa account/retention bàn giao BL-S03/D08.

Quy tắc token một lần, hết hạn, lưu an toàn và không tự login sau reset dựa [OWASP Forgot Password](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html). Quy trình operator/kênh gốc là lựa chọn cho nhóm thử nghiệm, không phải cơ chế identity proofing phổ quát.

## 3. Mật khẩu và đăng nhập

Mật khẩu 15–128 Unicode code point, cho phép khoảng trắng và paste/password manager, không trim hoặc âm thầm chuẩn hóa/truncate. Không ép bộ ký tự hoa/thường/số/ký hiệu. Từ chối mật khẩu phổ biến/đã lộ theo bộ kiểm tra được chọn ở D08; không gửi mật khẩu thô ra dịch vụ ngoài. Lưu hash một chiều qua PasswordEncoder phù hợp; thuật toán/tham số và chi phí đo trên môi trường triển khai chốt D08, không mã hóa đảo ngược để lấy lại mật khẩu.

Login thất bại dùng thông báo chung cho tên sai, mật khẩu sai, tài khoản chưa kích hoạt hoặc disabled; không khác response để dò danh sách account. Giới hạn thử cả theo account-key và IP, kể cả loginName không tồn tại. Cấu hình ban đầu: sau 5 lần sai/account-key trong 15 phút chặn thử tiếp 15 phút; IP tối đa 30 lần sai/15 phút. Không khóa vĩnh viễn hay xóa dữ liệu. Độ trễ/giới hạn có thể hiệu chỉnh bằng số đo; username không tồn tại dùng cùng quy tắc. D08 kiểm RAM/storage giới hạn cho key giả và proxy/IP trusted để tránh bypass hoặc làm đầy bộ đếm.

Tham khảo [OWASP Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html); các ngưỡng cụ thể là quyết định ban đầu của app. Password/token/cookie và nội dung bài làm không được chép vào log xác thực. Audit chỉ cần loại sự kiện, actor/target khi biết, thời điểm, kết quả và correlation phù hợp; retention chốt BL-S03/D08.

Đổi mật khẩu khi đang đăng nhập yêu cầu mật khẩu hiện tại và mật khẩu mới; thành công thu hồi mọi phiên, yêu cầu login lại. Logout-all yêu cầu xác minh lại mật khẩu để hạn chế thao tác nhạy cảm trên phiên bị bỏ quên. Throttling cũng áp dụng activation/reset/password-check, với giới hạn và response không lộ account; D08 đưa cấu hình cụ thể vào kiểm thử.

## 4. Vòng đời phiên và CSRF

Idle timeout ban đầu 30 phút, absolute timeout 12 giờ, không “remember me” ở R1. Các thời hạn là cấu hình sản phẩm, không khẳng định framework tự cung cấp đúng như vậy. Request người dùng hợp lệ có thể gia hạn idle, polling/background không được giữ phiên sống vô hạn; absolute luôn tính từ login. Backend là đồng hồ quyết định, client chỉ hiển thị cảnh báo.

Login thành công tạo/rotate session ID, không tái sử dụng credential anonymous. Bootstrap/me trả user hiện tại và một `sessionContextId` không phải credential, để kiểm request có đúng phiên giao diện đang thao tác. Server vẫn phải kiểm cookie và authorization; contextId không thay xác thực. Request ghi mang contextId, mismatch phải từ chối trước mutation.

Request thay đổi trạng thái, kể cả login/logout và credential flow, cần CSRF protection phù hợp. SPA lấy token qua bootstrap được cấu hình, gửi trong header, lấy token mới sau login/logout. SameSite không thay CSRF. CORS chỉ cho origin được cấu hình, không thay authorization. CSRF sai → báo phiên/token thay đổi, rebootstrap; không tự lặp một POST có tác dụng chưa rõ. Nền kỹ thuật: [Spring Security CSRF cho SPA](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

| Sự kiện | Server | UI |
|---|---|---|
| Mở app | Kiểm session qua bootstrap/me | Chưa biết identity thì chưa render nháp cá nhân từ cache |
| Login đúng | Phiên mới, identity/context mới | Bỏ query/cache phiên trước, tải dữ liệu của user hiện tại |
| Hết idle/absolute hoặc revoked | Từ chối quyền truy cập; không tự tạo anonymous submission | Khóa hành động ghi, hiện cần login; nháp RAM còn cùng owner trong màn đã khóa |
| Logout online | Identity ngừng admission, fence Content/Learning, completed sau đủ ACK; revoke canonical session/context/CSRF | Pending khóa UI; sau completed về anonymous, loại private data/RAM |
| Logout-all/password reset | Thu hồi tất cả phiên account | Các thiết bị cũ bị từ chối ở request sau, không đợi TTL riêng |
| Session store lỗi | Không xác thực/ghi dựa vào cache identity client | Báo tạm không xác minh được, không hiểu nhầm là đã logout hay đã lưu |

Logout là POST bảo vệ CSRF, không GET gây tác dụng. Cần gọi đúng logout pipeline của Spring Security; API tùy biến phải thực hiện đầy đủ invalidation/cleanup, không chỉ xóa cookie. Tham khảo [Spring Security logout](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html).

MS02 làm rõ cleanup: logout/status response không gửi Set-Cookie xóa auth cookie có thể đến sau login B. Cookie cũ đã mất quyền server, được login rotation thay hoặc hết hạn; vẫn cleanup SecurityContext/session/CSRF ở Identity và cache/nháp UI theo lựa chọn người học. Pending revoke chưa được báo complete. authGeneration không ngăn browser áp dụng Set-Cookie trễ; login/preauth một lần và context checks giữ fail closed. Wire/state/race ở [session LLD v3](sessions/protocol.md).

## 5. Nháp, logout mất mạng và đổi tài khoản

R1 lưu nháp chính ở server. Bản chưa autosave chỉ giữ trong RAM, không lưu nội dung riêng tư vào localStorage/IndexedDB ở bản đầu; cache public catalog có thể lưu riêng. Vì vậy reload/đóng tab có thể mất phần chưa lưu, UI phải báo đúng. BL-S03 định nghĩa revision/conflict; offline bền vững có mã hóa là phạm vi sau nếu cần.

Trước logout, nếu có nháp chưa lưu: cho chọn lưu rồi logout, hủy logout, hoặc bỏ thay đổi chưa lưu rồi logout. Đây là lựa chọn của learner trong sản phẩm, không tự xóa server draft. Expiry tự động thì khóa và giữ nháp RAM dưới owner cũ; login lại đúng user mới được phục hồi/gửi. Nếu chọn user khác, phải xử lý bỏ nháp cũ rõ ràng trước khi đổi, không chuyển nháp A sang B.

Logout mất mạng: khóa UI ngay, dừng autosave/requests, báo **“Đã khóa trên máy này; chưa xác nhận đăng xuất server”**. Không tự mở lại dữ liệu vì cookie vẫn còn. Khi có mạng, kiểm context cũ và thu hồi đúng phiên trước khi cho login khác. Nếu backend xác nhận đã anonymous thì hoàn tất; nếu cookie thuộc context mới thì không gửi lại logout cũ để thu hồi phiên mới. Pending-lock marker chỉ chứa trạng thái/context không nhạy cảm, tồn tại qua reload; không chứa mật khẩu hoặc nội dung nháp. Bỏ marker/đổi trình duyệt không chứng minh phiên server đã hết, UI không hứa điều đó.

Mọi response/query và job autosave gắn identity + auth generation/context tại lúc tạo. Khi auth đổi, hủy request nếu có thể và luôn bỏ response trễ khác generation. Request ghi phải khớp owner **và** sessionContextId trên server, kể cả request cũ của chính user sau một login mới. Không tự đổi owner/idempotency key của một request để gửi dưới tài khoản mới.

Một browser profile dùng chung cookie giữa các tab: login/logout ở tab này ảnh hưởng tab khác. Broadcast chỉ giúp khóa UI nhanh; API vẫn kiểm phiên thật. Back/forward cache và khi tab được focus lại phải kiểm identity trước hiển thị dữ liệu cá nhân. Phản hồi private dùng cache policy không lưu qua HTTP/service worker; không cache API cá nhân dưới public catalog key.

Request đã commit trước logout vẫn thuộc user ban đầu và tồn tại trong lịch sử. Request đang xử lý chưa commit khi thu hồi phiên hoàn tất phải bị chặn bằng admission/local gate/fence theo [MS02](sessions/protocol.md); T1/T2 local Learning theo MS01/MS03. Không hứa abort ở browser có thể undo việc server đã commit. Response trễ bị loại ở UI, không xuất hiện dưới user tiếp theo.

## 6. Ma trận quyền

R1 chỉ cho anonymous hoặc learner active. Admin là khái niệm dành sau R1; không cấp role admin mặc định cho người tạo hệ thống và không expose API quản lý học viên. Quyền operator bảo trì không là session learner.

| Resource/action | Anonymous | Learner chủ dữ liệu | Learner khác | Admin UI R1 |
|---|---|---|---|---|
| Metadata roadmap/đề cương public | Đọc | Đọc | Đọc | Không triển khai |
| Activity published, hint/assessment | Yêu cầu login | Theo policy bài/version | Theo policy bài của chính mình | Không triển khai |
| Nháp create/read/update/delete | Không | Có, theo version/revision | Không | Không triển khai |
| Submit và lịch sử/result/evidence | Không | Nộp/xem của mình; không sửa snapshot đã nộp | Không | Không triển khai |
| Progress/readiness/export/import/reset | Không | Của mình, theo contract BL-S03 | Không | Không triển khai |
| Đổi mật khẩu/logout-all | Không | Có xác minh lại | Không | Không triển khai |
| Xem danh sách user/publish/sửa role | Không | Không | Không | Không triển khai |

Repository/query luôn lọc owner từ principal; list/count/pagination/search cũng áp dụng, không chỉ endpoint detail. Path ID của draft/attempt/version/job export phải kiểm quan hệ chủ sở hữu tới gốc. ID khó đoán không thay kiểm quyền. DTO không nhận role/owner/pass ngoài field được phép; nếu có thì từ chối, không im lặng ghi đè. Resource của người khác trả như resource không tồn tại, không lộ tiêu đề hoặc total count.

## 7. Hợp đồng API ở mức hành vi

Tên route dưới là đề xuất giao diện để D08 chuẩn hóa, chưa là endpoint tồn tại. Không dùng các tên này để gọi dịch vụ thật.

| Nhóm thao tác | Kết quả và điều kiện |
|---|---|
| Bootstrap/CSRF/me | Danh tính/context từ server hoặc trạng thái anonymous; không gửi credential cho JavaScript |
| Activate/login | Validate/CSRF/throttle; activate chỉ tạo active account, login riêng; login thành công rotate phiên |
| Logout/logout-all | Kiểm context/CSRF; all cần xác minh lại; xác nhận revoked hoặc trạng thái chưa xác định khi mất response |
| Change-password/reset-password | Verify current password hoặc token đúng purpose; commit một lần và revoke-all; không tự login sau đó |
| Learning endpoints | Cùng hàng rào identity/ownership/context; revision/idempotency và result contract thuộc BL-S03 |

Phân loại lỗi: chưa có phiên hợp lệ → 401; không có quyền thao tác/CSRF → 403; resource cá nhân không thấy hoặc thuộc user khác → 404; revision/context xung đột → 409; input sai → 400; throttle → 429 có hướng thử lại; dependency/server lỗi → 503 hoặc lỗi server phù hợp, không ghi thành sai mật khẩu. Login sai account/mật khẩu dùng cùng mã/thông điệp, không dùng mã disabled riêng để lộ account. Thứ tự lỗi 401/403 của Spring filter cần được cấu hình và kiểm tra D08; frontend không replay mutation chỉ vì refresh CSRF thành công.

## 8. Ma trận nghiệm thu thiết kế

Các case IA dưới là expected cho implementation tương lai; chưa là test đã chạy.

| ID | Ca | Expected |
|---|---|---|
| IA-01 | LoginName khác hoa/thường/space; hai lời mời cùng tên chuẩn hóa | Một identity hợp lệ; unique backend chặn trùng |
| IA-02 | Dùng activation token hai lần/đồng thời | Một lần active; lần sau không đổi credential |
| IA-03 | Token sai purpose/hết hạn/reissued | Từ chối, không kích hoạt/reset nhầm account |
| IA-04 | Login tên sai, password sai, invited/disabled | Không có session learner; phản hồi chung |
| IA-05 | Vượt giới hạn thử | Bị throttle có giới hạn thời gian; không khóa vĩnh viễn |
| IA-06 | Login từ session anonymous cũ | Rotate credential; session cũ không thành session learner hợp lệ |
| IA-07 | B GET/list/search/count/export draft/submission của A | Không rò nội dung/metadata, list/count chỉ của B |
| IA-08 | B update/delete/submit/reset dữ liệu A hoặc giả owner/role/pass | Từ chối trước mutation; dữ liệu A không đổi |
| IA-09 | Learner gọi API admin/publish/user-list | Không có quyền, admin UI/API không được mở trong R1 |
| IA-10 | Session hết hạn giữa lúc viết | Khóa gửi, giữ nháp RAM theo owner; không tạo submission anonymous |
| IA-11 | Logout online rồi dùng lại cookie/session cũ | Backend từ chối protected action; server draft/history còn |
| IA-12 | Logout mất mạng rồi reload | UI vẫn locked/pending, không tuyên bố server đã revoke hoặc tự mở dữ liệu |
| IA-13 | Retry logout A khi cookie đã thuộc context B | Không logout B; context mismatch xử lý rõ |
| IA-14 | A logout/B login, response và autosave A tới trễ | UI bỏ response; backend chặn context/owner sai, không nộp nháp sang B |
| IA-15 | Commit diễn ra trước logout so với sau revocation | Trước: bài A giữ; sau: ghi chưa commit bị từ chối theo biên giao dịch |
| IA-16 | Hai tab dùng cookie chung; Back/focus tab cũ | Kiểm lại identity trước hiển thị/ghi, không phục hồi màn cá nhân cũ |
| IA-17 | Logout-all hoặc đổi/reset mật khẩu | Mọi session cũ bị revoke; login lại cần mật khẩu mới khi đã đổi |
| IA-18 | Yêu cầu reset giả nhưng chưa có token | Không khóa account; operator không giao qua contact mới tự khai |
| IA-19 | Reset đồng thời hoặc response reset bị mất | Token chỉ consume một lần; không ghi mật khẩu hai lần, login riêng sau khi xác nhận |
| IA-20 | CSRF sai/thiếu hoặc token cũ sau login/logout | Mutation bị chặn; lấy token mới, không tự replay yêu cầu có tác dụng |
| IA-21 | Session DB lỗi | Không cho ghi dựa cookie/client cache; báo unavailable, dữ liệu tốt giữ |
| IA-22 | Mật khẩu Unicode/khoảng trắng/paste và quá giới hạn | Không truncate/trim secret; validate nhất quán client/server; không log raw |
| IA-23 | Hủy logout khi còn nháp, hoặc chủ động bỏ nháp rồi logout | Hủy giữ phiên/nháp; bỏ chỉ loại RAM đã nêu, không xóa server draft |
| IA-24 | Account disabled trong khi đang có phiên | Protected request bị chặn, không xóa bài làm; re-enable không hồi sinh session cũ |

## 9. Bàn giao và review

BL-S03 tiếp nhận owner/context, draft RAM/server, biên commit khi revoke, import và idempotency; phải làm rõ transaction và consistency. D07 tiếp nhận activate/login/recovery/expired/locked/conflict và nháp chưa lưu. D08 chốt Spring version/PasswordEncoder/session store/CSRF/migration, maintenance command, rate limiter, cấu hình HTTPS và API schemas; kiểm nguồn chính thức theo version chọn. Không cần provider email cho pilot thủ công; mở signup public cần task riêng.

Review 28/09/2026: đối chiếu REQ-14/15 và IA-01–24, giới hạn R1/admin, ownership cả query lẫn mutation, identity change và race logout. Đã đọc nguồn OWASP Authentication/Forgot Password và Spring CSRF/logout ở các phần dẫn chiếu. Các thời hạn/quy trình invite là quyết định sản phẩm, chưa được kiểm chứng với learner hoặc runtime. Kiểm tra ID/link/trạng thái ghi trong backlog; không chạy app/backend test vì chỉ đặc tả tài liệu.
