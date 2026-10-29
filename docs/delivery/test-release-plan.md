# Test, review và release plan v3

BL-MS04 · 30/09–01/10/2026 · Expected/command contract. Sau chỉ dẫn code, R01a foundation và [R01b Identity bootstrap](../platform/bootstrap/identity-verification.md) và [R01c Content bootstrap](../platform/bootstrap/content-verification.md) và [R01d Learning bootstrap](../platform/bootstrap/learning-verification.md) có evidence riêng; SEC/DATA/DB/storage/release suites chưa runtime PASS từ các mốc này. [Implementation plan](implementation-plan.md) giữ stack/topology/budgets; [backlog](../delivery-backlog.md) giữ owner task/status; [quality](../quality-process.md) và [delivery contract](contract.md) giữ Ready/Done. SEC/DATA expected vẫn lấy [security](../architecture/security-design.md)/[data protocols](../architecture/data-protocols.md), không sao chép implementation làm đáp án test.

## 1. Fixtures và lớp bằng chứng

| Fixture ID | Đầu vào / điều khiển | Nơi tạo dự kiến / task |
|---|---|---|
| FX-01 OWNERS | A/B khác UUID, absent C, same loginName historical orphan; no prod data | contracts/fixtures/identity/ — R03/R04d |
| FX-02 DB_RACE | PostgreSQL18.6 thật, hai connection + latch/barrier, rollback/fault hooks theo transaction boundary | backend/services/*/src/test/ + scripts DB lab — R02a |
| FX-03 TRUST_TIME | R02b actual keytool CA/env/service wrong/expired/SAN/EKU, own-DB clock drift/cutoff + packaged TLS; same-DB multi-replica admission R04d còn pending | scripts/trust_lab.py + owned service TransportTrustIT/WireContractIT + contracts/http/common; [evidence](../platform/service-trust/verification.md) |
| FX-04 DELIVERY | Lost request/ACK, crash producer/receiver, duplicate/gap/poison/different digest/generation; auth stream riêng | contracts/fixtures/delivery/ + fault adapter — R02c |
| FX-05 PUBLICATION | Catalog schema/DAG negatives; P/P+1 cosmetic/withdrawn/invalid rubric/checksum mismatch, staged/fenced/committed prefix | R05 standalone catalog subset; contracts/fixtures/publication/ — R06a/b/c |
| FX-06 EXPOSURE | Clean/known/solution/support family, first allocation/self-encounter, preparing/pending import; journal crash points | contracts/fixtures/exposure/ — R28a/R08/R10/R27 |
| FX-07 GRADING | D06 expected độc lập, CA/CM fixtures, pinned input/version; lease1/lease2; epoch old/new | contracts/fixtures/collections/ — R12/R13a/b |
| FX-08 RESTORE | Snapshot sets trước exposure/reset/delete/password change; mixed heads/orphan/missing prefix/key; maintenance crash/restart/late writer/stale resume; new realm/generation | ops/backup/fixtures/ — R32a/b |
| FX-09 BROWSER | Backend/edge TLS actual launch/teardown, two cookies/contexts, tabs, delayed responses, expiry/offline, keyboard320px/theme/reduced motion | R31a base harness; frontend/tests/ auth R24a–c và UI owners |
| FX-10 RELEASE | N/N-1 contract artifacts, old/new migrations, unsafe config/secret/private payload sentinel, scan deny fixture | contracts/fixtures/compatibility/ + ops/release/ — R31a/c |

DB lab unique project/DB names, named workspace-bound disposable volumes và test-only keys. Scripts verify resolved cleanup targets/project IDs trước delete; không reset dev/production DB để test. Barrier deterministically dừng đúng bước, không sleep đoán race. Network/fault hooks chỉ lab profile; prod startup từ chối test bypass. Live storage qualification dùng namespace test operator cấp, không credentials production trong fixture/log.

Unit reducer/grader kiểm semantics bằng expected độc lập; DB integration chứng minh local commits/locks/floors; cross-service HTTP/mTLS/fault chứng minh admission/delivery/ACK; storage actual-provider chứng minh durability/index/CAS; browser chứng minh cookie/cache/routing/UX; restore drill chứng minh whole system recovery. Mock allow hay in-memory DB không thay các lớp này.

## 2. Command contract và owner

| CMD | Cwd / lệnh | Owner / trạng thái hiện tại |
|---|---|---|
| CMD-01 FE | Repo root: `pwsh -File scripts/project.ps1 -Task content`, `-Task check`, `-Task build`, `-Task smoke` | Có helper sau R00; smoke theo browser/runtime thực; chưa chạy lại trong MS04 docs |
| CMD-02 UNIT | Root: `pwsh -File scripts/services.ps1 -Task verify -Service identity`; backend direct `./mvnw.cmd -pl services/identity-service -am clean verify` (đổi đúng service); Linux `./mvnw` | R01b Identity26tests/packaged JAR bootstrap đạt với Windows socket-temp override theo [evidence](../platform/bootstrap/identity-verification.md); chưa auth/DB/mTLS. R01c Content28tests/packaged JAR đạt với cùng override theo [Content evidence](../platform/bootstrap/content-verification.md); R01d Learning42tests/packaged JAR đạt theo [Learning evidence](../platform/bootstrap/learning-verification.md). Ba app selected verify riêng; không full-stack/business suite từ bootstrap |
| CMD-03 DB | Root: `pwsh -File scripts/services.ps1 -Task db -Service identity` (content/learning/all) | R02a isolated PostgreSQL18.6/27DB IT mỗi service; R03 Identity thêm22workflow/4packaged IT và35unit-bootstrap; [DB evidence](../platform/database/verification.md)/[credential evidence](../features/f10-identity-access/credentials/verification.md), strict XML/stop |
| CMD-04 CONTRACT | `pwsh -File scripts/services.ps1 -Task contract -Service all` hoặc targeted catalog/receiver suite | R02b packaged receiver/common-wire + strict reports; [evidence](../platform/service-trust/verification.md). R05 standalone catalog dùng `-Service content -EvidenceTask R05`; [schema/command](../../contracts/catalog/README.md), [evidence](../features/f08-content/tasks/BL-R05.md). R31c compatibility/strict full registry; catalog PASS không HTTP/business PASS |
| CMD-05 FAULT | `pwsh -File scripts/services.ps1 -Task fault -Service identity -Case SEC-12` hoặc SEC-22 cho R03; receiver SEC-14/15/20 giữ R02b; missing/unknown/nonimplemented case fail | R03 credential DB/packaged CLI layer và R02b TLS/client layer. Session/admission/edge/data faults còn owners sau, không full registry |
| CMD-06 JOURNAL | `pwsh -File scripts/services.ps1 -Task journal -Profile lab` hoặc `-Profile qualification` | R28a/b tạo command; lab không được ghi production-qualified PASS |
| CMD-07 E2E | `pwsh -File scripts/services.ps1 -Task e2e -Profile lab` | R31a tạo services/edge/TLS base start/readiness/teardown harness trước R14/R24a; R24a–c thêm auth fixtures, UI owners thêm flows; FE smoke cũ không thay E2E này |
| CMD-08 SCAN | `pwsh -File scripts/services.ps1 -Task scan -Profile release` | R31c pin Trivy/Gitleaks/SBOM tool version/digest, policy/advisory date; chưa cài/chạy |
| CMD-09 BACKUP | `pwsh -File scripts/services.ps1 -Task backup -Profile lab` | R32a tạo quiesce/drain/dump/manifest/verify/resume, prod scheduler runbook riêng |
| CMD-10 RESTORE | `pwsh -File scripts/services.ps1 -Task restore -Profile drill -BackupSet fixture-before-exposure` | R32b tạo isolated target + current journal/ref/realm/credential/readiness drill; không live overwrite |
| CMD-11 ALL | `pwsh -File scripts/services.ps1 -Task all -Profile lab` | R31c compose verified layers, strict suite registry; production qualification/recovery report riêng |

R01a tạo `scripts/services.ps1` cho toolchain/parent validate và selected verify contract; [evidence R01a](../platform/toolchain/verification.md) là foundation/helper. R01b/c/d có CMD-02 Identity26tests/Content28tests/Learning42tests bootstrap thật, không auth/DB/mTLS/business suite. CMD-03 đã có DB/fixture lifecycle R02a (81 real DB IT, không SEC/DATA nghiệp vụ PASS). CMD-04/05 receiver/common-wire layer R02b đã có; CMD-06–11 và chưa-implemented canonical business/security/data cases bị helper từ chối nonzero; R05 standalone catalog validator/contract branch không cần auth harness; R02b mở rộng HTTP/trust, owners mở rộng layers, R31c nối local/CI cùng commands. CI Linux có shell entry tương đương R31c tạo và parity test; không giả pwsh luôn có trên runner. Missing suite/unknown case/0 tests/required skipped trả nonzero. Không dùng `-DskipTests` để nghiệm thu, không in PASS cho layer chưa tạo. CMD-01 theo runtime/browser/helper có thật, không đổi FE lockfile.

Các số R01/R02a ở trên là checkpoints lịch sử. R03 qualified checkpoint Identity CMD02 có35tests (26bootstrap +8policy +1benchmark), CMD03all107IT/105unit-bootstrap, targeted SEC12=44 và SEC22=40 credential/DB/packaged IT, CMD04Identity75transport/wire IT; [R03 evidence](../features/f10-identity-access/credentials/verification.md). Full SEC12session race/SEC22browser abuse còn R04a/d; không cộng các run lặp thành coverage mới hoặc thay actual DB bằng unit tests.

Mỗi report đích `artifacts/quality/<runId>/` có commit/snapshot manifest (repo hiện untracked thì hash input), command/exit/runtime/versions/profile/env, case ID/fixture digest/expected/actual/status/log refs và schema. Logs redact password/cookie/CSRF/proof/answer/private expected; API oracle đọc DB lab bằng test role riêng, không runtime role cross-DB. Test skip ghi lý do, không đủ release nếu bắt buộc. CI retention30 ngày mặc định, failure/pilot signoff evidence giữ qua release review; không commit reports/secrets.

## 3. SEC case owners

Mỗi dòng là expected từ MS02, chưa chạy. Owner là leaf tạo/duy trì test; R33 audit, không chờ R33 mới implement. R04d kiểm receiver gate với local mutation fixture trước có T1; R11/R13b phải thêm actual submission/evidence assertions. Registry giữ từng layer, không dùng gate fixture để claim full T1/T2 pass.

| Case | Owner leaf | Fixture | Command / evidence |
|---|---|---|---|
| SEC-01 | BL-R04d,BL-R11 | FX-02/03 | CMD-05, auth fence→late T1 rollback DB |
| SEC-02 | BL-R04b,BL-R04c | FX-01/02/03 | CMD-03/05, absent session tombstone |
| SEC-03 | BL-R04d,BL-R11 | FX-02 | CMD-05, T1 commit order vs all ACK |
| SEC-04 | BL-R04b,BL-R04c | FX-02/03 | CMD-05, two replicas same DB gate |
| SEC-05 | BL-R04a,BL-R04d | FX-02/03 | CMD-05, late flush/admission not resurrect |
| SEC-06 | BL-R04d,BL-R24c | FX-04/09 | CMD-05/07, 202pending/UI locked |
| SEC-07 | BL-R04d | FX-04 | CMD-05, crash/ACK durable retry |
| SEC-08 | BL-R04d | FX-03/04 | CMD-04/05, wrong ACK rejected |
| SEC-09 | BL-R04b,BL-R04c | FX-02/04 | CMD-03/05, digest/epoch conflict |
| SEC-10 | BL-R04a,BL-R04d | FX-03/09 | CMD-05/07, cutoff/drift/poll no idle |
| SEC-11 | BL-R24c,BL-R04a | FX-09 | CMD-07 TLS cookie A/B reorder |
| SEC-12 | BL-R03,BL-R04d | FX-01/02/03 | CMD-03/05 KDF/token/reset/disable race |
| SEC-13 | BL-R32b | FX-08 | CMD-10 old realm/fence readiness deny |
| SEC-14 | BL-R02b,BL-R31a | FX-03/10 | CMD-04/05 receiver public-edge/private-service cert deny + actual edge route/header/CA/SAN; R02b partial layer không full edge PASS |
| SEC-15 | BL-R04b,BL-R04c | FX-03 | CMD-04/05 binding/caller/action deny |
| SEC-16 | BL-R06b,BL-R10 | FX-05/06 | CMD-04/05 bundle scope/no bypass |
| SEC-17 | BL-R09,BL-R11 | FX-01/02 | CMD-03/04 A/B root/con/field reject |
| SEC-18 | BL-R25,BL-R24a | FX-01/09 | CMD-04/07 receipt/export/cache no leak |
| SEC-19 | BL-R04d,BL-R24b | FX-03/09 | CMD-04/07 synchronizer/Origin/login CSRF |
| SEC-20 | BL-R02b,BL-R04d | FX-03/09 | CMD-04/05 CORS/cookie not service auth |
| SEC-21 | BL-R04a,BL-R24a,BL-R24c | FX-02/09 | CMD-03/07 preauth single consume/tab context |
| SEC-22 | BL-R03,BL-R04a | FX-01/03 | CMD-04/05 generic/throttle/KDF failure |
| SEC-23 | BL-R04d,BL-R29a | FX-02/03/06 | CMD-05 reauth purpose/intent/idempotency |
| SEC-24 | BL-R31b,BL-R03 | FX-03/04 | CMD-05 bounded auth/queue/poll load + measurements |
| SEC-25 | BL-R07,BL-R19 | FX-09/10 | CMD-04/07 escaped content/input/URLs |
| SEC-26 | BL-R02a,BL-R09 | FX-01/02 | CMD-03 DB role/SQL/sort allowlist |
| SEC-27 | BL-R26,BL-R27 | FX-01/06/07 | CMD-03/04 limits/no fake pass/no exec |
| SEC-28 | BL-R30,BL-R31a | FX-09/10 | CMD-04/07/08 payload/artifact/cache/headers + proxy request/response temp/log sentinel chunked/slow/near-limit/export/error |
| SEC-29 | BL-R31a,BL-R32b | FX-03/08/10 | CMD-05/10 rotation/connections/storage roles |
| SEC-30 | BL-R31c | FX-10 | CMD-08 scans/SBOM/unsafe fixture caught |

## 4. DATA case owners

| Case | Owner leaf | Fixture | Command / evidence |
|---|---|---|---|
| DATA-01 | BL-R06a,BL-R06b | FX-05 | CMD-03/04 invalid candidate no canonical commit |
| DATA-02 | BL-R06c,BL-R13b | FX-02/05/07 | CMD-05 pub gate vs T1/T2/snapshot |
| DATA-03 | BL-R06c | FX-04/05 | CMD-05 participant down before FENCE ACK |
| DATA-04 | BL-R06c | FX-04/05 | CMD-05 commit/release/ACK crash points |
| DATA-05 | BL-R06c | FX-04/05 | CMD-05 abort/stale phase cannot open newer fence |
| DATA-06 | BL-R13b | FX-02/05/07 | CMD-05 withdraw/invalid rubric midcompute |
| DATA-07 | BL-R22a,BL-R06c | FX-05/07 | CMD-03/05 shared CAP/current overlay |
| DATA-08 | BL-R13a,BL-R13b | FX-02/07 | CMD-05 partial T2/two leases/fencing |
| DATA-09 | BL-R14,BL-R22b | FX-05/09 | CMD-07 stale response/revision discard |
| DATA-10 | BL-R02c | FX-02/04 | CMD-03/05 outbox/inbox atomic crash |
| DATA-11 | BL-R02c | FX-04 | CMD-05 duplicate/different digest one effect |
| DATA-12 | BL-R02c | FX-04 | CMD-05 gap/compacted sequence/resync proof |
| DATA-13 | BL-R02c,BL-R02b | FX-03/04 | CMD-05 poison/size/cert/backoff no leak |
| DATA-14 | BL-R06c,BL-R29b | FX-03/04 | CMD-05 ACK wrong realm/generation/digest |
| DATA-15 | BL-R04d,BL-R02c | FX-04/05 | CMD-05 auth stream not behind pub poison |
| DATA-16 | BL-R29a | FX-02/06 | CMD-05/06 reset journal failure/crash |
| DATA-17 | BL-R29a,BL-R13b | FX-02/06/07 | CMD-05 reset/T2/autosave/import/key retry new work |
| DATA-18 | BL-R29a,BL-R17 | FX-06/09 | CMD-03/07 learning reset keeps exposure/model reset separate |
| DATA-19 | BL-R32b | FX-06/08 | CMD-10 durable intent before DB final |
| DATA-20 | BL-R29b | FX-01/04/06 | CMD-05 deletion ACK/down pending no fake success |
| DATA-21 | BL-R29b | FX-01/04/08 | CMD-05 absent user/old events/import terminal floor |
| DATA-22 | BL-R08,BL-R10,BL-R27 | FX-02/03/06 | CMD-05/06 no prompt/support/import before durable floor |
| DATA-23 | BL-R32b | FX-06/08 | CMD-10 snapshot before disclosure DB record |
| DATA-24 | BL-R32a,BL-R32b | FX-05/08 | CMD-09/10 checkpoint interlock crash/restart/late writer/stale resume/no partial complete + mixed heads/cursors/offline reconcile |
| DATA-25 | BL-R28b,BL-R32b | FX-06/08 | CMD-06 qualification/CMD-10 missing prefix/key/readiness deny |
| DATA-26 | BL-R32b | FX-01/05/08 | CMD-10 unknown owner/version quarantine, no name mapping |
| DATA-27 | BL-R32b,BL-R03 | FX-03/07/08 | CMD-10 old auth/credential/ACK/lease denied |
| DATA-28 | BL-R29c,BL-R32b | FX-06/08 | CMD-06/10 retention/deletion dominance/compaction floor |
| DATA-29 | BL-R13b,BL-R32b | FX-07/08 | CMD-03/10 projection/jobs rebuilt only trusted surviving input |
| DATA-30 | BL-R31d,BL-R32a,BL-R32b | FX-03/08/10 | CMD-09/10 checkpoint/resume/recovery readiness denied early; measured pause/RPO/RTO/backup age |

### 4.1. Common wire fixtures — bổ sung D14

Expected theo catalog §4.1, chưa runtime. R02b tạo schemas/examples, từng endpoint owner thêm enforcement/provider-consumer assertions khi API có thật; R31c registry/R33 audit. Schema validator PASS không thay endpoint/FE PASS.

| Case | Owner leaf | Fixture | Command / evidence |
|---|---|---|---|
| WIRE-01 | BL-R02b,BL-R09,BL-R24a | FX-10/09 | CMD-04/07 bigint decimal string0/2^53±1/LongMax exact round-trip, invalid/overflow reject, không Number coercion |
| WIRE-02 | BL-R02b,BL-R07,BL-R09 | FX-10 | CMD-04 duplicate/escaped JSON key, bad UTF-8/unpaired surrogate, privileged unknown field, ambiguous route/query rejected |
| WIRE-03 | BL-R02b,BL-R04a,BL-R24a,BL-R31a | FX-03/09/10 | CMD-04/07 error/status/trace redaction, pending!=completed, API503/413 không SPA HTML/overwrite |
| WIRE-04 | BL-R09,BL-R11,BL-R24a | FX-01/02/09 | CMD-03/04/07 key same intent receipt, changed intent409, new owner/context không implicit replay |
| WIRE-05 | BL-R21a,BL-R21b | FX-01/02/09 | CMD-03/04/07 cursor tamper/expiry/owner/epoch/filter/asOf/paging refresh, reset hides stale rows |
| WIRE-06 | BL-R02b,BL-R24a,BL-R31c | FX-10/09 | CMD-04/07 N/N-1 additive response, unsupported state/major/request subset deny, no silent pass/fallback |

## 5. Business acceptance và trace

| Case registry / source | Owner leaf | Fixtures/commands | REQ |
|---|---|---|---|
| IA-01–24 [identity](../features/f10-identity-access/business.md) | R03/R04a–d/R24/R29b/R32b | FX-01/02/03/09, CMD-03/04/05/07/10 | 09/14/15 |
| LD-01–08 [server data](../features/f11-learner-work/data-contract.md) | R08/R09/R10/R11/R20 | FX-01/02/06, CMD-03/04/05/07 | 05/09/15/16 |
| LD-09–20 | R06a–c/R13a/b/R21a/R22a | FX-02/05/07, CMD-03/05 | 05/06/10/16 |
| LD-21–30 | R25/R26/R27/R29a–c/R32a/b | FX-06/08/09, CMD-03/06/07/10 | 09/15/16 |
| AP-01–22 [assessment/progress](../features/f04-assessment/evidence-contract.md) | R08/R10/R12/R13b/R22a/R23a/R27 | FX-05/06/07, CMD-02/03/04/05 | 05/06/07/08/12 |
| CA-01–14 [Collections](../features/f04-assessment/collections/cases.md) | R12/R18/R19/R20/R23a/b | FX-07/09, CMD-02/03/07 | 04/05/06/08/12 |
| CM-01–25 [model](../features/f02-learning-experience/collections/knowledge.md) | R17 | FX-07/09, CMD-01/07 + reducer tests | 03/04/12 |
| UX-01–20 [experience](../features/f02-learning-experience/collections/experience.md) | R14/15/16a/18/19/20/21b/22b/23b/24/25–27/29a | FX-09, CMD-07 + visual/keyboard/320px/screen-reader review | 02–09/11/14–16 |
| Catalog/coverage/publish | R05/R06a/R07/R14/R30 | FX-05/10, CMD-04/08;95 TOP/190 CAP/DS/DP/shared/planned validation | 01/02/10/18 |
| Learner feedback | R34 | Thử thực tế trọn Collections/login/resume/error, report actor/date/consent; không automated feedback | 13 và toàn phạm vi R1 |

R31c tạo machine-readable coverage registry từ từng ID nguồn + fixture + test path/command/owner/report, range phải expand từng case. Multi-owner dòng là nhóm duy trì, không đủ pass chỉ một implementation; registry chọn test chính và secondary layers cụ thể. R33 kiểm từng ID không missing/duplicate conflicting expectation/required skip, REQ-17 excluded sau R1. Registry không là số test để đo chất lượng; asserts state/ownership/evidence đúng là điều cần chứng minh.

## 6. CI và compatibility

R31c tạo CI provider file khi biết repo hosting, không tự push/create PR/activate CI từ doc. Local commands cùng implementation runner; mỗi service unit/DB/contract và FE lockfile build job riêng. Always dependency audit/source-boundary/caller matrix validation, không Java import service khác, DB role negative trên cả ba DB. Parent Maven chỉ build conventions; `-pl … -am` không buộc đóng gói apps còn lại.

| Change | Gates bắt buộc |
|---|---|
| FE UI | CMD-01, reducer/adapter nếu đổi logic; CMD-07 affected flows/keyboard/responsive/error và visual review |
| One service | CMD-02/03 selected service, CMD-04 provider + every affected consumer (N/N-1/WIRE), CMD-05 affected SEC/DATA, dependency/image scan |
| Shared HTTP/event/schema/config | Contract N/N-1 across three apps, negative versions/private payload, edge/mTLS/fault + affected FE E2E; no simultaneous deployment assumed |
| Content/rubric/case | Catalog/coverage/technical source review, model/grader expected, impact/publication fault/artifact privacy; schema compatible không có nghĩa evidence retain |
| Journal/restore/migration | DB/storage durable boundary faults, actual qualification, CMD-09/10, owner/epoch/realm/reference checks; no unit-only pass |
| Pilot release | All applicable REL gates + full lab suite + provider/recovery/audit/learner reports, P0/P1 closed |

Compatibility fixture release N with provider N/consumer N-1 và ngược lại khi policy hỗ trợ, migration expand trước app/contract switch, retire field sau confirmed consumers. Breaking API/event/schema không ép đồng thời deploy để “pass”; version transition có task/signed inventory. Image rollback chỉ compatible schema/contract, không rollback journal/deletion/exposure. R31c canary/smoke dùng A/B lab, không private data/log unbounded.

## 7. Release DoD và review gates

| Gate | Evidence phải có / owner |
|---|---|
| REL-01 Reproducible | Three service artifacts/images + FE build, versions/digests/SBOM/checksums, source boundaries, selected-service builds — R01a–d/R31c |
| REL-02 Compatibility | Provider/consumer N/N-1/WIRE registry, counter/error/command/cursor semantics, DB role/migration preflight, no cross-DB access/source import; rollout/rollback order — R02a/b/R31c |
| REL-03 Security | SEC registry covered, auth barrier/cookie reorder/expiry/mTLS/service trust/CSRF/A-B/rate limits; advisory/secret/image scan policy — R04d/R24/R31c/R33 |
| REL-04 Data integrity | DATA registry covered with DB/fault/store reports, T1/T2/withdraw/current overlay, reset/delete/exposure/receipts/retention — case owners/R33 |
| REL-05 Content privacy | Collections coverage/source/rubric/runtime review, only final ready bundle publish; FE/API/sourcemap/cache/log no private expected; two roadmaps outline — R30 |
| REL-06 Usability | Full login→learn/practice→assessment/result/history/progress/apply/transfer/reset flow; Back/expiry/offline/errors/keyboard/320px/themes/reduced motion/screen reader — UI owners/R33 |
| REL-07 Recovery/ops | Actual provider qualification, daily encrypted off-host checkpoint schedule + durable interlock/crash/verified resume, isolated restore+journal/refs/realm/credential recovery, measured pause/RPO/RTO and key/operator runbook, pending/clock/cert/backup alerts — R28b/R31b/d/R32a/b |
| REL-08 Learner acceptance | Real learner report, requirement scope checked, no open P0/P1; P2/P3 owner and decision; required reports not skipped — R34 |

R31d có thể deploy pilot **giới hạn thử nghiệm** khi REL-01–07 có evidence và explicit deployment scope; R33/34 audit/learner sau pilot, không gọi R1 accepted trước REL-08. External publish/deploy authorization là bước riêng với bundle reviewable; CODE không mua cloud/publish. Operator không mở traffic recovery chỉ vì process healthy: gates MS03 + per-account RECOVERY_REQUIRED vẫn cần, missing journal/reference/clock giữ deny.

SCA policy: dependency/image findings reachable critical/high và secret/private leak chặn release; false positive hoặc mitigated finding có evidence/owner/expiry, không miễn true P0/P1 để qua gate. Performance chưa SLA: R31b đo KDF/admission/DB tx/gate pause/queue/journal/backup trên host ghi resources/concurrency, điều chỉnh budget có regression evidence. Không lấy một response nhanh hoặc FE build PASS làm distributed availability.

Review từng leaf: AC → test report → state cuối → negative boundary → ngoài scope → source/docs/checkpoint. Review độc lập với cách viết code có thể tự rà theo oracle/invariant sau test; không tự tạo agent/chat. R33 audit toàn trace; task acceptance bắt buộc vẫn được kiểm ngay nơi implement, không dồn correctness tới release.

R04b Content receiver: CMD03 bắt buộc isolation+`identityaccess.AdmissionGateIT`; CMD04 bắt buộc transport/common-wire+`identityaccess.AdmissionRuntimeIT`. Unit `AdmissionWireTests` và critical named cases được helper kiểm riêng. CMD05 SEC02/09 chọn actual runtime cases, SEC04 chạy DB gate cùng isolation prerequisites, SEC15 bắt buộc transport + admission receiver negatives. Scope/evidence tại [task R04b](../features/f10-identity-access/sessions/tasks/BL-R04b.md); không suy canonical INT01/full revoke từ controlled provider fixture, không owner/domain API trước R07, chưa N/N-1 pilot/recovery hoặc Linux parity.

R04c Learning receiver: cùng required isolation/gate/runtime/wire suites, riêng mutation binding Origin/CSRF/action/body/context và callback SQL/use atomic tới commit. SEC03/04 chạy full isolation+gate với mutation-holds-gate và committed-fence-first concurrent deny; SEC01/02/05/09 runtime tags, SEC15 transport+receiver strict negatives/upload timeout. Helper bắt named cases và unit `AdmissionWireTests`; report doubles không là actual PG/mTLS proof. SEC01/03/05 chỉ qualified local Java mutation hook↔local durable fence ở leaf này: **không** T1/global ACK/late framework flush/full canonical provider hoặc API-L03. Registry SEC owners phía trên giữ full-business owners R04d/R11; [task/DoD R04c](../features/f10-identity-access/sessions/tasks/BL-R04c.md) giữ exact layer evidence/limits.

R04a04/10 local leaf: CMD-03 Identity bắt buộc `IdentitySessionAuthorityIT` cùng isolation/credential/packaged CLI; CMD02/03/04/05 Identity cũng phải có unit `SessionHttpHandlerTests`, missing/zero/skip fail. Full CMD04 Identity còn bắt buộc `SessionRuntimeIT`/critical case names và actual browser report; không dùng nonzero transport/wire count thay auth suite. Profile `session` explicit chạy packaged own-lab auth TLS/config/Origin/body/cookie/restart; `bootstrap`/`transport` không tự mở auth. Browser fixture dùng existing locked dev dependencies + installed Edge, actual cookie application/context/Origin/multitab và held original responses; không production FE/private cache/edge R31a. [Evidence/DoD](../features/f10-identity-access/sessions/verification.md) giữ exact runs/hashes/cleanup/limits và failures. SEC-05/10/11/21/22 chỉ qualified phần local R04a có report; receiver/admission/full reauth/FE thuộc owners sau, các selectors chưa implement vẫn reject. Artifact/dependency candidate ghi riêng với trạng thái task thật, R03 qualified historical fields không bị ghi đè.
