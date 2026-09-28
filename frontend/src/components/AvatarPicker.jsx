import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../context/useAuth'
import { updateAvatarForRole } from '../api/accountApi'
import Avatar from './Avatar'
import { avatarKeysForRole } from './avatarKeys'
import './AvatarPicker.css'

// Grid of the signed-in role's 12 preset avatars (plus an initials option) with a save
// button. On save it updates the stored user in AuthContext so the shell avatar changes
// immediately, and calls onSaved(newKey) for the page's own state.
function AvatarPicker({ role, value = null, name, onSaved }) {
  const { t } = useTranslation('common')
  const { updateUser } = useAuth()
  const [selected, setSelected] = useState(value)
  const [lastValue, setLastValue] = useState(value)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState('')
  const [expanded, setExpanded] = useState(false)

  // Follow the saved value when it changes from outside (e.g. profile finished loading).
  // Adjusted during render per this project's react-hooks/set-state-in-effect rule.
  if (value !== lastValue) {
    setLastValue(value)
    setSelected(value)
  }

  const keys = avatarKeysForRole(role)
  const dirty = (selected || null) !== (value || null)

  function choose(key) {
    setSelected(key)
    setSaved(false)
    setError('')
  }

  async function handleSave() {
    setSaving(true)
    setError('')
    setSaved(false)
    try {
      const updated = await updateAvatarForRole(role, selected)
      const newKey = updated?.avatarKey ?? null
      updateUser({ avatarKey: newKey })
      setLastValue(newKey)
      setSelected(newKey)
      setSaved(true)
      setExpanded(false)
      onSaved?.(newKey)
    } catch (err) {
      setError(err.response?.data?.message || t('avatarPicker.saveError'))
    } finally {
      setSaving(false)
    }
  }

  function handleCancel() {
    setSelected(value)
    setError('')
    setExpanded(false)
  }

  return (
    <div className="af-avatar-picker">
      <div className="af-avatar-picker__preview">
        <Avatar avatarKey={selected} name={name} className="af-avatar-picker__preview-avatar" />
        {expanded ? (
          <p>{t('avatarPicker.hint')}</p>
        ) : (
          <button type="button" className="af-avatar-picker__change" onClick={() => setExpanded(true)}>
            {t('avatarPicker.change')}
          </button>
        )}
      </div>

      {expanded && (
        <>
          <div className="af-avatar-picker__grid" role="radiogroup" aria-label={t('avatarPicker.label')}>
            <button
              type="button"
              role="radio"
              aria-checked={!selected}
              className={`af-avatar-picker__option ${!selected ? 'af-avatar-picker__option--selected' : ''}`}
              onClick={() => choose(null)}
              title={t('avatarPicker.useInitials')}
            >
              <Avatar avatarKey={null} name={name} className="af-avatar-picker__initials" />
              <span className="af-avatar-picker__sr">{t('avatarPicker.useInitials')}</span>
            </button>
            {keys.map((key, index) => {
              const isSelected = selected === key
              const label = t('avatarPicker.optionLabel', { number: index + 1 })
              return (
                <button
                  key={key}
                  type="button"
                  role="radio"
                  aria-checked={isSelected}
                  className={`af-avatar-picker__option ${isSelected ? 'af-avatar-picker__option--selected' : ''}`}
                  onClick={() => choose(key)}
                  title={label}
                >
                  <Avatar avatarKey={key} name={name} />
                  <span className="af-avatar-picker__sr">{label}</span>
                </button>
              )
            })}
          </div>

          {error && <div className="af-avatar-picker__error">{error}</div>}

          <div className="af-avatar-picker__actions">
            <button type="button" className="af-avatar-picker__cancel" onClick={handleCancel} disabled={saving}>
              {t('avatarPicker.cancel')}
            </button>
            <button
              type="button"
              className="af-avatar-picker__save"
              onClick={handleSave}
              disabled={saving || !dirty}
            >
              {saving ? t('avatarPicker.saving') : t('avatarPicker.save')}
            </button>
          </div>
        </>
      )}

      {!expanded && saved && <span className="af-avatar-picker__saved">{t('avatarPicker.saved')}</span>}
    </div>
  )
}

export default AvatarPicker
