import axiosClient from './axiosClient'

export function openBillingCycle(periodMonth) {
  return axiosClient.post('/admin/billing-cycles', { periodMonth }).then((res) => res.data)
}

export function listBillingCycles() {
  return axiosClient.get('/admin/billing-cycles').then((res) => res.data)
}

export function getBillingCycleDetail(id) {
  return axiosClient.get(`/admin/billing-cycles/${id}`).then((res) => res.data)
}

export function finalizeBillingCycle(id) {
  return axiosClient.post(`/admin/billing-cycles/${id}/finalize`).then((res) => res.data)
}

export function archiveBillingCycle(id) {
  return axiosClient.post(`/admin/billing-cycles/${id}/archive`).then((res) => res.data)
}

export function setInvoicePaymentStatus(invoiceId, paid) {
  return axiosClient.put(`/admin/billing-cycles/invoices/${invoiceId}/payment-status`, { paid }).then((res) => res.data)
}

export function getMyInvoices() {
  return axiosClient.get('/resident/invoices').then((res) => res.data)
}

export function payInvoice(invoiceId) {
  return axiosClient.post(`/resident/invoices/${invoiceId}/pay`).then((res) => res.data)
}
