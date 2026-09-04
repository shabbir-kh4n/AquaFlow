import axiosClient from './axiosClient'

export function listBulkPurchases() {
  return axiosClient.get('/admin/bulk-water-purchases').then((res) => res.data)
}

export function createBulkPurchase(purchase) {
  return axiosClient.post('/admin/bulk-water-purchases', purchase).then((res) => res.data)
}

export function updateBulkPurchase(id, purchase) {
  return axiosClient.put(`/admin/bulk-water-purchases/${id}`, purchase).then((res) => res.data)
}

export function deleteBulkPurchase(id) {
  return axiosClient.delete(`/admin/bulk-water-purchases/${id}`).then((res) => res.data)
}
