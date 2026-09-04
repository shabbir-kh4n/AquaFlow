import axiosClient from './axiosClient'

export function getMyDashboard() {
  return axiosClient.get('/resident/dashboard').then((res) => res.data)
}

export function getMyUsageHistory() {
  return axiosClient.get('/resident/usage-history').then((res) => res.data)
}
