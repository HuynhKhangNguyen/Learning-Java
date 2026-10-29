# Data protocols v3 — microservices R1

BL-MS03 · 30/09/2026 · Thiết kế, chưa có Java/DB/service runtime. Nguồn hiện hành cho delivery, publication, lifecycle và recovery giữa ba service; thay cơ chế publication/reset/restore một-DB ở D08. [Publication LLD](../features/f08-content/publication/protocol.md) và [lifecycle/recovery LLD](lifecycle-recovery-protocol.md) cụ thể hóa các state và giao dịch. [MS02 session](../features/f10-identity-access/sessions/protocol.md) giữ auth barrier; [catalog](service-boundaries.md) giữ ownership.

## 1. Brief và quyết định

- Goal: giữ T1/T2 local Learning; publish/withdrawal có điểm hiệu lực; reset/delete/replay/restore không hồi sinh dữ liệu hoặc quyền/case đã biết. REQ-05/06/09/10/15/16, MD01/04–07/10/11; dependency MS02 DONE.
- Scope: transport/envelope/inbox/outbox, bundle/phase API INT-03/04/05, policy gates, journal/backup/recovery và failure expected. Ngoài phạm vi: code, broker/provider mới, SQL/OpenAPI thực thi, runner/admin/SLA.
- Plan: chốt invariant → wire/delivery → publication → reset/delete/exposure → restore → đối chiếu source/findings và checkpoint. Rules/DoD ở [AGENTS](../../AGENTS.md), [delivery contract](../delivery/contract.md), [quality](../quality-process.md).
- Quyết định R1: authenticated HTTP relay + producer outbox + receiver inbox; một stream publication và một Learning participant; journal tối thiểu ngoài snapshot; mọi disclosure ảnh hưởng assessment phải có exposure durable ngoài snapshot; restore cô lập và credential recovery trước mở traffic.

| AC | Tiêu chí thiết kế MS03 | Evidence review |
|---|---|---|
| MS03-AC1 | T1/T2 và publication race có thứ tự; không current mastery sai | Publication LLD, DATA-01–09 |
| MS03-AC2 | Delivery duplicate/gap/poison/crash có state/idempotency, không exactly-once giả | Mục 3–4, DATA-10–15 |
| MS03-AC3 | Reset/delete có intent durable, điểm commit/status, tombstone và retry | Lifecycle LLD, DATA-16–23 |
| MS03-AC4 | Restore nhiều DB/journal/exposure/realm/reference có readiness gate | Lifecycle LLD, DATA-24–30 |
| MS03-AC5 | MS-F01/03/04/06 có giải pháp, wire/owner/trace/source đồng bộ | Mục 6–7, backlog/checkpoint |

## 2. Invariants và nguồn sự thật

| Invariant | Cam kết |
|---|---|
| DATA-INV01 | Submission/job/result/evidence/progress cùng Learning; không remote call trong T1/T2 |
| DATA-INV02 | Producer business change + outbox local atomic; receiver inbox + effect + receipt local atomic; ACK sau commit |
| DATA-INV03 | Version/message/intent cùng ID không được thay bytes/digest; retry không thêm business effect |
| DATA-INV04 | Content chỉ commit canonical revision mới sau Learning đã fence content-sensitive traffic; release không trả mastery từ policy cũ |
| DATA-INV05 | History/result bất biến; current mastery/readiness dùng epoch và publication policy hiện hành; projection stale không là thẩm quyền |
| DATA-INV06 | Reset chỉ thành công sau journal durable và Learning final commit; operation cũ không reset epoch mới |
| DATA-INV07 | Account deletion terminal theo user UUID, không bù trừ bằng hồi sinh; metadata tối thiểu chặn replay giữ đúng retention |
| DATA-INV08 | Case đã tiết lộ/biết không trở lại clean vì reset/restore; exposure journal phải durable trước disclosure |
| DATA-INV09 | Ba snapshot không được coi là global atomic; restore cần journal/manifest/cursors/reference và realm/generation mới |
| DATA-INV10 | Timeout/missing source/poison/partial failure là pending/unavailable/quarantine; không giả pass, complete hoặc owner |

