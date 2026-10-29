# Chỉ mục nhánh mở rộng Java Backend và DevOps

Điều chỉnh 28/09/2026: [DevOps là roadmap riêng](devops-learning-path.md), đã có [catalog BL-S04](devops-catalog.md). Bảng này là chỉ mục mở rộng từ góc nhìn Backend, không phải prerequisite toàn bộ DevOps. Nhánh nào đã có TOP/CAP ở S04 dùng định nghĩa đó; các cụm còn lại vẫn chưa phân rã. Giữ nguồn progress chung theo user.

BL-D03 · bản thiết kế 1 · 23/09/2026. Tài liệu này nối [cây K01–K23](backend-knowledge-outline.md), [đường học S00–S10](java-backend-learning-path.md), [catalog S00–S03](core-foundations.md), [catalog S04–S07](core-api-data.md) và [catalog S08–S10](core-security-operations.md). Đây là **chỉ mục để chọn nhánh**, chưa là bài học, ngân hàng kiểm tra hoặc nội dung đã xuất bản.

## 1. Cách đọc và quy tắc mở nhánh

- **C** là phần cốt lõi của S00–S10, nằm trong các catalog lõi; bảng bên dưới chỉ nhắc điểm nối. **M** là mở rộng có thể áp dụng khi gặp nhu cầu. **S** là chuyên sâu, cần bài toán và bằng chứng kỹ thuật tương ứng. M/S không có nghĩa thấp/cao về giá trị nghề nghiệp.
- `TOP-X-NNN` yêu cầu hai năng lực A/B như quy ước catalog; `.A` hoặc `.B` chỉ yêu cầu năng lực đó. Điều kiện trong bảng là nền tối thiểu **để được gợi ý học**, không khóa quyền xem. Không ngầm lấy tất cả topic trước đó, toàn bộ nhóm K, hoặc trạng thái hoàn thành project làm prerequisite.
- “Sau P3/P4/…” là **thời điểm gợi ý** khi năng lực liên quan đã xuất hiện, không phải điều kiện phải hoàn thành milestone. TOP/CAP của S08–S10 dùng trực tiếp từ catalog lõi, không tạo ID giả hoặc bản sao tiến độ.
- Mỗi dòng là một **cụm chủ đề** để phân rã gần lúc biên soạn; tên sản phẩm chỉ là ví dụ để chọn một lab phù hợp. Không tạo topic cho từng sản phẩm, và không yêu cầu người học cài toàn bộ công cụ trong nhóm.
- Một nội dung dùng trong cả Java Backend và DevOps có **một ID, một version, một lịch sử evidence/progress**. Hai lộ trình chỉ có thể khác đường vào, lời dẫn hoặc project áp dụng. Docker ở K18, delivery ở K19, quan sát ở K20 không được sao chép sang K22 để tính hai lần.

## 2. Thứ tự gợi ý để người mới không bị quá tải

| Khi đã có nền | Nhánh có thể chọn, theo thứ tự bên trong nhánh | Điều kiện thật cần kiểm tra |
|---|---|---|
| Sau Collections/S02 | DSA gắn dữ liệu, Java API thường gặp, Stream hiện có ở `TOP-FILE-007` | `TOP-COL-006`; Stream dùng prerequisite đã ghi trong catalog S03 |
| Sau HTTP/S04 hoặc SQL/S05 | HTTP cache/API format; SQL nâng cao hoặc so sánh họ database | `TOP-HTTP-002` cho HTTP; `TOP-SQL-004`/`TOP-SQL-005` cho dữ liệu, không bắt hai nhánh học cùng lúc |
| Sau Spring API/S06 | Contract test, realtime/API khác, scheduler đơn giản | `TOP-SPR-006` và topic API liên quan; chưa cần database cho scheduler không lưu trạng thái |
| Sau persistence/S07 | Đo truy vấn → cache; mô hình messaging → broker lab; batch có checkpoint; search; DDD/nghiệp vụ phức tạp; JVM/concurrency theo lỗi đo được | `TOP-DATA-007` hoặc tập TOP nhỏ hơn được chỉ rõ ở mục 3–4; các nhánh này **song song**, không đòi học cache trước queue |
| Sau năng lực bảo mật S08 | Upload/integration có dữ liệu người dùng, OAuth/OIDC, tenant hoặc AI dùng dữ liệu riêng | `TOP-SEC-001,TOP-SEC-004,TOP-SEC-006` theo phạm vi input/ownership/secret; nhánh identity sâu cần `TOP-SEC-008` |
| Sau năng lực container S09 | Chạy broker/cache/Search trong container, tối ưu image/network/volume | `TOP-CTR-003,TOP-CTR-004` để vận hành và chẩn đoán; thêm `TOP-CTR-005` nếu broker lưu dữ liệu; khái niệm messaging/cache học được trước bước lab |
| Sau năng lực triển khai S10 | Cloud/IaC, Kubernetes, hệ phân tán, SRE/reliability, kiến trúc nhiều dịch vụ | `TOP-OPS-003,TOP-OPS-005` cho deploy và quan sát; nhánh có recovery thêm `TOP-OPS-007`; mỗi nhánh thêm điều kiện riêng, không mặc định học cloud trước system design |

