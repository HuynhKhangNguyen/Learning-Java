# Backend — Khung kiến thức toàn chương trình

Trạng thái: B02 — khung nội dung v1 đã hoàn thiện ở cấp nhóm/chủ đề để review. Đã bổ sung các khoảng trống từ đối chiếu Backend/Java/SQL/DevOps của roadmap.sh. Chưa biên soạn bài học chi tiết hoặc triển khai code sản phẩm. ID K01–K23 là chuẩn, không dùng số thứ tự khác trong câu trả lời trước làm ID.

## Phạm vi và quy ước

Backend ứng dụng web/dịch vụ: nền tảng máy tính, Java, API, dữ liệu, nghiệp vụ, chất lượng, bảo mật, triển khai, vận hành và thiết kế hệ thống. Từ 26/09/2026, [DevOps có roadmap riêng](devops-learning-path.md) từ nền tảng đến vận hành; Docker là một chặng trong đó. Cây Backend vẫn giữ K18–K22 để phân loại/điểm nối kiến thức chung, không nhân bản TOP/CAP. Frontend chỉ học phần cần để hiểu giao tiếp backend; mobile, ML training, hệ nhúng không thuộc lõi.

Mỗi nhánh tách khái niệm, loại giải pháp và sản phẩm minh họa. Công nghệ liệt kê không đồng nghĩa phải học sâu hoặc cài tất cả. View phân mức: C = cốt lõi để xây/vận hành backend cơ bản; M = mở rộng theo nhu cầu; S = chuyên sâu. Quan hệ “thuộc nhóm” trên cây không phải quan hệ “phải học trước”. Mức độ gắn từng nhánh con, không mặc định tất cả nội dung của một nhóm đều bắt buộc.

## Cây cấp 1 và cấp 2

### K01. Máy tính, hệ điều hành và môi trường
- CPU, RAM, disk, filesystem, encoding/Unicode, binary và đơn vị dữ liệu.
- Process/thread, memory, I/O, environment variable, port, socket, signal.
- Linux: shell, file/permission, process/service, package, SSH, log và lệnh chẩn đoán.
- Cài JDK/IDE, terminal, cấu hình môi trường và quản lý phiên bản công cụ.
- Shell scripting Bash/PowerShell, thao tác text và chẩn đoán CPU/RAM/disk/process/network.

### K02. Internet và networking
- Client/server, IP, subnet, routing, NAT, DNS.
- TCP/UDP, connection, latency, bandwidth, timeout.
- HTTP/HTTPS, TLS/certificate, HTTP/1.1–2–3 ở mức cơ chế và khác biệt.
- Proxy/reverse proxy, load balancer, CDN, firewall.
- Phân biệt web server/application server/reverse proxy; Nginx, Apache HTTP Server, Caddy; Tomcat/Jetty nằm ở K09.
- Browser: cookie, storage, same-origin, CORS, form, DevTools; HTML/CSS/JS đủ để hiểu client.

### K03. Quy trình phát triển
- Git: commit/branch/merge/rebase/conflict, pull request và code review.
- Đọc yêu cầu, chia task, đọc codebase, debug, tài liệu và giao tiếp kỹ thuật.
- Dependency/package, semantic versioning, config, reproducible build.
- Maven/Gradle; formatter, lint, static analysis và quản lý dependency.

### K04. Java nền tảng
- Type/variable/operator, control flow, method/scope, array, String.
- Type casting, access modifier, static/final, package, nested class, initialization, object lifecycle, overload/override và binding.
- Primitive/reference, pass-by-value, null, equality và immutability.
- Class/object, encapsulation, inheritance, polymorphism, interface/composition.
- Generics, Collections, equals/hashCode, Comparable/Comparator.
- Iterator, mutable/immutable collection, thứ tự phần tử và lựa chọn implementation.
- Exception, resource handling, file/I/O/NIO, serialization.
- Lambda/Stream/Optional, enum/record, annotation/reflection, module.
- Date/time/time zone, BigDecimal và rounding, regex, locale/encoding.
- Logging API/facade/implementation: SLF4J, Logback, Log4j2; Java HTTP client/socket ở mức ứng dụng.