Identity sở hữu auth/lifecycle account; Content sở hữu immutable authoring versions và canonical publication; Learning sở hữu private replica, work/progress/exposure/reset. Journal là infrastructure storage port, không service nghiệp vụ thứ tư. `credentialEpoch`, `learningEpoch`, `publicationRevision`, `deliverySequence`, `authRealmId`, `participantDbGeneration` có nghĩa khác nhau; không dùng timestamp thay thứ tự hoặc counter này.

## 3. Transport và envelope

Chọn HTTP relay/outbox vì R1 có ít consumer cố định, cần đưa command tới đúng service và đối soát phase. Auth request/admission vẫn synchronous theo MS02; job chấm vẫn DB-backed nội bộ Learning. Chưa có nhu cầu fan-out/log streaming buộc broker. Broker tương lai phải giữ contract delivery/replay; thay transport không tự giải quyết domain atomicity.

Producer ghi state/outbox và cấp sequence trong transaction DB riêng; dispatcher claim lease ngắn, gửi ngoài transaction qua mTLS. Receiver xác minh caller/action/env/schema/size/digest trước domain effect. Local transaction ghi inbox unique + effect + receipt/cursor; commit rồi trả ACK. Producer chỉ mark delivered/advance operation từ ACK đúng receiver/operation/phase/digest/generation. Crash sau receiver commit trước ACK gây resend cùng message, không tạo effect lần hai.

| Envelope field | Nghĩa / constraint |
|---|---|
| messageId, operationId, type, phase | UUID/message immutable; operation liên kết nhiều phase, type/phase allowlist |
| producer, consumer, environment, protocolVersion | Bound mTLS identities/action và schema compatibility; không nhận env khác |
| streamKey, deliverySequence | Sequence local producer theo `(producer,consumer,streamKey)`; cursor durable của receiver |
| publicationRevision hoặc lifecycleVersion/learningEpoch | Version nghiệp vụ đúng type; không thay deliverySequence |
| payloadBytes, payloadDigest | SHA-256 trên đúng UTF-8 bytes đã lưu; resend cùng bytes, không reserialize đổi message |
| correlationId, createdAt | Đối soát/trace; không quyết ordering/expiry thay server guard |
| expectedParent, manifestDigest, authRealmId/generation khi liên quan | Chống stale phase/restore; chỉ field được schema type cho phép |

Stream publication dùng `content-global`, serialize một operation R1. Lifecycle theo user UUID; reset journal do Learning tự điều phối, không dùng publication stream. INT-02 auth fences giữ monotonic/dominance semantics MS02 và delivery records riêng; không chờ sau một poison publication/delete message. Cookie/password/CSRF/raw answer/private expected không nằm trong event envelope/log.

| Message | Producer → receiver / effect |
|---|---|
| EVT-01 PUBLICATION_STAGE | Content → Learning: stage verified bundle/candidate/parent |
| EVT-02 PUBLICATION_FENCE | Content → Learning: durable local content gate fence trước canonical commit |
| EVT-03 PUBLICATION_RELEASE_OR_ABORT | Content → Learning: release revision đã commit hoặc abort trước commit; phase/digest immutable |
| EVT-04 ACCOUNT_SUPPRESS | Identity → Learning/Content: terminal suppression của user, local receipt/cleanup schedule |
| EVT-05 LIFECYCLE_PURGE_STATUS | Participant → Identity qua authenticated status response: logical/purge state, không user data |

EVT là type catalog, không public browser API. Mỗi phase command có messageId/sequence riêng. ACK response không phải producer business event cần relay vòng ngược; nếu mất response thì producer retry command để lấy durable receipt. Bundle pull GET thực hiện trước staging transaction, không remote call khi giữ lock.

