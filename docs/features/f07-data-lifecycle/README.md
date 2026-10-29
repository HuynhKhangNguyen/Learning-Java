# F07 — Giữ, chuyển và phục hồi dữ liệu học

[Mục lục feature](../README.md) · [Toàn bộ doc](../../README.md)

## Mục tiêu và phạm vi

Export/import/reset có phạm vi và giữ dữ liệu tốt khi lỗi hoặc retry.

Transfer/reset/retention phía owner; reset mô phỏng khác reset dữ liệu; không tự coi import là pass đáng tin.

- Module: MD06, MD09.
- Requirement: REQ-09,15,16; [định nghĩa và acceptance](../../product-requirements.md).
- Hợp đồng chung: [business](../../business-design.md) / [feature–module](../../feature-module-map.md).

## Hiện trạng

Có thiết kế lifecycle; backup/restore production và provider qualification còn task riêng. [Trạng thái task hiện hành](../../delivery-backlog.md) là nguồn chuẩn; mô tả trong tài liệu thiết kế không tự là bằng chứng runtime DONE.

## Đọc theo phần

- [LLD dữ liệu, reset và phục hồi](lld.md)
- [Lưu và nhập dữ liệu kiểm tra](assessment-storage.md)
- [Hợp đồng dữ liệu học theo owner](../f11-learner-work/data-contract.md)
- [Protocol lifecycle và recovery xuyên service](../../architecture/lifecycle-recovery-protocol.md)

## Task thuộc feature

| Task | Đọc và cập nhật ở đâu |
|---|---|
| BL-R25 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R26 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R27 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R29 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R29a | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R29c | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |

## Kiểm tra và Definition of Done

Dùng acceptance của leaf được giao, [DoD chung](../../delivery/contract.md) và [quality process](../../quality-process.md). Khi đổi feature, kiểm invariant/biên và phần ngoài phạm vi, ghi bằng chứng tại file task; chỉ cập nhật trạng thái registry khi acceptance đạt. [Test/release mapping](../../delivery/test-release-plan.md) giữ CMD/SEC/DATA/REL owners dùng chung.
