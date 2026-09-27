import axiosClient from './axiosClient'

// Resident

export function listMySupportTickets() {
  return axiosClient.get('/resident/support-tickets').then((res) => res.data)
}

export function createMySupportTicket(request) {
  return axiosClient.post('/resident/support-tickets', request).then((res) => res.data)
}

export function getMySupportTicketDetail(id) {
  return axiosClient.get(`/resident/support-tickets/${id}`).then((res) => res.data)
}

export function addMySupportTicketMessage(id, message) {
  return axiosClient.post(`/resident/support-tickets/${id}/messages`, { message }).then((res) => res.data)
}

// Community Admin

export function listAdminSupportTickets() {
  return axiosClient.get('/admin/support-tickets').then((res) => res.data)
}

export function createAdminSupportTicket(request) {
  return axiosClient.post('/admin/support-tickets', request).then((res) => res.data)
}

export function getAdminSupportTicketDetail(id) {
  return axiosClient.get(`/admin/support-tickets/${id}`).then((res) => res.data)
}

export function addAdminSupportTicketMessage(id, message) {
  return axiosClient.post(`/admin/support-tickets/${id}/messages`, { message }).then((res) => res.data)
}

// Community Admin — "Resident Tickets" inbox (tickets residents addressed to this admin)

export function listAdminSupportTicketsInbox() {
  return axiosClient.get('/admin/support-tickets/inbox').then((res) => res.data)
}

export function getAdminSupportTicketInboxDetail(id) {
  return axiosClient.get(`/admin/support-tickets/inbox/${id}`).then((res) => res.data)
}

export function addAdminSupportTicketInboxMessage(id, message) {
  return axiosClient.post(`/admin/support-tickets/inbox/${id}/messages`, { message }).then((res) => res.data)
}

// Platform (System Admin)

export function listPlatformSupportTickets(status) {
  return axiosClient.get('/platform/support-tickets', { params: status ? { status } : {} }).then((res) => res.data)
}

export function getPlatformSupportTicketDetail(id) {
  return axiosClient.get(`/platform/support-tickets/${id}`).then((res) => res.data)
}

export function updatePlatformSupportTicketStatus(id, status) {
  return axiosClient.put(`/platform/support-tickets/${id}/status`, { status }).then((res) => res.data)
}

export function addPlatformSupportTicketMessage(id, message) {
  return axiosClient.post(`/platform/support-tickets/${id}/messages`, { message }).then((res) => res.data)
}
