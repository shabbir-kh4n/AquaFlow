import { useCallback, useMemo, useState } from 'react'
import { loginUser, registerUser } from '../api/authApi'
import { AuthContext } from './authContextInstance'

const TOKEN_KEY = 'aquaflow_token'
const USER_KEY = 'aquaflow_user'

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    const stored = localStorage.getItem(USER_KEY)
    return stored ? JSON.parse(stored) : null
  })

  function persistSession(response) {
    const { token, ...profile } = response
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(profile))
    setUser(profile)
  }

  const login = useCallback(async (credentials) => {
    const response = await loginUser(credentials)
    persistSession(response)
    return response
  }, [])

  const register = useCallback(async (details) => {
    const response = await registerUser(details)
    persistSession(response)
    return response
  }, [])

  const setSession = useCallback((response) => {
    persistSession(response)
  }, [])

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, isAuthenticated: !!user, login, register, setSession, logout }),
    [user, login, register, setSession, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
