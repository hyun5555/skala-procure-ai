import api from './index.js'

export const recommendApi = {
  getForUser(userId) {
    return api.get(`/api/recommend/${userId}`)
  }
}
