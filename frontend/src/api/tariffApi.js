import axiosClient from './axiosClient'

export function getTariffPlan() {
  return axiosClient.get('/admin/tariff-plan').then((res) => res.data)
}

export function saveTariffPlan(tiers) {
  return axiosClient.put('/admin/tariff-plan', { tiers }).then((res) => res.data)
}

export function getBillEstimate() {
  return axiosClient.get('/resident/bill-estimate').then((res) => res.data)
}
