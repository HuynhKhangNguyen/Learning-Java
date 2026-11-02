# BL-R07 — Content browser API

[Registry](../../../delivery-backlog.md) · [Content LLD](../lld.md) ·
[Service boundaries](../../../architecture/service-boundaries.md) ·
[Security](../../../architecture/security-design.md) ·
[Publication protocol](../publication/protocol.md) ·
[Quality process](../../../quality-process.md) ·
[DoD](../../../delivery/contract.md)

## Goal, trạng thái và quyền

**Goal:** Content phục vụ ba public API-C01–03 từ snapshot đã published trong
Content DB: catalog và topic outline cho anonymous; unit body cho phiên đăng nhập
qua canonical admission/fence. Response phải gắn revision/as-of, phân biệt
`learning_sync_pending` và không làm lộ private expected/case/rubric/hint/solution.

Đầu ra chính là public Content browser contract, Content-owned read model/runtime
và packaged HTTPS evidence cho `GET /api/v1/catalog`,
`GET /api/v1/topics/{topicId}` và
`GET /api/v1/topics/{topicId}/units/{unitId}`. R1/MD01,
REQ-01/02/10/15; SEC-16/25/28; API-C01–03.

**Trạng thái 02/11/2026: `DONE`.** BL-R04d và BL-R06c đã `DONE`; parent
BL-R06 được Control Center roll up sau khi R06a/b/c đều `DONE`. Implementation
session riêng đã bắt đầu trên branch `codex/bl-r07-content-browser-api` với quyền
CODE và tạo branch cho đúng leaf. Ngày 03/11/2026 người dùng cấp quyền stage và
commit task đã nghiệm thu; chưa cấp quyền push, merge, PR hoặc deploy.

## Dependency và baseline hiện hành

- BL-R04d cung cấp canonical Identity admission, Content `ContentAccess` và
  durable local fence đã qualified.
- BL-R06c cung cấp canonical publication head, Learning-ready revision và
  coordinator đã qualified. Không sửa lại V1–V5 hoặc contract publication.
- Git baseline `main` hiện có và clean; task code đáng kể nên dùng branch
  `codex/bl-r07-content-browser-api` khi được cấp quyền Git riêng.
- `content-v2/catalog/catalog.json` là authoring source, không phải runtime
  published source. R07 không đọc file này trực tiếp để trả browser response.
- R06 fixture private bundle chỉ là publication/security oracle; không được dùng
  làm public catalog hoặc unit body.

## Execution role, mode và ownership

- Role: Backend/Content executor; Control Center là integrator/reviewer.
- Mode: Mode B, một implementation chat và một writer. Task không đủ lớn để
  delegation; không dùng subagent cho cùng source.
- Executor chuyển task sang `IN_PROGRESS` khi session CODE thật bắt đầu, cập nhật
  checkpoint và bàn giao tối đa `REVIEW`. Control Center sở hữu final `DONE`.
- Branch/stage/commit là quyền riêng theo Git workflow; quyền sửa code không tự
  cấp các quyền đó. Không push/PR/merge/deploy nếu chưa được yêu cầu rõ.

## Context cần đọc

1. `AGENTS.md`, dòng BL-R07 trong registry, checkpoint mới nhất của file này,
   DONE-01–05 và đúng mục kiểm tra trong quality process.
2. Content LLD mục “Publish và API”; service-boundaries API-C01–03 và wire
   conventions; security caller/control/test matrix SEC-16/25/28.
3. Publication protocol các mục canonical/readiness và public outline/body;
   R04d session protocol cho protected GET admission.
4. Chỉ các symbol hiện hành liên quan: `ContentAccess`, `AdmissionWire.Request`,
   `AdmissionGate`, Content transport filter/configuration, publication
   canonical/readiness tables và current Content POM/profile.
5. Public contract/content fixture được task tạo. Chỉ đọc authoring catalog để
   đối chiếu shape/ID khi cần; không biến authoring file thành runtime store.

Không đọc lại toàn bộ repository hoặc checkpoint lịch sử R06 khi các owner và
current source trên đã đủ trả lời.

## Write scope

Được sửa:

