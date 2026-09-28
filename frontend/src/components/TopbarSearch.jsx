import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import './TopbarSearch.css'

const MAX_PAGES = 4
const MAX_RECORDS = 6

// Shell topbar search, shared by the admin/resident/platform layouts. Matches the role's own
// pages instantly, plus role-specific records (`loadRecords`, fetched once on first focus and
// cached for the session) - e.g. residents for an admin, communities for the System Admin.
// `pages`/records are { id, label, sublabel?, to } with labels already translated by the caller.
function TopbarSearch({ className, icon, placeholder, pages, loadRecords }) {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const containerRef = useRef(null)
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [records, setRecords] = useState(null)
  const [recordsError, setRecordsError] = useState(false)
  const [activeIndex, setActiveIndex] = useState(0)
  const loadStarted = useRef(false)

  useEffect(() => {
    function handleClickOutside(event) {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  function ensureRecords() {
    if (loadStarted.current || !loadRecords) return
    loadStarted.current = true
    loadRecords()
      .then((items) => setRecords(items))
      .catch(() => setRecordsError(true))
  }

  const term = query.trim().toLowerCase()
  const results = useMemo(() => {
    if (!term) return []
    const matches = (item) => [item.label, item.sublabel].some((field) => field?.toLowerCase().includes(term))
    const pageHits = pages.filter(matches).slice(0, MAX_PAGES).map((item) => ({ ...item, kind: 'page' }))
    const recordHits = (records || []).filter(matches).slice(0, MAX_RECORDS).map((item) => ({ ...item, kind: 'record' }))
    return [...pageHits, ...recordHits]
  }, [term, pages, records])

  // Keep the highlighted row valid as results change (adjust-during-render, not an effect).
  const [lastTerm, setLastTerm] = useState(term)
  if (lastTerm !== term) {
    setLastTerm(term)
    setActiveIndex(0)
  }

  function select(item) {
    setOpen(false)
    setQuery('')
    navigate(item.to)
  }

  function handleKeyDown(event) {
    if (event.key === 'Escape') {
      setOpen(false)
    } else if (event.key === 'ArrowDown' && results.length > 0) {
      event.preventDefault()
      setOpen(true)
      setActiveIndex((i) => (i + 1) % results.length)
    } else if (event.key === 'ArrowUp' && results.length > 0) {
      event.preventDefault()
      setActiveIndex((i) => (i - 1 + results.length) % results.length)
    } else if (event.key === 'Enter' && results[activeIndex]) {
      event.preventDefault()
      select(results[activeIndex])
    }
  }

  const loadingRecords = loadRecords && records === null && !recordsError
  const showPanel = open && term.length > 0

  return (
    <div className="af-topsearch" ref={containerRef}>
      <label className={className}>
        {icon}
        <input
          type="search"
          placeholder={placeholder}
          aria-label={t('shell.search')}
          aria-expanded={showPanel}
          aria-autocomplete="list"
          role="combobox"
          value={query}
          onFocus={() => {
            ensureRecords()
            setOpen(true)
          }}
          onChange={(event) => {
            setQuery(event.target.value)
            setOpen(true)
          }}
          onKeyDown={handleKeyDown}
        />
      </label>

      {showPanel && (
        <ul className="af-topsearch__panel" role="listbox">
          {results.map((item, index) => (
            <li key={`${item.kind}-${item.id}`}>
              <button
                type="button"
                role="option"
                aria-selected={index === activeIndex}
                className={`af-topsearch__option${index === activeIndex ? ' af-topsearch__option--active' : ''}`}
                onMouseEnter={() => setActiveIndex(index)}
                onClick={() => select(item)}
              >
                <span className="af-topsearch__label">{item.label}</span>
                {item.sublabel && <span className="af-topsearch__sublabel">{item.sublabel}</span>}
                <span className="af-topsearch__kind">
                  {item.kind === 'page' ? t('search.kindPage') : t('search.kindRecord')}
                </span>
              </button>
            </li>
          ))}
          {results.length === 0 && (
            <li className="af-topsearch__empty">{loadingRecords ? t('search.loading') : t('search.noResults')}</li>
          )}
          {recordsError && <li className="af-topsearch__empty">{t('search.recordsUnavailable')}</li>}
        </ul>
      )}
    </div>
  )
}

export default TopbarSearch