## 4. Ordering, failure và retention

- Duplicate messageId/stream sequence + cùng digest: trả durable receipt sau kiểm caller/current suppression; không chạy effect lại. Cùng ID/sequence khác digest: conflict/security event, giữ state tốt.
- Strict stream `sequence > expected`: STREAM_GAP, không áp future phase. Producer resend prefix đã lưu; không skip cursor theo timestamp. `sequence < expected` chỉ ACK nếu receipt hoặc monotonic state chứng minh đã bao phủ; nếu đã compact mà không chứng minh được thì RESYNC_REQUIRED/quarantine.
- Receiver/domain conflict (parent/epoch/phase) không retry mù như mạng lỗi; publisher/coordinator đối soát current operation qua status. Stale/aborted operation không được mở gate của operation mới.
- Timeout/503/network: bounded exponential backoff+jitter, cùng message/key. Hết automatic budget chuyển paused/alert, vẫn lưu pending, không drop hay đánh dấu delivered. Numeric budgets/batch/queue/capacity đo và cấu hình MS04.
- Poison/unknown schema/checksum: quarantine metadata/digest có giới hạn, không ghi nguyên private payload vào log. Không advance strict stream; sửa bằng versioned replacement/recovery operation có audit, không edit message đã phát hoặc reset inbox cho qua.
- Recovery poison stream: operator kiểm producer/receiver cursor + exact offending digest + verified covered-prefix; trong maintenance, recovery coordinator bound realm/generation mới ghi disposition `REJECTED_NO_EFFECT` cho message lỗi và terminal ABORT của candidate chưa canonical commit, rồi đối soát fence trước resume. Recovery receipt/audit lưu prefix và lý do; không nhận browser override hay auto-skip gap. Nếu canonical đã commit, không abort/skip policy: phải recover valid committed bundle/release hoặc giữ quarantine. Không đủ provenance thì giữ pending, không tự sửa payload dưới ID cũ.
- Inbox/outbox receipts/cursors và terminal floors không hết hạn sớm hơn replay/backup window. Cleanup chỉ sau inventory chứng minh consumer ACK/retention; restore cursor cũ không chấp nhận live ACK trước recovery generation. Policy chi tiết ở lifecycle LLD.

## 5. Wire API và local lock order

| Contract | Provider/caller | Method/path và payload chính |
|---|---|---|
| INT-03 | Content / Learning | GET `/internal/v1/content-bundles/{bundleId}`: immutable stored bytes + manifest/version/checksum, scope private; same ID khác digest reject |
| INT-04 phase | Learning / Content | PUT `/internal/v1/publication-operations/{operationId}/phases/{phase}`: STAGE/FENCE/RELEASE/ABORT envelope, parent/revision/manifest; durable phase receipt |
| INT-04 status | Learning / Content | GET `/internal/v1/publication-operations/{operationId}`: staged/fenced/released/aborted + digest/revision/generation; không bundle answers |
| INT-05 command | Learning/Content / Identity | PUT `/internal/v1/account-lifecycle/{operationId}/phases/{phase}`: SUPPRESS terminal user UUID/lifecycleVersion/epoch floor/journal ref |
| INT-05 status | Learning/Content / Identity | GET `/internal/v1/account-lifecycle/{operationId}`: suppressed/purging/live-purged + receipt/generation |

Auth/service allowlist MS02 vẫn áp dụng cho mọi endpoint. Namespace không được edge publish. Content giữ canonical operation/ACK trong DB riêng, không cần HTTP callback ACK endpoint; Identity tương tự cho account deletion. Operator OPS-02/03 đọc coordinator state qua maintenance đã phân quyền, không thêm learner admin API. Learning reset/support preparation tra API-L08 receipt hoặc API-L02 attempt state, giữ 28 browser routes hiện hành.

