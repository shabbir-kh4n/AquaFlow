import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { AuthModalProvider } from './context/AuthModalContext'
import AuthModal from './components/AuthModal'
import ProtectedRoute from './components/admin/ProtectedRoute'
import AdminLayout from './components/admin/AdminLayout'
import { STUB_PAGES } from './components/admin/adminRoutes'
import Landing from './pages/Landing'
import SetPassword from './pages/SetPassword'
import AdminDashboard from './pages/admin/AdminDashboard'
import HouseholdsDirectory from './pages/admin/HouseholdsDirectory'
import MeterReadings from './pages/admin/MeterReadings'
import TariffPlans from './pages/admin/TariffPlans'
import BillingCycles from './pages/admin/BillingCycles'
import BulkWaterPurchases from './pages/admin/BulkWaterPurchases'
import AdminProfile from './pages/admin/AdminProfile'
import ComingSoon from './pages/admin/ComingSoon'
import ResidentLayout from './components/resident/ResidentLayout'
import ResidentHome from './pages/resident/ResidentHome'
import UsageHistory from './pages/resident/UsageHistory'
import ResidentBilling from './pages/resident/ResidentBilling'
import './styles/theme.css'

function App() {
  return (
    <AuthProvider>
      <AuthModalProvider>
        <BrowserRouter>
          <AuthModal />
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/set-password/:token" element={<SetPassword />} />

            <Route
              element={
                <ProtectedRoute role="RESIDENT">
                  <ResidentLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/resident/home" element={<ResidentHome />} />
              <Route path="/resident/usage-history" element={<UsageHistory />} />
              <Route path="/resident/billing" element={<ResidentBilling />} />
            </Route>

            <Route
              element={
                <ProtectedRoute role="ADMIN">
                  <AdminLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/admin/dashboard" element={<AdminDashboard />} />
              <Route path="/admin/households" element={<HouseholdsDirectory />} />
              <Route path="/admin/meter-readings" element={<MeterReadings />} />
              <Route path="/admin/tariff-plans" element={<TariffPlans />} />
              <Route path="/admin/billing" element={<BillingCycles />} />
              <Route path="/admin/bulk-purchases" element={<BulkWaterPurchases />} />
              <Route path="/admin/profile" element={<AdminProfile />} />

              {STUB_PAGES.map((page) => (
                <Route key={page.path} path={page.path} element={<ComingSoon title={page.title} description={page.description} />} />
              ))}
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthModalProvider>
    </AuthProvider>
  )
}

export default App
