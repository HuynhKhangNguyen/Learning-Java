# Yêu cầu IT hỗ trợ WSL cho Docker Desktop

Ngày kiểm tra: 03/10/2026. Có thể sao chép đoạn sau gửi IT; chưa gửi yêu cầu ra ngoài từ dự án.

## Nội dung yêu cầu

Tôi cần chạy Linux containers bằng Docker Desktop để phát triển và kiểm thử dự án Java Backend. Tài khoản hiện tại không có quyền Windows Administrator. Docker Desktop đã cài theo user, nhưng engine không khởi động vì WSL quá cũ.

Nhờ IT cập nhật WSL bằng quy trình được tổ chức cho phép. Docker yêu cầu WSL từ 2.1.5; bản stable đã kiểm tra là 3.0.1. Sau cập nhật, cần kiểm tra từ tài khoản người dùng hiện tại rằng WSL và Docker Linux engine hoạt động. Không cần chuyển sang Windows containers cho dự án này.

| Thông tin máy | Kết quả |
|---|---|
| Windows | Windows 11, build 26200.9457, x64 |
| Docker Desktop | 4.93.0, cài tại `%LOCALAPPDATA%\Programs\DockerDesktop` |
| WSL đang dùng | 2.0.14.0; kernel 5.15.133.1-1 |
| WSL / Virtual Machine Platform | Enabled |
| Windows Hypervisor Platform | Disabled |

Hai lần thử đã có kết quả:

- `wsl --update`: yêu cầu elevation; `Wsl/UpdatePackage/ERROR_CANCELLED`.
- Cập nhật gói MSIX chính thức bằng `Add-AppxPackage` từ token thường: Windows trả `0x80073D28`, `Administrator privileges required to install packaged service`. Activity ID: `6700eb53-48f3-0005-37e1-b469f348dd01`.

WSL vẫn đăng ký phiên bản 2.0.14.0, status `Ok`. Docker vẫn trả `Docker Desktop is unable to start`. Chưa reset dữ liệu Docker hoặc unregister distro.

## Gói đã chuẩn bị

Nguồn: [Microsoft WSL 3.0.1](https://github.com/microsoft/WSL/releases/tag/3.0.1).

Gói MSIX đã tải vào workspace:

```text
C:\Users\khang.nguyen-huynh\Documents\ChatGPT\Learning Java\.tools\wsl-downloads\Microsoft.WSL_3.0.1.0_x64_ARM64.msixbundle
```

Kích thước: 712,515,210 bytes. SHA256 khớp digest ở release chính thức:

```text
b73a4c77f65421dc307e0b094241557453aca720392909fdecc24966b0a102de
```

Authenticode status `Valid`, signer `Microsoft Corporation`. Bundle giữ đúng package identity `MicrosoftCorporationII.WindowsSubsystemForLinux`, version `3.0.1.0`, có x64/ARM64; không sửa nội dung gói. [Microsoft Add-AppxPackage](https://learn.microsoft.com/en-us/powershell/module/appx/add-appxpackage) là API đã thử; bản WSL này có packaged service nên thao tác update trên máy bị từ chối với quyền thường. IT cần chọn phương thức cài phù hợp, bảo đảm WSL dùng được từ tài khoản khang.nguyen-huynh nếu thao tác bằng một tài khoản admin khác.

## Xác minh sau khi IT cập nhật

Từ terminal của người dùng hiện tại:

```powershell
wsl --version
docker desktop restart --detach
docker version
docker run --rm hello-world
```

Cần thấy WSL phiên bản mới, Docker có phần Server và container smoke kết thúc thành công. Đây là kiểm tra môi trường, chưa phải qualification Compose/edge hoặc nghiệm thu release dự án.

Nếu tổ chức chọn Docker VMM thay WSL, nhờ IT bật Windows Hypervisor Platform và kiểm precondition của backend; sau đó chọn tại Settings → General → Virtual Machine Manager. [Docker VMM](https://docs.docker.com/desktop/features/vmm/) đang Beta, yêu cầu ít nhất 4 GB RAM cho VM. Chưa áp dụng hướng này trên máy.

FE/BE hiện tại dùng runtime portable theo [hướng dẫn môi trường](development.md); việc sửa Docker không bắt buộc nâng Java/Node/framework hoặc đổi kiến trúc sản phẩm.
