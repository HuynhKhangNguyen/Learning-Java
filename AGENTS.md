# Backend Lab — Quy định làm việc

## Đọc đúng phần trước khi làm

1. Task backlog: đọc dòng registry → DoD/checkpoint mới nhất trong brief. Chỉ dùng `docs/README.md` khi chưa rõ owner/path. Maintenance nhỏ ngoài backlog đọc source sở hữu thay đổi, không cần tạo task.
2. Chỉ đọc source được brief dẫn và symbol/file liên quan; checkpoint cũ chỉ khi checkpoint hiện hành còn dẫn tới.
3. Chọn kiểm tra từ đúng mục trong `docs/quality-process.md`; ưu tiên search, range và report tóm tắt thay vì toàn repo/file/log.

## Nguồn sự thật và phạm vi

- Chỉ dẫn hiện tại của người dùng có ưu tiên. Khi quyết định thay đổi, cập nhật source đang sở hữu quyết định đó; không giữ hai hướng trái nhau.
- `docs/delivery-backlog.md` chỉ giữ trạng thái, dependency và link. Brief, acceptance, bảng evidence và checkpoint nằm trong file task được link từ registry.
- Owner docs: `features/` giữ business/LLD, `platform/` giữ kỹ thuật/môi trường, `architecture/` giữ contract xuyên feature, `curriculum/` giữ chương trình; history chỉ để đối chiếu.
- R1 hướng tới nhiều user, login/logout, bài làm và progress server theo owner; Java Backend và DevOps là hai roadmap, Docker thuộc DevOps, admin sau R1. Thiết kế không chứng minh app đã hỗ trợ.
- Kiến trúc hiện hành là cùng repo với `frontend/` và ba microservice trong `backend/`; mỗi service sở hữu database và authorization của mình. Đọc `docs/architecture/README.md` trước khi đổi boundary.
- Một task có một đầu ra chính, ngoài phạm vi, dependency và acceptance đo được. Parent chỉ roll up children; một session thường chọn một leaf. Không tự mở sibling hoặc task kế tiếp.
- Task code chỉ bắt đầu khi chỉ dẫn hiện tại hoặc brief/checkpoint ghi quyền thực thi phù hợp. Trong scope đã giao, tự thực hiện chỉnh sửa, test, sửa lỗi và quyết định cục bộ; thay đổi mục tiêu sản phẩm phải được nêu rõ.

## Điều phối và session

- Khi điều phối/delegation, chỉ đọc các mục Control Center, execution mode và handoff trong `docs/execution-model.md`.
- Main session giữ trách nhiệm tích hợp và nghiệm thu. Native subagent chỉ nhận phần việc độc lập, context tối thiểu và đầu ra kiểm chứng được; một file hoặc vùng source chỉ có một writer tại một thời điểm.
- Không dùng subagent cho task ngắn hoặc giao nhiều agent cùng một câu hỏi. Prompt chỉ gồm ID/path/AC/write scope; handoff chỉ gồm kết luận, file, validation, finding và giới hạn, không chép history/source/log.
- Subagent dùng chung workspace và quyền với main session. Không coi role trong Markdown là agent chạy tự động, không giả chat khác chia sẻ live state, và không tự tạo chat người dùng.
- Trạng thái Git phải được kiểm từ repository thật và tuân theo [Git workflow](docs/platform/repository/git-workflow.md). Khi chưa có baseline `HEAD` đã review, không dùng diff/branch/worktree để chứng minh scope; write song song phải tách file owner và được main review.
- Quyền sửa file không tự cấp quyền stage/commit/branch/worktree/push/merge/tag. Chỉ thực hiện Git mutation khi người dùng hoặc brief/checkpoint hiện hành cấp đúng quyền; commit không bao gồm push hay merge.
- Không tự chạy thao tác có nguy cơ mất dữ liệu như `reset --hard`, `clean`, destructive checkout/restore, xóa branch/worktree, shared-history rebase, amend work người khác hoặc force-push. Handoff chỉ ghi branch/commit/PR khi chúng thật sự tồn tại.
- Không commit, push, merge, deploy, cài dependency, đổi secret hoặc thực hiện thao tác phá huỷ nếu task hiện tại không yêu cầu rõ.