Trong mỗi nhánh: hiểu bài toán → làm baseline không công nghệ mới → đo hoặc tái hiện giới hạn → chọn cơ chế → kiểm thử lỗi/rollback. Bài áp dụng có thể là milestone tự chọn của Task API hoặc mini-project; không kéo K12–K15 vào P1–P7 bắt buộc. Readiness của nhánh là năng lực đầu vào; đã áp dụng cần bằng chứng riêng theo [REQ-06/08/12](../product-requirements.md).

## 3. Bản đồ mở rộng đủ K01–K23

Các điểm nối lõi là **topic đã có** trong ba catalog D01–D03. Tên nhánh M/S dưới đây chưa mang TOP/CAP riêng cho tới khi được biên soạn.

| Nhóm | Nhánh thêm và mức | Điểm vào tối thiểu / thứ tự trong nhánh |
|---|---|---|
| K01 · OS/môi trường | M: shell script, Linux diagnostics/SSH; S: chẩn đoán tài nguyên và network sâu | `TOP-ENV-002` → script lặp thao tác file/process an toàn; S sau `TOP-OPS-005` khi đã theo log/process của dịch vụ và có lỗi cụ thể |
| K02 · Network | M: proxy/reverse proxy, load balancer, CDN, HTTP version; S: routing/NAT/TLS chi tiết | `TOP-HTTP-001,TOP-HTTP-002` → phân biệt hop và timeout; S sau `TOP-OPS-002,TOP-OPS-003` khi có deployment/network để chẩn đoán |
| K03 · Workflow/tooling | M: branch/rebase/code review, build/plugin/static analysis; S: quản trị build nhiều module | `TOP-ENV-003` cho Git, `TOP-ENV-004` cho build; nhiều module sau khi có nhu cầu chia codebase, không là cổng vào Spring |
| K04 · Java | M: `TOP-FILE-007` Stream/Optional; BigDecimal/locale/regex; S: reflection/annotation/module và API nâng cao | Stream đã có ID dùng chung; BigDecimal sau `TOP-JAVA-001,TOP-FILE-006`; reflection sau `TOP-JAVA-006,TOP-SPR-001` để đối chiếu DI thực tế |
| K05 · DSA | M: stack/queue/heap/tree/graph, top K/LRU; S: dynamic programming và phân tích sâu | `TOP-COL-006` → chọn cấu trúc cho bài toán → đo trade-off; graph/DP chỉ sau duyệt/tìm căn bản, không chặn P1 |
| K06 · JVM/concurrency | M: executor/Future/virtual thread, profiling; S: Java Memory Model, lock-free/reactive/backpressure, benchmark chuẩn | `TOP-SPR-005` cho thao tác đồng thời; `TOP-DATA-006` khi xét xung đột dữ liệu; profiling sau API có log/test và số đo |
| K07 · Thiết kế/nghiệp vụ | M: refactor/hexagonal, order/inventory, DDD cơ bản; S: bounded context, audit/multi-tenant domain | `TOP-SPR-003,TOP-DATA-005` → invariant & transaction → đổi cấu trúc khi có lý do; tenant thêm `TOP-SEC-004` cho ownership |
| K08 · API | M: gRPC/GraphQL, SSE/WebSocket/webhook, API version/contract; S: gateway/BFF ở nhiều client/service | `TOP-HTTP-005,TOP-SPR-004` → so với REST baseline; webhook thêm `TOP-SEC-001,TOP-SEC-006` cho trust boundary/secret và thử chữ ký/replay; gateway sau bài toán K21 nhiều dịch vụ |
| K09 · Spring | M: AOP/filter sâu, scheduling/Batch, Spring Modulith; S: WebFlux/reactive, Spring Cloud hoặc khảo sát Jakarta EE/Quarkus/Micronaut | AOP sau `TOP-SPR-001,TOP-SPR-002`; scheduling/Batch theo K14; reactive sau HTTP/API và K06 bất đồng bộ; framework khác chỉ học khi cần so sánh |
| K10 · Database | M: SQL CTE/window, một họ dữ liệu khác, full-text/time-series; S: replication/sharding/distributed SQL và tuning sâu | `TOP-SQL-004` → SQL nâng cao; `TOP-SQL-001.A,TOP-SQL-005` → so mô hình/consistency; vận hành phân tán sau `TOP-OPS-005,TOP-OPS-007`; xem mục 4 |
| K11 · Java data access | M: SQL mapper/jOOQ, batching/fetch/locking sâu, Testcontainers; S: tối ưu pool/transaction propagation theo tải | `TOP-DATA-001` → mapper; `TOP-DATA-004,TOP-DATA-005` → đo query/transaction; Testcontainers thêm `TOP-CTR-002,TOP-CTR-003` và giữ năng lực integration test `TOP-DATA-007` dùng chung |
| K12 · Cache | M: HTTP cache hoặc cache truy vấn; S: invalidation, stampede, hot key/consistency nhiều node | HTTP từ `TOP-HTTP-002`; dữ liệu từ `TOP-SQL-006,TOP-DATA-004` và truy vấn lặp đã đo; xem mục 5 |
| K13 · Messaging | M: queue/pub-sub + retry/idempotency; S: outbox/inbox, stream/CDC/schema evolution | `TOP-HTTP-002.B,TOP-DATA-005` → xử lý lặp → broker lab sau `TOP-CTR-003,TOP-CTR-004`; xem mục 5 |
| K14 · Job/batch/workflow | M: scheduler, batch/checkpoint; S: workflow dài hạn/compensation và nhiều worker | Scheduler sau `TOP-SPR-006`; batch bền vững sau `TOP-DATA-007`; workflow sau khi đã tái hiện retry/idempotent consumer của K13 và rollback `TOP-DATA-005`; xem mục 5 |
| K15 · File/search/tích hợp | M: upload/object storage, search, API ngoài/email; S: pipeline tìm kiếm hoặc tích hợp có reconciliation | Search sau `TOP-SQL-006,TOP-DATA-007`; upload sau `TOP-SPR-004,TOP-SEC-001,TOP-SEC-004`; API ngoài/email thêm `TOP-SEC-006`; xem mục 5 |
| K16 · Security | M: OAuth/OIDC, MFA, API key, security header; S: SAML/SSO doanh nghiệp, tenant/service identity, threat model sâu | `TOP-SEC-008` cho identity sâu sau test âm đăng nhập/ownership; tenant thêm `TOP-SQL-002,TOP-SEC-004` để cách ly ở DB, không chỉ UI |
| K17 · Testing | M: contract/property/mutation test; S: load/soak, security/resilience/chaos test | Contract sau `TOP-SPR-006`; property/mutation sau `TOP-JAVA-009,TOP-COL-007`; load sau `TOP-OPS-005,TOP-OPS-008`, resilience thêm `TOP-OPS-006` |
| K18 · Docker | M: network/image optimization, registry/digest, resource diagnosis; S: isolation/runtime/security sâu | `TOP-CTR-006,TOP-CTR-007` cho tối ưu vận hành; isolation/security thêm `TOP-CTR-008`; bổ sung chiều sâu vào **cùng** K18 và cùng progress hai lộ trình |
| K19 · Delivery | M: blue-green/canary/feature flag, multi-environment; S: supply-chain/release strategy quy mô lớn | `TOP-OPS-001,TOP-OPS-003,TOP-OPS-004` cho release/rollback và DB compatibility; traffic shift chỉ sau khi smoke/observability hoạt động |
| K20 · Observability | M: trace xuyên dịch vụ, SLI/SLO, incident drill; S: capacity/reliability nhiều vùng | `TOP-OPS-005,TOP-OPS-006`; trace phân tán khi có ≥2 dịch vụ/hop thực, không bắt dựng microservice chỉ để học công cụ |
| K21 · System design | M: capacity, caching/queue/scale, monolith vs service, resilience; S: consensus, saga/CQRS/event sourcing, phân tán sâu | `TOP-OPS-008,TOP-DATA-006` cho tải/xung đột; thêm K12/K13 **chỉ với bài toán dùng chúng**; không buộc học microservices |
| K22 · Cloud/DevOps sâu | M: một cloud, IAM/network/storage, IaC/managed container; S: Kubernetes/GitOps/mesh/multi-region/FinOps | Cloud/IaC từ `TOP-OPS-002,TOP-OPS-003,TOP-CTR-004`; Kubernetes thêm `TOP-CTR-007`; mesh sau nhiều service, không là tiền đề Java Backend |
| K23 · Chuyên biệt | M: AI API/retrieval, data integration, geospatial theo mục tiêu; S: RAG đánh giá/riêng tư, CDC/streaming analytics, ngành yêu cầu cao | AI từ `TOP-HTTP-003,TOP-DATA-007,TOP-SEC-006` và `TOP-SEC-004` nếu dùng dữ liệu riêng; retrieval sau K15 search; CDC sau K13; geospatial sau `TOP-SQL-004`, xem mục 6 |

