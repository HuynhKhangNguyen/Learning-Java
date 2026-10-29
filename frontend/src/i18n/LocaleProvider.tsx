import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { LocaleContext, type LocaleValue } from './locale-store';
import type { Locale } from './types';

const STORAGE_KEY = 'backend-lab.locale.v1';
const DEFAULT_LOCALE: Locale = 'vi';

function readLocale(): Locale {
  // Storage có thể bị browser chặn; fallback Việt giữ app vẫn dùng được.
  try {
    const saved = window.localStorage.getItem(STORAGE_KEY);
    return saved === 'en' || saved === 'vi' ? saved : DEFAULT_LOCALE;
  } catch {
    return DEFAULT_LOCALE;
  }
}

/** Một locale source cho shell, page và lesson; đổi locale không remount Outlet. */
export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocale] = useState<Locale>(readLocale);

  useEffect(() => {
    document.documentElement.lang = locale;
    const description = document.querySelector<HTMLMetaElement>('meta[name="description"]');
    if (description) description.content = locale === 'en'
      ? 'A self-learning space for Java Backend through guided lessons, simulations, and practice.'
      : 'Không gian tự học Java Backend qua bài học, mô phỏng và thực hành.';
    try { window.localStorage.setItem(STORAGE_KEY, locale); } catch { /* App vẫn hoạt động khi storage không khả dụng. */ }
  }, [locale]);

  useEffect(() => {
    // Đồng bộ preference giữa các tab, nhưng chỉ nhận hai giá trị đã whitelist.
    const sync = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY && (event.newValue === 'en' || event.newValue === 'vi')) setLocale(event.newValue);
    };
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);

  const value = useMemo<LocaleValue>(() => ({ locale, setLocale, text: value => value[locale] }), [locale]);
  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}
