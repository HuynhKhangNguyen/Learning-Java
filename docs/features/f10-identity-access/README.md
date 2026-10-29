# F10 — Tài khoản, phiên và quyền

[Mục lục feature](../README.md) · [Toàn bộ doc](../../README.md)

## Mục tiêu và phạm vi

Có tài khoản riêng, phiên an toàn và không đọc/sửa dữ liệu của người khác.

Credentials, canonical session, admission/revoke và FE session client; quyền phải được kiểm ở từng receiver.

- Module: MD10, MD06, MD09.
- Requirement: REQ-09,14,15; [định nghĩa và acceptance](../../product-requirements.md).
- Hợp đồng chung: [Business tài khoản, phiên và quyền](../../business-design.md) / [feature–module](../../feature-module-map.md).

## Hiện trạng

R03 DONE trong phạm vi credentials local; R04a DONE cho local authority/opt-in lab auth HTTP-browser; R04b/R04c DONE cho Content/Learning receivers. R04d DONE13/10 cho canonical provider/coordinator/reauth trong owned-lab scope, gồm faults/browser/closing DoD; R04 parent rollup DONE không là toàn feature hoặc production readiness. FE/production edge/lifecycle journal vẫn thuộc owners sau. [Trạng thái task hiện hành](../../delivery-backlog.md) là nguồn chuẩn; [checkpoint R04d](sessions/tasks/BL-R04d.md) giữ exact inputs/runs/failures/limits.

## Đọc theo phần

- [Business tài khoản, phiên và quyền](business.md)
- [LLD Identity](lld.md)
- [Credentials: tài khoản và mật khẩu](credentials/README.md)
- [Sessions: phiên, admission và revocation](sessions/README.md)
- [Security xuyên service](../../architecture/security-design.md)

## Task thuộc feature

| Task | Đọc và cập nhật ở đâu |
|---|---|
| BL-R03 | [Brief/checkpoint](credentials/tasks/BL-R03.md) |
| BL-R04 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R04a | [Brief/checkpoint](sessions/tasks/BL-R04a.md) |
| BL-R04b | [Brief, acceptance và checkpoint Content receiver](sessions/tasks/BL-R04b.md) |
| BL-R04c | [Brief, acceptance và checkpoint Learning receiver](sessions/tasks/BL-R04c.md) |
| BL-R04d | [Brief, acceptance và DoD canonical coordinator/reauth](sessions/tasks/BL-R04d.md) |
| BL-R24 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R24a | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R24b | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R24c | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R29b | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |

## Kiểm tra và Definition of Done

Dùng acceptance của leaf được giao, [DoD chung](../../delivery/contract.md) và [quality process](../../quality-process.md). Khi đổi feature, kiểm invariant/biên và phần ngoài phạm vi, ghi bằng chứng tại file task; chỉ cập nhật trạng thái registry khi acceptance đạt. [Test/release mapping](../../delivery/test-release-plan.md) giữ CMD/SEC/DATA/REL owners dùng chung.
