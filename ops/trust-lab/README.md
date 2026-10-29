# FX-03 trust/clock lab — BL-R02b

[Backlog](../../docs/delivery-backlog.md) giữ AC/checkpoint. Lab dùng pinned JDK21/keytool, PostgreSQL18.6 portable của R02a, test-only CA/leaf theo service/env. Không cài CA vào Windows/browser trust hoặc chạy cloud/edge. Keys/config nằm ignored .tools/trust-lab/run-*; signer CA private keys được loại khỏi fixture hoàn tất trước app start, runtime chỉ giữ trust anchors public và leaf stores.

```powershell
pwsh -NoProfile -File scripts/setup-backend.ps1
pwsh -NoProfile -File scripts/setup-postgresql.ps1
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service identity -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service all -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service all -Case SEC-14 -Offline
```

Bỏ -Offline khi cache chưa đủ. R02b contract chạy own wire + real receiver TLS suites từng service. Fault selector chỉ SEC14/15/20 receiver transport groups đã tạo, không full registry; unsupported task/case/config/missing suite/0tests/skip/failure nonzero. WIRE04/05 mới common shape, còn semantics ở owner sau. DB cluster fresh/roles riêng, selected services tuần tự; fixed8080/8443 loopback phải trống, không kill process đang dùng cổng. finally dừng cluster và context thuộc test; build timeout dùng owned process tree. Không recursive delete/test trên DB có sẵn.

Spring profile `transport` là foundation lab explicit; `bootstrap` giữ riêng. Compose lab/pilot profiles của R31 khác Spring profile. Source chưa pilot mode/domain auth. 8080 HTTPS yêu cầu client cert edge,8443 HTTPS yêu cầu service/operator, URI SAN `spiffe://backend-lab/lab/<identity>` và EKU/validity; không tin CN/header/cookie/CORS. Public/private route và action policy tách ở filter trước probe. Cert đúng CA vẫn phải đúng identity/env/action/audience/binding. Real clients giữ HTTPS hostname verification và expected receiver SAN, fixed peer URI, TLS1.2/1.3, no redirect/application retries, connect1s/request2s và bounded64KiB response. General request5s/endpoint budgets mở rộng bởi API owners.

JDBC runtime chỉ profile transport dùng own app role để query clock_timestamp/current_database/current_user/runId; bootstrap không kết nối DB và app không tự migrate. Không ORM/business tables/auth gates. Server-JVM vs own DB clock sample bounded1s và drift500ms; request cutoff bảo thủ còn6s+500ms và TTL<=15s. Wrong/missing/unavailable clock fail closed; test.clockOffsetMs chỉ owned lab config/profile transport cho fault. Không chứng minh distributed Identity clock/realm/generation/fence hay multi-replica semantics của R04 từ clock lab cùng PostgreSQL instance.

Config yêu cầu owned path/UUID/env=lab/service/realm/generation/DB runId, own leaf store/trust store và generated credentials. Files/passwords không argv/log/source/JAR. Cross-service keys đặt cùng fixture cho test chủ thể, chưa proof production secret mount isolation; R31 mount mỗi app chỉ material của nó. Rotation/revocation trên existing connections, browser sessions/CSRF/admission/user owner/domain actions, actual edge/proxy privacy, Linux/container/scan/release vẫn chưa qualify. Metadata/probe luôn businessReady=false.
