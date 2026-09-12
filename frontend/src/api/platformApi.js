import axiosClient from './axiosClient'

export function listCommunities() {
  return axiosClient.get('/platform/communities').then((res) => res.data)
}

export function getCommunityDetail(adminId) {
  return axiosClient.get(`/platform/communities/${adminId}`).then((res) => res.data)
}

export function updateCommunityStatus(adminId, status) {
  return axiosClient.put(`/platform/communities/${adminId}/status`, { status }).then((res) => res.data)
}

export function deleteCommunity(adminId) {
  return axiosClient.delete(`/platform/communities/${adminId}`).then((res) => res.data)
}

export function getSystemStatus() {
  return axiosClient.get('/platform/system-status').then((res) => res.data)
}

export function getSystemMetrics(range) {
  return axiosClient.get('/platform/system-metrics', { params: { range } }).then((res) => res.data)
}

export function getPlatformReport({ range, from, to } = {}) {
  const params = range ? { range } : { from, to }
  return axiosClient.get('/platform/reports', { params }).then((res) => res.data)
}
