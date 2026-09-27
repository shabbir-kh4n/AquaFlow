import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { AuthModalProvider } from './context/AuthModalContext'
import AuthModal from './components/AuthModal'
import InstallPrompt from './components/InstallPrompt'
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
import WaterLeakage from './pages/admin/WaterLeakage'
import AdminProfile from './pages/admin/AdminProfile'
import ComingSoon from './pages/admin/ComingSoon'
import ResidentLayout from './components/resident/ResidentLayout'
import ResidentHome from './pages/resident/ResidentHome'
import UsageHistory from './pages/resident/UsageHistory'
import ResidentBilling from './pages/resident/ResidentBilling'
import ResidentNotifications from './pages/resident/ResidentNotifications'
import Recommendations from './pages/resident/Recommendations'
import ResidentSupportTickets from './pages/resident/ResidentSupportTickets'
import SupportConcerns from './pages/admin/SupportConcerns'
import PlatformLayout from './components/platform/PlatformLayout'
import PlatformDashboard from './pages/platform/PlatformDashboard'
import Communities from './pages/platform/Communities'
import CommunityDetail from './pages/platform/CommunityDetail'
import PlatformReports from './pages/platform/PlatformReports'
import PlatformSupportTickets from './pages/platform/SupportTickets'
import SystemStatus from './pages/platform/SystemStatus'
import './styles/theme.css'

function App() {
  return (
    <AuthProvider>
      <AuthModalProvider>
        <BrowserRouter>
          <AuthModal />
          <InstallPrompt />
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
              <Route path="/resident/notifications" element={<ResidentNotifications />} />
              <Route path="/resident/recommendations" element={<Recommendations />} />
              <Route path="/resident/support" element={<ResidentSupportTickets />} />
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
              <Route path="/admin/water-leakage" element={<WaterLeakage />} />
              <Route path="/admin/support" element={<SupportConcerns />} />
              <Route path="/admin/profile" element={<AdminProfile />} />

              {STUB_PAGES.map((page) => (
                <Route key={page.path} path={page.path} element={<ComingSoon titleKey={page.titleKey} descriptionKey={page.descriptionKey} />} />
              ))}
            </Route>

            <Route
              element={
                <ProtectedRoute role="SYSTEM_ADMIN">
                  <PlatformLayout />
                </ProtectedRoute>
              }
            >
              <Route path="/platform/dashboard" element={<PlatformDashboard />} />
              <Route path="/platform/communities" element={<Communities />} />
              <Route path="/platform/communities/:adminId" element={<CommunityDetail />} />
              <Route path="/platform/reports" element={<PlatformReports />} />
              <Route path="/platform/support-tickets" element={<PlatformSupportTickets />} />
              <Route path="/platform/system-status" element={<SystemStatus />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AuthModalProvider>
    </AuthProvider>
  )
}

export default App
