import { test, expect } from '@playwright/test';

test('HTTP bài chuẩn: method examples, feedback, shared bilingual state và link bài tiếp', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await page.getByRole('link', { name: 'Bắt đầu bài mẫu', exact: true }).click();
  await expect(page.getByRole('heading', { level: 1 })).toContainText('HTTP: từ thông điệp');
  await page.getByRole('radio', { name: 'English', exact: true }).check();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('HTTP: from a message');
  await page.getByRole('button', { name: /Anatomy of a request/ }).click();
  await expect(page.locator('.annotated-message')).toContainText('Content-Type: application/json');
  const headerQuestion = page.locator('[data-check-id="check-headers"]');
  await expect(headerQuestion.getByRole('button', { name: 'Check answer' })).toBeDisabled();
  await headerQuestion.getByRole('radio', { name: 'Accept', exact: true }).check();
  await headerQuestion.getByRole('button', { name: 'Check answer' }).click();
  await expect(headerQuestion.getByRole('status')).toContainText('Not yet');
  await expect(headerQuestion.getByRole('status')).toContainText('response formats');
  await page.getByRole('button', { name: /Choose an HTTP method/ }).click();
  for (const method of ['get', 'post', 'put', 'patch', 'delete']) await expect(page.locator(`.method-${method}`)).toBeVisible();
  await expect(page.locator('.method-patch')).toContainText('application/merge-patch+json');
  await expect(page.locator('.method-put')).toContainText('name is required');
  await page.getByRole('radio', { name: 'Tiếng Việt', exact: true }).check();
  await page.getByRole('button', { name: /Cấu tạo request/ }).click();
  await expect(headerQuestion.getByRole('radio', { name: 'Accept', exact: true })).toBeChecked();
  await expect(headerQuestion.getByRole('status')).toContainText('Chưa chính xác');
  await headerQuestion.getByRole('button', { name: 'Chọn lại' }).click();
  await headerQuestion.getByRole('radio', { name: 'Content-Type', exact: true }).check();
  await headerQuestion.getByRole('button', { name: 'Kiểm tra câu trả lời' }).click();
  await expect(headerQuestion.getByRole('status')).toContainText('Chính xác.');
  await page.getByRole('button', { name: /Áp dụng và tự kiểm tra/ }).click();
  // Dùng câu trả lời cố định theo contract được dạy, không đọc correctId từ implementation.
  for (const [id, answer] of [['apply-filter', 1], ['apply-patch', 1], ['apply-validation', 1], ['apply-empty', 1], ['apply-auth', 1]] as const) {
    const check = page.locator(`[data-check-id="${id}"]`);
    await check.getByRole('radio').nth(answer).check();
    await check.getByRole('button', { name: 'Kiểm tra câu trả lời' }).click();
    await expect(check.getByRole('status')).toContainText('Chính xác.');
  }
  await expect(page.getByText('Bạn trả lời đúng 6/6 câu đã nộp và còn 2 câu chưa trả lời.', { exact: true })).toBeVisible();
  await page.getByRole('radio', { name: 'English', exact: true }).check();
  await expect(page.getByText('6 correct out of 6 submitted; 2 unanswered.', { exact: true })).toBeVisible();
  // Đã nộp sáu câu rồi reload: chứng minh trạng thái thật sự ở RAM, không giữ score giả.
  await page.reload();
  await expect(page.getByText('0/8 self-checks submitted.', { exact: false })).toBeVisible();
  await page.getByRole('navigation', { name: 'Lesson contents' }).getByRole('button').last().click();
  await page.getByRole('link', { name: /Next lesson: Request lifecycle/ }).click();
  await expect(page).toHaveURL(/\/learn\/request-lifecycle$/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('How does a request travel');
  expect(errors).toEqual([]);
});

test('all sections in EN/VI fit small screens; keyboard and screenshots', async ({ page }, info) => {
  test.setTimeout(90_000);
  await page.goto('/learn/http-foundations');
  for (const language of ['English', 'Tiếng Việt']) {
    await page.getByRole('radio', { name: language, exact: true }).check();
    const navigationName = language === 'English' ? 'Lesson contents' : 'Mục lục bài học';
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      for (let section = 0; section < 7; section++) {
        const button = page.getByRole('navigation', { name: navigationName }).getByRole('button').nth(section);
        await button.focus();
        await page.keyboard.press('Enter');
        await expect(page.locator('#reader-heading')).toBeFocused();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        if (width !== 320 && [0, 2, 4].includes(section)) await page.screenshot({ path: info.outputPath(`http-${language}-${section}-${width}.png`), fullPage: true });
      }
    }
  }
  await page.emulateMedia({ colorScheme: 'dark', reducedMotion: 'reduce' });
  await page.getByRole('navigation', { name: 'Mục lục bài học' }).getByRole('button').first().click();
  await page.screenshot({ path: info.outputPath('http-dark.png'), fullPage: true });
  await page.reload();
  await expect(page.getByText('Bạn đã nộp 0/8 câu tự kiểm tra.', { exact: false })).toBeVisible();
});
