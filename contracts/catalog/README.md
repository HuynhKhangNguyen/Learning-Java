# Catalog schema2 — R05

`catalog-v2.schema.json` dùng JSON Schema draft-07 cho authoring outline: topic/CAP, placements, all-of prerequisites, DP readiness, units/packages/explicit aliases. `private-case-v2.schema.json` là private expected/rubric contract; không nằm trong public outline hoặc FE import. JSON Schema kiểm shape; `scripts/catalog/validate.mjs` kiểm references, ownership, DAG và mappings.

`content-v2/catalog/catalog.json` là snapshot95TOP/190CAP của bốn catalog thiết kế. Importer stdout không tự sửa file/publish; required test so snapshot với source và independent anchors. Chuyển vị trí/ngôn ngữ không tạo topic/progress mới. All-of TOP cần các CAP của topic; selector competency chỉ yêu cầu CAP nêu tên. Stage order không tạo prerequisite hoặc khóa khám phá; readiness không là milestone completion.

Public outline là allowlist metadata, không có units/private cases/source provenance. Public unit chỉ trả published unit có các topic published; R05 không phải endpoint/auth/publication gate. Viewed mapping là subset explicit của unit topics; server owner về sau resolve unit/version, không tin list CAP client. Mở unit không cấp competency hoặc toàn bộ shared topic. Ambiguous aliases không đoán stage-index.

Fixture mapping chỉ phục vụ tests, không Collections content được publish. Draft catalog hiện không có units/packages; không giả95topic đã có bài. Contract paragraph fixture có EN/VI dùng chung IDs; structural presence không chứng minh translation semantics hoặc chất lượng bài. Metadata titles canonical hiện theo source tiếng Việt, chưa claim outline English đầy đủ; không đổi legacy schema1/backend/runtime localization từ FE prototype.

Chạy từ root:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service content -EvidenceTask R05 -Offline
```

Branch này chỉ Node24+ hiện có, Python hiện có để đối chiếu source, và Ajv6.15.0 từ `frontend/package-lock.json`/installed locked modules. Không tải/cài dependency, không Java/DB/TLS hoặc publication. Missing runtime/module/suite, zero/skipped/failed test hoặc sai input phải trả nonzero. Evidence UUID dưới `artifacts/quality/R05/`; command không sửa catalog. Không dùng report này thay R06 candidate store, R07 API privacy hay R30 publication review.
