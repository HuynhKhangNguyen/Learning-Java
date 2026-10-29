# F08 — Nội dung, version và xuất bản

[Mục lục feature](../README.md) · [Toàn bộ doc](../../README.md)

## Mục tiêu và phạm vi

Nội dung hợp lệ, có năng lực/rubric/nguồn/version và được publish có kiểm soát.

Content authoring/catalog và publication; Learning giữ replica đã verify, không quyền sửa nguồn Content.

- Module: MD01.
- Requirement: REQ-01,02,10,13,16,18; [định nghĩa và acceptance](../../product-requirements.md).
- Hợp đồng chung: [business](../../business-design.md) / [feature–module](../../feature-module-map.md).

## Hiện trạng

R05 schema2/catalog, R06a Content candidate store/private INT-03 provider, R06b Learning verified replica/INT-04 và R06c Content coordinator/canonical activation đã `DONE` trong phạm vi lab đã ghi; chưa có learner content API hay production publication. Catalog outline không là bài đã publish/dịch đủ hai ngôn ngữ. [Trạng thái task hiện hành](../../delivery-backlog.md) là nguồn chuẩn; [R05 evidence/limits](tasks/BL-R05.md) không thay business/semantic publication review.

## Đọc theo phần

- [LLD catalog và nội dung](lld.md)
- [Mẫu biên soạn mục học](authoring/topic-template.md)
- [Chuẩn content-first và song ngữ EN–VI](authoring/bilingual-standard.md)
- [Khối bài học và mẫu HTTP prototype](authoring/lesson-blocks.md)
- [Protocol xuất bản nội dung](publication/protocol.md)
- [Chương trình học Backend và DevOps](../../curriculum/README.md)

## Task thuộc feature

| Task | Đọc và cập nhật ở đâu |
|---|---|
| BL-R05 | [Brief, acceptance và checkpoint schema2/catalog](tasks/BL-R05.md) |
| BL-R06 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R06a | [Brief, acceptance và checkpoint candidate/version store](tasks/BL-R06a.md) |
| BL-R06b | [Learning verified replica — brief/DoD/checkpoint](tasks/BL-R06b.md) |
| BL-R06c | [Content publication coordinator — brief/DoD/checkpoint](tasks/BL-R06c.md) |
| BL-R07 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-E07a | [Workbench product/content contract ở F02](../f02-learning-experience/tasks/BL-E07a.md) |
| BL-E07b | [HTTP Foundations interactive pilot ở F02](../f02-learning-experience/tasks/BL-E07b.md) |

## Kiểm tra và Definition of Done

Dùng acceptance của leaf được giao, [DoD chung](../../delivery/contract.md) và [quality process](../../quality-process.md). Khi đổi feature, kiểm invariant/biên và phần ngoài phạm vi, ghi bằng chứng tại file task; chỉ cập nhật trạng thái registry khi acceptance đạt. [Test/release mapping](../../delivery/test-release-plan.md) giữ CMD/SEC/DATA/REL owners dùng chung.