### K05. Cấu trúc dữ liệu và thuật toán
- Big O, time/memory trade-off, recursion.
- Array/list, hash table, stack/queue/deque, heap, tree/trie, graph.
- Search/sort, traversal, hashing, two pointers, sliding window, basic dynamic programming.
- Bài toán gắn Backend: frequency/grouping, deduplication, pagination, top K, LRU, dependency graph.

### K06. Java runtime, concurrency và hiệu năng
- JVM, bytecode, class loading, JIT, stack/heap, garbage collection.
- Thread, executor/pool, Future/CompletableFuture, virtual thread.
- Race condition, synchronization/lock, atomic, visibility, Java memory model, deadlock.
- Concurrent collections, blocking/non-blocking, async và reactive/backpressure.
- Profiling, thread/heap dump, memory leak, JFR/JMC, benchmark JMH.

### K07. Thiết kế phần mềm và nghiệp vụ
- Cohesion/coupling, SOLID, composition, design patterns và refactoring.
- Layered, hexagonal/clean architecture, dependency boundaries.
- Domain model, invariants, state machine, DDD, aggregate và bounded context.
- Tiền tệ, thời gian, tồn kho/đặt hàng, quyền sở hữu, đa tenant và audit.
- Design document, ADR, yêu cầu chức năng/phi chức năng và trade-off.

### K08. API và kiểu giao tiếp
- HTTP semantics, resource, method/status/header, cache semantics.
- REST; RPC/gRPC; GraphQL; SOAP/XML để đọc hệ thống tích hợp cũ.
- JSON/XML/Protocol Buffers, schema và serialization.
- Validation, error contract, pagination/filter/sort, versioning/backward compatibility.
- Authentication boundary, rate limit/quota, idempotency, concurrency control.
- WebSocket, SSE, polling/long polling; webhook/callback.
- OpenAPI, API docs, consumer contract và công cụ thử API.
- API key, content negotiation; HATEOAS/JSON:API ở mức nhận biết/tùy chọn.

### K09. Java web và hệ sinh thái Spring
- Servlet, web server/application server, request lifecycle; Tomcat/Jetty.
- Spring Core: IoC/DI, bean lifecycle/scope, configuration và AOP.
- Spring Boot: auto-configuration, starter, profile, external config, Actuator.
- Spring MVC, validation, exception handling, filter/interceptor và client HTTP.
- Spring Data, transaction boundary, Spring Security, Session.
- Spring Batch, scheduling, Integration/messaging.
- WebFlux/reactive là nhánh lựa chọn; Spring Cloud/Modulith theo nhu cầu kiến trúc.
- Nhận biết lựa chọn khác: Jakarta EE, Quarkus, Micronaut; không học sâu mọi framework.

### K10. Database và quản lý dữ liệu
- Mô hình dữ liệu và các họ sản phẩm: xem bảng phân loại bên dưới.
- Thiết kế schema, key/constraint/relationship, normalization/denormalization.
- SQL: CRUD, join, grouping, subquery, CTE, window function, view; procedure/trigger khi phù hợp.
- DDL/DML, kiểu dữ liệu, NULL/three-valued logic, CASE/COALESCE, string/date/numeric function, set operations; phân biệt JOIN và UNION.
- Savepoint, GRANT/REVOKE; recursive query, dynamic SQL và pivot/unpivot là phần mở rộng/chuyên sâu, lưu ý khác biệt dialect.
- Transaction, ACID, isolation, MVCC, lock/deadlock và consistency.
- Index, execution plan, query tuning, partitioning, replication và sharding.
- Migration, backup/restore, retention, data quality, access control và audit.
- Thực hành một RDBMS chính; so sánh dialect và đặc tính của RDBMS khác.

