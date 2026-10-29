# Identity credential maintenance — BL-R03

Local operator CLI, không HTTP admin/public signup/login session. [Evidence](../../docs/identity-credential-verification.md), [backlog](../../docs/delivery-backlog.md) và [session protocol](../../docs/architecture/session-protocol.md) là nguồn scope. Chỉ chạy khi operator đã được cấp own Identity app-role credential; secret provisioning/rotation/mount và durable maintenance interlock thuộc R31/R32. Không dùng migrator/superuser để chạy CLI. HTTP bootstrap/transport không tự mở credential routes hoặc migrate.

R04a [session evidence](../../docs/features/f10-identity-access/sessions/verification.md) giữ production V3/Spring Session JDBC, credential+preauth+reset-intent callback atomic và [public contract](../../contracts/http/identity/README.md). Auth routes chỉ bật bằng explicit owned-lab `session` profile và cấu hình/provision dưới đây; không phải deployment/production edge. CMD03 Identity bắt buộc `IdentitySessionAuthorityIT`/unit adapter, CMD04 thêm `SessionRuntimeIT`/actual browser report. Các lệnh R03 phía dưới giữ credential scope; không trở thành full browser/revoke ACK qualification. CLI R03 không tạo browser-bound operation/session/cookie hoặc clear pending; callback mới không đổi CLI contract. Không có retention/purge hoặc realm reset command dùng cho deployment; trạng thái chuẩn ở backlog.

## R04d named canonical lab — owned-lab qualified

