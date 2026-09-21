import axiosClient from './axiosClient'

export function getRecommendations() {
  return axiosClient.get('/resident/recommendations').then((res) => res.data)
}