### K11. Truy cập dữ liệu từ Java
- Driver/JDBC, connection pool, prepared statement và SQL injection boundary.
- JDBC template, SQL mapper/query builder: MyBatis/jOOQ.
- ORM: JPA là đặc tả, Hibernate là implementation; Spring Data là abstraction repository.
- Entity lifecycle/mapping, fetch/lazy loading, N+1, cascade, pagination và batching.
- Transaction propagation/rollback, optimistic/pessimistic locking.
- Flyway/Liquibase, integration test với database và Testcontainers.

### K12. Cache
- Browser/HTTP/CDN, application-local và distributed cache.
- Cache-aside, read/write-through, write-behind; TTL, eviction/invalidation.
- Cache stampede, penetration, hot key, consistency và stale data.
- Công cụ tiêu biểu: Caffeine, Redis, Memcached.

### K13. Messaging và xử lý bất đồng bộ
- Task queue, pub/sub, event stream/log; sync và async.
- Producer/consumer, broker, topic/partition, consumer group.
- Ack, retry/backoff, dead-letter queue, ordering, delivery semantics, deduplication.
- Idempotent consumer, outbox/inbox, CDC, schema evolution.
- RabbitMQ, Kafka, Pulsar; managed queue/pub-sub như SQS/SNS.

### K14. Background job, batch và workflow
- Scheduler/cron, worker, job queue, async task.
- Batch/chunk, checkpoint, retry, resume, cancel, distributed scheduling.
- Workflow dài hạn và compensation.
- Spring Batch, Quartz, Temporal là các ví dụ thuộc những nhu cầu khác nhau.

### K15. File, tìm kiếm và tích hợp ngoài
- File/block/object storage; upload/download, multipart, streaming, presigned URL.
- Object storage: S3, Azure Blob, Google Cloud Storage; CDN phân phối nội dung.
- Search: full-text, inverted index, analyzer, relevance; Elasticsearch/OpenSearch.
- Email/SMS/push, thanh toán, danh tính và API bên thứ ba.
- Email delivery: SMTP, SPF/DKIM/DMARC; SFTP và API integration theo nhu cầu.
- Timeout/retry, webhook signature/replay protection, reconciliation và kiểm thử sandbox.

### K16. Bảo mật Backend
- Threat modeling, trust boundary, least privilege và secure defaults.
- Authentication/authorization: session, token/JWT, OAuth 2.0, OIDC, SSO, MFA.
- Basic Auth/API key; SAML cho tích hợp doanh nghiệp. Phân biệt hash thường, password hash, encryption và signature; CSP/security headers.
- RBAC/ABAC, resource ownership, tenant isolation, service identity.
- Password hashing, encryption/signature, TLS, secrets/key rotation.
- Injection, XSS ảnh hưởng backend/browser, CSRF, SSRF, broken access control, unsafe deserialization/file upload.
- Resource exhaustion, abuse/rate limit, dependency vulnerabilities, security tests/audit log và bảo vệ dữ liệu nhạy cảm.

### K17. Kiểm thử và chất lượng
- Unit, integration, API, contract, end-to-end; mock/stub/fake và test data.
- TDD/BDD, test pyramid và kiểm thử nghiệp vụ; testing bắt đầu cùng Java, không chờ đến cuối lộ trình.
- JUnit, Mockito, Testcontainers, REST Assured, WireMock.
- Property-based, mutation, regression, deterministic test và test isolation.
- Load/stress/soak, security testing, resilience/chaos testing.
- Coverage có ý nghĩa, static analysis, review và chất lượng tài liệu.

### K18. Docker và container
- Image/container, registry/tag/digest, build context, Dockerfile/layer/cache.
- Container so với virtual machine; nền tảng isolation và giới hạn tài nguyên.
- Multi-stage build, runtime config, non-root và image security.
- Lifecycle, logs, exec, signal, graceful shutdown và resource limits.
- Network/port/service name, bind mount/volume và persistence.
- Compose, app + database, health check, startup/readiness và troubleshooting.

