import axiosClient from './axiosClient'

export function getCommonAreaConfig() {
  return axiosClient.get('/admin/common-area-config').then((res) => res.data)
}

export function saveCommonAreaConfig(distributionMethod, costItems) {
  return axiosClient.put('/admin/common-area-config', { distributionMethod, costItems }).then((res) => res.data)
}