- `backend/services/content-service/src/main/java/lab/backend/content/browser/**`
  và test tương ứng;
- forward Content migration V6 riêng cho immutable browser read snapshot nếu
  implementation cần DB representation; không sửa V1–V5;
- Content transport/configuration/profile/POM tối thiểu để đăng ký API-C01–03;
- `contracts/http/content/public/**` và fixture browser mới, không rewrite
  private publication fixture đã qualified;
- `ops/content/**`, Content README, explicit R07 selector/report/review helper
  trong `scripts/**` và field release-manifest riêng nếu thật sự cần evidence;
- file task này và dòng BL-R07 trong registry.

Được đọc nhưng mặc định không sửa: R04d/R06 source và evidence,
`content-v2/catalog/**`, Identity/Learning implementation, common wire contract
và existing release manifests. Nếu dependency contract thật sự thiếu hoặc sai,
ghi finding và dừng phần bị chặn; không âm thầm sửa leaf đã `DONE`.

Ngoài phạm vi: FE/journey/player/progress/readiness, Learning case/support/exposure,
Identity session authority, content authoring/CMS/admin/publish UI, populate catalog
production R30, private INT-03/04, sửa publication V1–V5, framework/dependency mới,
edge production R31a, remote/push/PR/merge/deploy và task kế tiếp.

## Invariants bắt buộc

1. Runtime chỉ đọc snapshot immutable gắn canonical revision trong Content DB.
   Missing/corrupt/digest mismatch hoặc snapshot không khớp head phải fail closed;
   không fallback sang authoring file, fixture hoặc revision cũ không được chỉ ra.
2. Một response lấy `canonicalRevision`, `appliedRevision`/Learning-ready,
   `learningSyncPending` và `asOf` từ một DB snapshot nhất quán. Bigint revision
   đi wire dưới dạng decimal string; timestamp UTC millisecond.
3. API-C01/02 cho anonymous qua verified public edge caller và chỉ trả public
   metadata/outline. Planned topic hiện trạng thái rõ, không trả CTA/body giả.
4. API-C03 yêu cầu opaque session cookie + exact `X-Session-Context`, tạo
   `AdmissionWire.Request` từ route đã validate, gọi fresh `ContentAccess.read`
   rồi đọc DB dưới local gate. Không cache ALLOW; Identity unavailable/fenced/
   context mismatch phải deny trước khi trả body.
5. GET không dùng browser `owner`, `role`, `userId`, `passed`, admission header
   hoặc CSRF value làm quyền. Safe GET không biến CSRF header thành requirement;
   forged/missing CSRF không thể bypass canonical session/context admission.
6. Public DTO là allowlist riêng; không serialize internal/private bundle rồi
   “lọc sau”. Không response/log/cache/error private expected, case, rubric,
   hint, solution, credential, admission hoặc principal.
7. API-C03 luôn `Cache-Control: private, no-store`, `nosniff`, không `Set-Cookie`.
   API-C01/02 chỉ cache theo publication revision/validator rõ; không dùng key có
   thể trộn authenticated body với anonymous outline.
8. Content text/block payload không chứa raw executable HTML/script hoặc unsafe
   URL scheme. Unknown schema/state/major version fail closed; error envelope
   bounded, không SQL/stack/private field.
9. Route/method/query/encoding/header/content-type ambiguity bị reject theo common
   wire convention. 404 không tiết lộ private-resource existence.
10. Test seed published snapshot trong DB test riêng không biến production draft
    hoặc authoring catalog thành published; R30 vẫn sở hữu actual content publish.

## Acceptance criteria

