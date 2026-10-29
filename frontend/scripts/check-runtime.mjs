const [major, minor] = process.versions.node.split('.').map(Number);
const supported = (major === 22 && minor >= 22) || major >= 24;

if (!supported) {
  console.error(`Backend Lab cần Node 22.22+ (nhánh 22) hoặc Node 24+. Hiện tại: ${process.version}.`);
  console.error('Dùng Node 24 hoặc chạy ../scripts/project.ps1 từ frontend/ để chọn runtime phù hợp. Xem README.md ở root repo.');
  process.exit(1);
}
