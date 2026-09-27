import axiosClient from './axiosClient'
import i18next from '../i18n/config.js'

// The selected UI language rides along so the assistant replies in it.
export function sendChatMessage(endpoint, message, history) {
  const language = i18next.resolvedLanguage || 'en'
  return axiosClient.post(endpoint, { message, history, language }).then((res) => res.data)
}