| AC | Điều kiện quan sát được | Evidence bắt buộc |
|---|---|---|
| R07-AC01 — Published snapshot | Forward representation/read path giữ immutable snapshot theo revision và đọc atomically với canonical/readiness head; missing/corrupt/mismatch/old head fail closed; app role least privilege, fresh/rerun/rollback đúng | Actual PostgreSQL CMD-03, constraints/role negatives, two-connection head/read race và V1–V5 hashes không đổi |
| R07-AC02 — API-C01 catalog | Anonymous GET trả hai roadmap, placement, stable TOP/CAP IDs, publication status và planned labels đúng public contract; method/query/path/header negatives không trả body riêng | Provider contract/schema/example tests + packaged HTTPS public-edge runtime CMD-04 |
| R07-AC03 — API-C02 topic outline | Anonymous GET trả đúng topic/CAP/prerequisite/unit outline/status ở canonical snapshot; alias chỉ resolve khi duy nhất; planned/not-found rõ, không body activity/private field | Positive/negative contract + DB/runtime cases, shared ID/alias/prerequisite oracles |
| R07-AC04 — API-C03 protected body | Published unit body chỉ trả sau fresh canonical admission và local fence check; cookie/context/action/route binding đúng, Identity unavailable/revoked/fenced/stale response deny; response no-store/no Set-Cookie | Actual Identity→Content mTLS + own PostgreSQL CMD-04/05, restart/concurrency and lost/late admission cases |
| R07-AC05 — DTO privacy và render safety | Ba DTO dùng exact allowlist/schema; không expected/case/rubric/hint/solution/secret/admission/principal trong response, cache, error, log hoặc artifact; HTML/script/unsafe URL/unknown fields bị reject | SEC-16/25/28 corpus, sentinel scan trên payload/JAR/report/log/cache và schema negatives |
| R07-AC06 — Revision/cache/error semantics | Response có decimal canonical/applied revision, DB `asOf`, `learningSyncPending`; canonical-ahead/readiness-pending minh bạch. C01/02 cache revision-bound; C03 private/no-store. DB/Identity timeout hoặc malformed snapshot giữ dữ liệu tốt và trả bounded error | Fault/race tests CMD-05, header assertions, old/new snapshot ordering và no stale-success oracle |
| R07-AC07 — Packaged evidence và regressions | Explicit R07 selectors fail closed cho missing/zero/skip/unexpected suite; packaged artifact chỉ mở browser profile được cấu hình, giữ R04d/R06/transport/bootstrap behavior và không chứa authoring/private fixture ngoài test scope | Strict reports/current input hashes, artifact/dependency/secret/privacy/cleanup review, affected regression commands |

## Validation contract

