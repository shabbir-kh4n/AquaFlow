import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth'
import { getPlatformProfile } from '../../api/accountApi'
import AvatarPicker from '../../components/AvatarPicker'
import ChangePasswordForm from '../../components/ChangePasswordForm'
import './PlatformProfile.css'

function PlatformProfile() {
  const { t } = useTranslation('platform')
  const { user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getPlatformProfile()
      .then(setProfile)
      .catch((err) => setError(err.response?.data?.message || t('profile.loadError')))
  }, [t])

  const name = profile?.fullName || user?.fullName
  const email = profile?.email || user?.email

  return (
    <div className="af-pprofile">
      <div className="af-pprofile__main">
        <section className="af-pprofile__panel">
          <h3>{t('profile.accountTitle')}</h3>
          {error && <div className="af-pprofile__error">{error}</div>}
          <dl className="af-pprofile__list">
            <div className="af-pprofile__row">
              <dt>{t('profile.fields.fullName')}</dt>
              <dd>{name || '—'}</dd>
            </div>
            <div className="af-pprofile__row">
              <dt>{t('profile.fields.email')}</dt>
              <dd>{email || '—'}</dd>
            </div>
            <div className="af-pprofile__row">
              <dt>{t('profile.fields.role')}</dt>
              <dd>{t('profile.fields.roleValue')}</dd>
            </div>
          </dl>
        </section>

        <section className="af-pprofile__panel">
          <h3>{t('profile.avatarTitle')}</h3>
          <AvatarPicker
            role="SYSTEM_ADMIN"
            value={profile ? profile.avatarKey : user?.avatarKey ?? null}
            name={name}
            onSaved={(avatarKey) => setProfile((prev) => (prev ? { ...prev, avatarKey } : prev))}
          />
        </section>
      </div>

      <section className="af-pprofile__panel">
        <h3>{t('profile.passwordTitle')}</h3>
        <ChangePasswordForm />
      </section>
    </div>
  )
}

export default PlatformProfile
