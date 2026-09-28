import axiosClient from './axiosClient'

// Logged-in account operations shared by all three roles (RESIDENT, ADMIN, SYSTEM_ADMIN).

export function changePassword({ currentPassword, newPassword }) {
  return axiosClient
    .post('/auth/change-password', { currentPassword, newPassword })
    .then((res) => res.data)
}

export function getResidentProfile() {
  return axiosClient.get('/resident/profile').then((res) => res.data)
}

export function updateResidentAvatar(avatarKey) {
  return axiosClient.put('/resident/profile/avatar', { avatarKey }).then((res) => res.data)
}

export function updateAdminAvatar(avatarKey) {
  return axiosClient.put('/admin/profile/avatar', { avatarKey }).then((res) => res.data)
}

export function getPlatformProfile() {
  return axiosClient.get('/platform/profile').then((res) => res.data)
}

export function updatePlatformAvatar(avatarKey) {
  return axiosClient.put('/platform/profile', { avatarKey }).then((res) => res.data)
}

// Picks the avatar-save call for a role, so AvatarPicker can be role-agnostic.
export function updateAvatarForRole(role, avatarKey) {
  if (role === 'RESIDENT') return updateResidentAvatar(avatarKey)
  if (role === 'ADMIN') return updateAdminAvatar(avatarKey)
  return updatePlatformAvatar(avatarKey)
}
