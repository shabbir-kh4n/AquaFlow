import axiosClient from './axiosClient'

export function getVapidPublicKey() {
  return axiosClient.get('/resident/push/vapid-public-key').then((res) => res.data)
}

export function subscribe(subscriptionJson) {
  return axiosClient.post('/resident/push/subscribe', subscriptionJson)
}

export function unsubscribe(endpoint) {
  return axiosClient.post('/resident/push/unsubscribe', { endpoint })
}