## 4. Nhánh dữ liệu: chọn theo câu hỏi, không học hết sản phẩm

Lõi K10 là **một RDBMS** để chứng minh schema, SQL, transaction, index và restore (`TOP-SQL-001`–`007`). Sau `TOP-SQL-001.A` chỉ được *nhận diện* các mô hình; lab so sánh một sản phẩm khác cần `TOP-SQL-005` để hỏi rõ consistency/transaction. Bảng này là nhãn phân loại chồng lấn, không là chuỗi prerequisite:

| Câu hỏi cần giải | Họ/khả năng để khảo sát · mức | Điểm vào thêm |
|---|---|---|
| Dữ liệu có quan hệ/constraint và giao dịch? | Relational: PostgreSQL/MySQL/SQLite… · C với **một** hệ; so dialect/sản phẩm thứ hai · M | Lõi S05/S07; không chép lại toàn bộ bài SQL cho mỗi hãng |
| Document hoặc lookup theo key? | Document: MongoDB/Couchbase/CouchDB; key-value: Redis/Valkey; DynamoDB có thể thuộc cả key-value và document · M | `TOP-SQL-001.A,TOP-SQL-005`; Redis làm cache đi theo K12, không tự coi là DB nguồn chân lý |
| Đường đi quan hệ phức tạp, partition rất lớn? | Graph: Neo4j/Neptune; wide-column: Cassandra/HBase; multi-model: ArangoDB · M/S tùy workload | Có truy vấn và giới hạn của mô hình quan hệ được diễn đạt/đo; wide-column/distributed thêm K21 consistency/partition |
| Phân tích, chuỗi thời gian, tìm kiếm, vector hay địa lý? | OLAP: ClickHouse/DuckDB/warehouse; time-series: InfluxDB/TimescaleDB; search: Elasticsearch/OpenSearch; vector: pgvector/Qdrant/Milvus; geospatial: PostGIS · M/S theo mục tiêu | `TOP-SQL-004,TOP-SQL-006`; full-text/vector đi cùng K15/K23, không mặc định thay OLTP |
| Khác biệt do triển khai/quy mô? | Embedded/client-server; self-hosted/managed; single/distributed SQL như CockroachDB/TiDB; row/columnar · S khi cần vận hành | `TOP-SQL-007,TOP-OPS-005,TOP-OPS-007`; phân biệt wide-column với columnar analytics |

