import axiosClient from './axiosClient'

function reportParams({ range, from, to } = {}) {
  return range ? { range } : { from, to }
}

export function getAdminReport(params) {
  return axiosClient.get('/admin/reports', { params: reportParams(params) }).then((res) => res.data)
}

export function downloadAdminReportPdf(params) {
  return axiosClient
    .get('/admin/reports/pdf', { params: reportParams(params), responseType: 'blob' })
    .then((res) => res.data)
}