### K19. Triển khai và delivery
- Artifact/JAR/container, environment/config/secret, reverse proxy/domain/TLS.
- Twelve-Factor App, artifact repository và secret management là các năng lực riêng.
- CI/CD: test/build/scan/package/deploy, artifact registry, release/version.
- Rolling, blue-green, canary, rollback và feature flag.
- Migration tương thích khi deploy, zero-downtime considerations, smoke test.
- GitHub Actions/GitLab CI/Jenkins là lựa chọn công cụ.

### K20. Observability và vận hành
- Structured log, correlation ID, metrics, distributed traces; OpenTelemetry.
- Health/readiness/liveness, dashboard, alert, SLI/SLO/SLA.
- Prometheus/Grafana, log backend, trace backend như Jaeger/Tempo.
- Incident response, runbook, postmortem, on-call ở mức hiểu quy trình.
- Backup drill, disaster recovery, RPO/RTO, capacity và cost management.

### K21. System design, hiệu năng và hệ phân tán
- Requirements, capacity estimate, throughput/latency, percentile, bottleneck.
- Vertical/horizontal scale, load balance, stateless/stateful và session distribution.
- Monolith/modular monolith, microservices, event-driven, serverless.
- SOA, API gateway, service discovery; mô hình BFF khi có nhiều loại client.
- Replication/partitioning, consistency models, CAP trong ngữ cảnh network partition, quorum/consensus ở mức chuyên sâu.
- Timeout/retry/jitter, circuit breaker, bulkhead, backpressure và load shedding.
- Graceful degradation/fallback, failover và traffic shifting.
- Distributed transaction/saga, CQRS, event sourcing, distributed ID/lock và clock.
- Bài thiết kế: URL shortener, rate limiter, notification, chat, order/inventory, file service.

### K22. Cloud và DevOps chuyên sâu
- Compute, network/VPC/subnet/firewall, IAM, load balancer, managed database/storage.
- AWS/Azure/GCP: học khái niệm chung, thực hành một nền tảng khi cần.
- IaC: Terraform/OpenTofu; configuration management như Ansible.
- Kubernetes: pod/deployment/service/ingress, config/secret, storage, probes/autoscaling; Helm.
- GitOps, policy/supply-chain security, SRE, multi-region/reliability và FinOps.
- Service mesh (Istio/Linkerd), secret manager (Vault/cloud), artifact manager (Nexus/Artifactory); lựa chọn managed container/serverless như ECS/Fargate/Lambda.
- Docker, delivery và observability dùng chung K18–K20; nhánh này không lặp lại tiến độ.

### K23. Nhánh chuyên biệt theo nhu cầu
- Backend cho AI: model API, streaming, embedding/vector retrieval, RAG, đánh giá, giới hạn dữ liệu/chi phí; không gồm huấn luyện ML chuyên sâu.
- Data integration: ETL/ELT, CDC, streaming analytics; giao với data engineering.
- Geospatial, multi-region compliance, high-frequency/low-latency hoặc ngành đặc thù: thêm theo mục tiêu, không mặc định bắt buộc.

## Database: phân loại đúng trước khi chọn sản phẩm

### A. Theo mô hình dữ liệu

| Loại | Sản phẩm tiêu biểu | Nhận diện |
|---|---|---|
| Quan hệ / relational | PostgreSQL, MySQL, MariaDB, SQL Server, Oracle Database, SQLite | Bảng, quan hệ, constraint; SQL là ngôn ngữ, không phải tên sản phẩm |
| Document | MongoDB, Couchbase, CouchDB | Document và cấu trúc lồng nhau |
| Key-value | Redis, Valkey, Memcached | Tra cứu theo key; Redis/Valkey hỗ trợ nhiều cấu trúc, Memcached chủ yếu là cache |
| Wide-column | Cassandra, HBase | Column family/wide-column, mô hình truy cập theo partition/key |
| Graph | Neo4j, Amazon Neptune | Node/edge, truy vấn quan hệ/đường đi |
| Multi-model | ArangoDB; một số sản phẩm khác hỗ trợ nhiều mô hình | Các nhóm có thể chồng lấn, không phải phân vùng tuyệt đối |

