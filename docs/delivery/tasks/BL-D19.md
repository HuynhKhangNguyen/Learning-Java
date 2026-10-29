# BL-D19 — Tích hợp workflow điều phối Codex

16/10/2026. Yêu cầu từ người dùng: tích hợp Project Knowledge, Control Center, execution roles, task session và handoff vào repo hiện có; giữ nguyên application code và tránh tạo tài liệu/cấu hình trùng lặp. [Registry](../../delivery-backlog.md) giữ trạng thái.

## Goal và phạm vi

Sau task, một Codex session mới có thể tìm đúng source, chọn một leaf task, giao phần việc độc lập cho native subagent hoặc tạo prompt cho manual chat, tích hợp kết quả và đóng session bằng evidence/checkpoint mà không dựa vào chat memory.

Đầu ra chính:

- `AGENTS.md` ngắn, chỉ giữ rule ổn định và trỏ tới protocol.
- `docs/execution-model.md` sở hữu Control Center protocol, execution roles, hai execution modes, file ownership và prompt dùng lại.
- Task brief, quality process, entry points và registry nối cùng workflow.

Ngoài phạm vi: sửa application source/test/runtime, tạo `docs/ai/`, task board/decision/handoff source thứ hai, tạo `.agents/` hoặc `.codex/` runtime config, skill/MCP, cài dependency, commit/push/deploy, triển khai BL-R06a.

## Discovery và quyết định

- Knowledge base hiện có: root/docs README, business, requirements, feature, architecture, curriculum và platform docs.
- `docs/delivery-backlog.md` + linked task files đã là tracker; `delivery/contract.md`, `execution-model.md`, `task-brief-template.md` và `quality-process.md` đã sở hữu Ready/Done/session/handoff.
- Repo có React/Vite frontend, ba Spring Boot microservice và ba PostgreSQL database riêng. Foundation/auth đạt tới các leaf đã ghi trong registry; learner flow và FE–BE integration còn theo backlog.
- Chỉ có root `AGENTS.md`; không có nested instruction, repo skill, MCP hoặc custom-agent config. Role trong tài liệu là execution label, không là agent tự chạy. MCP/plugin được cấu hình ngoài repo, nếu có, là unknown và không được suy đoán.
- Git đang ở unborn `master`, chưa có `HEAD`; toàn bộ file là untracked. Không thể dùng branch/worktree/diff làm isolation/evidence đáng tin cho tới khi có Git baseline riêng.
- Quyết định: mở rộng nguồn hiện có, không tạo bộ `docs/ai/`. Cho phép native subagent cho phần độc lập dưới một integrator; manual chat vẫn do người dùng tạo và trao đổi bằng task checkpoint.

## Plan và acceptance

1. Rút gọn rule và loại checkpoint/quyền lịch sử khỏi `AGENTS.md`.
2. Bổ sung protocol điều phối, role, execution mode, concurrency, handoff và prompt vào delivery sources.
3. Đồng bộ registry/entry points/quality; sửa các mâu thuẫn hoặc status snapshot chạm trực tiếp tới khả năng discovery.
4. Dry-run read-only với BL-R06a và kiểm link/source ownership; không triển khai R06a.

| AC | Cách kiểm / expected |
|---|---|
| D19-AC01 | Một session mới đi từ `docs/README.md` → registry → task file → relevant source mà không cần đọc toàn repo |
| D19-AC02 | Control Center có input, 10 bước, lifecycle hiện hành và trách nhiệm integrator rõ |
| D19-AC03 | Role có scope/context/input-output/validation/escalation; không được mô tả như runtime agent tự động |
| D19-AC04 | Native subagent và manual chat đều dùng được; one-writer-per-file, shared-workspace và no-live-chat-state được ghi rõ |
| D19-AC05 | Task brief và handoff giữ objective, dependencies, files, AC, constraints, deliverables, validation và next step |
| D19-AC06 | Ba prompt ngắn có thể dùng để control/resume, chạy task và finish/handoff |
| D19-AC07 | Không application source/dependency/runtime config thay đổi; links và source-of-truth không trùng |

## Dry-run read-only — BL-R06a

1. Registry xác định `BL-R06a · PLANNED`, goal là Content candidate/version store; dependency R02a, R05 và R02b đã `DONE` theo registry.
2. Đường context tìm được: F08/content → publication README/protocol → R05 task/evidence → Content service, DB/trust/data contracts liên quan.
3. Không có file task/brief/checkpoint cho R06a và không có chỉ dẫn mở code R06a trong task hiện hành. Vì thiếu Ready inputs, Control Center phải dừng trước implementation; không được biến chữ “tiếp tục” hoặc dependency đã đạt thành quyền code.
4. Role dự kiến khi task được chuẩn bị: Backend/Platform executor; Product/Architecture review impact/publication invariant; Verification review DATA-01/CMD-03/04 evidence. Mode mặc định là single executor cho brief và dependent implementation; chỉ dùng native subagent cho audit độc lập hoặc write scope tách biệt.
5. Dry-run không tạo task R06a, không sửa source và không đổi trạng thái. Bước mở khóa là người dùng chọn R06a, sau đó điền brief theo template và chuyển `READY` khi đủ context/fixture/acceptance/quyền.

## Checkpoint

DONE 16/10/2026 — Hoàn thành D19-AC01–07 trong phạm vi tài liệu.

Đã thay đổi: rút `AGENTS.md` từ 86 dòng/17.717 byte xuống 46 dòng/6.345 byte; bổ sung Control Center 10 bước, sáu execution roles, native/manual modes, one-writer/integrator rules, persistent handoff và ba prompt; mở rộng task brief/quality; sửa source ownership và các status snapshot ở entry points. Không tạo `docs/ai/`, agent config, skill, MCP hoặc tracker/handoff thứ hai.

Validation:

- Kiểm 11 Markdown entry/workflow files: 0 local link thiếu, 0 code fence lệch.
- Required checks đều đạt: Control Center, hai modes, ba prompts, D19 registry, one-writer rule và Git-baseline limitation.
- Dry-run R06a tìm đúng registry/owner/protocol/dependency và dừng vì thiếu task file/Ready permission; không triển khai R06a.
- Review mâu thuẫn lifecycle/delegation/source ownership không còn P0/P1. Lượt reviewer subagent cuối bị giới hạn usage nên không được tính PASS; integrator đã tự review lại các file và checks trên.
- Không chạy application tests vì thay đổi thuần Markdown/AGENTS/root README. Mọi write của task chỉ nhắm instruction/documentation; repo chưa có `HEAD` nên không thể dùng Git diff để chứng minh baseline trước/sau, giới hạn này được ghi trong workflow.

Handoff: dùng `docs/README.md` → registry → task file → `docs/execution-model.md` để bắt đầu Control Center. Task runtime tiếp theo chưa tự mở; R06a trong dry-run vẫn `PLANNED`.
