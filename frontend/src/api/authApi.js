import axiosClient from './axiosClient'

export function registerUser({ fullName, email, password, role, phone, communityName }) {
  return axiosClient
    .post('/auth/register', { fullName, email, password, role, phone, communityName })
    .then((res) => res.data)
}

export function loginUser({ email, password }) {
  return axiosClient
    .post('/auth/login', { email, password })
    .then((res) => res.data)
}

export function getSetPasswordInfo(token) {
  return axiosClient.get(`/auth/set-password/${token}`).then((res) => res.data)
}

export function setPassword(token, password) {
  return axiosClient.post('/auth/set-password', { token, password }).then((res) => res.data)
}

export function forgotPassword(email) {
  return axiosClient.post('/auth/forgot-password', { email }).then((res) => res.data)
}

export function resetPassword(token, password) {
  return axiosClient.post('/auth/reset-password', { token, password }).then((res) => res.data)
}