DynamoDB hỗ trợ key-value và document; đặt ở cả hai khả năng bằng một tham chiếu dùng chung. Realtime/BaaS như Firebase là góc dịch vụ/khả năng đồng bộ, không là mô hình dữ liệu loại trừ các nhóm trên.

### B. Theo workload hoặc khả năng chuyên biệt — có thể chồng lấn nhóm A

| Nhóm | Ví dụ | Dùng cho |
|---|---|---|
| OLTP | PostgreSQL, MySQL | Giao dịch của ứng dụng |
| OLAP / phân tích | ClickHouse, DuckDB; warehouse như BigQuery, Snowflake | Tổng hợp/phân tích khối lượng dữ liệu lớn |
| Time-series | InfluxDB, TimescaleDB | Chuỗi dữ liệu theo thời gian; TimescaleDB mở rộng PostgreSQL |
| Search engine | Elasticsearch, OpenSearch | Full-text và truy xuất/tổng hợp; không mặc định thay database giao dịch |
| Vector search | Qdrant, Milvus; extension pgvector cho PostgreSQL | Tìm gần nhau theo vector; database riêng hoặc tính năng của DB |
| Geospatial | PostGIS trên PostgreSQL | Dữ liệu không gian và truy vấn địa lý |

### C. Theo kiến trúc và triển khai

- Embedded: SQLite; client/server: PostgreSQL/MySQL.
- Single-node hoặc distributed; distributed SQL: CockroachDB, TiDB.
- Self-hosted hoặc managed/cloud; có thể dùng cùng họ database.
- Row-oriented hoặc column-oriented là cách tổ chức lưu trữ. Wide-column như Cassandra không đồng nghĩa columnar analytics như ClickHouse.
- In-memory/disk/persistence là thuộc tính cần hiểu thêm, không đủ để quyết định loại database.

Học chung trước: modeling → query → transaction/consistency → index/performance → migration → backup/restore. Sau đó so sánh sản phẩm theo workload, consistency, quy mô, vận hành và chi phí. Không chia MySQL và PostgreSQL thành hai khóa lặp toàn bộ kiến thức SQL.

## Kết quả rà bản đồ cũ

12 chặng hiện có là bản tổng quan demo, không phải mục lục đầy đủ. Các vùng thiếu hoặc bị gộp quá mức: K05, K06, K07, phân loại K10, K13–K15, độ sâu K16–K17 và K19–K22. Các nhánh API, Spring và Java cũng cần bổ sung như cây trên.

Khung này đủ để bắt đầu review toàn cảnh trong phạm vi Java Backend + vận hành + nhánh chuyên sâu đã nêu; không tuyên bố bao gồm mọi chuyên ngành hay sản phẩm. View đi kèm có mức độ từng nhánh con, đầu ra nhóm, nền tảng nên biết và mốc project. B02 ở REVIEW; sau khi chốt khung mới chuyển B03 để viết từng mục.

## Đường học chính và project song song

Đường học theo chặng, điều kiện đầu vào, đầu ra và ánh xạ 23 nhóm đã được cụ thể hóa tại [java-backend-learning-path.md](java-backend-learning-path.md). S00–S10 là thứ tự học; K01–K23 vẫn là cây phân loại kiến thức.

Đường cốt lõi: K01/K02/K03 nền tảng → K04 Java + K05 cơ bản + K17 testing cơ bản → K08 HTTP/API + K09 Spring → K10 SQL + K11 persistence → K16 identity/authorization → K18 Docker → K19 deploy + K20 quan sát/vận hành. K07 thiết kế cơ bản học cùng quá trình; K06 concurrency nâng cao, K12–K15 và K21–K23 mở theo nhu cầu. Linux, Git, testing và security là kiến thức xuyên suốt, không là chương phải học hết trước khi xây.