SQL mở rộng: `TOP-SQL-004` → CTE/window/view/CASE/COALESCE/set operations → đo bằng `TOP-SQL-006` → recursive query/procedure/trigger/pivot/dynamic SQL khi bài toán đòi hỏi. Quyền GRANT/REVOKE nối `TOP-SQL-007`; MVCC/lock/isolation sâu nối `TOP-SQL-005`. Tên và cú pháp có thể khác dialect; bài thực hành chọn rõ một hệ và version. Database managed, BaaS/realtime là kiểu dịch vụ/khả năng, không là mô hình dữ liệu độc lập.

## 5. Nhánh tính năng bổ sung cho Task API

| Nhánh | Học theo nhịp | Bài toán để mở lab; ranh giới cần giữ |
|---|---|---|
| K12 Cache | HTTP cache sau `TOP-HTTP-002` → cache-aside sau `TOP-DATA-004,TOP-SQL-006` → TTL/invalidation → stampede/consistency (S) | Có số đo đọc lặp/độ trễ và yêu cầu freshness; test kết quả stale, cập nhật và khi cache trống. Chọn cache local hoặc Redis theo phạm vi; không dùng cache để che query sai |
| K13 Messaging | Sync vs async và idempotency sau `TOP-DATA-005` → queue/pub-sub, ack/retry/DLQ → chạy một broker sau `TOP-CTR-003,TOP-CTR-004` (thêm `TOP-CTR-005` nếu lưu dữ liệu) → outbox/inbox/stream (S) | Có việc chậm/cần tách xử lý; thử giao lặp, mất broker, retry và thứ tự; không hứa exactly-once toàn hệ thống chỉ từ ack của broker |
| K14 Job | Scheduler không lưu trạng thái sau `TOP-SPR-006` → batch đọc/ghi và checkpoint sau `TOP-DATA-007` → workflow/compensation (S) sau khi kiểm tra được retry/idempotent consumer K13 và rollback `TOP-DATA-005` | Task định kỳ hoặc khối lượng dữ liệu lớn; thử restart/retry/cancel, bảo vệ tác dụng phụ lặp; không cần broker cho cron đơn giản |
| K15 File/search/tích hợp | Upload/download sau `TOP-SPR-004,TOP-SEC-001,TOP-SEC-004`; full-text sau `TOP-SQL-006,TOP-DATA-007`; API client/email sau `TOP-HTTP-003,TOP-SEC-006`; reconciliation (S) sau integration thật | Có file/dữ liệu người dùng hoặc dịch vụ ngoài; kiểm tra kích thước/type/path/quyền, timeout/retry và dữ liệu chưa đồng bộ. Object storage/search engine là lựa chọn theo yêu cầu, không thành project bắt buộc |

