# BL-E07a — Product/UX contract và reusable scene model

[Parent](BL-E07.md) · [F02 LLD](../lld.md) · [F08 lesson blocks](../../f08-content/authoring/lesson-blocks.md) · [Topic template](../../f08-content/authoring/topic-template.md) · [Progress](../../f05-progress/business.md) · [Quality](../../../quality-process.md)

## Objective và business outcome

**Objective:** chốt một contract owner-level cho `Interactive Learning Workspace` và scene model declarative trước khi sửa schema/runtime. Contract phải đủ cụ thể để HTTP Foundations pilot triển khai một lần, đồng thời không khóa Spring/Database/Microservices/DevOps vào visual hoặc state machine riêng của HTTP.

**Business outcome:** product/content/frontend dùng chung một định nghĩa về khi nào topic có workbench, dữ liệu nào thuộc content, state nào thuộc runtime và hành động nào được phép tạo evidence. Người học luôn có đường text/static tương đương và hiểu rõ simulation không phải real execution.

**Trạng thái 29/10/2026: `PLANNED`.** Các nền prototype/content/publication đã đạt, nhưng activation cố ý chờ BL-R15 chốt topic page/CTA/deep-link. Thời điểm tốt nhất là ngay sau R15 và trước hoặc ở đầu extension planning của R16a; task này không block R16a và không được tự chen vào task kế tiếp.

## Dependency, owner và module ảnh hưởng

- BL-P03/P04/P05 `DONE`: HTTP content blocks, global locale và bilingual copy hiện hành.
- BL-R05/R06c `DONE`: stable catalog/unit mapping và publication coordinator; không suy R07/player đã có.
- BL-R15 phải `DONE`: topic page, action availability, Back/deep-link và điểm vào player đã ổn định để contract không dựa vào prototype route tạm.
- Owners: F01 navigation boundary, F02 workspace/runtime, F08 content/authoring; tham chiếu F04/F05/F06 evidence mà không sửa authority.
- Modules/requirements: MD01/02/03/04/05/07/09; REQ-02/03/05/06/08/10/11/12/13/19.
- HTTP pilot bind existing `TOP-HTTP-001` và `TOP-HTTP-002`; không tạo mastery/topic mới vì đổi presentation.

## In scope

1. Tạo owner contract `docs/features/f02-learning-experience/interactive-workbench.md` cho navigation, layout, scene lifecycle, inspector và fallback.
2. Chốt descriptor versioned/allowlisted cho scene, component, connection, hotspot, scenario, controlled input, prediction, step, observation, explanation và inspector reference. Mọi ID ổn định, không phụ thuộc locale hoặc vị trí render.
3. Tách MD01 content data khỏi MD03 deterministic reducer/state; không cho executable HTML/JavaScript, arbitrary URL hoặc service call trong descriptor.
4. Chốt HTTP pilot mapping Browser → DNS → Server → Controller → Service → Database → HTTP Response cùng scenario method/header/status/payload/timeout có kiểm soát.
5. Chốt progress/evidence events: điểm hoàn thành unit có thể đề xuất Viewed qua owner API; formative Check, Run/Next/replay/reset không tạo Proven/Applied; official Check/Apply chỉ là reference sang owner workflow.
6. Chốt EN/VI, keyboard, focus order, screen-reader names/live status, reduced-motion, responsive và static/text fallback.
7. Chốt bounds mặc định cho pilot: tối đa 32 components, 48 connections, 24 steps, 8 controlled inputs và 100 KiB UTF-8 cho một scene descriptor. Contract phải ghi rationale; thay bound phải version/review, không nới im lặng.
8. Ghi chiến lược compatibility: P03 block reader vẫn chạy; topic không có descriptor dùng reader thường; future scene kind không được làm hỏng version cũ.

## Out of scope

- Không sửa `frontend/`, `backend/`, contract JSON schema executable, database hoặc publication fixture.
- Không viết HTTP lesson/asset, không dựng animation, không thêm dependency/framework và không chọn WebGL/3D.
- Không thiết kế lại official assessment/grader/project hoặc progress API.
- Không mở BL-E07b/sibling sau khi hoàn thành.

