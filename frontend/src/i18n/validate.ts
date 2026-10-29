/** Kiểm Pair trong catalog tĩnh; không dùng hàm này để tin payload API bên ngoài. */
export function validateLocalePairs(value: unknown, path = 'locale'): string[] {
  const errors: string[] = [];
  function visit(item: unknown, currentPath: string): void {
    if (!item || typeof item !== 'object') return;
    const record = item as Record<string, unknown>;
    if ('en' in record || 'vi' in record) {
      for (const locale of ['en', 'vi']) {
        if (typeof record[locale] !== 'string' || !(record[locale] as string).trim()) errors.push(`Missing ${locale}: ${currentPath}`);
      }
      return;
    }
    for (const [key, nested] of Object.entries(record)) visit(nested, `${currentPath}.${key}`);
  }
  visit(value, path);
  return errors;
}
