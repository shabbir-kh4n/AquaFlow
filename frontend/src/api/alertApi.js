import axiosClient from './axiosClient'

export function getAlertConfig() {
  return axiosClient.get('/admin/alert-config').then((res) => res.data)
}

export function saveAlertConfig(config) {
  return axiosClient.put('/admin/alert-config', config).then((res) => res.data)
}

export function listAlerts(status) {
  return axiosClient.get('/admin/alerts', { params: status ? { status } : {} }).then((res) => res.data)
}

export function updateAlertStatus(id, status) {
  return axiosClient.put(`/admin/alerts/${id}/status`, { status }).then((res) => res.data)
}

export function runAlertDetectionNow() {
  return axiosClient.post('/admin/alerts/run-now').then((res) => res.data)
}

export function getMyAlerts() {
  return axiosClient.get('/resident/alerts').then((res) => res.data)
}
