import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { sendChatMessage } from '../../api/chatApi'
import './ChatWidget.css'

// Translation keys (common namespace), resolved with t() at render time.
const TITLE_KEYS = {
  landing: 'chat.titles.landing',
  resident: 'chat.titles.resident',
  admin: 'chat.titles.admin',
  platform: 'chat.titles.platform',
}

const SUGGESTED_PROMPT_KEYS = {
  landing: [
    'chat.prompts.landing.whatIs',
    'chat.prompts.landing.tieredBilling',
    'chat.prompts.landing.leaks',
  ],
  resident: [
    'chat.prompts.resident.billEstimate',
    'chat.prompts.resident.alerts',
    'chat.prompts.resident.readings',
  ],
  admin: [
    'chat.prompts.admin.households',
    'chat.prompts.admin.billingCycles',
    'chat.prompts.admin.tariff',
  ],
  platform: [
    'chat.prompts.platform.communities',
    'chat.prompts.platform.usageReport',
    'chat.prompts.platform.systemStatus',
  ],
}

function errorKeyFor(err) {
  const status = err?.response?.status
  if (status === 429) {
    return 'chat.errors.rateLimited'
  }
  if (status === 503) {
    return 'chat.errors.unavailable'
  }
  return 'chat.errors.generic'
}

function ChatWidget({ endpoint, persona, title }) {
  const { t } = useTranslation('common')
  const [isOpen, setIsOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [error, setError] = useState(null)
  const messagesEndRef = useRef(null)

  useEffect(() => {
    if (isOpen) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
    }
  }, [messages, isOpen])

  function handleSend(text) {
    const trimmed = text.trim()
    if (!trimmed || isSending) return

    const history = messages.map(({ role, content }) => ({ role, content }))
    const nextMessages = [...messages, { role: 'user', content: trimmed }]
    setMessages(nextMessages)
    setInput('')
    setError(null)
    setIsSending(true)

    sendChatMessage(endpoint, trimmed, history)
      .then((data) => {
        setMessages((prev) => [...prev, { role: 'model', content: data.reply }])
      })
      .catch((err) => {
        setError(errorKeyFor(err))
      })
      .finally(() => setIsSending(false))
  }

  function handleSubmit(event) {
    event.preventDefault()
    handleSend(input)
  }

  const panelTitle = title || t(TITLE_KEYS[persona] || 'chat.titles.default')
  const suggestions = (SUGGESTED_PROMPT_KEYS[persona] || []).map((key) => t(key))

  return (
    <div className="af-chat">
      {isOpen && (
        <div className="af-chat__panel" role="dialog" aria-label={panelTitle}>
          <header className="af-chat__header">
            <span>{panelTitle}</span>
            <button
              type="button"
              className="af-chat__close"
              onClick={() => setIsOpen(false)}
              aria-label={t('chat.closeAssistant')}
            >
              ×
            </button>
          </header>

          <div className="af-chat__messages">
            {messages.length === 0 && (
              <div className="af-chat__empty">
                <p>{t('chat.emptyHint')}</p>
                <div className="af-chat__suggestions">
                  {suggestions.map((prompt) => (
                    <button
                      key={prompt}
                      type="button"
                      className="af-chat__suggestion"
                      onClick={() => handleSend(prompt)}
                    >
                      {prompt}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {messages.map((message, index) => (
              <div
                key={index}
                className={`af-chat__bubble af-chat__bubble--${message.role === 'user' ? 'user' : 'assistant'}`}
              >
                {message.content}
              </div>
            ))}

            {isSending && (
              <div className="af-chat__bubble af-chat__bubble--assistant af-chat__bubble--loading">
                <span className="af-chat__dot" />
                <span className="af-chat__dot" />
                <span className="af-chat__dot" />
              </div>
            )}

            {error && <div className="af-chat__error">{t(error)}</div>}

            <div ref={messagesEndRef} />
          </div>

          <form className="af-chat__footer" onSubmit={handleSubmit}>
            <input
              type="text"
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder={t('chat.inputPlaceholder')}
              aria-label={t('chat.inputLabel')}
            />
            <button type="submit" disabled={isSending || !input.trim()}>
              {t('chat.send')}
            </button>
          </form>
        </div>
      )}

      <button
        type="button"
        className="af-chat__fab"
        onClick={() => setIsOpen((open) => !open)}
        aria-label={isOpen ? t('chat.closeAssistant') : t('chat.openAssistant')}
      >
        {isOpen ? (
          <span aria-hidden="true">×</span>
        ) : (
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
          </svg>
        )}
      </button>
    </div>
  )
}

export default ChatWidget
