import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { LANGUAGES } from '../i18n/languages'
import './LanguageSwitcher.css'

function LanguageSwitcher() {
  const { i18n } = useTranslation()
  const [isOpen, setIsOpen] = useState(false)
  const [query, setQuery] = useState('')
  const containerRef = useRef(null)

  const current = LANGUAGES.find((l) => l.code === i18n.resolvedLanguage) || LANGUAGES[0]

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return LANGUAGES
    return LANGUAGES.filter(
      (l) => l.label.toLowerCase().includes(q) || l.nativeLabel.toLowerCase().includes(q) || l.code.toLowerCase().includes(q),
    )
  }, [query])

  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  function selectLanguage(code) {
    i18n.changeLanguage(code)
    setIsOpen(false)
    setQuery('')
  }

  return (
    <div className="af-langswitch" ref={containerRef}>
      <button
        type="button"
        className="af-langswitch__button"
        onClick={() => setIsOpen((open) => !open)}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
      >
        <span>{current.nativeLabel}</span>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>

      {isOpen && (
        <div className="af-langswitch__panel">
          <input
            type="text"
            className="af-langswitch__search"
            placeholder="Search language…"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            autoFocus
          />
          <ul className="af-langswitch__list" role="listbox">
            {filtered.map((language) => (
              <li key={language.code}>
                <button
                  type="button"
                  className={`af-langswitch__option ${language.code === current.code ? 'af-langswitch__option--active' : ''}`}
                  onClick={() => selectLanguage(language.code)}
                  role="option"
                  aria-selected={language.code === current.code}
                >
                  <span className="af-langswitch__native">{language.nativeLabel}</span>
                  <span className="af-langswitch__label">{language.label}</span>
                </button>
              </li>
            ))}
            {filtered.length === 0 && <li className="af-langswitch__empty">No matches</li>}
          </ul>
        </div>
      )}
    </div>
  )
}

export default LanguageSwitcher
