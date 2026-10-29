# BL-E07d — Workbench accessibility, responsive và performance qualification

[Parent](BL-E07.md) · [Pilot](BL-E07b.md) · [Progress integration](BL-E07c.md) · [Visual design](../visual-design.md) · [Quality](../../../quality-process.md)

## Objective, outcome và trạng thái

**Objective:** nghiệm thu độc lập HTTP workbench candidate về keyboard, screen-reader semantics, reduced motion, responsive layout, visual clarity và bounded performance/fallback.

**Outcome:** có report chỉ rõ môi trường, browser, viewport, interaction/performance budget, ảnh/traces, findings và giới hạn; không dùng test unit hoặc screenshot đơn lẻ để claim accessibility/learner acceptance.

**Trạng thái 29/10/2026: `PLANNED`.** Final qualification chờ E07b/E07c. Preflight có thể chuẩn bị sau E07b nhưng không chuyển DONE trước progress integration.

## In scope

- Keyboard-only: vào/ra scene, hotspot/component, inspector tabs, prediction/form controls, step/replay/reset, Check/Apply và focus recovery sau lỗi/route change.
- Screen-reader semantics: landmark/heading/name/description/state, selected/current/expanded, live observation không spam, text equivalent cho scene/flow và không phụ thuộc màu.
- Reduced motion/static fallback: không autoplay bắt buộc, không mất step/causal relation, animation failure vẫn hoàn thành được lesson.
- Responsive/zoom: 320, 390, 768 và 1440 px, 200% zoom, portrait/landscape hợp lý, không page overflow; code/table chỉ cuộn trong vùng có nhãn.
- Visual: light/dark, contrast, status/focus/error/pending và English/Tiếng Việt.
- Performance: enforce descriptor bounds E07a, không continuous animation khi idle; trên profile/browser được ghi trong report, p95 từ action đến observation sau warm-up phải ≤200 ms. Nếu animation path không giữ budget hoặc init lỗi, static stepper phải tự dùng được và không báo simulation success giả.

## Out of scope và invariants

- Không sửa product source trong verification leaf. P0/P1 hoặc contract mismatch trả lại E07b/E07c và giữ task `IN_PROGRESS`/`PLANNED` phù hợp.
- Không claim learner acceptance, WCAG conformance toàn app hoặc performance production từ một máy lab.
- Không thêm analytics/third-party asset/tool runtime hoặc nới threshold để làm xanh.

## Acceptance criteria

| AC | Điều kiện đo được | Evidence dự kiến |
|---|---|---|
| E07d-AC01 | Full primary flow hoàn thành keyboard-only, focus order/recovery đúng ở cả hai locale | Playwright + manual keyboard report |
| E07d-AC02 | Accessibility tree có name/role/state/live text và scene có text equivalent; axe/static checks nếu repo đã có, không thêm dependency chỉ cho task | Accessibility snapshot/manual review |
| E07d-AC03 | Reduced-motion và forced animation failure dùng static path đủ chức năng | Browser negative cases |
| E07d-AC04 | 320/390/768/1440 + 200% không mất CTA/nội dung/meaning hoặc page overflow | Screenshots + layout assertions |
| E07d-AC05 | Descriptor bounds đạt, idle không loop, p95 ≤200 ms hoặc automatic static fallback hoạt động đúng | Repeatable timing/trace report |
| E07d-AC06 | No P0/P1; findings thấp hơn có severity/owner/limit, docs/task/registry không claim vượt evidence | Scoped review report |

## DoD, evidence và execution

Đạt AC01–06 + DONE-01–05; chạy candidate build và affected Playwright trên browser/runtime ghi rõ, kiểm report không zero/skip/hidden failure. Manual screen-reader semantics/visual review phải phân biệt với automated assertions.

Role: Verification/Reviewer. Mode B separate chat; read product source, chỉ write targeted browser tests/fixtures nếu brief lúc READY cho phép, `artifacts/quality/E07d/**` và file task/checkpoint. Không sửa registry hoặc product implementation.

## Checkpoint ban đầu — 29/10/2026

- Planning-only; blocked bởi E07b/E07c. Chưa có candidate, browser run, screenshot, timing hoặc accessibility evidence.