K09 Spring Batch/Integration, K11 Testcontainers, K17 contract/load test là **công cụ hoặc kỹ thuật dùng trong các nhánh trên**, không tạo thành bản sao của năng lực batch, DB integration hay kiểm thử. Nhánh tùy chọn muốn ghi “đã áp dụng” vẫn cần rubric và bằng chứng phù hợp; demo mô phỏng được gắn nhãn mô phỏng theo REQ-12.

## 6. DevOps và chuyên biệt: một nền, nhiều hướng đi

Người từ Backend sang DevOps dùng lại **cùng K18–K20**: `TOP-CTR-001`–`008` và `TOP-OPS-001`–`008`. Người bắt đầu DevOps độc lập đi từ OS/network/Git và app mẫu theo catalog S04, không phải qua Java/P7. K22 thêm cloud/IAM → IaC hoặc orchestration theo nhu cầu; GitOps/mesh/multi-region nằm trong gap mở rộng. Người chỉ vận hành Task API cơ bản có thể dừng ở P7. Chuyển đường học không reset hoặc nhân đôi TOP/CAP.

K23 chọn theo bài toán: gọi model API có giới hạn secret/chi phí/dữ liệu sau `TOP-HTTP-003,TOP-DATA-007,TOP-SEC-006` (thêm `TOP-SEC-004` khi dùng dữ liệu riêng); embedding/retrieval sau K15 search; RAG và đánh giá câu trả lời sau khi có truy xuất thật; ETL/ELT sau SQL/persistence; CDC/streaming sau K13; geospatial sau SQL và bài toán vị trí. Các trường hợp multi-region compliance hoặc độ trễ rất thấp cần mục tiêu, ràng buộc và môi trường thử riêng trước khi mở thành topic. Không biến chuyên ngành thành prerequisite của lõi.

## 7. Kiểm tra ranh giới thiết kế

- Mỗi K01–K23 có điểm nối lõi hoặc entry mở rộng ở mục 3; K12–K15 và K22–K23 có lộ trình nhánh ở mục 5–6.
- P0–P7 giữ theo catalog lõi. Không có cạnh “phải hoàn thành Pn” vào mọi nhánh; người đã chứng minh năng lực tương đương vẫn vào được nhánh.
- HTTP và SQL vẫn độc lập; Spring API không chờ SQL; broker lab cần kỹ năng container nhưng khái niệm messaging không chờ Docker; Testcontainers không trở thành prerequisite của integration test P4.
- Các cụm M/S chưa phân rã vẫn chưa có TOP/CAP/version; phần DevOps đã được S04 khai báo dùng catalog đó, không tạo lại ID. Chưa có assessment/lesson/lab hoặc nguồn phiên bản cho từng ví dụ. Khi chọn nhánh để biên soạn, tách topic theo năng lực quan sát được, giữ reference dùng chung rồi review kỹ thuật theo [quy trình chất lượng](../quality-process.md).