| Mốc | Điều kiện năng lực mở | Đầu ra project |
|---|---|---|
| P0 | Chưa đủ OOP/Collections | Bài tập nhỏ; không bắt đầu project |
| P1 | Method, class/object, List/Map, input/exception cơ bản | CLI quản lý công việc, dữ liệu trong bộ nhớ |
| P2 | I/O và unit test | Lưu file, đọc lại sau khởi động, test logic |
| P3 | HTTP/REST + Spring MVC | API với validation và error contract |
| P4 | Schema/SQL + JDBC/JPA + transaction cơ bản | Database, migration, integration test |
| P5 | Authentication + authorization + ownership | Đăng nhập và cách ly dữ liệu người dùng, test quyền |
| P6 | Docker/Compose + cấu hình | App/database chạy cùng nhau; kiểm tra persistence và health |
| P7 | CI/CD + deployment + log/metrics + restore | Bản triển khai có test, quan sát, rollback và phục hồi |

Mở mốc bằng năng lực đã chứng minh; cho xem trước khi chưa đủ. Progress project tách khỏi kiến thức; bằng chứng tự khai, mô phỏng và thực thi được xác minh phân biệt rõ. K12 cache, K13 queue, K14 job và K21 distributed dùng mini-project hoặc milestone tùy chọn khi có bài toán phù hợp, không bắt project chính dùng mọi công nghệ.

## Kiểm tra độ bao phủ v1

- Đủ chu trình: hiểu hệ thống → viết → giao tiếp → lưu/xử lý → kiểm tra/bảo vệ → triển khai → vận hành → mở rộng.
- Mỗi K01–K23 có phạm vi; view mở được nhánh con, mức độ và công nghệ minh họa.
- Database có ba trục: mô hình, workload/khả năng, kiến trúc/triển khai.
- Đã bổ sung web server, logging Java, SQL cơ bản/nâng cao, TDD/BDD, security headers/SAML, shell/email, virtualization, Twelve-Factor/service discovery/mesh và degradation.
- Project khởi động sau đủ nền tảng; nguồn gốc evidence được phân biệt.
- Việc còn lại sau review khung: prerequisite cấp bài, câu hỏi/test/rubric, nội dung giải thích, nguồn từng bài và thực thi lab.

## Nguồn đối chiếu chính

- Roadmap.sh bản PDF đã đọc trong lượt đối chiếu: [Backend](https://roadmap.sh/pdfs/roadmaps/backend.pdf), [Java](https://roadmap.sh/pdfs/roadmaps/java.pdf), [SQL](https://roadmap.sh/pdfs/roadmaps/sql.pdf), [DevOps](https://roadmap.sh/pdfs/roadmaps/devops.pdf). Dùng đối chiếu độ bao phủ, không sao chép nguyên thứ tự hoặc coi mọi công cụ là bắt buộc.

- [Java learning topics](https://dev.java/learn/): ngôn ngữ và các nhóm API/runtime.
- [JEP 444](https://openjdk.org/jeps/444): virtual threads.
- [PostgreSQL SQL language](https://www.postgresql.org/docs/18/sql.html): query, index, concurrency và tối ưu.
- [MongoDB — database types](https://www.mongodb.com/resources/basics/databases/types): các mô hình dữ liệu; dùng để đối chiếu phân loại, không dùng tuyên bố tiếp thị để chọn sản phẩm.
- [ClickHouse — columnar database](https://clickhouse.com/resources/engineering/what-is-columnar-database): phân biệt mô hình quan hệ với tổ chức lưu theo cột.
- [Spring projects](https://spring.io/projects/): phạm vi hệ sinh thái.
- [OWASP API Security](https://owasp.org/projects/api-security-project): các nhóm rủi ro API.

Đây là thiết kế chương trình học tổng hợp của dự án; nguồn trên hỗ trợ các nhánh kỹ thuật, không phải bằng chứng rằng một roadmap có thể liệt kê hết mọi sản phẩm Backend.
