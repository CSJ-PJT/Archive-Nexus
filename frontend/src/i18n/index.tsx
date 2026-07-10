import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { en } from './en';
import { ja } from './ja';
import { ko } from './ko';
import type { Locale, TranslationVariables } from './types';
import { zhCN } from './zh-CN';

const STORAGE_KEY = 'archive.locale';
const LEGACY_STORAGE_KEY = 'archive-nexus-language';

export const languageOptions: { code: Locale; label: string }[] = [
  { code: 'ko', label: '한국어' },
  { code: 'en', label: 'English' },
  { code: 'ja', label: '日本語' },
  { code: 'zh-CN', label: '简体中文' }
];

const dictionaries = {
  ko,
  en,
  ja,
  'zh-CN': zhCN
} satisfies Record<Locale, Record<string, string>>;

type I18nContextValue = {
  locale: Locale;
  setLocale: (locale: Locale | string) => void;
  t: (key: string, variables?: TranslationVariables, fallback?: string) => string;
  statusLabel: (value?: string | null) => string;
  formatDate: (value?: string | null) => string;
  formatTime: (value: Date) => string;
};

const I18nContext = createContext<I18nContextValue | null>(null);

export function normalizeLocale(value?: string | null): Locale {
  if (value === 'zh') return 'zh-CN';
  return languageOptions.some((item) => item.code === value) ? value as Locale : 'ko';
}

export function readStoredLocale(): Locale {
  if (typeof window === 'undefined') return 'ko';
  return normalizeLocale(window.localStorage.getItem(STORAGE_KEY) ?? window.localStorage.getItem(LEGACY_STORAGE_KEY));
}

export function translateDirect(key: string, variables?: TranslationVariables, fallback?: string): string {
  const locale = readStoredLocale();
  return interpolate(dictionaries[locale][key] ?? dictionaries.ko[key] ?? fallback ?? key, variables);
}

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(() => readStoredLocale());

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, locale);
    window.localStorage.setItem(LEGACY_STORAGE_KEY, locale);
    document.documentElement.lang = locale;
    document.documentElement.dataset.language = locale;
  }, [locale]);

  const setLocale = useCallback((next: Locale | string) => setLocaleState(normalizeLocale(next)), []);

  const t = useCallback((key: string, variables?: TranslationVariables, fallback?: string) => {
    return interpolate(dictionaries[locale][key] ?? dictionaries.ko[key] ?? fallback ?? key, variables);
  }, [locale]);

  const value = useMemo<I18nContextValue>(() => ({
    locale,
    setLocale,
    t,
    statusLabel: (status) => status ? t(`status.${status}`, undefined, status) : '-',
    formatDate: (input) => {
      if (!input) return '-';
      return new Intl.DateTimeFormat(toIntlLocale(locale), { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(input));
    },
    formatTime: (input) => new Intl.DateTimeFormat(toIntlLocale(locale), { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(input)
  }), [locale, setLocale, t]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const value = useContext(I18nContext);
  if (!value) throw new Error('useI18n must be used inside I18nProvider');
  return value;
}

function interpolate(text: string, variables?: TranslationVariables) {
  if (!variables) return text;
  return Object.entries(variables).reduce((next, [key, value]) => next.split(`{${key}}`).join(String(value)), text);
}

function toIntlLocale(locale: Locale) {
  if (locale === 'zh-CN') return 'zh-CN';
  if (locale === 'ja') return 'ja-JP';
  if (locale === 'en') return 'en-US';
  return 'ko-KR';
}
