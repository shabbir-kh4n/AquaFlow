import axiosClient from './axiosClient'

export function sendChatMessage(endpoint, message, history) {
  return axiosClient.post(endpoint, { message, history }).then((res) => res.data)
}
