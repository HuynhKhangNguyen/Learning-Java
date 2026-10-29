# BL-R05 — Catalog schema2 và standalone CMD-04

[Registry](../../../delivery-backlog.md) · [F08](../README.md) · [LLD](../lld.md)

## Brief / quyền / scope

15/10/2026: người dùng cho phép leaf kế tiếp theo backlog, giữ kiểm tra theo ảnh hưởng, không commit/deploy. Chọn R05 vì dependency R01c DONE; không suy dependency chỉ từ số. Một đầu ra chính: hợp đồng authoring schema2 và validator catalog độc lập, không khởi động auth/DB. REQ01/02/10/13/18, MD01; không tạo Goal mới khi chưa được yêu cầu.

Đầu vào canonical: core-foundations/core-api-data/core-security-operations/devops-catalog dưới docs/curriculum; giữ95TOP/190CAP, stable/shared IDs, selectors all-of, stages S00–S10/DS00–DS11 và readiness DP. Legacy schema1/FE/lockfile giữ nguyên. Public/private/viewed mapping là contract/fixtures, không publish bài hoặc cấp progress. Chuẩn song ngữ áp dụng khi viết nội dung mới; không dịch cả95topic, không coi metadata outline tiếng Việt hiện có là bài EN/VI đã publish.

## Acceptance / DoD

| AC | Điều kiện | Kiểm chứng |
|---|---|---|
| AC01 | Schema2 strict,95TOP/190CAP canonical, ID/version/status/reference và shared placements đúng | Snapshot đối chiếu source docs; duplicate/unknown/malformed negatives |
| AC02 | Prerequisite all-of TOP hoặc CAP selector, DAG; DS độc lập Java, DP readiness không thêm cạnh từ thứ tự | Independent known anchors + cycle/dangling/selector/duplicate/stage negatives |
| AC03 | Public/private tách contract, unit→TOP/CAP/canonicalTopic/viewed mapping kiểm được, không leak private expected | Positive/negative fixtures; public projection allowlist và input không bị sửa khi lỗi |
| AC04 | CMD04 catalog branch standalone qua helper, required nonzero/no skips, unknown/missing/bad schema fail | Actual command + native negative invocations; không auth/DB fake PASS |
| AC05 | Legacy v1 không đổi; docs/runbook/tests/comment/source/evidence nhất quán | Legacy content regression, diff/hash scope và final review; không P0/P1 |

## Plan / evidence hiện hành

1. Import canonical outline từ docs thành snapshot authoring; fail closed khi source không đúng cấu trúc/count, không đoán ID từ thứ tự.
2. Schema/validator/tests độc lập và mapping fixtures; schema kiểm structural, validator kiểm referential/DAG/business invariants.
3. Nối helper branch rồi chạy targeted tests/actual CMD04; chỉ chạy legacy regression phù hợp, không lặp auth suites.
4. Đóng băng candidate, review AC01–05/docs/evidence, chỉ DONE khi đủ.

Checkpoint15/10: brief tạo trước IN_PROGRESS; chưa có schema/validator/fixture hay test PASS. Không publication store/API/runtime/DB, không R06/R12/FE hoặc schema1 migration. Ngôn ngữ/semantic lesson review không được suy từ schema PASS.

## Closing — 15/10/2026

R05 DONE trong scope schema/catalog authoring standalone. `contracts/catalog/` giữ JSON schemas/public-private mapping fixture/runbook; `content-v2/catalog/catalog.json` là snapshot planned95TOP/190CAP/130placements/6DP của nguồn canonical, không lesson đã publish. `scripts/catalog/` giữ importer, Ajv validator, required-report gate, tests và actual CMD04 runner. Helper chỉ thêm explicit R05 early branch và EvidenceTask enum; không đụng logic cũ hoặc backend app/DB/source/migrations.

| AC | Evidence hiện hành |
|---|---|
| AC01 | Current CMD04 run `83e9031f-6990-424d-a06d-1d7476a65d28`:36actual tests PASS,0failure/skip; exact snapshot↔four source docs và independent95/190/known-ID anchors, real JSON Schema negative cases |
| AC02 | Cùng run: exact .A/.B và TOP all-of, self/transitive cycle/dangling selectors;11S/12DS và6DP; full readiness closure không kéo JAVA/SPR/DATA vào DevOps |
| AC03 | Mapping fixture/negative tests: canonical topic và CAP/viewed subset, public projection allowlist không expected, private rubric bound unit CAP, draft/planned không public body, thiếu EN/VI bị reject structural; không semantic publication claim |
| AC04 | Actual `pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R05 -Offline` terminal PASS;10report-gate tests và9native CLI/helper negatives PASS; missing/zero/skip/native fail/wrong arguments/schema/different valid snapshot fail closed |
| AC05 | Legacy `scripts/project.ps1 -Task content`:8tests PASS; không sửa FE/schema1/package/lockfile. Helper normalized comparison với retained366-case fixture chỉ đúng R05 branch+enum addition; PowerShell AST và scoped Markdown links/UTF8/fences PASS. Không rerun auth/DB suites vì executable paths cũ không đổi |

Final report `artifacts/quality/R05/83e9031f-6990-424d-a06d-1d7476a65d28/report.json` SHA256 `81a5f3a55158ad858564fca5d3d6b62c72009651f4b1f8abfe54445898e62eb3`, exact18current input hashes + TAP36case names/counts verified after freeze. Runtime Node24.19.0 hiện có, Ajv6.15.0 khớp FE lock; không install/download/dependency change. Preliminary runs42a8702a/1b638bbe giữ history, không dùng thay final hash sau comment/runner changes. Gate/CLI/legacy runs là kiểm tra bổ sung riêng, không cộng thành một suite giả hoặc pretend browser/HTTP PASS.

Importer lần đầu báo95TOP/214CAP vì đọc nhầm bảng ba cột giải thích shared prerequisites thành competency. Đã sửa nhận đúng header A/B canonical; snapshot/source parity và counts tests bắt regression. Một lần capture stdout JSON bị tool truncate, chưa tạo snapshot từ output lỗi; sau đó đọc đủ JSON rồi tạo file qua patch. Không có failure bị đổi thành PASS hoặc checkpoint cũ bị xóa.

Review: schema structural khác referential/DAG validation; alias ambiguous bị từ chối, không suy stage-index; shared topic/CAP chỉ một definition, không progress theo roadmap/locale. Public DTO là explicit allowlist, authoring/private nằm ngoài Vite; không dùng prototype locale đổi legacy/backend runtime. Comment Việt giải thích importer table selection, no-mutation errors, DFS, report gates, process bounds và private disclosure boundary. Không còn P0/P1 trong supported standalone scope.

Limits/handoff: schemas ở đây không chứng minh nguồn kỹ thuật bài, EN/VI semantic equivalence, đầy đủ rubric/diagram/model renderer, learner API hay auth/publication/journal. Paragraph mapping fixture không phải Collections lesson; toàn95topic vẫn planned. Publication store/immutable bundle/INT03 và review gate thuộc R06a (dependencyR05/R02a/R02b đã đạt), chưa tự mở trong lượt này. Không commit/deploy/agent/chat hoặc Goal mới.

Final closing audit:18captured input hashes unchanged;8Markdown files/134local links/UTF8/fences và registryR05DONE PASS. `frontend/src`/`frontend/public` không reference content-v2/contracts/catalog/scripts/catalog; không dùng source scan thay artifact scan R07/R31 hoặc UI browser QA. Durable `closing-review.json` nằm cạnh final report/TAP; không sửa executable candidate sau final CMD04. Không còn required work trong R05 scope.
