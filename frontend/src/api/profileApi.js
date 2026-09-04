import axiosClient from './axiosClient'

export function getAdminProfile() {
  return axiosClient.get('/admin/profile').then((res) => res.data)
}

export function updateAdminProfile(profile) {
  return axiosClient.put('/admin/profile', profile).then((res) => res.data)
}

export function getApartment() {
  return axiosClient.get('/admin/apartment').then((res) => res.data)
}

export function updateApartment(apartment) {
  return axiosClient.put('/admin/apartment', apartment).then((res) => res.data)
}
