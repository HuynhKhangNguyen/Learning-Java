# Journal storage contract v1

Contract này dùng chung làm oracle cho adapter service-local của Identity và Learning; đây không
phải business API. Canonical payload được lưu nguyên byte, tên record/namespace chỉ nhận
`[a-z0-9][a-z0-9._-]{0,127}` để không tạo alias do filesystem không phân biệt hoa/thường.

Mỗi index node là UTF-8 với sáu dòng: namespace, sequence thập phân, previous node SHA-256,
record ID, record SHA-256 và dòng rỗng kết thúc. Mỗi immutable head file gồm sequence, node
SHA-256, previous head SHA-256 và newline kết thúc; tên slot là sequence 19 chữ số. Writer dùng
create-if-absent của slot kế tiếp làm local filesystem CAS. CAS loser đọc slot thắng, verify head
mới rồi retry cùng record. Sequence đầu là 1, predecessor đầu là 64 ký tự `0`. Digest inventory
bind namespace, fixed head và từng entry theo sequence.

Create cùng ID/cùng bytes là idempotent; cùng ID/khác bytes là `CONFLICT`. Missing/malformed,
wrong predecessor/namespace/sequence, head gap hoặc digest mismatch là `CORRUPT`, không trả
partial inventory hay durable receipt. Record/node ngoài fixed verified prefix được báo riêng là
orphan và không phải ACK.

Filesystem implementation chỉ được claim local-host lab. Off-host/provider qualification thuộc
BL-R28b.
