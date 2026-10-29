# Quy trình test và review

Cập nhật 04/10/2026. Áp dụng cho backlog BL-S/BL-D/BL-MS/BL-R/BL-E (BL-I chỉ là lịch sử) trong delivery-backlog.md. Các đối chiếu T/M ở mục 5–6 là lịch sử, không phải chỉ dẫn tiếp tục code cũ. Mục tiêu là phát hiện lỗi gần nơi tạo ra nó, giữ vòng phản hồi nhanh và chỉ tuyên bố hoàn thành khi có bằng chứng phù hợp. Không dùng số lượng test làm thước đo chất lượng.

Quy ước tài liệu04/10/2026: [backlog](delivery-backlog.md) gồm registry trạng thái/dependency và file task theo feature/platform. Ghi lệnh, kết quả và checkpoint vào file task được link; registry chỉ cập nhật trạng thái khi đủ acceptance.

## 1. Năm cổng chất lượng cho mỗi task

Các cổng áp dụng theo loại đầu ra: tài liệu chỉ dùng cổng hành vi/review và kiểm tra tính nhất quán; không bắt chạy build/E2E của app không thay đổi. Nội dung chạy trong app và code dùng những cổng tương ứng ở bảng dưới. Acceptance của task chỉ rõ phần phải làm; ghi lý do cho phần không áp dụng.

1. **Chốt hành vi:** nối tiêu chí task/acceptance ID với thay đổi; ghi rủi ro dữ liệu, nội dung hoặc UX cần kiểm tra.
2. **Kiểm tra nhanh:** content validation, typecheck, lint và unit test logic bị đổi. Chạy trong lúc phát triển.
3. **Kiểm tra tích hợp:** production build và test giữa data → logic → UI. Không mock phần đang cần chứng minh.
4. **Kiểm tra hành trình:** Playwright cho luồng người học quan trọng, reload/Back, keyboard, reduced-motion và kích thước có rủi ro.
5. **Review:** đọc diff/phạm vi, xem giao diện thật, rà độ đúng kiến thức và cập nhật backlog/bằng chứng. Feedback người dùng chỉ được ghi là đã có khi thực sự thử.

Một task chỉ `DONE` khi tiêu chí của nó đạt, test phù hợp pass, giới hạn còn lại được ghi và không còn lỗi P0/P1 chưa xử lý trong phạm vi. Test lỗi do môi trường phải được phân biệt với lỗi sản phẩm.

