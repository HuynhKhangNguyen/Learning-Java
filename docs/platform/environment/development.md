# Môi trường phát triển không cần Windows Administrator

Kiểm tra ngày 03/10/2026 trên máy hiện tại. Đây là checkpoint sửa môi trường theo yêu cầu người dùng; không thay trạng thái BL-R03 hoặc nghiệm thu Linux/container/R31.

## Phạm vi và kết quả

Mục tiêu: tiếp tục phát triển FE/BE bằng runtime đã có, không cần cài Windows service hoặc thay PATH/registry hệ thống. Người dùng xác nhận không có quyền Administrator. Kế hoạch: kiểm kê runtime → xác định trở ngại Docker → chọn runtime theo terminal → kiểm tra lệnh thật và ghi giới hạn.

Đầu ra: [enter-dev-shell.ps1](../../../scripts/enter-dev-shell.ps1) và hướng dẫn này. Helper đọc JDK pin từ [manifest](../../../ops/release/dependencies.json), chọn Node theo cùng ngưỡng hỗ trợ của helper FE và sử dụng npm CLI đã có. Không tải dependency/runtime mới. Các helper [FE](../../../scripts/project.ps1) và [BE](../../../scripts/services.ps1) tiếp tục là lệnh chạy chính; nguồn sản phẩm và backlog nghiệp vụ không được sửa trong checkpoint này.

| Thành phần | Kết quả kiểm tra trên máy |
|---|---|
| JDK dự án | Microsoft OpenJDK 21.0.12.1+1-LTS, portable trong `.tools/jdk/`; `java` và `javac` chạy được |
| Maven | Wrapper 3.9.16, cache trong `.tools/maven-user-home/`; toolchain offline exit 0 |
| Node dự án | 24.19.0 từ runtime có sẵn của Codex; kiểm runtime FE exit 0 |
| npm/npx | 8.5.5 từ npm CLI có sẵn, thực thi bằng Node 24; hai lệnh version exit 0 |
| PostgreSQL | 18.6 portable đã có theo [DB lab](../database/verification.md); không cài Windows service. Checkpoint này chỉ kiểm version, không chạy lại DB suite |
| Docker | Desktop 4.93.0, CLI 29.8.1 và Compose 5.5.1 có sẵn; Linux engine chưa khởi động được. CLI có sẵn không chứng minh container chạy được |
| WSL | 2.0.14.0; kernel 5.15.133.1-1. Lệnh cập nhật thất bại do yêu cầu Administrator |

Máy có RAM khoảng 15.9 GB; lần kiểm kê còn khoảng 1.2 GB RAM trống và 38.1 GB đĩa C: trống. Đây là snapshot, không phải mức tài nguyên bảo đảm cho các lần chạy sau. Khi chạy lab, chủ động đóng ứng dụng không cần dùng; chưa tự đóng ứng dụng khác của người dùng.

## Mở terminal dự án

Dùng PowerShell 7.4 trở lên. Máy hiện tại có PowerShell 7.6.5. Nếu terminal mặc định là Windows PowerShell 5.1, có thể mở runtime PowerShell đã có sẵn bằng lệnh sau; không cần Run as administrator:

```powershell
& "$env:USERPROFILE\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe" -NoProfile
```

Từ root repo, chạy bằng dot-source — có dấu chấm và dấu cách trước đường dẫn:

```powershell
. .\scripts\enter-dev-shell.ps1
java --version
node --version
npm --version
```

Helper chỉ đặt `JAVA_HOME`, `MAVEN_USER_HOME`, PATH và ba biến `BACKEND_LAB_*_CLI/NODE_EXE` liên quan trong process terminal. Nó tạo hai shim npm/npx trong `.tools/dev-shell/bin/` đã được ignore. Những process con mở từ terminal này thừa hưởng cấu hình. Đóng terminal để quay lại cấu hình mặc định; mỗi terminal mới chạy lại helper nếu muốn dùng lệnh trực tiếp. Không sửa PowerShell profile hoặc execution policy.

