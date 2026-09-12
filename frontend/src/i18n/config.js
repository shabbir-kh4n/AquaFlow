import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'
import HttpApi from 'i18next-http-backend'
import LanguageDetector from 'i18next-browser-languagedetector'
import { LANGUAGE_CODES } from './languages'

i18next
  .use(HttpApi)
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    fallbackLng: 'en',
    supportedLngs: LANGUAGE_CODES,
    ns: ['landing'],
    defaultNS: 'landing',
    backend: {
      loadPath: '/locales/{{lng}}/{{ns}}.json',
    },
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'hl_lang',
    },
    interpolation: {
      escapeValue: false,
    },
    react: {
      useSuspense: true,
    },
  })

// Keep <html lang> in sync so CSS can scope Latin-only typographic tweaks (tight/negative
// letter-spacing) to English via :lang(en) - those tweaks visually mangle scripts whose
// glyphs are contextually shaped/connected (Devanagari, Tamil, Arabic, etc.), where any
// manual letter tracking causes conjuncts and vowel signs to overlap.
function syncHtmlLang(lng) {
  document.documentElement.lang = lng
}
i18next.on('languageChanged', syncHtmlLang)
if (i18next.resolvedLanguage) {
  syncHtmlLang(i18next.resolvedLanguage)
}

export default i18next
