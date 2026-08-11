import api from './index.js'

export const enrollmentApi = {
  getMyEnrollments() {
    return api.get('/api/enrollments/my')
  },
  enroll(orderRequest) {
    return api.post('/api/enrollments', orderRequest)
  },
  evaluate(enrollmentId, performance) {
    return api.patch(`/api/enrollments/${enrollmentId}/performance`, performance)
  },
  updateQuality(enrollmentId, quality) {
    return api.put(`/api/enrollments/${enrollmentId}/quality`, quality)
  }
}
