# Git workflow — Backend Lab

[Task initial baseline](tasks/BL-R00a.md) · [Execution model](../../execution-model.md) · [Quality process](../../quality-process.md) · [Finding STR-01](../../architecture/project-structure-review.md#3-findings-và-mức-ưu-tiên)

File này là owner duy nhất của Git policy. `AGENTS.md` giữ rule bắt buộc ngắn; execution model chỉ giữ orchestration/integration và link về đây. Task/checkpoint ghi quyền và evidence của từng lượt, không sao chép policy.

## 1. Xác định giai đoạn từ repository thật

Trước mọi quyết định Git, kiểm repository root, `.git`, `git rev-parse --verify HEAD`, `git status`, `git ls-files`, branch/ref và remote thật. Có file `.git` hoặc symbolic branch chưa đồng nghĩa có baseline. Baseline chỉ hợp lệ khi initial commit tồn tại, inventory/evidence đã được review và task BL-R00a được Control Center nghiệm thu.

Các lệnh read-only như `git status`, `git ls-files`, `git check-ignore`, `git rev-parse`, `git branch --show-current`, `git remote -v` và đọc config được dùng để audit. Không diễn giải `git diff` rỗng là clean/PASS khi tracked count bằng 0 hoặc `HEAD` chưa tồn tại.

## 2. Trước initial baseline

- Không dùng `git diff`, branch hoặc worktree để chứng minh scope/cô lập.
- Không giả working tree clean. Scope review dùng task write scope, explicit inventory, source ownership, timestamp khi phù hợp, checksum và validation thật.
- Write song song chỉ khi mỗi writer có tập file hoàn toàn tách biệt; integrator review toàn bộ output.
- Không `git add`, commit, branch, tag, push, tạo/sửa remote hoặc mutation index/ref/config nếu task và người dùng chưa cấp đúng quyền.
- Checkpoint không ghi branch/commit/PR giả; nêu rõ repository chưa có baseline và cách review đã dùng.

## 3. Task initial baseline

BL-R00a là leaf duy nhất sở hữu initial baseline. Nó không trộn feature/refactor và thực hiện theo các gate sau.

### Gate A — audit và hygiene

1. Xác minh root, `.git`, unborn branch/ref, tracked/untracked/ignored inventory và remote.
2. Lập danh sách file dự kiến track theo owner/path/type/size; review file mới, binary, generated/vendor và machine-specific path.
3. `.gitignore` phải loại trừ secret/credential thật, real `.env`, runtime private key/certificate/keystore, dependency cache, `node_modules`, `target`, `dist`, test output, coverage, cache, `.tools`, artifact tái tạo, database/runtime data, log/crash dump, IDE và OS temporary files.
4. Không dùng ignore rộng làm mất source, lockfile, migration, contract, fixture hợp lệ, wrapper/license hoặc tài liệu cần bàn giao. Test certificate/key chỉ được track nếu task chứng minh đó là non-secret deterministic fixture cần thiết.
5. Review secret/sensitive patterns bằng helper hiện có hoặc scoped scan; không cài scanner/dependency mới chỉ cho baseline. Mọi hit phải classify, không chỉ đếm.
6. Review file lớn: mọi file từ 1 MiB phải có owner/rationale; binary/generated bất thường phải loại hoặc được chấp nhận rõ. Không dùng Git LFS hay vendor artifact mới nếu chưa có task.
7. Chốt `.gitattributes` cho text/binary/EOL; wrapper/script phải chạy được trên checkout Windows/Unix theo scope. Không normalize hàng loạt mà chưa review diff bytes.
8. Chọn validation theo `docs/quality-process.md`. Source không đổi không bắt full suite vô nghĩa, nhưng baseline phải có đủ check chứng minh checkout chứa input hợp lệ; retained evidence chỉ được dùng khi captured hashes còn current.

### Gate B — proposed index review

Trước mutation, executor trình proposed tracked/ignored inventory, hygiene changes, validation và các decision còn lại. `git add` chỉ được chạy khi session/task đã có explicit stage authorization. Sau stage, review toàn bộ index bằng status, cached stat/name-status/diff, file mode/EOL, secret/generated/large-file checks và `git diff --cached --check`.

Không stage path theo glob chưa review, không dùng `git add -f` để vượt ignore nếu task không chỉ đích danh file và lý do. Untracked output/secret không được “dọn” bằng destructive command; chỉ classify/ignore hoặc xin quyền xử lý riêng.

### Gate C — initial commit

Initial commit chỉ được tạo khi người dùng cấp quyền commit rõ và đã chốt integration branch name. Default message nếu chưa có convention khác:

```text
chore(repository): establish initial reviewed baseline [BL-R00a]
```

Sau commit, ghi commit ID, parent state (root commit), branch, exact staged summary, working-tree status và validation revision. Không tự tạo remote, push, tag hoặc PR. STR-01 chỉ được Control Center đóng sau khi review commit/evidence thật.

## 4. Sau khi có baseline hợp lệ

### Branch

- Một leaf code hoặc docs đáng kể dùng task branch khi brief cho phép. Maintenance nhỏ có thể dùng checkout hiện tại nếu Control Center ghi branch không thêm giá trị.
- Tên mặc định: `codex/<task-id-lowercase>-<short-slug>`, chỉ lowercase và dấu gạch ngang; ví dụ `codex/bl-e07b-http-flow-scene`.
- Base/integration ref phải được task chỉ ra và resolve thật. Không tự đoán `main`, `master` hoặc remote default; không làm trực tiếp trên integration branch khi task yêu cầu branch riêng.

### Worktree

- Chỉ dùng khi có `HEAD`, task cho phép, công việc độc lập, write scope không giao nhau và integrator rõ.
- Không dùng worktree để che conflict ownership/contract; một owner file chỉ có một writer.
- Trước archive/remove, kiểm uncommitted/untracked/ignored artifact và bảo toàn phần cần giữ. Không xóa worktree/branch chứa work chưa tích hợp.

### Commit authorization và nội dung

- Quyền sửa không tự cấp quyền commit. Chỉ commit khi người dùng yêu cầu hoặc brief/checkpoint hiện hành ghi rõ.
- Commit permission không bao gồm push, merge, tag, release hoặc deploy. Subagent không tự commit nếu assignment không ghi rõ; mặc định integrator sở hữu commit.
- Mỗi commit có một mục đích review được. Không trộn feature, refactor, formatting diện rộng và generated output.
- Không commit secret, runtime/local data, dependency cache, generated build/test output, sensitive log, file cá nhân hoặc IDE state. Lockfile/migration/contract được commit khi task thật sự thay đổi chúng.
- Commit không thay checkpoint hoặc validation evidence.

### Commit message

Convention mặc định:

```text
<type>(<scope>): <mô tả ngắn> [<TASK-ID>]
```

Type tối thiểu: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `build`, `ci`. Nếu repository sau này chốt convention khác, cập nhật file owner này một lần; không dùng nhiều convention song song.

### Pre-commit review

1. Đọc lại acceptance/write scope và xác định revision được test.
2. Chạy formatter/lint/check/test phù hợp; missing/zero/skip/hidden failure không PASS.
3. Kiểm `git status`, unstaged/staged diff, `git diff --check` và `git diff --cached --check`.
4. Review file mới/xóa/rename, file mode, executable bit, EOL và binary.
5. Kiểm secret, generated output, artifact ngoài scope và staged changes theo task ID.
6. Nếu validation chạy trước thay đổi cuối, chạy lại phần affected; không gắn evidence source cũ vào commit mới.

### Handoff

Task/checkpoint vẫn là handoff bền; không bắt commit chỉ để bàn giao. Chỉ ghi Git evidence khi thật sự tồn tại:

- branch và base revision;
- commit revision;
- working-tree status và file chưa commit;
- conflict/limitation;
- validation nào gắn với revision nào;
- PR/remote chỉ khi thật sự được tạo.

### Integration và conflict

- Integrator review source/evidence trước integration; agent báo xong không đồng nghĩa merge.
- Không merge khi acceptance thiếu hoặc evidence stale. Branch đổi contract chung được tích hợp tuần tự.
- Conflict business/architecture do owner/integrator hiểu cả hai phía giải quyết; không tự chọn `ours`/`theirs`.
- Sau conflict resolution chạy lại affected validation. Merge không cấp quyền deploy hoặc mở task kế tiếp.

### Remote, push và pull request

- Local commit không cho phép push. Chỉ push khi người dùng/task cấp quyền rõ.
- Không tự tạo/thay remote, không force-push mặc định.
- Chỉ tạo PR khi được yêu cầu; PR mô tả behavior, scope, validation và limitation thật.
- Không merge PR nếu chưa có quyền merge rõ.

## 5. Thao tác có nguy cơ mất dữ liệu

Không tự chạy `git reset --hard`, `git clean -fd` hoặc biến thể, destructive checkout/restore, xóa branch/worktree có work, rebase shared history, amend commit người khác, force-push, xóa tag hoặc remote branch.

Nếu thật sự cần: kiểm trạng thái, bảo toàn thay đổi, nêu chính xác dữ liệu có thể mất và chỉ làm sau explicit authorization. Không dùng stash, patch hoặc temporary commit để tự động che thay đổi không rõ owner; nếu được phép dùng, checkpoint phải ghi owner, nội dung và cách phục hồi.
