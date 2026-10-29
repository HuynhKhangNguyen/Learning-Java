# BL-E07c — Interactive Workbench progress integration

[Parent](BL-E07.md) · [Pilot](BL-E07b.md) · [Progress business](../../f05-progress/business.md) · [Assessment](../../f04-assessment/evidence-contract.md) · [F02 LLD](../lld.md)

## Objective, outcome và trạng thái

**Objective:** nối HTTP workbench với owner workflows của Viewed, official assessment/Proven và project/Applied mà không đưa progress authority hoặc rubric vào component mô phỏng.

**Outcome:** người học thấy ba chiều riêng cùng nguồn/version/pending state; hoàn thành điểm xem hợp lệ có thể ghi Viewed qua server, còn Check/Apply chuyển sang official workflow và chỉ hiển thị kết quả owner trả về.

**Trạng thái 29/10/2026: `PLANNED`.** Blocked bởi E07b, E02, R16b, R20, R22b và R23b. E02 phải được phân rã và bàn giao exact HTTP assessment/apply content IDs theo owner trước khi leaf này READY. Nếu các owner API không biểu diễn được mapping HTTP pilot, phải ghi gap/tạo owner leaf riêng; không mở rộng backend trong task này.

## In scope

- Gửi viewed event cho exact unit/version tại completion point do content định nghĩa; server resolve `viewedTopicIds` và dedupe.
- Hiển thị Viewed/Proven/Applied riêng, gồm current/as-of/pending/review reason và shared progress.
- `Check` điều hướng/nhúng exact official HTTP assessment ID do E02/content owner bàn giao vào attempt/result owner; public formative answers không được nâng thành official evidence.
- `Apply` điều hướng tới exact official HTTP exercise/project ID do E02/content owner bàn giao; simulation observation không thành project submission.
- Xử lý login/expiry, stale response, publication pending/withdrawn, owner A/B switch, reload/Back và offline/server-unavailable không ghi success giả.

## Out of scope và invariants

- Không tạo assessment case/rubric/project mới, không sửa Learning DB/API semantics hoặc chấm ở client.
- Run, Next, replay, reset, xem animation hoặc formative score không POST Proven/Applied.
- Client không gửi CAP pass/list để server tin; không cache owner progress bền trong browser.
- Reset simulation độc lập hoàn toàn với learning reset.

## Acceptance criteria

| AC | Điều kiện đo được | Validation dự kiến |
|---|---|---|
| E07c-AC01 | Viewed chỉ ghi khi exact completion point/unit/version hợp lệ; retry/reload dedupe, wrong owner/pub/version bị từ chối | Actual API/E2E owner A/B |
| E07c-AC02 | Run/Next/replay/reset và formative Check không đổi Proven/Applied trong mọi scenario | Network/event negative assertions |
| E07c-AC03 | Official Check dùng attempt/result workflow, giữ pending/error/support/provenance; không lộ expected | R20 affected journey |
| E07c-AC04 | Apply dùng project workflow và evidence owner; readiness khác Applied | R23b affected journey |
| E07c-AC05 | Ba chiều hiển thị riêng, stale/pending/withdrawn/reverify/shared state đúng và accessible | R22b adapter + browser tests |
| E07c-AC06 | Logout/expiry/user switch xóa RAM/discard response trễ, không lộ hoặc ghi sang user khác | Auth/browser reorder tests |

## DoD, evidence và execution

Đạt AC01–06 + DONE-01–05 bằng actual owner APIs/edge journey phù hợp; unit adapter/event tests, build và Playwright affected flow đạt, zero required skip. Mock chỉ dùng unit test, không thay integration evidence.

Role: Frontend integration; backend owners là dependencies, không writer trong leaf này. Mode B separate chat, một writer. Write scope tương lai giới hạn vào frontend workbench/progress adapters/routes/tests và task/checkpoint; backend/DB/contracts chỉ read. Gap contract phải escalation về Control Center.

## Checkpoint ban đầu — 29/10/2026

- Planning-only; dependency chưa đạt nên giữ `PLANNED`. Không có implementation/evidence.
