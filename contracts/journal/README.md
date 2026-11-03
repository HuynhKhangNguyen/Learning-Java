# Journal storage contract v1

Contract này dùng chung làm oracle cho adapter service-local của Identity và Learning; đây không
phải business API. Canonical payload được lưu nguyên byte, tên record/namespace chỉ nhận
`[A-Za-z0-9][A-Za-z0-9._-]{0,127}`.

Mỗi index node là UTF-8 với sáu dòng: namespace, sequence thập phân, previous node SHA-256,
record ID, record SHA-256 và dòng rỗng kết thúc. Mỗi immutable head file gồm sequence, node
SHA-256 và newline kết thúc; tên file bind node digest. Sequence đầu là 1, predecessor đầu là 64 ký tự `0`. Digest inventory bind namespace, fixed
head và từng entry theo sequence.

Create cùng ID/cùng bytes là idempotent; cùng ID/khác bytes là `CONFLICT`. Missing/malformed,
wrong predecessor/namespace/sequence hoặc digest mismatch là `CORRUPT`, không trả partial
inventory hay durable receipt. Record/node ngoài fixed verified prefix là orphan và không phải ACK.

Filesystem implementation chỉ được claim local-host lab. Off-host/provider qualification thuộc
BL-R28b.
