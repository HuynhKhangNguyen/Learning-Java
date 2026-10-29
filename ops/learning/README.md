# Learning owned-lab admission provisioning

[Task/DoD R04c](../../docs/features/f10-identity-access/sessions/tasks/BL-R04c.md) · [Wire](../../contracts/http/admission/README.md) · [DB lab](../db-lab/README.md)

Chỉ disposable PostgreSQL18.6/PKI owned workspace, không service/global install hoặc production DSN. Managed helper tạo own `learning_db`/app/migrator roles, migrations bằng migrator, secrets trong ignored lab directories, finally dừng processes/listeners/cluster. Không truyền passwords qua argv/log/report.

Production V2 auth receiver giữ environment, monotonic account floor, session tombstone, immutable command receipt và admission-use. App không migrate/provision/reset environment hoặc xóa receipt/deny; thiếu schema/readiness/realm/generation thì runtime fail closed.

`provision-admission-realm.sql` chạy cùng `learning_migrator` connection sau migrations, settings `backend_lab.provision_realm`, `backend_lab.provision_generation`, `backend_lab.provision_run_id`. Run ID phải khớp owned cluster. Advisory lock serialize initial provision/exact retry; sai realm/generation/readiness hoặc retained state chưa có environment bị từ chối. Đây không là restore/reset workflow; không xóa tables để ép chạy lại.

Profile `admission` yêu cầu `backendlab.admission.config` trong đúng owned trust directory với đúng keys `schemaVersion=1`, `enabled=true`, `realm`, `generation`, `topologyVersion=r1-static-v1`, `dbRunId`, `identityPort`, `origin=https://127.0.0.1`. Identifiers khớp transport+own DB; port unprivileged loopback, không8080/8443; clock offset không được bypass. Origin chỉ lab trong leaf này; production edge/config có owner sau.

CMD04 chạy own packaged JAR, actual PG/mTLS với controlled Identity-provider fixture, strict body/digest/caller/deadline/config/lost ACK/restart. SEC03/04 chạy full isolation+gate prerequisites; SEC01/02/05/09/15 có named suites. Giữ report/input hashes/retained artifact và `clusterStopped`/listener closed; PID exit chưa đủ. Không mở draft API/T1/T2/coordinator/global COMPLETED hay production recovery.

## R06b publication realm

[Task R06b](../../docs/features/f08-content/tasks/BL-R06b.md) · [INT-04](../../contracts/http/learning/internal/publication/README.md). `provision-publication-realm.sql` chỉ chạy bằng `learning_migrator` sau V4, với settings `backend_lab.provision_realm`, `backend_lab.provision_generation`, `backend_lab.provision_content_generation` và `backend_lab.provision_run_id` từ owned lab. Provision exact retry phải hội tụ; realm/generation/run khác hoặc retained state không khớp bị từ chối, không truncate/reset dữ liệu tốt.

Profile `publication` yêu cầu trust config schema 2, `listenerTopology=r06b-content-learning-loopback-v1`, Learning `18083/18443`, Content peer cố định private `8443`, own DB run ID/realm/generation khớp provision. Runtime không migrate/provision, không nhận arbitrary peer URL/port và không giữ DB lock trong lúc tải INT-03. App role chỉ đọc environment/replica/receipt, insert immutable rows và đổi operation/head theo transaction; không DDL, update/delete replica/receipt hoặc đổi security generation.

Managed R06b dùng `services.ps1 -EvidenceTask R06b`: CMD-03 chạy V4/store/race/gate/ABORT trên PostgreSQL thật; CMD-04 chạy packaged Content + Learning mTLS; CMD-05 chỉ nhận `DATA-01`, local `DATA-02`, `SEC-16`. Mỗi run phải có exact named reports, current input hashes, retained artifacts và `clusterStopped=true`. Đây là lab participant evidence, không phải R06c coordinator/canonical completion, learner T1/T2/progress hay production deployment.
