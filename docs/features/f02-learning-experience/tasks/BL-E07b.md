# BL-E07b — HTTP Foundations interactive pilot

[Parent](BL-E07.md) · [Contract leaf](BL-E07a.md) · [F02 LLD](../lld.md) · [HTTP prototype](BL-P03.md) · [F08 LLD](../../f08-content/lld.md) · [Quality](../../../quality-process.md)

## Objective, outcome và trạng thái

**Objective:** triển khai HTTP Foundations workbench đầu tiên trên product player bằng contract E07a, tái sử dụng source nội dung P03 và stable HTTP topic IDs thay vì tạo lesson/progress song song.

**Outcome:** từ topic published, người học mở được workspace 2D, dự đoán kết quả, thay input có allowlist, theo dõi Browser → DNS → Server → Controller → Service → Database → HTTP Response, đọc contextual inspector và dùng được full text/static fallback.

**Trạng thái 29/10/2026: `PLANNED`.** Blocked bởi BL-E07a và BL-R16a; R16a transitively kéo product content/journey/topic/player/auth/viewed dependencies. Không được code chỉ từ brief này.

## In scope

- Mở rộng schema/validator/public DTO cần thiết cho descriptor E07a, giữ unknown/private/executable field fail closed.
- Dùng published unit/version và existing `TOP-HTTP-001/002`; migrate/reuse P03 content/block IDs hoặc ghi mapping rõ, không giữ hai source authoritative.
- Scene 2D, hotspots, inspector Theory/Flow/Example/Check/Apply, prediction và controlled scenarios method/header/status/payload/timeout.
- Deterministic reducer, step/replay/reset, explanation sau Run; Run luôn gắn nhãn simulation.
- EN/VI dùng global locale, same scene/state IDs; fallback text/static cho reduced motion hoặc renderer/animation unavailable.
- Bounds E07a được validator thực thi; không continuous animation khi idle và không tải asset/third-party code ngoài contract.

## Out of scope và invariants

- Chưa nối official Viewed/Proven/Applied: thuộc E07c. Formative Check không là mastery.
- Không chạy request/backend/Java/Docker thật, không WebGL/3D, CMS/asset production, dependency/framework/service boundary mới.
- Không đưa private expected/rubric/hint/solution vào public bundle, log, DOM hoặc sourcemap.
- Topic không có descriptor tiếp tục dùng reader bình thường; lỗi descriptor giữ nội dung tốt/fallback, không blank page.

## Acceptance criteria

| AC | Điều kiện đo được | Validation dự kiến |
|---|---|---|
| E07b-AC01 | Published topic→unit mở đúng workspace; Back/deep-link/reload/locale không tạo duplicate source hoặc mất state ngoài contract | Content/API adapter tests + Playwright |
| E07b-AC02 | Exact descriptor validates bounds/references; unknown, duplicate, cyclic/invalid step, executable/private field và over-limit fail closed | Validator positive/negative tests |
| E07b-AC03 | Prediction và từng controlled scenario cho deterministic observation/explanation; replay/reset không tạo hidden future state | Reducer unit matrix |
| E07b-AC04 | Scene/inspector đầy đủ bằng keyboard và text/static fallback; reduced motion không mất causal relation | Browser keyboard/no-motion/mobile tests + visual review |
| E07b-AC05 | EN/VI semantic parity, technical terms đúng; đổi locale giữ scenario/input/result | Content validation + bilingual review |
| E07b-AC06 | Public artifact/privacy/package không lộ private case/secret; UI nói rõ simulation, không claim real execution | Artifact/source/privacy review |

## DoD, evidence và execution

Đạt AC01–06 + DONE-01–05; `project.ps1 -Task content/check/build`, targeted reducer tests và affected Playwright journeys đạt; browser review 320/390/768/1440, light/dark/reduced-motion; content/technical review có nguồn. Không lấy mock/local prototype thay actual published/player integration.

Role: Frontend + Content, Control Center integrator. Mode B separate chat, một writer. Future write scope chỉ được chốt sau E07a/R16a checkpoint; dự kiến giới hạn vào owned catalog/schema fixture, `frontend/src` workbench/player/content adapter, targeted tests và file task này. Nếu cần Learning/backend API mới, dừng và trả finding; không tự nới scope.

## Checkpoint ban đầu — 29/10/2026

- Planning-only; chưa READY vì E07a/R16a chưa đạt. Không có source, schema, asset hoặc test mới.
