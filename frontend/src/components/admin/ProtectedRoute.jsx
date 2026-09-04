import { useEffect } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../../context/useAuth'
import { useAuthModal } from '../../context/useAuthModal'

function ProtectedRoute({ children, role }) {
  const { user, isAuthenticated } = useAuth()
  const { openAuthModal } = useAuthModal()

  useEffect(() => {
    if (!isAuthenticated) {
      openAuthModal('signin')
    }
  }, [isAuthenticated, openAuthModal])

  if (!isAuthenticated) {
    return <Navigate to="/" replace />
  }

  if (role && user?.role !== role) {
    return <Navigate to="/" replace />
  }

  return children
}

export default ProtectedRoute
