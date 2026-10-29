# Prerequisite cho browser qualification Identity

05/10/2026 · STR-03/D17. [Backend](../../../backend/README.md) · [Identity](../../../backend/services/identity-service/README.md) · [Session evidence](../../features/f10-identity-access/sessions/verification.md).

Đây là hướng dẫn cho launcher R04a đã có, không là hướng dẫn khởi động frontend sản phẩm hoặc qualification Linux/container. Service build/unit/DB và browser qualification có đầu vào khác nhau.

## 1. Các lớp cần môi trường nào?

| Lớp kiểm | Đầu vào cần chuẩn bị |
|---|---|
| Backend build/unit | JDK21/Maven Wrapper và resolved dependencies theo backend setup. |
| CMD-03 actual DB | Các đầu vào trên, PowerShell7.4+, Python, PostgreSQL portable workspace theo DB lab; helper sở hữu cluster/cleanup. |
| CMD-04 Identity session/browser | DB/PKI/JAR harness, Node tại đúng đường dẫn launcher bên dưới, FE lockfile modules và Microsoft Edge đã cài. Có prerequisite không tự làm suite PASS. |
| FE learner E2E | Là task/fixture khác, cần backend/edge tích hợp theo R31a/R14/R24; không thay bằng browser qualification Identity. |

## 2. Node và browser launcher hiện hành

`SessionRuntimeIT` chọn executable từ Java `user.home`:

```text
<user.home>/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe
```

Launcher hiện không lấy Node từ PATH hoặc tham số `project.ps1 -NodeDirectory`. Node đã có trong terminal chưa đủ nếu executable ở đường dẫn này thiếu. Trên máy Windows đã qualified, có thể kiểm tại PowerShell bằng `$env:USERPROFILE` tương ứng với Java `user.home`:

```powershell
$taskAuthNode = Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe'
Test-Path -LiteralPath $taskAuthNode -PathType Leaf
& $taskAuthNode --version
```

`session_browser.mjs` dùng Playwright Chromium với `channel: 'msedge'`: cần Microsoft Edge đã cài. Nó không chọn browser từ `PLAYWRIGHT_CHANNEL`, không tự tải browser, không cài CA/trust vào Windows và không chạy giao diện FE sản phẩm. Nếu Node/Edge thiếu, chưa thể gọi môi trường đã sẵn sàng; portability/launcher configuration cần task riêng có qualification, không đổi file đã qualified trong D17.

## 3. Dependency browser lấy từ FE lockfile

Script dùng `createRequire` tại `frontend/package.json`, resolve `@playwright/test` và Ajv từ cây dependency đã khóa. Ajv hiện là dependency bắc cầu đã có trong lockfile. Không thêm package/version hoặc cài riêng Ajv để chạy qualification hiện hành.

Trên checkout mới, dùng helper đang có từ root repo:

```powershell
.\scripts\project.ps1 -Task install
```

Helper chạy `npm ci` trong frontend với Node phù hợp; không thay lockfile. Nếu npm cache chưa đủ, bước install cần truy cập registry. Chuẩn bị npm modules không đồng nghĩa chạy FE server. Sau đó mới chạy managed suite:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task contract -Service identity -EvidenceTask R04a -Offline
```

`-Offline` của lệnh trên áp dụng Maven cache, không thay thế bước chuẩn bị Node/npm/browser. Lệnh này build/khởi động managed DB/JAR/TLS/browser và tạo evidence mới; D17 chỉ kiểm prerequisite tĩnh, không chạy lại command hoặc sửa retained R04a reports.

## 4. Thiếu prerequisite thì xử lý gì?

- Thiếu JDK/PG: theo backend setup và DB lab hiện có, không cài Windows service.
- Thiếu modules: `npm ci` theo lockfile qua helper; không dùng một bộ dependency ngoài repo thay input đang qualified.
- Thiếu Node ở đường dẫn launcher hoặc Edge: ghi giới hạn môi trường; không giả backend-only qualification đủ browser và không âm thầm skip suite.
- Code/launcher/schema/input đổi: phải có qualification cho candidate mới; bằng chứng R04a cũ giữ lịch sử, không sửa hash để qua gate.