## Quy tắc sản phẩm và nội dung

- Giữ ID nội dung ổn định. Kiến thức dùng chung giữa Backend/DevOps dùng một source nội dung và một tiến độ.
- Phân biệt đã xem, đã chứng minh năng lực và đã áp dụng. Không cấp bằng chứng chỉ từ Next/replay; không trình bày mô phỏng như thực thi Java/Docker thật.
- Không ghi đè dữ liệu học đang tốt khi đọc/import/migrate lỗi. Reset mô phỏng và reset dữ liệu học là hai hành động riêng.
- Bài học mới hoặc được sửa tuân thủ `docs/features/f08-content/authoring/bilingual-standard.md`: English giữ thuật ngữ chuẩn; tiếng Việt giải thích tự nhiên và vẫn giữ thuật ngữ English khi chính xác hơn. Review theory, example, diagram, assessment, feedback và nguồn.

## Comment giải thích code

- Với FE/BE/SQL/scripts/tests, comment tiếng Việt cho logic không hiển nhiên để người mới học backend hiểu; giữ tên API/biến và thuật ngữ English. Không sửa generated/vendor code.
- Class/module/hàm đáng kể mô tả trách nhiệm, input/output, tác động dữ liệu và lỗi; đặt giải thích cạnh invariant dễ vỡ như transaction/lock, authorization/CSRF, idempotency, retry/race và constraint. Không comment getter hoặc dòng tự giải thích.
- Test/fixture phức tạp nêu tình huống và điều chứng minh. Không đưa secret/dữ liệu cá nhân, giữ code chết bằng comment-out hoặc để comment sai behavior.

## Định dạng code dễ đọc

- Code handwritten trong FE/BE/SQL/scripts/tests phải theo formatter và convention hiện có của repo. Không minify hoặc dồn nhiều field, statement, method, constructor, assertion hay nhánh control-flow lên cùng một dòng để giảm số dòng.
- Mỗi statement/declaration đặt trên dòng riêng; method, constructor, lambda dài, `if`/`switch`/`try`, SQL nhiều clause và test nhiều bước phải xuống dòng, indent nhất quán và tách các khối logic bằng khoảng trắng hợp lý. Ưu tiên dòng không quá 100 ký tự; chỉ vượt khi tách dòng làm khó đọc hơn.
- Khi task chạm một symbol đang bị dồn dòng khó đọc, format lại toàn bộ symbol hoặc khối logic liên quan trước khi bàn giao; không lợi dụng task nhỏ để reformat hàng loạt file ngoài scope. Generated/vendor/minified artifact được miễn và không sửa tay.
- Nếu repo đã có format/lint check thì chạy check tương ứng cho file bị ảnh hưởng; không thêm formatter hoặc dependency mới chỉ để phục vụ việc format.

## Kiểm tra và bàn giao

- Dùng runtime, lockfile và helper hiện có. Không đổi framework, thêm dependency hoặc tạo service chỉ vì tên module; thay đổi kiến trúc phải có nhu cầu và task rõ.
- Chọn kiểm tra theo ảnh hưởng trong `docs/quality-process.md`; tài liệu thuần không cần full application suite. Missing suite, zero test, skip hoặc mô phỏng không được báo PASS cho lớp bằng chứng khác.
- Review acceptance, logic biên, security/data ownership và UI thực tế khi liên quan. Không ghi người dùng đã thử, nguồn đã kiểm hoặc test pass khi chưa có bằng chứng.
- Task backlog chỉ checkpoint phần thay đổi so với DoD hiện hành: file, AC còn thiếu, validation thật, giới hạn, quyết định và bước tiếp theo; không chép lại log/evidence còn hiệu lực. Maintenance nhỏ ngoài backlog không cần checkpoint. Chưa đủ DoD thì giữ `IN_PROGRESS`.
