import axiosClient from './axiosClient'

export function listResidents() {
  return axiosClient.get('/admin/residents').then((res) => res.data)
}

export function createResident(resident) {
  return axiosClient.post('/admin/residents', resident).then((res) => res.data)
}

export function updateResident(id, resident) {
  return axiosClient.put(`/admin/residents/${id}`, resident).then((res) => res.data)
}

export function deleteResident(id) {
  return axiosClient.delete(`/admin/residents/${id}`).then((res) => res.data)
}

export function bulkUploadResidents(file) {
  const formData = new FormData()
  formData.append('file', file)

  return axiosClient
    .post('/admin/residents/bulk-upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((res) => res.data)
}