Các command dưới là interface đích. Nếu explicit R07 selector chưa tồn tại, việc
tạo selector/report gate là implementation scope; không được chạy generic suite
rồi gắn nhãn R07:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service content -EvidenceTask R07 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R07 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -EvidenceTask R07 -Case SEC-16 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -EvidenceTask R07 -Case SEC-25 -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task fault -Service content -EvidenceTask R07 -Case SEC-28 -Offline
```

| Gate | Nội dung tối thiểu |
|---|---|
| CMD-03 Content/R07 | V6/read representation fresh/rerun/rollback/least privilege, canonical snapshot consistency, head/read race |
| CMD-04 API-C01–03 | Schema/provider-consumer + packaged HTTPS public edge; anon outline và protected body canonical admission |
| CMD-05 SEC-16/25/28 | Private bypass, malicious content/artifact leak, cache/error/log sentinel, Identity/fence/DB faults |
| Affected regression | R04d canonical admission/fence, R06 canonical/readiness, Content bootstrap/transport/publication strict suites theo input impact |
| Final review | Source/contract/package/dependency/secret/private sentinel, migration hashes, process/listener/cluster cleanup, docs/status/links và no P0/P1 |

Full backend/FE/browser journey không tự bắt buộc nếu không bị ảnh hưởng; missing,
zero-test, skipped hoặc mocked-only evidence không PASS cho lớp runtime tương ứng.

## Definition of Done

Đạt R07-AC01–07 và DONE-01–05; contracts/provider/consumer và packaged behavior
khớp; validation gắn current source/fixture/artifact; không P0/P1; task checkpoint
ghi exact command/result/limit. Executor chỉ chuyển tối đa `REVIEW`; Control Center
review source/security/data ownership/artifacts trước `DONE`. Không tự mở R08/R14/
R24a hoặc task kế tiếp.

## Evidence hiện hành — 03/11/2026

| Phạm vi | Kết quả và evidence |
|---|---|
| CMD-03 Content/R07 | `PASS`, PostgreSQL 18.6, bootstrap 137 test và DB 88 test, không skip/failure/error; exact publication component binding, temporal/canonical guards, app-role negatives, abort/retry và two-connection MVCC race: `artifacts/quality/R07/79e69163-afa2-4c92-b80f-f66ece2e7162/report.json` |
| CMD-04 API-C01–03 | `PASS`, 11 browser unit + 4 packaged HTTPS runtime case; actual Identity peer artifact và 329 Identity unit test được giữ; catalog TOP/CAP/planned metadata và raw request negative nằm trong current selector: `artifacts/quality/R07/688cad29-a171-4e20-b8cb-5104dea2f794/report.json` |
| CMD-05 SEC-16 | `PASS`, exact actual canonical Identity restart/logout-fence race + controlled Identity-unavailable case, 11 browser unit, 2 runtime case và qualified Identity peer 329 test: `artifacts/quality/R07/90318c3e-ddaa-4953-b457-e1be6961dcf7/report.json` |
| CMD-05 SEC-25 | `PASS`, exact one packaged runtime case + 11 browser unit test, gồm raw TLS `Transfer-Encoding: chunked` GET bị reject: `artifacts/quality/R07/e0f2287c-a385-4f05-95db-2de8a4561407/report.json` |
| CMD-05 SEC-28 | `PASS`, exact one packaged runtime case + 11 browser unit test: `artifacts/quality/R07/ac749099-1b58-4a27-9e1c-923e87ebc19a/report.json` |
| Affected R04d | Canonical three-service SEC-20 `PASS`, 153 Learning bootstrap + 3 canonical runtime variants; Identity 329 và Content 137 test peer đều đạt: `artifacts/quality/R04d/dbd5f192-e7b6-4938-99d9-064fe7112836/report.json` |
| Affected R06c | Publication coordinator CMD-04 `PASS`, 2 Content unit + 1 packaged runtime case và Learning peer 153 test: `artifacts/quality/R06c/19cf8122-36d9-497f-8996-bc1246949d93/report.json` |
| Artifact/final review | `PASS`; current JAR SHA-256 `afb89a7c613b1d06ec0dec8b7258f2b67297efee6e8cbaa864ed3808a99b525a`, packaged V1–V6 bytes khớp source, V1–V5 không có Git diff từ `HEAD`, không có authoring/private/test path hoặc sentinel: `artifacts/quality/R07/final-browser-review.json` |

Mọi owned cluster/listener của các run trên đều đóng thành công. Chỉ các evidence
`PASS` trong bảng được dùng để qualification; các diagnostic/transient run được
giữ riêng và không cộng vào kết quả hiện hành.

## Checkpoint pre-commit validation — 03/11/2026

- Người dùng cấp quyền stage/commit sau nghiệm thu; branch hiện hành là
  `codex/bl-r07-content-browser-api`, base `5c1db9c`. Không có quyền push/merge/PR.
- Proposed index chỉ gồm 32 file trong write scope BL-R07. Pre-commit review phát
  hiện bảy file mới dư blank line ở EOF; đã sửa và `git diff --cached --check`
  trở lại sạch. Vì source/contract hash đổi, không tái sử dụng evidence 02/11.
- Đã rerun exact năm gate R07 và affected R04d/R06c; các run `PASS` hiện hành nằm
  trong bảng trên, đóng listener/cluster và có current input mismatch bằng `0`.
  Final JAR/privacy review và contract schema gate cũng `PASS` trên bytes hiện tại.
- R07 SEC-16 diagnostic run
  `e4aba22b-25d5-42ca-a906-679c7149e63b` fail trước R07 runtime do JDK loopback
  selector transient `Address already in use`; cluster cleanup thành công. Exact
  retry `90318c3e-ddaa-4953-b457-e1be6961dcf7` là evidence `PASS` được dùng.
- Bước tiếp theo: tạo local task commit, ghi commit ID trong handoff; không push,
  merge, deploy hoặc trộn task kế tiếp vào commit BL-R07.

## Checkpoint sửa finding Control Center — 02/11/2026

- R07-F01 đã đóng bằng snapshot keyed theo exact publication operation, candidate
  component `PUBLIC-BROWSER-SNAPSHOT` mang digest public, pre-canonical temporal
  gate và canonical binding được trigger ghi atomically khi head tiến P→P+1.
  Runtime join head→canonical binding→operation snapshot trong một MVCC view;
  DB test cover arbitrary digest/bundle, missing binding, abort/retry,
  post-canonical install, app-role và concurrent head/read negatives.
- R07-F02 đã đóng: API-C01 roadmap embed public topic label/status cùng stable
  TOP/CAP IDs; schema, example, parser, unit và packaged assertions đồng bộ.
  R07-F04 đã đóng bằng invariant `placementTopicIds` là subset của `topicIds`
  trong chính roadmap và negative snapshot tương ứng.
- R07-F03 đã đóng: handler reject mọi `Transfer-Encoding` trên GET; SEC-25 gửi
  raw chunked request qua packaged TLS và nhận bounded `400 BAD_CONTENT_TYPE`
  trước content response. R07-F05 đã đóng bằng comment tiếng Việt cho các
  responsibility/invariant không hiển nhiên và format readable cho browser code.
- Đã chạy exact payload của năm command R07 qua `python -X utf8
  scripts/db_lab.py`: `db`, `contract`, và `fault` cho `SEC-16`, `SEC-25`,
  `SEC-28`; tất cả current run trong bảng `PASS`. Affected regression chạy exact
  R04d `fault/learning/SEC-20` và R06c `contract/content`, đều `PASS`.
- `node scripts/browser/contract.mjs` trả `PASS` cho ba schema;
  `git diff --check` không có whitespace error (chỉ cảnh báo LF/CRLF), V1–V5
  không có diff từ `HEAD`, final browser review `PASS` trên JAR của CMD-04 mới.
- R06c run `d09aa53f-e485-420d-89fc-c16994699ac4` fail trước coordinator do
  transient JDK selector `Address already in use`; retained report cleanup thành
  công và không được dùng. R07 contract run `a33d44a9-0b2b-40c0-8f83-2ff8934d4f9c`
  bị final helper ghi đè report do dùng nhầm output path, nên cũng bị loại; exact
  replacement `0117cb4f-4230-4ed3-8ef0-b58bf54aac15` là evidence hiện hành.
- R07-AC01–07 và DONE-01–05 có evidence current, không còn P0/P1 đã biết.
  Executor chuyển tối đa `REVIEW`; bước tiếp theo duy nhất là Control Center
  review lại source/security/data ownership/artifacts để quyết định `DONE`.
  Không mở BL-R08/R14/R24a hoặc task kế tiếp.

## Checkpoint Control Center nghiệm thu lại — 02/11/2026

- Kết luận: `DONE`. Control Center review lại source, security/data ownership,
  contract, packaged artifacts và đóng R07-F01–05; không phát hiện P0/P1 mới.
- F01: public bytes được candidate commitment theo exact operation/digest, chỉ
  install trước canonical; FENCE transaction tạo immutable canonical binding và
  runtime join exact binding trong một MVCC view. DB negatives cover wrong digest/
  bundle, missing binding, abort/retry, post-canonical install, least privilege và
  concurrent head/read.
- F02/F04: API-C01 trả TOP label/status cùng stable CAP IDs; schema/example/test
  đồng bộ. Placement bắt buộc là subset của chính roadmap và có negative fixture.
  F03 có cả unit và raw packaged TLS chunked GET negative. F05 đạt comment tiếng
  Việt cho responsibility/invariant đáng kể và browser code giữ format dễ đọc.
- Năm evidence R07 trong bảng đều `PASS`, đóng cluster và khớp current source:
  `content-inputs.json` lần lượt 97, 197, 197, 103 và 103 file, đều `0` mismatch.
  Affected R04d/R06c cũng `PASS`, đóng cluster và `0` mismatch trên 270/273 input.
- Current JAR SHA-256
  `afb89a7c613b1d06ec0dec8b7258f2b67297efee6e8cbaa864ed3808a99b525a`
  từ affected R06c build khớp final browser review; từng run vẫn giữ artifact hash
  riêng. Packaged V1–V6 khớp source, V1–V5 không đổi.
  `node scripts/browser/contract.mjs` trả `PASS` ba schema;
  `git diff --check` không có whitespace error, chỉ cảnh báo line-ending LF/CRLF.
- Giới hạn giữ nguyên: đây là owned-lab Content API/evidence, không phải R30 actual
  production catalog population, R31a production edge, FE journey/player hoặc
  Learning assessment/private disclosure. Không stage/commit/push/merge/deploy và
  không mở task kế tiếp.

## Checkpoint implementation tiếp tục — 01/11/2026

- Đã tạo V6 immutable browser snapshot gắn exact publication
  `(revision, bundle_id, bundle_digest)`, read head/readiness/snapshot trong một
  MVCC view và release-only install SQL; V1–V5 giữ nguyên.
- Đã triển khai API-C01–03 bằng public DTO allowlist, safe-block parser,
  revision-bound public cache và protected unit `private, no-store` qua
  `ContentAccess`; browser profile là opt-in và chỉ được gắn public edge listener.
- Ba draft-07 response schema, example và AJV lock gate đã chạy; strict R07
  selectors, current input hashes, exact report sets và final JAR/privacy auditor
  đều fail closed cho missing/zero/skip/unexpected suite.
- `BrowserCanonicalIdentityIT` login qua packaged canonical Identity, gọi API-C03
  trên packaged Content, kiểm wrong context, Identity down/restart, concurrent
  logout/read và chờ durable Content session fence trước deny cuối. Controlled
  provider fixture vẫn giữ fault oracle độc lập; trust files được restore exact
  giữa hai runtime classes để không trộn topology/evidence.
- Một development run đầu chứng minh actual canonical case PASS nhưng controlled
  class sau đó fail do trust file chưa restore; run kế tiếp dừng ở lỗi JDK selector
  `Address already in use` trước R07 runtime. Cả hai là `FAIL`, cleanup thành công
  và không được dùng làm qualification. Run current trong bảng đã PASS exact final
  input, giữ Identity peer artifact/report và đóng toàn bộ listener/cluster.
- R07-AC01–07 và DONE-01–05 có evidence current, không còn P0/P1 đã biết. Executor
  chuyển task tối đa `REVIEW`; bước tiếp theo duy nhất là Control Center review
  source/security/data ownership/artifacts để quyết định `DONE`. Không mở
  BL-R08/R14/R24a hoặc task kế tiếp.

## Checkpoint Control Center review — 02/11/2026

- Kết luận: chưa nghiệm thu `DONE`; registry và task quay về `IN_PROGRESS`. Source
  review phát hiện một P1 và bốn P2; trong đó R07-F01 chặn AC01/REQ-10 và R07-F02
  chặn exact AC02 dù evidence hiện hành đều báo `PASS`.
- **R07-F01 · P1 — public snapshot chưa thuộc publication decision.** V6 chỉ FK
  snapshot vào tuple `(revision, bundle_id, bundle_digest)`. Installer tự kiểm
  `snapshot_sha256 = sha256(snapshot_bytes)` và bundle `validated`, nhưng digest/
  bytes public không nằm trong bundle/manifest/candidate đã được STAGE/FENCE/
  canonical commit xác nhận. Installer cũng không reject revision đã canonical,
  trái với `ops/content/README.md` nói projection phải có trước canonical. Vì vậy
  `content_migrator` có thể gắn arbitrary public projection vào một bundle hợp lệ,
  kể cả sau commit, rồi API phục vụ nó như content published. Cần binding bất biến
  do publication decision xác nhận, pre-canonical temporal gate và DB negatives.
- **R07-F02 · P2 — API-C01 không đạt exact contract trong brief.** Catalog DTO/
  schema hiện chỉ có roadmap `id/title/status/topicIds/placementTopicIds`; không có
  stable CAP IDs hoặc planned TOP labels/status mà R07-AC02 yêu cầu. API-C02 có CAP
  và status không thay evidence cho response API-C01. Phải đồng bộ implementation,
  schema/example/test với AC02, hoặc Control Center sửa acceptance có chủ đích nếu
  owner contract thực sự chỉ yêu cầu metadata roadmap.
- **R07-F03 · P2 — chunked GET body không fail closed.** Handler chỉ reject khi
  `Content-Length > 0` hoặc có `Content-Type`; request `Transfer-Encoding: chunked`
  không content type có content length `-1`, nên body bị bỏ qua và route vẫn có thể
  trả success. Bổ sung wire guard và raw packaged negative cho request body/TE.
- **R07-F04 · P2 — placement relation chưa được validate.** Parser chỉ kiểm cả
  `topicIds` và `placementTopicIds` tồn tại trong tập topic global; nó không buộc
  placement của một roadmap là subset của chính roadmap đó. Snapshot có thể đặt
  TOP chỉ thuộc roadmap khác rồi được catalog trả như mapping hợp lệ. Bổ sung
  invariant và negative fixture/test.
- **R07-F05 · P2 — code comment chưa đạt quality gate của repo.** Các class và
  invariant mới trong browser main/test phần lớn dùng Javadoc/comment tiếng Anh
  ngắn hoặc không mô tả đủ input/output, tác động dữ liệu và lỗi; ví dụ transaction
  MVCC/local fence, DTO allowlist và safe-block ingestion. `quality-process.md`
  đưa rule comment tiếng Việt trong `AGENTS.md` vào code review/DoD. Chuyển các
  giải thích cần thiết sang tiếng Việt, giữ thuật ngữ English và không comment các
  dòng tự hiển nhiên.
- Validation review: năm evidence run được checkpoint dẫn đều `PASS` và toàn bộ
  `content-inputs.json` còn khớp current files (`0` mismatch trên lần lượt 96,
  196, 196, 102 và 102 inputs); `node scripts/browser/contract.mjs` `PASS` ba
  schema; `git diff --check` không có whitespace error (chỉ cảnh báo LF/CRLF).
  Targeted Maven unit rerun không phát output và được dừng thủ công, nên không tính
  là evidence mới; packaged evidence current vẫn được giữ nhưng chưa cover năm
  finding trên.
- Bước tiếp theo duy nhất: executor sửa R07-F01–05 trong write scope hiện hành,
  rerun exact affected R07 gates và cập nhật checkpoint tối đa `REVIEW`; Control
  Center nghiệm thu lại. Không mở BL-R08/R14/R24a hoặc task kế tiếp.

## Checkpoint chuẩn bị — 30/10/2026

- Control Center xác định BL-R07 là leaf kế tiếp: BL-R04d và BL-R06c đều `DONE`;
  không còn dependency chặn brief.
- Source hiện có đã có `ContentAccess` canonical admission/fence và publication
  canonical/readiness head, nhưng chưa có public Content browser contract/runtime
  hoặc published browser read snapshot. Source existence của R04d/R06 không chứng
  minh API-C01–03.
- Brief chốt boundary: public read model trong Content DB, anonymous outline,
  protected no-store body và privacy sentinel. R30 vẫn sở hữu actual catalog
  population/publish; R31a vẫn sở hữu production edge.
- Task giữ `PLANNED`. Bước tiếp theo duy nhất: Control Center mở implementation
  session riêng sau khi có quyền CODE/Git phù hợp; executor tạo checkpoint
  `IN_PROGRESS`, không mở downstream task.

## Checkpoint implementation bắt đầu — 30/10/2026

- Implementation chat riêng bắt đầu đúng leaf BL-R07 trên branch
  `codex/bl-r07-content-browser-api`; Control Center ở chat gốc giữ vai trò
  integrator/reviewer.
- Người dùng cấp quyền CODE và tạo branch cho leaf này. Không có quyền stage,
  commit, push, merge, PR hoặc deploy; hai thay đổi planning hiện có trong
  registry và brief được giữ lại.
- Registry và trạng thái hiện hành chuyển `PLANNED` → `IN_PROGRESS` trước khi
  sửa runtime. Write scope, acceptance và validation giữ nguyên theo brief;
  không mở BL-R08, BL-R14, BL-R24a hoặc task kế tiếp.
