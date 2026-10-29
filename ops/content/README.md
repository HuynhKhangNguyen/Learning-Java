# Content owned-lab admission provisioning

[Task/DoD/evidence R04b](../../docs/features/f10-identity-access/sessions/tasks/BL-R04b.md) · [Wire contract](../../contracts/http/admission/README.md) · [DB ownership](../db-lab/README.md)

Chỉ disposable PostgreSQL18.6/PKI trong workspace; không production, Windows service hoặc tùy ý trỏ DSN. Managed CMD03/04/05 qua `scripts/services.ps1` tạo ownership markers, own `content_db`/app/migrator roles, chạy migration bằng migrator và dừng owned processes trong finally. Credentials và private keys chỉ ở ignored lab directories, không truyền password qua argv/log/report hoặc commit.

Production schema V2 giữ `auth_environment`, account floor, session deny, immutable command receipt và admission-use. App role không migrate, bật readiness, đổi realm/generation, hạ floor hoặc xóa deny/receipt. Admission runtime không tự provision; schema/readiness sai thì fail closed trước listeners.

`provision-admission-realm.sql` chạy cùng connection `content_migrator` sau migrations, với session settings `backend_lab.provision_realm`, `backend_lab.provision_generation`, `backend_lab.provision_run_id`; run ID phải trùng owned cluster marker. Realm/generation do lab trust harness cấp. Script giữ advisory lock, chỉ khởi tạo môi trường trống hoặc nhận exact retry; khác identity/realm/generation, ready=false hoặc retained state khi chưa có environment đều bị từ chối. Không dùng script này để reset/recover snapshot; không xóa tables để ép script chạy.

Profile `admission` chỉ nhận `backendlab.admission.config` trong đúng owned trust directory. File properties có đúng các key: `schemaVersion=1`, `enabled=true`, `realm`, `generation`, `topologyVersion=r1-static-v1`, `dbRunId`, `identityPort`. Ba identifiers phải khớp transport config và own DB environment; port là loopback fixture unprivileged, không8080/8443. Không override clock offset để mở traffic. Production endpoints/secret lifecycle/restore handshake có owner sau, không suy deployment-ready từ profile lab.

CMD04 `AdmissionRuntimeIT` thực hiện migration/provision twice, khởi động packaged JAR, sử dụng real mTLS controlled Identity-provider fixture, rồi kiểm caller/body/config/generation/clock/lost ACK/restart. Nó không mở learner route hoặc thay canonical Identity INT01. SEC04 chạy full own isolation+gate prerequisites; SEC02/09/15 giữ lớp evidence riêng theo task. Cần giữ report status/input hashes/JUnit/retained artifact và kiểm clusterStopped/listener closed; PID đã exit chưa đủ khẳng định TCP đã đóng.

## Publication coordinator R06c

`provision-publication-coordinator.sql` chỉ chạy bằng `content_migrator` trong
owned three-service lab sau V5, auth realm và canonical head. Script bind exact
realm, Content generation, Learning generation và initial learning-ready revision;
exact retry được phép nhưng không rotate generation hoặc ghi đè retained operation.

Runtime `content_app` chỉ có `SELECT` trên coordinator tables; ba trusted
transitions còn yêu cầu capability ngẫu nhiên riêng của coordinator. Provisioner
chỉ lưu SHA-256 của capability trong DB; raw bytes nằm ở file
`publication-coordinator.cap` trong owned trust directory và không được in vào
log/report. General app connection biết binding/digest nhưng không có capability
thì không thể advance canonical/readiness.

STAGE/FENCE/RELEASE/ABORT bytes được tạo từ binding đã provision; CLI không nhận
DSN, peer URL, realm, generation hoặc capability qua argv. Network tới Learning
luôn ở ngoài Content transaction. FENCE receipt chỉ commit canonical P+1 cùng
RELEASE outbox khi không có precommit abort intent; nếu intent đã bền thì exact
FENCE prefix tạo ABORT và giữ P. Sau PUB-04 chỉ dispatch/resume RELEASE, không
abort hoặc rollback head.

`deliverySequence` là cursor global của luồng Content→Learning, không reset về 1
khi operation cũ `COMPLETE` hoặc `ABORTED`. Operation mới sau ABORT phải tiếp tục
cursor và tái dùng exact immutable bundle cho cùng target revision; Learning giữ
replica/history của operation cũ. CMD-03/04/05 managed runner dùng `EvidenceTask
R06c`; CMD-05 chỉ nhận DATA-02–07/14 và mỗi selector bắt buộc exact named report.
DATA-02/06/07 ở đây chỉ chứng minh lớp coordinator/manifest, không thay learner
T1/T2 hoặc current-overlay owner.
