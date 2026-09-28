import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth'
import { getAdminProfile, getApartment, updateAdminProfile, updateApartment } from '../../api/profileApi'
import AvatarPicker from '../../components/AvatarPicker'
import ChangePasswordForm from '../../components/ChangePasswordForm'
import '../../components/AuthForm.css'
import './AdminProfile.css'

const SETTINGS_TABS = [
  { key: 'profile', labelKey: 'profile.tabs.profile', built: true },
  { key: 'apartment', labelKey: 'profile.tabs.apartment', built: false },
  { key: 'notifications', labelKey: 'profile.tabs.notifications', built: false },
  { key: 'billing', labelKey: 'profile.tabs.billing', built: false },
  { key: 'security', labelKey: 'profile.tabs.security', built: false },
]

function AdminProfile() {
  const { user } = useAuth()
  const { t } = useTranslation('admin')

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
      setProfileError(err.response?.data?.message || t('profile.profileSaveError'))
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
      setApartmentError(err.response?.data?.message || t('profile.apartmentSaveError'))
    } finally {
      setApartmentSaving(false)
    }
  }

  return (
    <div className="af-profile__layout">
      <aside className="af-profile__tabs">
        {SETTINGS_TABS.map((tab) =>
          tab.built ? (
            <button key={tab.key} type="button" className="af-profile__tab af-profile__tab--active">
              {t(tab.labelKey)}
            </button>
          ) : (
            <button
              key={tab.key}
              type="button"
              className="af-profile__tab"
              disabled
              title={t('profile.tabNotBuilt', { tab: t(tab.labelKey) })}
            >
              {t(tab.labelKey)}
            </button>
          ),
        )}
      </aside>

      <div className="af-profile__main">
        <section className="af-profile__panel">
          <h3>{t('profile.specsTitle')}</h3>
          <div className="af-profile__photo-row">
            <p className="af-profile__sessions-label">{t('profile.avatarLabel')}</p>
            <AvatarPicker
              role="ADMIN"
              value={profile ? profile.avatarKey : user?.avatarKey ?? null}
              name={profile?.fullName || user?.fullName}
              onSaved={(avatarKey) => setProfile((prev) => (prev ? { ...prev, avatarKey } : prev))}
            />
          </div>

          <form onSubmit={handleProfileSubmit}>
            {profileError && <div className="af-form__error">{profileError}</div>}
            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="fullName">{t('profile.fields.fullName')}</label>
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
                <label htmlFor="email">{t('profile.fields.email')}</label>
                <input id="email" value={profile?.email || ''} disabled title={t('profile.fields.emailLocked')} />
              </div>
            </div>
            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="phone">{t('profile.fields.phone')}</label>
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
                <label htmlFor="accessLevel">{t('profile.fields.accessLevel')}</label>
                <input id="accessLevel" value={t('profile.fields.accessLevelValue')} disabled title={t('profile.fields.roleLocked')} />
              </div>
            </div>
            <div className="af-profile__save-row">
              {profileSaved && <span className="af-profile__saved">{t('shared.saved')}</span>}
              <button type="submit" className="af-form__submit" disabled={profileSaving || !profile}>
                {profileSaving ? t('shared.saving') : t('profile.saveProfile')}
              </button>
            </div>
          </form>
        </section>

        <section className="af-profile__panel">
          <h3>{t('profile.apartmentTitle')}</h3>
          <form onSubmit={handleApartmentSubmit}>
            {apartmentError && <div className="af-form__error">{apartmentError}</div>}
            <div className="af-form__row">
              <div className="af-form__field">
                <label htmlFor="apartmentName">{t('profile.fields.apartmentName')}</label>
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
                <label htmlFor="mainsSource">{t('profile.fields.mainsSource')}</label>
                <input
                  id="mainsSource"
                  placeholder={t('profile.fields.mainsSourcePlaceholder')}
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
                <label htmlFor="address">{t('profile.fields.address')}</label>
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
              {apartmentSaved && <span className="af-profile__saved">{t('shared.saved')}</span>}
              <button type="submit" className="af-form__submit" disabled={apartmentSaving || !apartment}>
                {apartmentSaving ? t('shared.saving') : t('profile.saveApartment')}
              </button>
            </div>
          </form>
        </section>
      </div>

      <aside className="af-profile__side">
        <section className="af-profile__panel">
          <h3>{t('profile.passwordTitle')}</h3>
          <ChangePasswordForm />
        </section>

        <section className="af-profile__panel">
          <h3>{t('profile.security.title')}</h3>
          <p className="af-profile__preview-note">{t('profile.security.previewNote')}</p>
          <div className="af-profile__security-row">
            <div>
              <strong>{t('profile.security.twoFactor')}</strong>
              <p>{t('profile.security.twoFactorHint')}</p>
            </div>
            <button
              type="button"
              className="af-profile__toggle af-profile__toggle--off"
              disabled
              title={t('profile.security.twoFactorUnavailable')}
              aria-label={t('profile.security.twoFactorAria')}
            />
          </div>

          <p className="af-profile__sessions-label">{t('profile.security.sessionsLabel')}</p>
          <p className="af-profile__empty">{t('profile.security.sessionsEmpty')}</p>
        </section>
      </aside>
    </div>
  )
}

export default AdminProfile
