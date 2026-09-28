import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../context/useAuth'
import { getResidentProfile } from '../../api/accountApi'
import AvatarPicker from '../../components/AvatarPicker'
import ChangePasswordForm from '../../components/ChangePasswordForm'
import './ResidentProfile.css'

function formatDate(value, language) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleDateString(language, { year: 'numeric', month: 'long', day: 'numeric' })
}

function ResidentProfile() {
  const { t, i18n } = useTranslation('resident')
  const { user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getResidentProfile()
      .then(setProfile)
      .catch((err) => setError(err.response?.data?.message || t('profile.loadError')))
  }, [t])

  const notSet = t('profile.notSet')
  const joined = formatDate(profile?.dateOfJoining || profile?.createdAt, i18n.language)
  const rows = profile
    ? [
        { key: 'fullName', value: profile.fullName },
        { key: 'email', value: profile.email },
        { key: 'phone', value: profile.phone },
        { key: 'houseNumber', value: profile.houseNumber },
        { key: 'meterNumber', value: profile.meterNumber },
        { key: 'community', value: profile.communityName },
        {
          key: 'flatSize',
          value:
            profile.flatSizeSqft != null
              ? t('profile.flatSizeValue', { size: Number(profile.flatSizeSqft).toLocaleString(i18n.language) })
              : null,
        },
        { key: 'joined', value: joined },
      ]
    : []

  return (
    <div className="af-rprofile">
      <section className="af-rprofile__panel af-rprofile__details">
        <h3>{t('profile.detailsTitle')}</h3>
        <p className="af-rprofile__note">{t('profile.managedNote')}</p>

        {error && <div className="af-rprofile__error">{error}</div>}
        {!profile && !error && <p className="af-rprofile__muted">{t('profile.loading')}</p>}

        {profile && (
          <dl className="af-rprofile__list">
            {rows.map((row) => (
              <div key={row.key} className="af-rprofile__row">
                <dt>{t(`profile.fields.${row.key}`)}</dt>
                <dd className={row.value ? '' : 'af-rprofile__muted'}>{row.value || notSet}</dd>
              </div>
            ))}
          </dl>
        )}
      </section>

      <div className="af-rprofile__side">
        <section className="af-rprofile__panel">
          <h3>{t('profile.avatarTitle')}</h3>
          <AvatarPicker
            role="RESIDENT"
            value={profile ? profile.avatarKey : user?.avatarKey ?? null}
            name={profile?.fullName || user?.fullName}
            onSaved={(avatarKey) => setProfile((prev) => (prev ? { ...prev, avatarKey } : prev))}
          />
        </section>

        <section className="af-rprofile__panel">
          <h3>{t('profile.passwordTitle')}</h3>
          <ChangePasswordForm />
        </section>
      </div>
    </div>
  )
}

export default ResidentProfile
