import axiosClient from './axiosClient'

export function recordUsageReading({ meterNumber, readingDate, readingValue }) {
  return axiosClient
    .post('/admin/usage-readings', { meterNumber, readingDate, readingValue })
    .then((res) => res.data)
}

export function listUsageReadings({ meterNumber, from, to } = {}) {
  const params = {}
  if (meterNumber) params.meterNumber = meterNumber
  if (from) params.from = from
  if (to) params.to = to

  return axiosClient.get('/admin/usage-readings', { params }).then((res) => res.data)
}

export function bulkUploadUsageCsv(file) {
  const formData = new FormData()
  formData.append('file', file)

  return axiosClient
    .post('/admin/usage-readings/bulk-csv', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((res) => res.data)
}
