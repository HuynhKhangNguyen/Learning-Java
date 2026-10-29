import { createContext, useContext } from 'react';
import type { Locale, Pair } from './types';

export type LocaleValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  text: (value: Pair) => string;
};

export const LocaleContext = createContext<LocaleValue | null>(null);

export function useLocale(): LocaleValue {
  const value = useContext(LocaleContext);
  if (!value) throw new Error('useLocale phải được dùng bên trong LocaleProvider.');
  return value;
}
