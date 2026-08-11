import api from './index.js'

export const paymentApi = {
  getById(id) {
    return api.get(`/api/payments/${id}`)
  },

  getByUser(userId) {
    return api.get(`/api/payments/user/${userId}`)
  }
}