Lý do cần shim: `npm.cmd` global nằm cạnh Node 16 và có thể tự chọn Node 16 dù PATH đã ưu tiên Node 24. Shim gọi Node đã chọn cùng npm CLI rõ ràng, giữ nguyên argument và mã thoát. Không cài thêm npm. Có thể chỉ định runtime khác đã có bằng `-NodeDirectory 'C:\path\to\node'`; thiếu/sai runtime trả lỗi trước khi đổi environment.

```powershell
# FE: helper chọn runtime và chạy trong frontend/.
.\scripts\project.ps1 -Task dev

# BE: helper dùng wrapper, local repository và pinned JDK.
pwsh -NoProfile -File scripts/services.ps1 -Task toolchain -Offline
```

Không có lệnh `mvn` global mới. Dùng helper BE hoặc wrapper `backend/mvnw.cmd` theo [backend README](../../../backend/README.md). Để kiểm/build service, chọn đúng task/leaf đang được giao; không tự chạy các build song song lên cùng thư mục target.

## Trở ngại Docker và các hướng hợp lệ

Docker hiện báo `starting`; log backend ghi `WSL update required`. `wsl --update` đã kiểm tra và thử cập nhật lên 3.0.1, nhưng trả exit 1 với `Wsl/UpdatePackage/ERROR_CANCELLED` và yêu cầu elevation. WSL vẫn ở 2.0.14.0; không ghi là đã cập nhật.

