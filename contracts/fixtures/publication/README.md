# FX-05 publication fixtures

`fx05-private-bundle.json` là exact R05 private-case-v2 bytes oracle; `fx05-candidate-cosmetic.json` là candidate P=1 → P+1=2 hợp lệ nhưng chỉ được prepare. `fx05-impact-oracle.json` độc lập liệt kê tuple policy expected cho cosmetic, reverify, withdrawal và old rubric invalid; unit suite áp từng policy và mọi contradiction vào candidate. SHA-256 luôn tính trên exact file bytes, gồm newline cuối file. Negative cases dùng JSON-tree mutation để đổi đúng field, rồi chỉ recompute review subject khi case cần đi qua report binding.

Fixture này không phải nội dung Collections đã review hoặc published, không tạo publication phase và không cho phép advance canonical head.
