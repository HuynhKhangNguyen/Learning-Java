# Journey — điều chỉnh giao diện 20/09/2026

Yêu cầu mới: có cấu trúc như sơ đồ roadmap tham chiếu nhưng sinh động, dễ khám phá. Quyết định này thay thế quy tắc không dùng graph tại màn Journey trong T01/T02. Home và Lab giữ thiết kế theo nhiệm vụ.

- Trục dọc đánh số biểu diễn thứ tự học gợi ý; nét chấm nối các chủ đề thuộc chặng. Không tuyên bố đây là đồ thị prerequisite đầy đủ.
- 12 chặng Java, 53 chủ đề; 6 chặng DevOps, 26 chủ đề. Docker dùng chung một đối tượng đề cương giữa hai lộ trình.
- Biểu tượng, ba nhóm màu và nhãn chữ giúp phân biệt các chặng mà không phụ thuộc riêng vào màu.
- Mặc định mở chặng đầu; mở/thu từng chặng hoặc tất cả. Chọn chủ đề để mở mục tiêu ngay tại nhánh, chọn lại để đóng.
- Mobile chuyển sang cây một cột; không thu nhỏ chữ hoặc yêu cầu kéo ngang. Nút có focus và aria-expanded; chuyển động ngắn tôn trọng reduced-motion.
- Đề cương điều hướng đặt tại src/data/journey.ts và tách khỏi schema bài học T04 cho đến T11. Chủ đề có thể trỏ đến lesson ID; bài draft chỉ hiện metadata, không có nút bắt đầu. Nhánh/chủ đề được giữ trong query URL để reload/Back phục hồi ngữ cảnh; chưa lưu tiến độ học.

Roadmap tiếp tục là màn định hướng toàn cảnh. Chọn một topic mở trang topic để xem mục tiêu, prerequisite và hành động phù hợp; chỉ topic có descriptor đã publish mới có thể mở `Interactive Learning Workspace`. Roadmap không nhúng scene, simulation state, assessment hoặc progress authority và không bị workbench thay thế. Quyết định reusable workbench/pilot thuộc [BL-E07](../f02-learning-experience/tasks/BL-E07.md); implementation vẫn chờ các leaf journey/topic/player trong registry.

Kiểm tra: TypeScript/ESLint và production build. Playwright kiểm tra mở nhánh bằng bàn phím, chi tiết chủ đề, mở/thu tất cả, chuyển lộ trình, reduced-motion, overflow 320/390/900/1440px; chụp ảnh desktop/mobile/dark để kiểm tra trực quan. Đây chưa phải nghiệm thu chất lượng chương trình học hoặc bài lab.
