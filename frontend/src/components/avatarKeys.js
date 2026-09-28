// Preset avatar keys: '<set>-<nn>' (e.g. 'resident-07'), one 12-avatar set per role.
// Files live in public/avatars/<set>/<nn>.svg. Mirrors backend AvatarKeys validation.

export const AVATAR_SET_SIZE = 12

const ROLE_SET = {
  RESIDENT: 'resident',
  ADMIN: 'admin',
  SYSTEM_ADMIN: 'system',
}

const KEY_PATTERN = /^(resident|admin|system)-(0[1-9]|1[0-2])$/

export function avatarSetForRole(role) {
  return ROLE_SET[role] || null
}

export function avatarKeysForRole(role) {
  const set = avatarSetForRole(role)
  if (!set) return []
  return Array.from({ length: AVATAR_SET_SIZE }, (_, i) => `${set}-${String(i + 1).padStart(2, '0')}`)
}

export function avatarUrl(key) {
  const match = typeof key === 'string' ? key.match(KEY_PATTERN) : null
  if (!match) return null
  return `${import.meta.env.BASE_URL}avatars/${match[1]}/${match[2]}.svg`
}

export function initialsFor(name, fallback = '') {
  return (name || fallback)
    .split(' ')
    .filter(Boolean)
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()
}
