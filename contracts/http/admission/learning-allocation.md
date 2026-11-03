# Learning allocation admission — BL-R08 extension

Owner: Identity canonical INT-01 provider và Learning receiver. Giữ wire schemaVersion1, cùng strict
codec/budget/mTLS/requestBinding và canonical authority của [admission contract](README.md).
Đây là specification để implement trong R08; chưa là runtime qualification.

| Action / audience | Method và exact route | Quyền |
|---|---|---|
| LEARNING_ATTEMPT_CREATE / learning | POST /api/v1/attempts | Mutation: session/context + CSRF + configured Origin |
| LEARNING_ATTEMPT_READ / learning | GET /api/v1/attempts/{attemptId} | Authenticated read, owner do Learning kiểm |
| LEARNING_ALLOCATION_RECEIPT_READ / learning | GET /api/v1/receipts/allocation/{key} | Optional API-L08 allocation lookup; chỉ enable nếu handler được implement/test |

attemptId/key là canonical lowercase UUID theo common wire, query rỗng. GET body rỗng và bodySha256
là SHA256(empty). POST body exact bytes đã được receiver validate; bind hash exact body. Không
decode/re-encode path để chấp nhận slash/encoding/trailing slash hoặc query alias chưa đăng ký.

Mutation dùng exact MUTATION_FIELDS (READ_FIELDS + csrfToken + origin); read dùng exact READ_FIELDS.
Phân loại mutation theo registered action+method+route, không theo caller=learning. Giữ nguyên
LEARNING_DRAFT_SAVE/CONTENT_BODY_READ và deny tất cả action/caller/route chưa đăng ký. Provider xác
thực session/context/realm/deadlines/revoke/CSRF như hiện hành; không cấp user/owner từ body caller.

Request binding giữ SHA256(method + LF + canonicalPath + LF + emptyQuery + LF + bodySha256).
ALLOW phải bind exact action/audience/context/nonce/requestBinding và csrfVerified tương ứng policy;
read không cấp mutation rights. Learning verifier/gate phải kiểm cùng action và binding, không
dùng draft-save admission cho create hoặc GET. Không cache/reuse ALLOW cho request khác; refresh
admission trước disclosure khi cần theo budget MS02, ngoài domain locks.

Tests bắt buộc trên canonical Identity + packaged Learning: POST có/thiếu/sai CSRF/Origin, GET hợp
lệ không mang mutation fields, cross-action/route/method/audience replay, nonempty GET body/query,
anonymous/wrong context/expired/revoked session, A/B owner lookup. Regression draft-save và Content
body read giữ nguyên semantics. Optional receipt action chưa implemented phải deny, không wildcard.
