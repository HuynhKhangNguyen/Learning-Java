export type Locale = 'en' | 'vi';
export type Pair = { en: string; vi: string };
export const pair = (en: string, vi: string): Pair => ({ en, vi });
