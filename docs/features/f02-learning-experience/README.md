# F02 — Học qua nội dung và tương tác

[Mục lục feature](../README.md) · [Toàn bộ doc](../../README.md)

## Mục tiêu và phạm vi

Người học thấy cơ chế qua thao tác, quan sát và lời giải thích phù hợp.

Player, trạng thái mô phỏng, điều hướng và khả dụng; không tự chấm mastery hoặc giả thực thi Java/Docker.

- Module: MD01, MD03, MD09.
- Requirement: REQ-02,03,11,12; [định nghĩa và acceptance](../../product-requirements.md).
- Hợp đồng chung: [business](../../business-design.md) / [feature–module](../../feature-module-map.md).

## Hiện trạng

Thiết kế và prototype có bằng chứng riêng; chưa có luồng học R1 hoàn chỉnh. [Trạng thái task hiện hành](../../delivery-backlog.md) là nguồn chuẩn; mô tả trong tài liệu thiết kế không tự là bằng chứng runtime DONE.

## Đọc theo phần

- [LLD trải nghiệm học](lld.md)
- [Thiết kế giao diện trực quan](visual-design.md)
- [Kiến thức Collections](collections/knowledge.md)
- [Tương tác học Collections](collections/experience.md)

## Task thuộc feature

Bài HTTP Fundamentals ở `/learn/http-foundations`; bài mô phỏng cũ chuyển sang `/learn/request-lifecycle` trong [BL-P03](tasks/BL-P03.md). [BL-P01](tasks/BL-P01.md) giữ evidence lịch sử. Cả hai chỉ tự kiểm tra trong RAM, không là progress/assessment R1 theo owner. [Khối nội dung dùng chung](../f08-content/authoring/lesson-blocks.md) mô tả renderer và mapping mục tiêu bài mới.

[BL-P02](tasks/BL-P02.md) giữ evidence lịch sử của switch cục bộ EN/VI/EN+VI trong bài mẫu. [BL-P04](tasks/BL-P04.md) thay hành vi hiện hành bằng switch English/Tiếng Việt toàn frontend đang có theo [chuẩn biên soạn](../f08-content/authoring/bilingual-standard.md); chưa tuyên bố curriculum chưa đưa lên UI đã được dịch.

[BL-P05](tasks/BL-P05.md) biên tập lại copy Tiếng Việt sau phản hồi trải nghiệm thực tế: giữ thuật ngữ kỹ thuật English nhưng dùng câu và động từ tiếng Việt tự nhiên.

Initiative [BL-E07](tasks/BL-E07.md) bổ sung `Interactive Learning Workspace` như một chế độ tùy chọn của topic, không thay roadmap hoặc ép mọi lesson thành animation. Pilot đầu là HTTP Foundations, ưu tiên SVG/HTML/CSS 2D; scene mô phỏng phải có text/static fallback và không được trình bày như Java, Docker, network hoặc hạ tầng thật đã chạy. Contract, pilot, progress integration và qualification được tách thành bốn leaf để không nhập progress authority hoặc accessibility review vào renderer.

| Task | Đọc và cập nhật ở đâu |
|---|---|
| BL-R16 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R16a | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R16b | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R18 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-R19 | [Registry: scope, dependency và acceptance](../../delivery-backlog.md) |
| BL-E07 | [Parent Interactive Backend Workbench](tasks/BL-E07.md) |
| BL-E07a | [Product/UX contract và reusable scene model](tasks/BL-E07a.md) |
| BL-E07b | [HTTP Foundations interactive pilot](tasks/BL-E07b.md) |
| BL-E07c | [Viewed/Proven/Applied integration](tasks/BL-E07c.md) |
| BL-E07d | [Accessibility, responsive và performance qualification](tasks/BL-E07d.md) |

## Kiểm tra và Definition of Done

Dùng acceptance của leaf được giao, [DoD chung](../../delivery/contract.md) và [quality process](../../quality-process.md). Khi đổi feature, kiểm invariant/biên và phần ngoài phạm vi, ghi bằng chứng tại file task; chỉ cập nhật trạng thái registry khi acceptance đạt. [Test/release mapping](../../delivery/test-release-plan.md) giữ CMD/SEC/DATA/REL owners dùng chung.