Thứ tự user mutation: MS02 auth_account_gate → learner_guard → Learning publication gate nếu content-sensitive → resource/receipt. Worker T2: learner_guard → publication gate → job/final result. Publication chỉ khóa publication gate, không quét/khóa từng learner. Fence/activation dùng exclusive gate; T1/T2/read current policy dùng shared gate giữ tới local commit/snapshot. Reset/deletion dùng learner_guard, không giữ job lock khi chờ guard. Lock mode/SQL cụ thể phải được test DB sau MS04; gọi HTTP/journal/KDF ngoài mọi transaction đang giữ domain locks.

## 6. Failure acceptance matrix

Các case là expected cho implementation, chưa chạy. MS04 gắn fixture, DB/fault/browser/restore command và task owner; không dùng unit mock để chứng minh barrier/durability.

Mapping MS04 hiện ở [test-release plan](../delivery/test-release-plan.md): mỗi DATA ID có leaf/fixture/command/evidence, cùng SEC/business/REL registry. [Implementation plan](../delivery/implementation-plan.md) chốt journal adapter qualification/budgets/ops; R28a trước disclosure, R32b drill trước pilot. Không thay expected hoặc claim durability từ mapping.

| Case | Trigger | Expected / owner |
|---|---|---|
| DATA-01 | Candidate bundle/schema/ref/DAG/checksum sai | Không fence/commit canonical; release tốt giữ — Content/Learning |
| DATA-02 | FENCE tranh T1/T2/progress snapshot | Chung pub gate sắp thứ tự; sau fence content-sensitive action pending — Learning DB race |
| DATA-03 | Learning down trước FENCE ACK | Content không canonical commit, operation pending — Content fault |
| DATA-04 | Content commit revision mới rồi mất RELEASE/ACK | Learning vẫn fenced hoặc đã release cùng receipt; retry hội tụ, không old mastery — cả hai |
| DATA-05 | Abort trước canonical commit, phase tới trễ sau operation mới | Chỉ unfence đúng operation/parent, không undo revision đã committed — cả hai |
| DATA-06 | Withdraw/invalid rubric trong lúc chấm ngoài lock | T2 kiểm current eligibility, history giữ, không cấp current mastery sai — Learning |
| DATA-07 | Cosmetic update/shared CAP/old projection | Impact đúng CAP/version, shared một progress; stale projection reconcile trước đọc — Learning/Content |
| DATA-08 | Worker crash/T2 partial write/two leases | Result/evidence/progress/job cùng commit hoặc rollback, stale fence token không finalize — Learning |
| DATA-09 | Response revision cũ về sau catalog mới | FE giữ revision/as-of, bỏ/reload incompatible state; không dựng current mastery client — FE |
| DATA-10 | Outbox tx rollback hoặc dispatcher crash trước/sau ACK | State/message cùng rollback hoặc pending/delivered đúng receipt — producer |
| DATA-11 | Duplicate cùng ID, ID trùng khác payload | One effect hoặc conflict; không duplicate evidence/reset — receiver |
| DATA-12 | Future sequence/phase/gap/old sequence compacted | Gap/resync/verified covered receipt; không skip bằng timestamp — receiver |
| DATA-13 | Poison schema/size/checksum, mạng/cert lỗi | Quarantine/pending, bounded retry; không advance hoặc leak private log — receiver/ops |
| DATA-14 | ACK wrong service/digest/DB generation | Reject, không completed/release/purge giả — coordinator |
| DATA-15 | Auth revoke đang cần ACK nhưng publication stream poison | Auth delivery độc lập theo MS02, không wait unrelated stream — Identity/ops |
| DATA-16 | Reset journal lỗi hoặc crash mỗi phase | Giữ dữ liệu tốt/pending, chưa complete; resume same intent — Learning |
| DATA-17 | Reset final commit tranh autosave/import/T2; retry sau học lại | Guard/epoch chặn old writes/jobs; old receipt không reset new epoch — Learning |
| DATA-18 | Reset giữ exposure; mô phỏng reset | Không xóa exposure; mô phỏng không mất work/progress — Learning/FE |
| DATA-19 | Journal intent durable nhưng DB final chưa commit rồi restore | Hoàn tất intent, không phục vụ dữ liệu cũ; receipt recovered — recovery |
| DATA-20 | Xóa account có participant down/ACK mất | Pending, principal deny; logical deleted chỉ sau đủ suppression ACK — Identity |
| DATA-21 | Delete user absent local, replay old event/import/assignment | Terminal tombstone, không auto-create user/profile — receiver |
| DATA-22 | Disclosure case/hint/solution trước journal ACK hoặc session reset | Không tiết lộ; epoch/context kiểm lại; marker conservative giữ — Learning |
| DATA-23 | Restore trước disclosure DB record nhưng external exposure record tồn tại | Merge monotonic exposure; case/family không thành clean lần nữa — recovery |
| DATA-24 | Backup ba DB khác thời điểm, publication head/cursor lệch | Offline reconcile deterministic operation/prefix/ref, không gọi global atomic — recovery |
| DATA-25 | Missing/corrupt journal, manifest/key/inventory không đủ | Protected traffic/assessment giữ offline, không overwrite nguồn tốt — recovery |
| DATA-26 | Snapshot có user/case/version không tồn tại canonical | Quarantine/durable proof recovery, không gán owner theo loginName hay guess rubric — recovery |
| DATA-27 | Restore password/session/token/proof/ACK/lease cũ | New realm/generations, RECOVERY_REQUIRED credential, old auth/jobs lease không dùng — Identity/ops |
| DATA-28 | Purge journal khi backup ngoại lệ còn hoặc user deleted có exposure record | Retain minimal suppression đủ window; delete dominates exposure; không resurrect — recovery |
| DATA-29 | Rebuild projection hoặc pending jobs sau restore | Từ surviving evidence/pinned input/current policy/epoch; không import official pass — Learning |
| DATA-30 | Recovery traffic mở trước release/barrier/clock/cert/drill evidence | Readiness deny; RPO/RTO đo thực và khoảng data loss nêu rõ — ops/MS04 |