## Security, data và progress invariants

- Public descriptor không chứa private expected/rubric, secret, credential, arbitrary executable content hoặc external tracking URL.
- Simulation state là state tạm của phiên; reset simulation không xóa learner data. Locale không fork ID/state/progress.
- Server owner resolve unit→TOP viewed mapping; client không gửi CAP mastery list.
- Official evidence phải giữ owner, version, source và hỗ trợ theo F04/F05; UI không tự diễn giải formative score thành pass.
- Static fallback phải truyền cùng causal relation/observation, không chỉ là thông báo “animation unavailable”.

## Acceptance criteria

| AC | Điều kiện đo được | Evidence dự kiến |
|---|---|---|
| E07a-AC01 — Ownership/navigation | Roadmap, topic, workspace, content descriptor, runtime state và evidence owner có một trách nhiệm duy nhất; walk-through Back/deep-link/topic không có scene không mâu thuẫn R14–R17 | Owner-doc link/ID check và dependency walk-through |
| E07a-AC02 — Reusable descriptor | Contract có field/type/version/ID/reference/bounds/error/fallback cho toàn scene lifecycle; reject unknown/executable/private fields được mô tả | Schema examples/negative matrix trong owner doc, chưa claim runtime validator PASS |
| E07a-AC03 — Deterministic learning loop | Prediction → controlled input/action → deterministic step/observation → explanation/replay/reset được định nghĩa; HTTP pilot mapping đủ nhưng không hard-code renderer cho HTTP | Scenario/state matrix review |
| E07a-AC04 — Progress boundary | Viewed/Proven/Applied mapping và event owner rõ; Run/Next/replay/reset/formative check không cấp official evidence | F05/F04/F06 trace table |
| E07a-AC05 — Bilingual/accessibility/performance | Same IDs/state cho EN/VI; keyboard/focus/screen-reader/reduced-motion/responsive/static fallback và numeric content bounds được chốt | Checklist + desktop/mobile/no-motion walkthrough |
| E07a-AC06 — Compatibility/scale | P03 compatibility và strategy thêm Spring/Database/Microservices/DevOps không fork schema/content/progress được ghi; 2D baseline, 3D optional future | Impact map và no-duplicate review |

## Definition of Done và validation

Đạt AC01–06 và DONE-01–05; links/IDs/dependency/traceability hợp lệ; review nhất quán với F01/F02/F05/F08, topic template và module map; không còn P0/P1. Đây là task tài liệu thuần nên không chạy app build/E2E. Validation dùng `rg`, relative-link check và manual walkthrough theo `docs/quality-process.md`; không tuyên bố schema/runtime đã triển khai.

## Execution role, mode và write scope

- Role: Product/Architecture + Content/Learning design; Control Center nghiệm thu.
- Mode: Mode B, một implementation chat riêng và một writer; không subagent nếu user không yêu cầu.
- Session được sửa: `docs/features/f02-learning-experience/interactive-workbench.md` (mới), `docs/features/f02-learning-experience/lld.md`, `docs/features/f08-content/authoring/lesson-blocks.md`, `docs/features/f08-content/authoring/topic-template.md` khi cần slot T05/T06, `docs/features/f01-journey/journey-map.md` nếu cần làm rõ navigation, và file task này.
- Không sửa registry hoặc chuyển `DONE`; executor ghi checkpoint và bàn giao tối đa `REVIEW` cho Control Center.

## Checkpoint ban đầu — 29/10/2026

- Brief đầy đủ nhưng scheduling gate BL-R15 chưa đạt, nên `BL-E07a · PLANNED`.
  Không dùng độ mới của task hoặc vị trí dòng registry để ưu tiên thực thi.
- Activation window: sau R15, trước hoặc ở đầu extension planning của R16a. R16a
  vẫn có thể hoàn thành core player mà không chờ E07a; E07b mới là consumer bắt
  buộc của cả E07a và R16a.
- Chưa có source/schema/runtime change hoặc validation PASS. BL-E07b–d giữ `PLANNED`.
