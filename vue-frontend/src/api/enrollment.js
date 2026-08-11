import api from './index.js'

export const enrollmentApi = {
  getMyEnrollments() {
    return api.get('/api/enrollments/my')
  },
  enroll(orderRequest) {
    return api.post('/api/enrollments', orderRequest)
  },
  cancel(enrollmentId) {
    return api.delete(`/api/enrollments/${enrollmentId}`)
  }
}
