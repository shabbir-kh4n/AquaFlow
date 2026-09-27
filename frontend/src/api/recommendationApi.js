import axiosClient from './axiosClient'
import i18next from '../i18n/config.js'

// Tips come back in the selected UI language; the backend regenerates them once when it changes.
export function getRecommendations() {
  const lang = i18next.resolvedLanguage || 'en'
  return axiosClient.get('/resident/recommendations', { params: { lang } }).then((res) => res.data)
}
