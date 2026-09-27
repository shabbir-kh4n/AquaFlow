import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import './InstallPrompt.css'

const DISMISSED_KEY = 'af_install_dismissed'

function InstallPrompt() {
  const { t } = useTranslation('common')
  const [deferredPrompt, setDeferredPrompt] = useState(null)
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (window.matchMedia('(display-mode: standalone)').matches) return
    if (localStorage.getItem(DISMISSED_KEY) === 'true') return

    function handleBeforeInstallPrompt(event) {
      event.preventDefault()
      setDeferredPrompt(event)
      setVisible(true)
    }

    function handleAppInstalled() {
      setVisible(false)
      setDeferredPrompt(null)
    }

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
    window.addEventListener('appinstalled', handleAppInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt)
      window.removeEventListener('appinstalled', handleAppInstalled)
    }
  }, [])

  async function handleInstall() {
    if (!deferredPrompt) return
    deferredPrompt.prompt()
    await deferredPrompt.userChoice
    setDeferredPrompt(null)
    setVisible(false)
  }

  function handleDismiss() {
    localStorage.setItem(DISMISSED_KEY, 'true')
    setVisible(false)
  }

  if (!visible) return null

  return (
    <div className="af-install-prompt" role="dialog" aria-label={t('installPrompt.title')}>
      <div className="af-install-prompt__text">
        <strong>{t('installPrompt.title')}</strong>
        <span>{t('installPrompt.description')}</span>
      </div>
      <div className="af-install-prompt__actions">
        <button type="button" className="af-install-prompt__dismiss" onClick={handleDismiss}>
          {t('installPrompt.dismiss')}
        </button>
        <button type="button" className="af-install-prompt__install" onClick={handleInstall}>
          {t('installPrompt.install')}
        </button>
      </div>
    </div>
  )
}

export default InstallPrompt
