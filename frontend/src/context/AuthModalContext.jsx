import { useCallback, useMemo, useState } from 'react'
import { AuthModalContext } from './authModalContextInstance'

export function AuthModalProvider({ children }) {
  const [mode, setMode] = useState(null) // null | 'signin' | 'signup'

  const openAuthModal = useCallback((initialMode = 'signin') => {
    setMode(initialMode)
  }, [])

  const closeAuthModal = useCallback(() => {
    setMode(null)
  }, [])

  const value = useMemo(
    () => ({ isOpen: mode !== null, mode, openAuthModal, closeAuthModal, setMode }),
    [mode, openAuthModal, closeAuthModal],
  )

  return <AuthModalContext.Provider value={value}>{children}</AuthModalContext.Provider>
}
