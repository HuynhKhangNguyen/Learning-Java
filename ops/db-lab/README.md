# PostgreSQL lab — BL-R02a

Lab riêng cho CMD-03, dùng PostgreSQL18.6-5 Windows x64 portable. Không nối database có sẵn. [Evidence/giới hạn](../../docs/database-lab-verification.md), [layout](layout.json) và [runtime pin](../release/postgresql.json) là nguồn hiện hành.

Từ root repo, cần Python3.9+, PowerShell7.4+, toolchain R01a và quyền khởi động process PostgreSQL local:

```powershell
pwsh -NoProfile -File scripts/setup-backend.ps1
pwsh -NoProfile -File scripts/setup-postgresql.ps1
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service all
# Hoặc kiểm một service trên cluster mới:
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service identity -Offline
```

Lần đầu Maven cần mạng; `-Offline` chỉ dùng khi cache đã đủ. PostgreSQL setup luôn kiểm archive hash trước extraction, rồi kiểm các file runtime đã pin. Hash ZIP được tính từ download HTTPS chính thức, chưa xác minh publisher checksum/signature. Không installer, registry, PATH hệ thống hoặc Windows service.

Mỗi lần chạy tạo `.tools/db-lab/run-*` với ownership marker/runId, loopback127.0.0.1/cổng tạm, SCRAM và password ngẫu nhiên. Ba database có owner NOLOGIN, migrator và app riêng; app/migrator chỉ CONNECT database của mình. PUBLIC không được CONNECT DB dịch vụ/postgres/template1 hoặc dùng schema public. Migrator sở hữu schema dịch vụ và migration_metadata, không cluster privilege hoặc role membership. App chỉ USAGE schema dịch vụ và DML trên bảng được cấp; service_descriptor chỉ SELECT. Không quyền DDL/migration/history hoặc xuyên DB.

`V1__service_descriptor.sql` là marker kỹ thuật riêng từng service. Các bảng transaction_probe/failed_probe/recovered_probe và V2/V3 là test fixtures, không nằm trong artifact sản phẩm. JDBC/Flyway chỉ test scope; bootstrap app không tự kết nối hoặc migrate DB.

Harness chọn từng service bằng Maven Failsafe profile db-it. Missing config/report/suite, zero tests, failure/error hoặc required skip trả nonzero. XML thật được sao chép vào evidence từng run trước khi target bị build sau thay thế. `-LabConfig` là đường nội bộ của harness, không DSN tùy ý. GUC runId và fresh schema được kiểm trước migrate để không reuse cluster cũ.

Cluster được dừng trong finally ở success/failure; harness kiểm port đã đóng. Stop kiểm resolved path, UUID/workspace/data marker, từ chối linked data directory. Timeout dừng process tree của build do harness tạo; cleanup không xác minh được phải báo lỗi. Không recursive delete hoặc reset DB đang dùng. Dữ liệu/log/credentials test tạm được giữ trong ignored `.tools/` và `artifacts/quality/R02a/`, không commit. Không chia sẻ test-credentials.properties.

Windows runner hiện cần chạy PostgreSQL lab ngoài sandbox: sandbox báo restricted-token87/start3. Không tự yêu cầu quyền admin máy. JDBC dùng plaintext chỉ trên loopback cluster tạm, chưa là production TLS; Linux/Compose/container/network isolation/scan/restore thuộc R31/R32. `CMD-03 PASS` không cấp evidence SEC/DATA nghiệp vụ chưa triển khai.

Kiểm helper (fixture doubles riêng, không chứng minh DB) và path/process guards:

```powershell
pwsh -NoProfile -File scripts/tests/backend-build.Tests.ps1 -EvidenceTask R02a
python -X utf8 scripts/tests/db_lab.Tests.py
```

Fixture lỗi lifecycle: `python -X utf8 scripts/db_lab.py --service identity --offline --fail-before-build` phải exit1 và report FAIL/clusterStopped=true, không chạy Maven. Đây không phải CMD-05 fault nghiệp vụ.
