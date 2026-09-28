import axiosClient from './axiosClient'

export function listAnnouncements() {
  return axiosClient.get('/admin/announcements').then((res) => res.data)
}

export function createAnnouncement(announcement) {
  return axiosClient.post('/admin/announcements', announcement).then((res) => res.data)
}

export function updateAnnouncement(id, announcement) {
  return axiosClient.put(`/admin/announcements/${id}`, announcement).then((res) => res.data)
}

export function deleteAnnouncement(id) {
  return axiosClient.delete(`/admin/announcements/${id}`).then((res) => res.data)
}

export function getMyAnnouncements() {
  return axiosClient.get('/resident/announcements').then((res) => res.data)
}
