# F11 — Nháp, nộp bài và lịch sử

[Mục lục feature](../README.md) · [Toàn bộ doc](../../README.md)

## Mục tiêu và phạm vi

Giữ nháp và nộp bài đúng owner; retry/reload không mất hoặc tạo trùng bài.

Allocation, revision/idempotency, T1 và immutable submission; kết quả chính thức từ server.

- Module: MD04, MD05, MD06, MD11, MD09.
- Requirement: REQ-05,06,07,09,15,16; [định nghĩa và acceptance](../../product-requirements.md).
- Hợp đồng chung: [business](../../business-design.md) / [feature–module](../../feature-module-map.md).

## Hiện trạng

Hợp đồng bài làm đã thiết kế; chưa work/submission runtime. [Trạng thái task hiện hành](../../delivery-backlog.md) là nguồn chuẩn; mô tả trong tài liệu thiết kế không tự là bằng chứng runtime DONE.

## Đọc theo phần

- [Hợp đồng nháp, bài nộp và lịch sử](data-contract.md)
- [LLD bài làm và assessment](lld.md)
- [Protocol dữ liệu xuyên service](../../architecture/data-protocols.md)

## Task thuộc feature

| Task | Đọc và cập nhật ở đâu |
|---|---|
| BL-R08 | [Brief, acceptance và checkpoint](tasks/BL-R08.md) |
| BL-R09 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R10 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |

## Kiểm tra và Definition of Done

Dùng acceptance của leaf được giao, [DoD chung](../../delivery/contract.md) và [quality process](../../quality-process.md). Khi đổi feature, kiểm invariant/biên và phần ngoài phạm vi, ghi bằng chứng tại file task; chỉ cập nhật trạng thái registry khi acceptance đạt. [Test/release mapping](../../delivery/test-release-plan.md) giữ CMD/SEC/DATA/REL owners dùng chung.