## 7. Findings và nguồn

MS-F01: T1/T2 local + publication shared/exclusive gate; MS-F03: FENCE → canonical COMMIT → RELEASE, không phục vụ policy cũ sau publish; MS-F04: intent/exposure journal, epoch/tombstone, restore realm/reference readiness; MS-F06: outbox/inbox/sequence/phase/digest/retry. Đây là giải pháp thiết kế, runtime còn BL-R sau MS04; MS-F07/09 rebaseline/ops thuộc MS04.

Nguồn đọc 30/09/2026: [Transactional outbox](https://microservices.io/patterns/data/transactional-outbox.html) mô tả ghi message cùng local transaction và relay có thể phát lặp; [Idempotent consumer](https://microservices.io/patterns/communication-style/idempotent-consumer.html) mô tả dedupe processed-message trong transaction; [PostgreSQL SQL dump](https://www.postgresql.org/docs/18/backup-dump.html) mô tả snapshot consistent từng DB, `pg_dumpall` không đồng bộ snapshot nhiều DB. HTTP transport, publication barrier, journals và exposure protocol là quyết định riêng của app, không là bảo đảm tự có từ các nguồn này.

Business sources: [S03](../features/f11-learner-work/data-contract.md), [D05](../features/f04-assessment/evidence-contract.md), [requirements](../product-requirements.md), [module map](../feature-module-map.md), [D08 baseline](../delivery/history/implementation-v2.md). Không đổi viewed/proven/applied, không xóa history vì đổi version, admin sau R1. Storage provider, lệnh restore/migration/benchmark và test runtime cụ thể thuộc MS04; không tuyên bố toàn hệ thống READY chỉ vì MS03 có sơ đồ.
