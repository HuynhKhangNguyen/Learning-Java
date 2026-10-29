# BL-R00a — Initial reviewed Git baseline

[Registry](../../../delivery-backlog.md) · [Git workflow](../git-workflow.md) · [STR-01](../../../architecture/project-structure-review.md#3-findings-và-mức-ưu-tiên) · [Quality](../../../quality-process.md) · [DoD](../../../delivery/contract.md)

## Objective, outcome và trạng thái

**Objective:** tạo initial Git baseline được review cho repository hiện hành, sau khi inventory/ignore/secret/large/binary/EOL/validation và staged index đều đạt. Task không thay đổi feature behavior và không tự tạo remote/push.

**Outcome:** repository có root commit và integration branch name được người dùng chốt; checkout có thể dùng diff/branch/worktree/CI/release input một cách có bằng chứng. STR-01 chỉ đóng sau Control Center review commit thật.

**Trạng thái 29/10/2026: `DONE`.** Gate 1–3 đạt; Control Center/user chốt integration branch `main`, cấp quyền stage/branch/commit local và nghiệm thu root baseline. Remote/push/PR/tag/release không được thực hiện.

## Audit đầu vào hiện hành

- Root: `C:/Users/khang.nguyen-huynh/Documents/ChatGPT/Learning Java`; `.git` là directory, inside work tree.
- `HEAD` không tồn tại; symbolic branch `master`; tracked count 0; 561 untracked entries; remote count 0.
- `.gitignore` có 19 dòng, đã ignore dependency/build/test/tool/quality/Python outputs chính. Bốn JVM crash/replay logs chưa ignore.
- `.gitattributes` có 3 dòng, chỉ pin LF/CRLF cho Maven wrappers.
- Candidate inventory có `.codex/config.toml`; audit key-only không thấy absolute Windows path hoặc secret-named key, nhưng task vẫn phải quyết định đây là shared repo config hay machine state.
- Không có non-ignored `.env`, private-key/certificate, database hoặc coverage file theo filename scan; kết quả này chưa thay content secret review.

## Dependency và vị trí graph

- Dependency: BL-R00 repository layout `DONE`.
- BL-R00a là dependency bắt buộc của BL-R31c CI/artifact compatibility; R31d/recovery nhận transitively qua R31c.
- Không block feature tasks chưa cần checkout/version input, branch hoặc worktree.
- Sau baseline, từng task vẫn cần quyền branch/worktree/commit riêng; BL-R00a không cấp quyền hàng loạt.

## In scope và write scope

- Read-only audit/inventory toàn repository.
- Chỉ hygiene edit cần thiết tại `.gitignore`, `.gitattributes` và task/policy/checkpoint owner; không sửa feature source để “làm baseline đẹp”.
- Review/validate proposed tracked inventory; stage/index và initial commit chỉ sau explicit authorization trong session.
- Evidence ghi tại file task/checkpoint; generated scan/report có thể ở ignored `artifacts/quality/R00a/**` nếu cần, không bắt commit evidence tái tạo.
- Không tự xóa logs/cache/output; ignore/classify hoặc xin quyền riêng nếu cần xử lý vật lý.

## Out of scope

- Feature/refactor/format diện rộng, dependency upgrade, secret rotation, source behavior change.
- Remote, push, PR, merge, tag, release, CI implementation, deploy.
- Worktree/task branch trước baseline; destructive reset/clean/checkout/restore, stash, rebase hoặc force operation.

## Acceptance criteria

| AC | Điều kiện quan sát được | Evidence bắt buộc |
|---|---|---|
| R00a-AC01 — Repo identity | Root/`.git`/unborn ref/remote/tracked/untracked/ignored state được ghi; integration branch name do user chốt, không đoán | Read-only command summary |
| R00a-AC02 — Ignore hygiene | Required secret/env/key/cache/dependency/build/test/coverage/tool/artifact/DB/runtime/log/IDE/OS outputs bị ignore; valid source/lock/migration/contract/fixture/docs không bị mất | `git check-ignore -v` positive/negative matrix |
| R00a-AC03 — Inventory | Mọi proposed tracked file có path/type/size classification; file ≥1 MiB, binary/generated/vendor và `.codex/config.toml` có decision/rationale | Inventory count/summary + exceptional-file review |
| R00a-AC04 — Sensitive review | Secret/sensitive scan phù hợp, mọi hit classify; không stage private/runtime secret hoặc sensitive log | Scan command/result/findings |
| R00a-AC05 — EOL và validation | `.gitattributes`/file mode/EOL review đạt; validation risk-based theo quality process và current inputs, không fake full PASS | Exact commands/results/limits |
| R00a-AC06 — Staged review | Sau explicit stage authorization, toàn index được review: status/stat/name-status/diff/mode/EOL/secret/generated/large/out-of-scope và cached diff check | Staged summary + zero unresolved P0/P1 |
| R00a-AC07 — Authorized root commit | Chỉ sau explicit commit authorization, root commit đúng reviewed index/message/branch; working tree và untracked/ignored limits ghi rõ; không remote/push | Commit ID, branch, root-parent proof và revision-bound validation |

## Definition of Done

Đạt AC01–07 và DONE-01–05; initial commit/evidence được Control Center review; STR-01 có thể chuyển CLOSED ở owner architecture doc. Nếu chưa có explicit stage/commit authorization hoặc branch decision, task không `DONE`; giữ `PLANNED`/`IN_PROGRESS` với exact gate còn thiếu. Empty diff, zero tracked file hoặc proposed inventory không thay commit evidence.

## Execution role, mode và approval gates

- Role: Repository/Platform executor; Control Center là integrator/reviewer.
- Mode: Mode B separate session, một writer. Không subagent/worktree/branch vì chưa có baseline.
- Gate 1: hoàn tất audit/hygiene/proposed inventory/validation bằng read-only Git; trình Control Center/user.
- Gate 2: chỉ chạy `git add` sau explicit stage authorization.
- Gate 3: review staged index; chỉ commit sau explicit commit authorization và branch-name decision.
- Executor ghi checkpoint và bàn giao tối đa `REVIEW`; không tự đóng STR-01, push, mở PR hoặc task tiếp theo.

## Checkpoint planning ban đầu — 29/10/2026

- Brief/policy/registry đã được tạo từ read-only audit. Không Git mutation nào được thực hiện.
- Blockers: user chưa cấp quyền stage/commit; integration branch name chưa được chốt. Current symbolic `master` là fact audit, không phải decision giữ tên đó.
- Bước tiếp theo chỉ khi user yêu cầu: mở session BL-R00a, thực hiện Gate 1 và dừng ở approval gate trước `git add`.

## DoD/evidence hiện hành — Gate 1, 29/10/2026

| AC/cổng | Evidence hiện hành | Trạng thái / bước còn lại |
|---|---|---|
| AC01 — repo identity | Root đúng workspace; `.git` là directory; inside work tree; `git rev-parse --verify HEAD` thất bại; symbolic ref và `git branch --show-current` đều là `master`; tracked 0; remote 0. Sau hygiene có 559 file candidate và 571438 file ignored hiện hữu. | Gate 1 đạt. `master` chỉ là fact; user chưa chốt integration branch. |
| AC02 — ignore hygiene | `.gitignore` bổ sung đúng hai rule hẹp `backend/hs_err_pid*.log` và `backend/replay_pid*.log`. Matrix `git check-ignore` đạt 15 positive cho dependency/build/cache/test/env/tool/artifact/Python/JVM/OS và 10 negative cho source/lock/wrapper/migration/contract/fixture/docs/project config; `.env.example` được xác minh visible bằng non-verbose exit 1. | Gate 1 đạt; staged matrix phải kiểm lại sau khi được phép stage. |
| AC03 — proposed inventory | `git ls-files --others --exclude-standard` + filesystem metadata: 559 file, 5906989 bytes sau checkpoint edits. Theo owner trước checkpoint cuối: backend 223, docs 189, frontend 52, scripts 32, contracts 30, ops 23, còn lại 10. Theo loại chính: 195 Markdown, 179 Java, 35 JSON, 33 SQL, 24 TS, 17 Python, 13 PNG, 13 TSX; không file candidate nào ≥1 MiB. 13 PNG tổng 920965 bytes là QA evidence được owner docs link; không thấy vendor/generated archive/class/database. `.codex/config.toml` là shared project policy (agent fan-out và project plugin toggles), không có absolute path/secret-named value nên đề xuất track. | Gate 1 đạt; index thật chưa tồn tại. |
| AC04 — sensitive review | Filename scan không thấy `.env`, private key/certificate/keystore/database/credential/secret file trong candidate. Scoped strong-content scan không thấy private-key block, AWS/GitHub/Slack/OpenAI token pattern. Assignment-like scan có 19 file: runtime random-secret generation/config parsing/validation, test-only deterministic password strings và Maven wrapper variable; không phải credential thật. Bốn JVM diagnostics được classify sensitive machine/runtime log và ignore, không xóa. | Gate 1 đạt; chạy lại trên staged paths ở Gate 2. |
| AC05 — EOL/validation | `.gitattributes` thêm `* text=auto`, giữ `mvnw=LF`, `mvnw.cmd=CRLF`, đánh dấu `*.png binary`. Byte scan: 13 binary PNG, 477 LF, 23 CRLF, 46 mixed text; không rewrite vật lý. `git check-attr` trả đúng wrapper/PNG/text policy. `project.ps1 -Task content`: 8/8 pass; `project.ps1 -Task check`: runtime/content/typecheck/ESLint exit 0; `services.ps1 -Task validate`: Maven enforcer BUILD SUCCESS; 35 JSON + 4 XML parse; relative links trong bốn repository-owner docs resolve; registry/task/STR-01 state nhất quán. | Gate 1 đạt. File mode, normalized staged bytes và `diff --cached --check` chỉ kiểm được ở Gate 2. |
| AC06 — staged review | Control Center/user đã cấp quyền Gate 2; `git add --all -- .` stage đúng 559/559 file, toàn bộ `A`, 5897928 blob bytes và 60291 insertions sau checkpoint update; working/candidate ngoài index đều 0. Index có 558 mode `100644` và `backend/mvnw` được sửa index-only thành `100755`; 546 text blob LF và 13 PNG `-text`. Không có file ≥1 MiB, generated/vendor/archive/sensitive filename hoặc strong credential pattern; assignment-like 20 file được classify là runtime random/config handling, test fixtures hoặc prose. Ignore matrix 15 positive/10 negative đạt và bốn JVM diagnostics không nằm trong index. `git diff --cached --check` exit 2: 3 trailing-whitespace và 45 blank-at-EOF finding có sẵn trong source/vendor docs; không có P0/P1, không sửa feature/vendor source ngoài write scope. | Gate 2 đạt trong phạm vi staged review; chờ integrator nghiệm thu, branch-name decision và explicit commit authorization. |
| AC07 — root commit | User cấp full local Git authorization trong phạm vi BL-R00a; Control Center chốt branch `main`. Root commit `5afd0a197f5471b6095be470ccfb1c4128ecca6f` có message `chore(repository): establish initial reviewed baseline [BL-R00a]`; `git rev-list --parents -n 1 HEAD` tại revision này chỉ trả chính commit ID, chứng minh không có parent. Commit có đúng 559 file/60291 insertions, `mvnw` mode `100755`; sau commit staged/unstaged/visible-untracked đều 0, ignored hiện hữu 571438, remote 0. Validation risk-based được chạy lại trên revision này và đạt. | Đạt; commit docs hẹp sau đó chỉ ghi resulting evidence/status và đóng STR-01. |

## Checkpoint Gate 1 — executor handoff, 29/10/2026

- Role/mode/write scope: Repository/Platform executor, Mode B separate session, một writer. Chỉ sửa `.gitignore`, `.gitattributes`, registry, task checkpoint và planning checkpoint STR-01; không sửa FE/BE/contracts/content/scripts/CI behavior.
- Repo state cuối Gate 1: unborn `master`, tracked 0, candidate 559 (5906989 bytes), ignored hiện hữu 571438, remote 0. Không chạy `git add`, commit, branch/worktree/tag/remote/push/PR/merge/rebase/reset/clean/stash/restore hoặc xóa vật lý output.
- Bốn file `backend/hs_err_pid16316.log`, `hs_err_pid29884.log`, `replay_pid16316.log`, `replay_pid29884.log` là JVM fatal-error/replay diagnostics phụ thuộc máy; kích thước lần lượt 64732, 65385, 334317 và 1846573 bytes. Chúng có thể chứa path/command/process state, không phải source/evidence cần bàn giao, nên được ignore bằng rule hẹp và vẫn còn nguyên trên disk.
- Machine-specific literals còn trong proposed docs được classify: root hiện hành trong task audit; WSL download path trong environment request; hai visualization path trong history/prototype docs. Đây là recorded environment/history text, không phải runtime config hoặc secret. Không thấy candidate machine-state/vendor/generated output khác.
- Validation limits: không chạy full backend verify, DB, HTTP/TLS, browser hoặc security suite vì Gate 1 chỉ đổi repository hygiene/docs và các suite đó thuộc leaf owner; PASS trên không chứng minh runtime/release. Mixed-EOL hiện hữu sẽ chỉ được đánh giá trên normalized index sau authorization; Windows filesystem chưa chứng minh executable bit của `mvnw`.
- File thực sự đổi: `.gitignore`, `.gitattributes`, `docs/delivery-backlog.md`, file task này và `docs/architecture/project-structure-review.md`. `docs/platform/repository/git-workflow.md` đã đủ policy nên không đổi.
- Finding P0/P1: không có trong Gate 1. STR-01 vẫn `OPEN`; task giữ `IN_PROGRESS` vì AC06/AC07 chưa được phép thực hiện.
- Bước tiếp theo duy nhất: Control Center review Gate 1, user chốt giữ `master` hay integration branch name khác và cấp explicit stage authorization. Sau đó mới chạy Gate 2; commit cần một authorization riêng sau staged-index review.

## Checkpoint Gate 2 — staged-index handoff, 29/10/2026

- Authorization: Control Center/user cấp quyền riêng cho stage/index mutation Gate 2; chưa cấp commit hoặc branch rename. Lần `git add` trong sandbox đầu tiên thất bại do không ghi được `.git/index.lock` và không đổi index; cùng lệnh được user phê duyệt chạy ngoài sandbox, exit 0.
- Staged scope: 559 file, tất cả status `A`, đúng owner counts Gate 1; `git diff --cached --stat` báo 60291 insertions sau checkpoint update. Staged blob total 5897928 bytes; không file ≥1 MiB. Index và working tree parity: unstaged 0, visible untracked 0.
- Mode/EOL: audit đầu phát hiện `backend/mvnw` là `100644`; Gate 2 sửa index-only thành `100755`. Mode cuối: 558 file `100644`, một file `100755`. `git ls-files --eol`: 546 text blob `i/lf`, 13 PNG `i/-text`; wrapper attributes vẫn `mvnw eol=lf`, `mvnw.cmd eol=crlf`, PNG binary.
- Secret/generated/scope: staged filename scan không thấy secret/env/key/cert/keystore/database; strong token/private-key patterns không hit. 20 assignment-like files là 19 runtime/test classifications từ Gate 1 cộng một lifecycle protocol prose match, không phải credential. Không có node_modules/target/dist/cache/quality artifact/class/JAR/archive/database; 13 binary chỉ là linked QA PNG. `.codex/config.toml` vẫn được chấp nhận là shared project policy.
- Ignore verification: matrix 15 positive/10 negative PASS sau stage; JVM crash/replay index count 0. Không dùng `git add -f`, không xóa output hoặc vượt ignore.
- Whitespace review: `git diff --cached --check` exit 2 với 48 finding thực (3 trailing whitespace, 45 blank line at EOF; output có thêm ba context lines). Đây là hygiene nợ có sẵn trong vendor NOTICE và source/docs/SQL, không có P0/P1 và không được sửa trong baseline write scope. Integrator quyết định chấp nhận cho root baseline hoặc mở cleanup owner riêng; không làm mất staged inventory.
- Git state: `HEAD` vẫn chưa tồn tại, symbolic branch vẫn `master`, remote 0. Không commit, branch/rename, worktree, tag, remote, push, PR, merge, rebase, reset/clean, stash hoặc destructive operation.
- Hai checkpoint docs đã được restage; staged count/bytes/stat, mode/EOL, secret/generated/ignore và whitespace checks bị ảnh hưởng đã được chạy lại. Task vẫn `IN_PROGRESS`, STR-01 vẫn `OPEN`; bước tiếp theo là Control Center nghiệm thu AC06, user chốt integration branch name và cấp commit authorization riêng.

## Checkpoint Gate 3 — DONE, 29/10/2026

- Authorization/decision: user cấp quyền local Git để hoàn tất task và yêu cầu commit; Control Center giới hạn vào BL-R00a, chốt integration branch `main` và chấp nhận 3 trailing-whitespace + 45 blank-at-EOF finding là hygiene nợ không P0/P1 ngoài write scope baseline.
- Root commit: `5afd0a197f5471b6095be470ccfb1c4128ecca6f`, message `chore(repository): establish initial reviewed baseline [BL-R00a]`, branch `main`. `git rev-list --parents -n 1` chỉ có một token nên đây là root commit; committed summary đúng 559 file, 60291 insertions, mode `100755` cho `backend/mvnw`.
- Revision-bound validation trên `5afd0a1`: `scripts/project.ps1 -Task content` PASS 8/8, zero fail/skip; `scripts/project.ps1 -Task check` exit 0 cho runtime/content/typecheck/ESLint; `scripts/services.ps1 -Task validate` Maven enforcer `BUILD SUCCESS`; 35 JSON + 4 XML parse PASS; relative links trong bốn repository-owner docs resolve.
- State ngay sau root commit: tracked 559; staged 0; unstaged 0; visible untracked 0; ignored hiện hữu 571438; remote 0; branch `main`. Bốn JVM diagnostics vẫn còn trên disk nhưng ignored, không nằm trong commit.
- Commit docs/checkpoint thứ hai được phép chỉ để lưu resulting root commit ID, đổi registry sang `DONE` và đóng STR-01; review phải xác nhận chỉ ba owner docs thay đổi. Commit docs này không thay root-parent proof hoặc mở thêm scope.
- AC01–AC07 và DONE-01–05 đạt, không còn P0/P1 trong phạm vi. Không tạo remote/push/PR/tag/release/deploy và không mở task tiếp theo.