Theo [yêu cầu Docker Windows](https://docs.docker.com/desktop/setup/install/windows-install/), WSL backend cần WSL 2.1.5 trở lên. Bản Docker trên máy cài theo user; [Docker xác nhận](https://docs.docker.com/desktop/setup/install/windows-permission-requirements/) chế độ này chạy bằng user thường, gồm WSL 2 hoặc Docker VMM. Các tính năng hệ thống cần thiết vẫn phải được bật trước.

Docker VMM là lựa chọn khác có từ Desktop 4.86, trạng thái Beta; [hướng dẫn VMM](https://docs.docker.com/desktop/features/vmm/) yêu cầu ít nhất 4 GB RAM cấp cho VM. [Release notes Docker](https://docs.docker.com/desktop/release-notes/) xác nhận Windows Hypervisor Platform là precondition liên quan tới backend này. Kiểm CIM trên máy cho kết quả:

| Windows feature | InstallState | Diễn giải |
|---|---|---|
| VirtualMachinePlatform | 1 | Enabled |
| Microsoft-Windows-Subsystem-Linux | 1 | Enabled |
| HypervisorPlatform | 2 | Disabled |

Giá trị 1/2 theo [Microsoft Win32_OptionalFeature](https://learn.microsoft.com/en-us/windows/win32/cimwin32prov/win32-optionalfeature). Có file `WinHvPlatform.dll` không đủ chứng minh feature đã bật. Vì Windows Hypervisor Platform đang tắt, chưa chuyển cấu hình sang Docker VMM; bật Windows feature cần người có quyền Administrator/IT thực hiện.

Các bước còn lại có thể chọn khi có điều kiện:

1. Kiểm tra cập nhật WSL từ Microsoft Store nếu Store được tổ chức cho phép. Đây là hướng chưa kiểm chứng trên máy; không bảo đảm Store bỏ được yêu cầu Administrator của bản cập nhật. Nếu yêu cầu quyền thì dừng và nhờ IT.
2. Nhờ IT cập nhật WSL để tiếp tục WSL backend; hoặc bật Windows Hypervisor Platform rồi chọn Docker VMM tại Settings → General → Virtual Machine Manager → Apply & restart. Sau thay đổi cần kiểm `docker version` có Server và chạy một container smoke thật.
3. Nếu không thể cấu hình máy, dùng Docker trên một máy Linux/remote host được phép truy cập. Chưa có endpoint/credentials và chưa kết nối hay tạo tài nguyên cloud. Việc cấp host và qualification container còn riêng.

### Lần kiểm tra tiếp theo ngày 03/10/2026

- Microsoft Store và WSL Appx hiện đăng ký status `Ok`; WinGet1.29.380 có sẵn. Query source `msstore` bằng `--disable-interactivity` dừng vì source agreements chưa được chấp nhận, nên không coi đó là đã tìm/cập nhật thành công qua Store. Chưa thay source agreements hoặc policy.
- [Release Microsoft WSL3.0.1](https://github.com/microsoft/WSL/releases/tag/3.0.1) có gói MSIX chính thức. Đã tải bundle 712,515,210 bytes vào ignored `.tools/wsl-downloads/`; SHA256 `b73a4c77f65421dc307e0b094241557453aca720392909fdecc24966b0a102de` khớp official release, Authenticode `Valid`/Microsoft Corporation. Bundle manifest đúng package identity hiện có/version3.0.1.0/x64; có `AppxSignature.p7x`.
- Xác nhận process token không có Administrator elevation, không có distro WSL đang chạy tại lúc kiểm. Thử [Add-AppxPackage](https://learn.microsoft.com/en-us/powershell/module/appx/add-appxpackage) bằng Windows PowerShell5.1 cho user hiện tại, không force/unsigned/policy/feature override. Windows từ chối HRESULT `0x80073D28`: `Administrator privileges required to install packaged service`; activityID `6700eb53-48f3-0005-37e1-b469f348dd01`.
- Kiểm lại sau lỗi: WSL/Appx vẫn2.0.14.0/statusOk; `wsl --version` vẫnkernel5.15.133.1-1; Docker API trả `Docker Desktop is unable to start`. Gói mới chưa đăng ký cho user; không chạy container hoặc ghi Docker repair DONE. Microsoft Store UI/managed update vẫn chưa kiểm chứng; đã hỏi người dùng kiểm tra nút Update, chưa có kết quả.
- Có [yêu cầu IT với gói/evidence và lệnh xác minh](it-wsl-request.md) để xử lý bước admin cụ thể; chưa gửi cho IT. Không retry elevation hoặc thay policy khi người dùng không có quyền. R03 tiếp tục trong chat được giao; không mở leaf sản phẩm khác ở checkpoint môi trường.

FE/BE và các lab PostgreSQL portable đã có không phụ thuộc việc Docker engine khởi động tại thời điểm này. Docker vẫn là dependency cho những task container/Compose tương ứng; chưa đánh dấu các gate đó PASS. Không thay môi trường thật bằng mô phỏng để cấp bằng chứng.

## Kiểm tra và Definition of Done của checkpoint

Đạt cho phần terminal: dot-source hai lần giữ PATH ổn định; lựa chọn Node 16 bị từ chối và giữ PATH/JAVA_HOME/preference của caller; `java/javac/node/npm/npx` version đúng; `node frontend/scripts/check-runtime.mjs` và `services.ps1 -Task toolchain -Offline` exit 0. `npm exec --offline --call 'node --version'` thực chạy child bằng Node 24; child intentional exit 17 được shim giữ nguyên. Invocation không dot-source bị từ chối, PowerShell parser không có lỗi và 6 link local trong tài liệu tồn tại. Không dùng full app build/test làm bằng chứng cho thay đổi môi trường này.

Giới hạn: chưa chạy container, chưa cập nhật WSL hoặc bật Windows feature; Docker repair còn chờ điều kiện trên. Runtime global Java 17/Node 16 vẫn tồn tại ngoài terminal dự án. Không có commit/publish, reset Docker, unregister distro, thay quyền hệ thống hoặc can thiệp task R03 ở chat khác. Không có automatic approval review rejection; trở ngại đã gặp là yêu cầu Administrator của Windows.
