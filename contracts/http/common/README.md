# Common wire — R02b foundation

[Catalog conventions](../../../docs/architecture/service-boundaries.md#41-wire-conventions-chung--bổ-sung-d14) giữ yêu cầu; [registry](registry.json) phân lớp bằng chứng/owner còn lại. Các schema/example này không là Java domain JAR hoặc OpenAPI cho API chưa triển khai.

`transport-probe.schema.json` và `probe-example.json` dành riêng profile transport/lab. Probe chỉ kiểm kênh, caller/action, binding và time window; response luôn businessReady=false, không user admission/receipt hoặc quyền domain. SHA256 vector trong example được tính độc lập khi tạo fixture; tamper phải fail. Canonical probe bytes là method, fixed path, action, audience, nonce, decimal revision và hai Instant UTC nối bằng newline. Đây là binding kỹ thuật của probe, chưa canonical fingerprint/binding endpoint nghiệp vụ MS02/S03.

Counter là decimal string0..9223372036854775807; JSON UTF8 strict, duplicate/escaped duplicate keys và unpaired surrogate bị reject; depth20/body64KiB; không unknown/privileged request fields. Timestamp input UTC uppercase T/Z với3digits milliseconds. Errors version1 không echo input/SQL/stack/credentials, no-store và traceUUID.

WIRE01/02/03/06 có executable codec/receiver layer. WIRE04 chỉ canonical UUID key shape, chưa same-intent receipt/idempotency/owner semantics R09/11. WIRE05 chỉ bounded opaque string shape (1..1024 printable ASCII/no whitespace), chưa verify/signing/expiry/owner/epoch/filter/paging R21. Decoder trả opaque value, không quyền. Additive optional response được giữ; unknown major/state fail closed, PENDING không bị đổi thành COMPLETED. Endpoint/FE/edge integration và compatibility registry vẫn task owner sau.

Mỗi service compile own WireCodec, read common examples từ test resources; fixture/schema/example không vào runtime JAR. Test gates không coi tên WIRE04/05 là full-case PASS. Giữ input/status độc lập với source khi thêm vectors; task thêm production API cập nhật enforcement/semantics/provider-consumer assertions.