Với task có code, cổng review và DoD gồm kiểm tra [quy tắc comment giải thích code](../AGENTS.md#comment-giải-thích-code): phần code viết mới/sửa có giải thích phù hợp cho người học, comment khớp hành vi và ràng buộc thực tế. Không dùng số lượng comment làm tiêu chí đạt.

## 2. Chọn test theo loại thay đổi

| Loại thay đổi | Bắt buộc | Review thủ công |
|---|---|---|
| Kế hoạch/requirement/tài liệu thuần | Kiểm tra link, ID, dependency/traceability và trạng thái; không bắt unit/build/E2E app | nhất quán business, scope, acceptance và đầu vào/đầu ra |
| Thiết kế microservices BL-MS | Ownership data/API/module, contract/dependency, phân biệt canonical/replica, links/ID; các task auth/data review thêm race/failure matrix | Walk-through qua boundary; giao dịch local, quyền anonymous/login/owner; ghi protocol chưa chốt; không dùng sơ đồ thay runtime test |
| Bản xem tương tác độc lập | Cú pháp/runtime và tương tác chính; responsive nếu có bố cục | xem ảnh/giao diện, keyboard; không thay bằng test app |
| Metadata/bài học | validator + unit case hợp lệ/sai | mục tiêu, prerequisite, đáp án, nguồn và trạng thái publish |
| Reducer/mô phỏng | unit transition, ma trận input × action, reset/replay | cơ chế kỹ thuật và giới hạn mô hình |
| UI/điều hướng | type/lint/build + E2E luồng bị ảnh hưởng | hierarchy, chữ, focus, mobile, light/dark |
| Chấm bài/tiến độ | unit evidence/version + E2E retry/reload/deep-link | không cấp thành tích từ thao tác không đủ bằng chứng |
| Storage/import | unit migration và failure/atomicity + E2E reload | thông báo lỗi, không ghi đè bản tốt |
| Backend/auth/API nhiều user | API/integration với DB cho quyền A/B, phiên/logout, transaction, idempotency và conflict; E2E đổi tài khoản | Server kiểm ownership trên mọi đường; cache/nháp không lộ hoặc nộp sang user khác |
| Java/Docker lab | test Java/API/Compose + positive/negative | lệnh README từ checkout mới, reset và recovery |
| Local runner | contract + path/origin/timeout/cancel negative tests | security boundary, process còn sót, secret/output |
| Nền build backend R01a | Vendor hashes/runtime + wrapper version/parent validate; helper ca thiếu module/suite/zero/skip/failure và toolchain sai | Parent không executable/shared domain, FE hashes không đổi; không app/DB/E2E PASS từ foundation |
| DB harness R02a | Actual PostgreSQL own roles/crossDB denial, fresh/rerun/checksum/failure rollback, two-connection locks/CAS/timeout; strict required reports; success/failure cleanup, path/process guards, bootstrap regression và dependency hashes | Disposable workspace ownership, secrets/logs, test-only fixtures không vào runtime, không SEC/DATA business PASS từ nền DB |
| Service trust R02b | Actual packaged JAR mTLS8080/8443, CA/SAN/EKU/env/hostname/caller/action/binding/cutoff/own-DB clock deny, bounded outbound faults; required suites/config/case strict, bootstrap/DB regression, cleanup/secret/artifact/tree hashes | Receiver/common-wire foundation khác canonical admission/CSRF/edge; controlled lab clock không pilot bypass, CA signer keys absent trước runtime; không full SEC/WIRE/business PASS |
| Identity credentials R03 | Actual PostgreSQL invite/unique/token races/purpose/expiry/reissue/reset/disable/recovery/rollback; KDF ngoài locks và stale snapshot không effect, Unicode/corpus/benchmark, throttle trước KDF/atomic cap, packaged CLI stdin/config negatives; strict CMD-03/05 SEC12/22 và CMD-04 transport regression, secret/hash/cleanup review | Local credential commit + durable pending intent không là session/revoke ACK; recovery không clear operator disable hoặc dùng lại hash cũ; corpus fixture không chứng minh production breached-password coverage |
| Identity sessions R04a | Actual PostgreSQL preauth single consume/cap/context/CSRF, atomic canonical+framework login/revoke intent rollback, account/KDF race, terminal/orphan/late framework save và DB-clock idle/absolute; packaged HTTP/TLS origin/schema/body/config/error/cookie + browser reorder/multitab trước DONE; strict required CMD-03/04 suite và candidate hashes/cleanup | DB core alone giữ IN_PROGRESS; cookie adapter/HTTP/browser chưa có không SEC full PASS. Pending static two intents không global ACK/COMPLETED; không Spring Session flush hoặc no-store header thay canonical/boundary proof |
| Service bootstrap R01b–d | Selected clean verify với tests thật, config fail-fast và HTTP server/client; executable JAR smoke từ working directory độc lập/stop; actual dependency tree/hash/runtime patch | Own artifact/loopback/boundaries/no cookie/no business readiness, baseline source/FE; DB/trust/business suite thuộc task sau, không lấy bootstrap làm evidence cho chúng |
| Content admission receiver R04b | Actual own PostgreSQL monotonic gate/tombstone/receipt, absent/late admission, two-connection blocking, effect/use rollback and cutoff; packaged mTLS caller/request/config/generation/duplicate/lost-ACK/restart; strict CMD03/04/05 SEC02/04/09/15 cases, helper/smoke/cleanup/source/artifact audit | INT01 controlled provider fixture không là canonical Identity (R04d); Java ContentAccess integration port chưa learner API (R07). Không full revoke COMPLETED, Learning/relay/FE/production qualification |
| Learning admission receiver R04c | Own PG mutation callback/use atomic rollback, gate giữ tới commit/fence wait và fence-first concurrent deny; Origin/CSRF/action/body/context bindings, fresh online deny; packaged mTLS schema/caller/generation/timeout/restart, strict CMD03/04/05 SEC01–05/09/15 và helper/smoke/source/artifact audit | Java hook chưa API-L03/draft/CAS/T1/T2; controlled provider chưa canonical Identity; không coordinator/global COMPLETED/progress/job/production recovery |
| Durable relay foundation R02c | Three own PG producer state/outbox atomic và receiver guard/inbox/effect/receipt/cursor atomic; covered-prefix/gap/poison, immutable authorization/terminal ABORT/no-effect recovery; actual mTLS process crash/lost ACK/restart, wrong ACK, bounded HTTP/TLS faults; DATA15 existing INT02 độc lập strict poison; strict full/selected suites, native selector arguments, helper/guards/smoke/cleanup/source/artifact/dependency/secret audit | Local SQL probe và test-only own-role producer proof port không production publication/lifecycle provider, coordinator, learner work/T1/T2 hoặc restore; focused command/report doubles không thay actual fault/DB proof hoặc full closing helper |

Không viết test chỉ lặp lại implementation hoặc snapshot toàn trang dễ vỡ. Ưu tiên invariant, chuyển trạng thái, dữ liệu biên và hành trình gây thiệt hại nếu hỏng.

R05 catalog/schema2: standalone CMD04 kiểm actual JSON Schema negatives, source snapshot/IDs, references/selectors/DAG/shared stage-DP và public-private-viewed mapping; required named reports nonzero/no-skip và native CLI negatives. Legacy content regression và helper impact review đủ khi không đổi FE/backend/runtime; không bắt chạy auth/DB/browser suite để chứng minh schema. Source parity/counts không thay review chất lượng/EN–VI semantic equivalence hoặc publication qualification.

Với bài học mới/sửa, review theo [chuẩn song ngữ](features/f08-content/authoring/bilingual-standard.md): nguồn và tính đúng, đủ lý thuyết cho mục tiêu, English terminology và EN/VI tương đương; kiểm theory/example/diagram/question/feedback, đổi ngôn ngữ giữ bài làm/kết quả. Schema validator legacy không là evidence độ đầy đủ của bài prototype ngoài catalog.

Với global locale, chạy các route hiện có ở cả English/Tiếng Việt; kiểm navigation/reload/deep link, preference sai fallback an toàn, `html lang`/title/aria, keyboard và 320px. Đổi language giữa simulation/quiz không được remount hoặc xóa state. Mode Việt được review để giữ thuật ngữ kỹ thuật English nhất quán; test không thay semantic review.

## 3. Nhịp chạy tối ưu

### Trong lúc sửa

- Nội dung: `project.ps1 -Task content`.
- Code FE: `project.ps1 -Task check`; code/backend build dùng command theo leaf/test-release, không thay bằng FE check.
- Chạy test Playwright file/luồng bị ảnh hưởng khi đang lặp giao diện; tránh chạy toàn bộ sau mỗi thay đổi CSS nhỏ.

### Kiểm tra theo ảnh hưởng và chốt candidate

- Giữ **một bảng DoD/evidence hiện hành** trong file task: AC/cổng, run hoặc report nguồn, candidate/input được kiểm, trạng thái còn hợp lệ hoặc cần kiểm lại, và hành động còn thiếu. Checkpoint chỉ ghi phần thay đổi; không chép lại toàn bộ bảng hay lịch sử mỗi lượt. Registry vẫn chỉ giữ trạng thái/dependency.
- Trước mỗi lượt chạy, ghi thay đổi nào hoặc bằng chứng thiếu nào khiến lượt đó cần thiết, suite nhỏ nhất đủ kiểm chứng và điều kiện dừng. Nếu không có thay đổi/rủi ro/bằng chứng thiếu mới thì không chạy lại suite đã đạt.
- Xét ảnh hưởng theo source, contract, config, dependency, runtime và công cụ tạo/đánh giá evidence. Đổi auditor có thể cần đánh giá lại report hoặc qualification của công cụ, nhưng không mặc nhiên buộc chạy lại mọi app suite; chỉ chạy runtime lại nếu input hoặc acceptance của chính gate đó yêu cầu. Ghi lý do rõ, không âm thầm nới inventory để giữ PASS.
- Kết quả cũ chỉ được tái sử dụng trong phạm vi đã chứng minh, khi input liên quan và điều kiện môi trường còn tương thích. Input đổi thì giữ report cũ làm lịch sử và đánh dấu gate bị ảnh hưởng cần kiểm lại. JAR có ZIP timestamp khác phải giữ raw hash riêng; bằng chứng byte-content tương đương không thay claim về raw artifact hiện hành.
- Khi suite tổng lỗi, giữ report/log/input và artifact của candidate thất bại trước clean/rebuild. Tái hiện case/selector nhỏ nhất trước, ghi giả thuyết và quan sát phân biệt nguyên nhân. Sau hai lượt cùng lỗi mà chưa có bằng chứng mới, dừng chạy lặp để đọc log hoặc bổ sung chẩn đoán; không tăng timeout sản phẩm, bỏ case hay đổi tiêu chí để làm xanh.
- Chỉ vào vòng nghiệm thu tổng sau khi case lỗi và regression bị ảnh hưởng đạt. Đóng băng candidate trong vòng này; nếu phải sửa, liệt kê gate mất hiệu lực và quay lại kiểm tra có mục tiêu. Vẫn chạy đủ suite tổng/review mà DoD bắt buộc, không dùng test chọn lọc thay chúng.
- Báo tiến độ bằng AC/cổng còn thiếu, lỗi đang chặn và bước kiểm chứng kế tiếp; số lượng test hoặc lượt chạy không phải tỷ lệ hoàn thành. Khi công cụ Goal bị giới hạn usage/budget, ghi checkpoint và báo giới hạn; không tự reset, tạo Goal thay thế hoặc lách giới hạn để tiếp tục.

### Trước khi đóng task

- Với code/nội dung thực thi ảnh hưởng nhiều luồng hoặc hợp đồng dùng chung: `project.ps1 -Task all` (content unit → check → production build → browser test). Với thay đổi hẹp: chạy check/build và test hành vi liên quan; không chạy lại toàn bộ khi không có thay đổi/rủi ro mới. Tài liệu thuần áp dụng bảng loại thay đổi ở trên.
- Xem screenshot các màn thật sự đổi ở desktop 1440px và mobile 390px; thêm dark/reduced-motion khi dùng token hoặc animation mới.
- Rà console/page errors, tab order và URL reload/Back của đường vừa đổi.
- Cập nhật task trong delivery-backlog.md với lệnh/cách kiểm tra, kết quả, môi trường và phần chưa kiểm chứng; không sửa trạng thái lịch sử T01–T05 theo task mới.

### Trước khi bàn giao mốc

- Chạy từ dependency lock trên môi trường sạch khi khả thi.
- Thực hiện checklist end-to-end của mốc và kiểm tra lỗi/recovery, không chỉ happy path.
- Review nội dung theo bảng đáp án/nguồn; review UX trên dữ liệu thật của mốc.
- T10/M1 và T23/M2 phải có báo cáo phát hiện, mức độ và quyết định. Phản hồi người dùng là một nguồn bằng chứng riêng, không thay thế test tự động.

## 4. Review hai lượt cho dự án một người

Lượt tác giả kiểm tra ngay theo acceptance criteria. Sau khi test xanh, nghỉ ngữ cảnh rồi review lại theo bốn góc:

- **Đúng:** kết quả khớp contract và nguồn; mô phỏng không giả thành thực thi thật.
- **Học được:** người mới biết phải làm gì, thấy hệ quả và hiểu vì sao; text không dồn quá nhiều trước thao tác.
- **Bền:** ID/version ổn định, failure có đường phục hồi, không mất dữ liệu.
- **Dùng được:** keyboard, focus, mobile, contrast, reduced motion và trạng thái loading/empty/error.

Finding ghi `P0` mất dữ liệu/an toàn hoặc không thể học; `P1` sai kết quả/chặn luồng chính; `P2` khó hiểu hoặc lỗi nhánh phụ; `P3` tinh chỉnh. Không bàn giao mốc với P0/P1 mở. P2/P3 phải có task hoặc quyết định không làm.

### Review khi có delegation

- Control Center/Integrator chịu trách nhiệm cuối cho scope, integration và evidence. Báo cáo của subagent hoặc manual chat là input cần review, không tự là PASS hay trạng thái task.
- Trước khi nhận output, integrator kiểm đúng task, write scope, file thực sự đổi và invariant liên quan; thay đổi ngoài scope phải được loại bỏ hoặc đưa thành finding riêng.
- Một file chỉ có một writer tại một thời điểm. Audit/review read-only có thể song song; write song song chỉ dùng cho tập file độc lập và phải có thứ tự tích hợp rõ trong brief.
- Executor ghi command/kết quả do chính nó quan sát. Integrator chạy hoặc xác minh cổng tích hợp bị ảnh hưởng và liên kết evidence vào task; không nhân đôi toàn bộ log trong checkpoint.
- Reviewer độc lập ưu tiên kiểm acceptance, negative/edge cases, security/data ownership và claim vượt bằng chứng. Finding P0/P1 quay về executor hoặc integrator trước khi task được `DONE`.

## 5. Kết quả rà kế hoạch cũ — tham chiếu lịch sử

- T04 đưa validator vào `check`; đây là nền cho T11/T12, không chờ đến lúc đã biên soạn 15 bài.
- T05 đã có shell và Journey sớm, nhưng chỉ hoàn thành khi catalog quyết định chính xác bài nào mở được; hiện bài mẫu vẫn `draft`.
- T06–T09 giữ thành một lát cắt dọc: player → simulator → challenge/evidence → persistence. Mỗi task thêm unit cho logic của nó và một E2E nối vào luồng đã có.
- Accessibility là yêu cầu của mọi UI task. T22 là đợt audit, tối ưu tải và sửa tồn đọng, không phải lần đầu kiểm tra keyboard/mobile.
- T12 chốt template trước khi T14/T15/T17 nhân nội dung. Mỗi bài cần review kỹ thuật và học thuật trước `published`.
- T18–T20 cần test failure và version từ đầu vì đây là vùng có nguy cơ mất dữ liệu cao nhất.
- T24 phải khóa security boundary trước T28. T28 không bắt đầu khi path/origin/task allowlist/timeout/cancel còn mơ hồ.
- T30 là CI và đóng gói chính thức; các lệnh local thống nhất được tạo từ bây giờ để CI sau này chỉ gọi lại cùng pipeline.

## 6. Ma trận bàn giao cũ — tham chiếu lịch sử

| Mốc | Bằng chứng tự động | Review bắt buộc |
|---|---|---|
| T04 | validator + dữ liệu sai/hợp lệ + build/UI smoke | schema, ma trận API 500 |
| M1 / T10 | unit simulator/evidence/storage + E2E toàn luồng | usability thật, mobile/dark/reduced motion, giới hạn mô phỏng |
| M2 / T23 | 15 bài valid + search/progress/import E2E | nội dung từng bài, a11y, hiệu năng baseline |
| M3 / T31 | Java/API/Compose/runner positive và negative | cài mới, lỗi môi trường, timeout/cancel/reset, ranh giới an toàn |

Chi tiết lệnh hiện tại ở README. Khi thêm test suite mới, nối vào `test:all`; CI tương lai không được có quy trình khác local.

## 7. Cổng cho mốc mới

Microservices v3 MS04: [test-release plan](delivery/test-release-plan.md) là mapping SEC-01–30/DATA-01–30→leaf owner/FX-01–10/CMD-01–11/evidence và REL-01–08. [Implementation plan](delivery/implementation-plan.md) giữ stack/budgets/ops/provider qualification. Test trên DB/HTTP/cert/storage thật cho boundary tương ứng; local filesystem/mocks không proof off-host, FE smoke không proof auth. Missing required case/suite/zero tests/skip fail release; CI/local cùng runner, artifacts per service/N-N-1 compatibility. Current backend commands chỉ contract chưa tạo/chạy.

[D14 review](architecture/readiness-review.md) bổ sung kiểm **fixture/môi trường có trước task tiêu thụ**, không chỉ DAG IDs: R31a base backend/TLS E2E trước R14/R24a, R02b receiver test không claim actual edge PASS. WIRE-01–06 common contract phải đi cả schema/provider/FE layer tương ứng; proxy spill sentinel và backup coordinator/replica restart/stale resume có evidence riêng. R15/R20 checkpoint demo review sớm, feedback learner chỉ ghi khi thử thật; R34 vẫn gate nghiệm thu cuối.

| Mốc | Bằng chứng cần có |
|---|---|
| R0 v2 / BL-D09 | BL-S02/03 về account/ownership/submission/server data, BL-S04 catalog DevOps, mapping requirement→module→task, mẫu/rubric/progress/UX nhất quán; review trước code |
| R1 v3 / MS04 backlog | REL-01–08: ba artifacts/DB/service trust/compatibility/SEC/DATA/Collections/privacy/UX/actual provider/isolated restore/learner reports; no P0/P1/required skip. R31d pilot không accepted trước R34; BL-I14/D08 một-app chỉ lịch sử |
| R2 / từng batch | Review từng mục và nguồn/phiên bản; đường prerequisite liên tục; lab có bằng chứng đúng loại; không chờ xong toàn khóa mới nghiệm thu |
| R3 / task runner được phân rã sau | Positive/negative boundary path/origin/task/cancel/timeout, output thật, không process mồ côi, recovery; không đủ bằng UI mock |

Review riêng không bắt buộc là agent hoặc chat riêng. Task nhỏ tự review sau kiểm tra; task thay đổi contract/progress/storage và mốc release cần một lượt rà độc lập với cách tác giả vừa triển khai. Native subagent hoặc manual chat chỉ dùng theo protocol trong [execution model](execution-model.md#10-execution-role-và-chế-độ-phối-hợp); main integrator vẫn sở hữu kết luận và trạng thái.
