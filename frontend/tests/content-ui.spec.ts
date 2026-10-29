import { expect, test } from '@playwright/test';

test('learning route renders lesson and step metadata from the catalog', async ({ page }) => {
  await page.goto('/learn/api-debug-500/diagnose');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Xử lý API trả lỗi 500');
  await expect(page.getByText('Phòng lab · HTTP & API · Bước 4/8')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Chẩn đoán nguyên nhân' })).toBeVisible();
  await expect(page.getByText('Chọn lời giải thích khớp với trace và dữ liệu quan sát được.')).toBeVisible();
  await expect(page.getByText('ĐANG BIÊN SOẠN', { exact: true })).toBeVisible();
  await expect(page.getByText('Khoảng 20 phút · 5 mục tiêu')).toBeVisible();
});

test('journey exposes draft lesson metadata without a fake start action', async ({ page }) => {
  await page.goto('/journey/java-backend');
  await page.getByRole('button', { name: 'Mở tất cả', exact: true }).click();
  await page.getByRole('button', { name: 'Validation & xử lý lỗi', exact: true }).click();
  const detail = page.getByRole('region', { name: 'Chi tiết Validation & xử lý lỗi' });
  await expect(detail).toContainText('Xử lý API trả lỗi 500');
  await expect(detail).toContainText('Khoảng 20 phút · 5 mục tiêu');
  await expect(detail).toContainText('ĐANG BIÊN SOẠN · CHƯA MỞ BÀI');
  await expect(detail.getByRole('link', { name: 'Bắt đầu bài' })).toHaveCount(0);
  await page.getByRole('link', { name: 'Xem phòng lab', exact: true }).click();
  await page.goBack();
  await expect(page).toHaveURL(/topic=http-2/);
  await expect(page.getByRole('region', { name: 'Chi tiết Validation & xử lý lỗi' })).toBeVisible();
});
