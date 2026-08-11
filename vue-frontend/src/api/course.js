import api from './index.js'

export const courseApi = {
  getCourses(params) {
    return api.get('/api/courses', { params })
  },

  getAll(params) {
    return api.get('/api/courses', { params })
  },

  getById(id) {
    return api.get(`/api/courses/${id}`)
  },

  getByCategory(category) {
    return api.get(`/api/courses/category/${category}`)
  },

  getMine() {
    return api.get('/api/courses/my')
  },

  create(data) {
    return api.post('/api/courses', data)
  },

  update(id, data) {
    return api.put(`/api/courses/${id}`, data)
  },

  updateStatus(id, status) {
    return api.put(`/api/courses/${id}/status`, { status })
  }
}
