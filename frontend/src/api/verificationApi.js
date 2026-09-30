import axiosClient from './axiosClient'

// ---- Community admin ----

export function getMyVerification() {
  return axiosClient.get('/admin/verification').then((res) => res.data)
}

export function submitVerification({ file, ...fields }) {
  const form = new FormData()
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== '' && value != null) form.append(key, value)
  })
  form.append('file', file)
  // Let the browser set the multipart boundary instead of the client's JSON default.
  return axiosClient
    .post('/admin/verification', form, { headers: { 'Content-Type': undefined } })
    .then((res) => res.data)
}

// ---- System admin ----

export function getCommunityVerification(adminId) {
  return axiosClient.get(`/platform/communities/${adminId}/verification`).then((res) => res.data)
}

export function approveVerification(adminId) {
  return axiosClient.put(`/platform/communities/${adminId}/verification/approve`).then((res) => res.data)
}

export function rejectVerification(adminId, reason) {
  return axiosClient
    .put(`/platform/communities/${adminId}/verification/reject`, { reason })
    .then((res) => res.data)
}

// The document endpoints need the Authorization header, so fetch as a blob and open it in a new tab
// instead of linking straight to the URL.
async function openPdf(url) {
  const res = await axiosClient.get(url, { responseType: 'blob' })
  const blobUrl = URL.createObjectURL(new Blob([res.data], { type: 'application/pdf' }))
  window.open(blobUrl, '_blank', 'noopener')
  setTimeout(() => URL.revokeObjectURL(blobUrl), 60000)
}

export function openMyVerificationDocument() {
  return openPdf('/admin/verification/document')
}

export function openCommunityVerificationDocument(adminId) {
  return openPdf(`/platform/communities/${adminId}/verification/document`)
}
