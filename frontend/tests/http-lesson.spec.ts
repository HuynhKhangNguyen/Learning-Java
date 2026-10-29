import { test, expect } from '@playwright/test';

test('bài mẫu có mô phỏng lỗi, feedback và không cấp kết quả từ Next', async ({ page }) => {
  await page.goto('/learn/request-lifecycle');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Một request đi qua backend như thế nào?');
  await page.getByRole('button', { name: '4. Phòng thực hành' }).click();
  // Mỗi tình huống phải kết thúc ở đúng ranh giới và reset không giữ response cũ.
  for (const [input, steps, status] of [['valid', 5, '201 Created'], ['invalid', 3, '400 Bad Request'], ['db-error', 5, '500 Internal Server Error']] as const) {
    await page.getByRole('combobox').selectOption(input);
    for (let i = 0; i < steps; i++) await page.getByRole('button', { name: 'Đi tiếp', exact: true }).click();
    await expect(page.getByRole('status')).toContainText(status);
    await expect(page.getByRole('button', { name: 'Đi tiếp', exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'Chạy lại mô phỏng' }).click();
    await expect(page.getByRole('status')).toContainText('Chưa có request');
  }
  await page.getByRole('button', { name: '5. Kiểm tra' }).click();
  await expect(page.getByRole('button', { name: 'Nộp câu trả lời' })).toBeDisabled();
  await expect(page.getByText('Kết quả tự kiểm tra:', { exact: false })).toHaveCount(0);
  const choices = [1, 1, 2, 0, 1];
  for (let i = 0; i < choices.length; i++) await page.locator('.lesson-body fieldset').nth(i).getByRole('radio').nth(choices[i]).check();
  await page.getByRole('button', { name: 'Nộp câu trả lời' }).click();
  await expect(page.getByRole('status')).toContainText('5/5');
  await page.getByRole('button', { name: 'Làm lại kiểm tra' }).click();
  for (let i = 0; i < 5; i++) await page.locator('.lesson-body fieldset').nth(i).getByRole('radio').first().check();
  await page.getByRole('button', { name: 'Nộp câu trả lời' }).click();
  await expect(page.getByRole('status')).toContainText('1/5');
  await expect(page.getByText('Chưa đúng.', { exact: false })).toHaveCount(4);
  await page.reload();
  await page.getByRole('button', { name: '5. Kiểm tra' }).click();
  await expect(page.getByRole('button', { name: 'Nộp câu trả lời' })).toBeDisabled();
});

test('bài mẫu dùng keyboard và không tràn màn nhỏ', async ({ page }, info) => {
  await page.goto('/learn/request-lifecycle');
  await page.getByRole('button', { name: '2. HTTP & dữ liệu' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'HTTP & dữ liệu', exact: true })).toBeVisible();
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    for (let i = 0; i < 5; i++) {
      await page.getByRole('navigation', { name: 'Các phần bài học' }).getByRole('button').nth(i).click();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      if (i === 0) await page.screenshot({ path: info.outputPath(`lesson-${width}.png`), fullPage: true });
    }
  }
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
  await page.getByRole('button', { name: '1. Nền tảng' }).click();
  await page.screenshot({ path: info.outputPath('lesson-dark.png'), fullPage: true });
});

test('EN/VI giữ mô phỏng, đáp án và kết quả chung; bản dịch hiện theo chế độ đọc', async ({ page }, info) => {
  await page.goto('/learn/request-lifecycle');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Một request đi qua backend như thế nào?');
  await page.getByRole('button', { name: '4. Phòng thực hành' }).click();
  await page.getByRole('combobox').selectOption('invalid');
  await page.getByRole('button', { name: 'Đi tiếp', exact: true }).click();
  await page.getByRole('radio', { name: 'English', exact: true }).check();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('How does a request travel through a backend?');
  await expect(page.getByRole('status')).toContainText('Client prepares');
  await expect(page.locator('.lesson-vi')).toHaveCount(0);
  await page.getByRole('button', { name: 'Next step', exact: true }).click();
  await page.getByRole('radio', { name: 'Tiếng Việt', exact: true }).check();
  await expect(page.getByRole('status')).toContainText('Controller đọc body');
  await page.getByRole('button', { name: 'Đi tiếp', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('400 Bad Request');
  await page.getByRole('button', { name: '5. Kiểm tra' }).click();
  const choices = [1, 1, 2, 0, 1];
  for (let i = 0; i < choices.length; i++) await page.locator('.lesson-body fieldset').nth(i).getByRole('radio').nth(choices[i]).check();
  await page.getByRole('radio', { name: 'English', exact: true }).check();
  for (let i = 0; i < choices.length; i++) await expect(page.locator('.lesson-body fieldset').nth(i).getByRole('radio').nth(choices[i])).toBeChecked();
  await page.getByRole('button', { name: 'Submit answers' }).click();
  await expect(page.getByRole('status')).toContainText('Self-check result: 5/5');
  await page.getByRole('radio', { name: 'Tiếng Việt', exact: true }).check();
  await expect(page.getByRole('status')).toContainText('Kết quả tự kiểm tra: 5/5');
  await expect(page.getByText('Contract bài dùng 400', { exact: false })).toBeVisible();
  for (const mode of ['English', 'Tiếng Việt']) {
    await page.getByRole('radio', { name: mode, exact: true }).check();
    for (const width of [1440, 320]) {
      await page.setViewportSize({ width, height: 900 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }
  }
  await page.getByRole('button', { name: '1. Nền tảng' }).click();
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 });
    await page.screenshot({ path: info.outputPath(`vi-${width}.png`), fullPage: true });
  }
});
