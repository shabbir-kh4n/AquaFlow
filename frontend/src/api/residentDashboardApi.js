import axiosClient from './axiosClient'

export function getMyDashboard() {
  return axiosClient.get('/resident/dashboard').then((res) => res.data)
}

export function getMyUsageHistory() {
  return axiosClient.get('/resident/usage-history').then((res) => res.data)
}

export function getMyUsageComparison() {
  return axiosClient.get('/resident/usage-comparison').then((res) => res.data)
}

export function getMyPeerRanking() {
  return axiosClient.get('/resident/peer-ranking').then((res) => res.data)
}

// params: { range: '3mo' | '6mo' | '12mo' } or { from: 'YYYY-MM', to: 'YYYY-MM' }
export function getMyReport(params) {
  return axiosClient.get('/resident/reports', { params }).then((res) => res.data)
}
