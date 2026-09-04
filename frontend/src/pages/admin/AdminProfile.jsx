import { useEffect, useState } from 'react'
import { useAuth } from '../../context/useAuth'
import { getAdminProfile, getApartment, updateAdminProfile, updateApartment } from '../../api/profileApi'
import '../../components/AuthForm.css'
import './AdminProfile.css'

const SETTINGS_TABS = [
  { key: 'profile', label: 'Profile Settings', built: true },
  { key: 'apartment', label: 'Apartment Profile', built: false },
  { key: 'notifications', label: 'Notification Rules', built: false },
  { key: 'billing', label: 'Billing Integrations', built: false },
  { key: 'security', label: 'Security & Access', built: false },
]

function AdminProfile() {
  const { user } = useAuth()

  const [profile, setProfile] = useState(null)
  const [profileForm, setProfileForm] = useState({ fullName: '', phone: '' })
  const [profileSaving, setProfileSaving] = useState(false)
  const [profileError, setProfileError] = useState('')
  const [profileSaved, setProfileSaved] = useState(false)

  const [apartment, setApartment] = useState(null)
  const [apartmentForm, setApartmentForm] = useState({ name: '', mainsSource: '', address: '' })
  const [apartmentSaving, setApartmentSaving] = useState(false)
  const [apartmentError, setApartmentError] = useState('')
  const [apartmentSaved, setApartmentSaved] = useState(false)

  useEffect(() => {
    getAdminProfile().then((data) => {
      setProfile(data)
      setProfileForm({ fullName: data.fullName, phone: data.phone || '' })
    })
    getApartment().then((data) => {
      setApartment(data)
      setApartmentForm({ name: data.name, mainsSource: data.mainsSource || '', address: data.address || '' })
    })
  }, [])

  async function handleProfileSubmit(event) {
    event.preventDefault()
    setProfileError('')
    setProfileSaved(false)
    setProfileSaving(true)
    try {
      const updated = await updateAdminProfile(profileForm)
      setProfile(updated)
      setProfileSaved(true)
    } catch (err) {
      setProfileError(err.response?.data?.message || 'Could not save your profile. Please try again.')
    } finally {
      setProfileSaving(false)
    }
  }

  async function handleApartmentSubmit(event) {
    event.preventDefault()
    setApartmentError('')
    setApartmentSaved(false)
    setApartmentSaving(true)
    try {
      const updated = await updateApartment(apartmentForm)
      setApartment(updated)
      setApartmentSaved(true)
    } catch (err) {
      setApartmentError(err.response?.data?.message || 'Could not save apartment details. Please try again.')
    } finally {
      setApartmentSaving(false)
    }
  }

  const initials = (profile?.fullName || user?.fullName || 'Admin')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  return (
    <div className="af-profile__layout">
      <aside className="af-profile__tabs">
        {SETTINGS_TABS.map((tab) =>
          tab.built ? (
            <button key={tab.key} type="button" className="af-profile__tab af-profile__tab--active">
              {tab.label}
            </button>
          ) : (
            <button
              key={tab.key}
              type="button"
              className="af-profile__tab"
              disabled
              title={`${tab.label} isn't built yet`}
            >
              {tab.label}
            </button>
          ),
        )}
      </aside>

      <div className="af-profile__main">
        <section className="af-profile__panel">
          <h3>Profile Specifications</h3>
          <div className="af-profile__photo-row">
            <span className="af-profile__avatar">{initials}</span>
            <div className="af-profile__photo-actions">
              <button type="button" className="af-btn af-btn--cyan" disabled title="Photo upload isn't available yet">
                Change Photo
              </button>
              <button type="button" className="af-btn af-btn--outline" disabled title="Photo upload isn't available yet">
                Delete
              </button>
              <p className="af-profile__hint">JPG or PNG. Max size 2MB.</p>
            </div>
          </div>

          <form onSubmit={handleProfileSubmit}>
            {profileError && <div className="af-form__error">{profileError}</div>}
            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="fullName">Full Name</label>
                <input
                  id="fullName"
                  value={profileForm.fullName}
                  onChange={(e) => {
                    setProfileForm((prev) => ({ ...prev, fullName: e.target.value }))
                    setProfileSaved(false)
                  }}
                />
              </div>
              <div className="af-form__field">
                <label htmlFor="email">Email Address</label>
                <input id="email" value={profile?.email || ''} disabled title="Email can't be changed here" />
              </div>
            </div>
            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="phone">Phone Number</label>
                <input
                  id="phone"
                  value={profileForm.phone}
                  onChange={(e) => {
                    setProfileForm((prev) => ({ ...prev, phone: e.target.value }))
                    setProfileSaved(false)
                  }}
                />
              </div>
              <div className="af-form__field">
                <label htmlFor="accessLevel">Access Level</label>
                <input id="accessLevel" value="Community Administrator" disabled title="Role management isn't available yet" />
              </div>
            </div>
            <div className="af-profile__save-row">
              {profileSaved && <span className="af-profile__saved">Saved.</span>}
              <button type="submit" className="af-form__submit" disabled={profileSaving || !profile}>
                {profileSaving ? 'Saving…' : 'Save Profile'}
              </button>
            </div>
          </form>
        </section>

        <section className="af-profile__panel">
          <h3>Apartment Details</h3>
          <form onSubmit={handleApartmentSubmit}>
            {apartmentError && <div className="af-form__error">{apartmentError}</div>}
            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="apartmentName">Apartment Name</label>
                <input
                  id="apartmentName"
                  value={apartmentForm.name}
                  onChange={(e) => {
                    setApartmentForm((prev) => ({ ...prev, name: e.target.value }))
                    setApartmentSaved(false)
                  }}
                />
              </div>
              <div className="af-form__field">
                <label htmlFor="mainsSource">Mains Source</label>
                <input
                  id="mainsSource"
                  placeholder="e.g. Municipal Supply + Supplemental Tankers"
                  value={apartmentForm.mainsSource}
                  onChange={(e) => {
                    setApartmentForm((prev) => ({ ...prev, mainsSource: e.target.value }))
                    setApartmentSaved(false)
                  }}
                />
              </div>
            </div>
            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="address">Physical Address</label>
                <input
                  id="address"
                  value={apartmentForm.address}
                  onChange={(e) => {
                    setApartmentForm((prev) => ({ ...prev, address: e.target.value }))
                    setApartmentSaved(false)
                  }}
                />
              </div>
            </div>
            <div className="af-profile__save-row">
              {apartmentSaved && <span className="af-profile__saved">Saved.</span>}
              <button type="submit" className="af-form__submit" disabled={apartmentSaving || !apartment}>
                {apartmentSaving ? 'Saving…' : 'Save Apartment Details'}
              </button>
            </div>
          </form>
        </section>
      </div>

      <aside className="af-profile__side">
        <section className="af-profile__panel">
          <h3>Security &amp; Access Preview</h3>
          <div className="af-profile__security-row">
            <div>
              <strong>Two-Factor Authentication</strong>
              <p>Enabled on login</p>
            </div>
            <button
              type="button"
              className="af-profile__toggle af-profile__toggle--off"
              disabled
              title="Two-factor authentication isn't available yet"
              aria-label="Two-factor authentication (preview only)"
            />
          </div>

          <p className="af-profile__sessions-label">Active Admin Sessions</p>
          <p className="af-profile__empty">Session tracking isn't available yet — this is a preview of the upcoming layout.</p>
        </section>
      </aside>
    </div>
  )
}

export default AdminProfile
