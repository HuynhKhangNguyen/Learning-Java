# F09 — Lab thật và output xác minh được

[Mục lục feature](../README.md) · [Toàn bộ doc](../../README.md)

## Mục tiêu và phạm vi

Chạy lab có output/môi trường và tiêu chí xác minh rõ ràng.

Runner thuộc R3; không chạy lệnh máy người học tùy ý từ nội dung bài.

- Module: MD08, MD03–06.
- Requirement: REQ-12,13; [định nghĩa và acceptance](../../product-requirements.md).
- Hợp đồng chung: [business](../../business-design.md) / [feature–module](../../feature-module-map.md).

## Hiện trạng

Sau R1/R3 theo roadmap sản phẩm; DB/transport lab nội bộ hiện có không phải runner cho người học. [Trạng thái task hiện hành](../../delivery-backlog.md) là nguồn chuẩn; mô tả trong tài liệu thiết kế không tự là bằng chứng runtime DONE.

## Đọc theo phần

- [Nền kỹ thuật và môi trường lab](../../platform/README.md)
- [Yêu cầu và giới hạn lab thật](../../product-requirements.md)
- [Business bằng chứng thực thi](../../business-design.md)

## Task thuộc feature

Chưa có leaf runtime được phép thực thi cho feature này. Giữ phạm vi theo requirement/backlog; chỉ tạo brief khi task đã được giao.

## Kiểm tra và Definition of Done

Dùng acceptance của leaf được giao, [DoD chung](../../delivery/contract.md) và [quality process](../../quality-process.md). Khi đổi feature, kiểm invariant/biên và phần ngoài phạm vi, ghi bằng chứng tại file task; chỉ cập nhật trạng thái registry khi acceptance đạt. [Test/release mapping](../../delivery/test-release-plan.md) giữ CMD/SEC/DATA/REL owners dùng chung.