Profile `canonical` là composition riêng, không bật ngầm từ `session`/`transport`/`delivery`. R04d DONE13/10 trong owned-lab scope cho INT01/INT06/public proof/sensitive authority/auth worker và closing TLS/fault/browser/DoD; chưa production edge/lifecycle journal/learner business. [Checkpoint/DoD](../../docs/features/f10-identity-access/sessions/tasks/BL-R04d.md) giữ exact current evidence/history/limits; [command/layer map](../../backend/README.md#r04d-canonical-three-app-driver--owned-lab-qualified) không dùng capability hoặc selector existence làm bằng chứng nghiệm thu.

Topology duy nhất `r04d-three-service-loopback-v1`, bind127.0.0.1, HTTPS/mTLS và pinned service identity:

| Service | Public edge-only | Private service/operator |
|---|---|---|
| Identity | 18081 | 18441 |
| Content | 18082 | 18442 |
| Learning | 18083 | 18443 |

Đây là static lab port mapping để ba artifacts chạy cùng máy; không sửa topologyVersion auth barrier `r1-static-v1`, không cho tự đổi required participants/generation hoặc arbitrary endpoint override. Profiles cũ giữ8080/8443. Transport properties của canonical dùng schemaVersion2 và thêm đúng `listenerTopology`; các fields/cert/marker/own DB checks cũ giữ nguyên, test.clockOffsetMs phải0. Explicit server.port nếu có phải đúng public port service, không nhận management/address/TLS override. Content/Learning admission config giữ schema1, identityPort bắt buộc18441 trong canonical profile; controlled-provider port chỉ thuộc admission fixture lịch sử.

Identity thêm `backendlab.canonical.config` trỏ immediate child của cùng owned trust-lab directory. Exact fields: schemaVersion1/enabledtrue/listenerTopology/realm/generation/dbRunId/origin/corpusPath/corpusSha256/throttleKey/identityGeneration/contentGeneration/learningGeneration. Ba generations canonical UUID khác nhau, Identity generation khớp transport; realm không trùng generation. Corpus phải own directory/hash đúng, throttle HMAC key32bytes nonzero; không có peerPort hoặc URL override trong config. [Template chưa điền](canonical-config.example.properties) không là config chạy được; không đặt secret thật trong repo/argv/log.

Startup verify own schema/role/clock/session realm/delivery realm+generations/guards/privileges/proof capacity trước listeners. Migrations/provision là owned-lab operator step riêng với own migrator, runtime không tự enable/reset realm/schema. Worker một luồng/replica có durable leases: sweep expiry, reconcile legacy pending IDs không consume token/password lần hai, gửi exact INT02 ngoài DB locks, lưu validated ACK rồi guarded completion sau đủ hai current mandatory participants. Peer down/poison/clock/config lỗi giữ deny/pending; restart không clear gate. Disable/enable auth completion không clear ACCOUNT_STATE journal gate R29b. Không learner API/T1/T2/reset business commit/LAB_APPLY production adapter hoặc deployment từ profile này.

## Cấu hình bắt buộc

Environment process: `IDENTITY_DB_URL`, `IDENTITY_DB_USER=identity_app`, `IDENTITY_DB_PASSWORD`, `IDENTITY_CORPUS_PATH` (absolute path), `IDENTITY_CORPUS_SHA256`, `IDENTITY_THROTTLE_KEY` (Base64, ít nhất32 random bytes). Không nhập secret vào argv/history; inject qua secret channel do môi trường quản lý. Missing/invalid corpus/hash/HMAC/own DB/schema fail nonzero với `MAINTENANCE_FAILED`, không stack trace/DSN/password. DB phải được migration job riêng áp dụng V1/V2; Flyway không vào runtime.

[Corpus manifest](password-corpus.json) + [fixture](password-corpus.txt) + [license](password-corpus.LICENSE.txt) pin version/hash/source/CC0. Đây là bốn fixture password tự viết, **không comprehensive production corpus**. Operator chọn offline corpus phù hợp trước pilot, ghi version/source/license/hash, UTF-8 strict exact lines, tối đa64MiB; thiếu/hỏng hash không fallback hoặc bỏ kiểm. Không trim/Unicode normalize password hay corpus lines.

## Lệnh từ root repo

Sau `scripts/services.ps1 -Task verify -Service identity`, executable JAR hỗ trợ alternate main qua Boot PropertiesLauncher (không cần source/test classpath):

```powershell
& './.tools/jdk/jdk-21.0.12.1+1/bin/java.exe' '-Dloader.main=lab.backend.identity.credential.IdentityMaintenanceCli' -cp './backend/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar' org.springframework.boot.loader.launch.PropertiesLauncher invite learner01
```

Các command đều có đúng hai args: `invite <loginName>`, `reissue-activation <userUUID>`, `issue-reset <userUUID>`, `disable <userUUID>`, `enable <userUUID>`, `require-recovery <userUUID>`, `verify <loginName>`, `activate stdin`, `reset stdin`. Không có `clear-recovery` để khôi phục hash cũ. LoginName trim + ASCII lowercase,3–32 `[a-z0-9][a-z0-9._-]*`; UUID không đổi theo tên.

Invite/reissue/reset output chứa token một lần ở **stdout operator**. Không capture stdout này vào CI/log/report; chuyển token qua kênh operator đã xác minh. Token256bits/43 Base64url chars, DB chỉ digest SHA256; ACTIVATE24h, RESET15m. Issue reset không đổi password/state/epoch và không khóa account. Reissue invalidate token cùng purpose cũ.

`verify` nhận một dòng password qua stdin; `activate/reset` nhận hai dòng token rồi password. UTF-8 strict, LF kết thúc mỗi dòng, đóng stdin/EOF; tối đa2048bytes. Không paste secret vào shell command hoặc file nguồn; CRLF không tự trim password, adapter phải tạo đúng protocol. Kết quả `VERIFIED`, `INVALID_CREDENTIALS`, `UNAVAILABLE` hoặc `THROTTLED`; activation/reset thành công chỉ cập nhật credential, **không cấp cookie/session**. CLI dùng trusted source loopback, không bằng chứng browser/proxy IP hoặc CSRF; R04/R31 adapter phải cấp source IP đã xác minh.

## Trạng thái, concurrency và giới hạn

`app_user` là account guard row: mọi token/state operation lock user trước token; KDF không giữ connection/transaction. Verify snapshot rồi KDF/dummy KDF, lock user và reread hash/epoch/state/pending trước callback effect trong cùng transaction. R04 phải tạo session qua boundary đó, không dùng kết quả KDF rời làm quyền.

Disable/enable/reset/recovery tăng epoch, invalidate open tokens và atomically ghi `credential_transition=PENDING` + `auth_transition_pending=true`. R03 không có participant delivery/ACK, không clear pending, không báo distributed completion. `ACCOUNT_TRANSITION_PENDING` là local command result, không lifecycle journal ACK. Disable không xóa work; enable không hồi sinh token/session cũ. Recovery xóa hash cũ; `operator_disabled` là gate riêng được bảo toàn khi mark recovery. Enable recovery chỉ clear disable, account vẫn RECOVERY_REQUIRED tới reset credential mới; pending vẫn deny effect. Invited account phải activate trước state maintenance.

Throttle dùng HMAC-SHA256 keys, DB clock, account/token5 và IP30 failures/15m, block15m, TTL30m, cap100000 keys (DB trigger + atomic admission), no permanent lock. New-key capacity exhausted trả THROTTLED, existing keys còn hoạt động theo quota. Hai KDF slots/process và in-flight reservations chặn hàng đợi không giới hạn; crash hoặc complete DB failure giữ reservation bảo thủ tới TTL30m. Không timing-equivalence/SLA/multi-replica load claim. Denied/error audit chỉ event/UUID/result/correlation/detail code, không input/password/token/IP raw.

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service identity -EvidenceTask R03 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service identity -Case SEC-12 -EvidenceTask R03 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service identity -Case SEC-22 -EvidenceTask R03 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service identity -EvidenceTask R03 -Offline
```

Commands tạo own disposable PostgreSQL cluster, giữ reports/input/JAR hashes rồi stop. Contract còn tạo owned PKI; credential faults không cần PKI. Required suite/case missing/0/skip/failure phải nonzero. Không dùng lab role/PKI/corpus làm production secrets hoặc coi suite là full SEC/session/recovery qualification.

## R04a session lab runtime — explicit opt-in

Chỉ profile `session`, không trộn với bootstrap/transport/pilot. Cần hai owned config files trong cùng `.tools/trust-lab/run-*` có marker; trust config giữ R02b exact keys/PKI/own DB credentials, `test.clockOffsetMs=0` cho session runtime. Không cung cấp secret qua argv hoặc log. Session config exact keys:

```properties
schemaVersion=1
enabled=true
realm=<provisioned realm UUID, same as trust config>
origin=https://localhost:<owned frontend fixture port>
corpusPath=<absolute external corpus file>
corpusSha256=<operator-verified SHA256>
throttleKey=<Base64 of 32 random bytes, not zero>
dbRunId=<same owned DB run UUID as trust config>
```

File là secret mount/lab fixture ngoài Git/image, không copy các placeholder thành config dùng thật. Config/schema/role/realm/corpus/key/origin/clock sai phải fail startup; app không tự sửa/migrate. Corpus repo chỉ deterministic fixture, không production breached-password qualification.

Forward migrations qua own migrator trước; [provision-session-realm.sql](provision-session-realm.sql) chạy bằng `identity_migrator`/`identity_db`, `psql -X --no-password -v ON_ERROR_STOP=1`, credential qua protected environment/channel. PGOPTIONS đặt `backend_lab.provision_realm` và `backend_lab.provision_run_id` UUID đã xác minh. Script transaction + lock, exact retry an toàn; existing realm khác hoặc auth state tồn tại khi environment thiếu thì fail. Không UPDATE/DELETE/reset realm; restore/deploy thuộc protocol/task khác.

Khởi động JAR với `--spring.profiles.active=session`, `--backendlab.trust.config=<owned path>`, `--backendlab.session.config=<owned path>`. Public8080 yêu cầu mTLS edge; edge phải strip browser identity/forwarded/source headers rồi overwrite `X-Backend-Lab-Client-IP` bằng IP literal resolved. Không dùng header browser tự khai hoặc mTLS service certificate thay session authority. Private8443 không nhận browser Origin/Cookie/source-IP. Production edge R31a chưa triển khai.

Sweeper10s/batch≤100 giữ guards/intents qua restart; scheduler failure không nới expiry vì từng access tự kiểm own DB clock/realm/guard. Không purge tombstones, clear pending, grant full reauth hoặc global COMPLETED. Profile lab không claim production readiness/capacity/corpus/load/Linux/restore.

Current `-Task contract -Service identity -EvidenceTask R04a -Offline` phải có packaged session suite + actual browser report; legacy R03/R02b command results phía trên là evidence lịch sử, không ứng với candidate hiện tại. Browser fixture dùng Node/Playwright dev dependencies đang có + installed Edge, isolated context, self-signed frontend acceptance chỉ trong fixture; upstream CA/SAN/mTLS verification vẫn bật. Không OS certificate trust/global browser install, trace/video/private payload logging hoặc FE edits.
