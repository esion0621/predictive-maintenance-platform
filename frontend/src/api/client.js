import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// 响应拦截器统一处理错误
api.interceptors.response.use(
  response => response.data,
  error => {
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

// 获取所有设备最新状态
export const getDevicesLatest = () => api.get('/devices/latest')

// 获取设备基础信息
export const getDevicesInfo = () => api.get('/devices/info')

// 获取最近告警列表
export const getRecentAlarms = (limit = 20) => api.get('/devices/alarms', { params: { limit } })

// 获取设备历史数据（预聚合）
export const getDeviceHistory = (deviceId, startDate, endDate, pageNum = 1, pageSize = 30) =>
  api.get(`/devices/${deviceId}/history`, { params: { startDate, endDate, pageNum, pageSize } })

// 获取设备剩余寿命
export const getDeviceRul = (deviceId) => api.get(`/devices/${deviceId}/rul`)

// 获取告警统计（按设备）
export const getAlarmStats = (days = 7) => api.get('/devices/reports/alarm-stats', { params: { days } })

// 获取当前模型版本
export const getCurrentModelVersion = (modelType = 'anomaly') => api.get('/devices/models/version', { params: { modelType } })
// 获取模型最新指标
export const getLatestModelMetrics = (modelId) => api.get(`/devices/models/${modelId}/metrics/latest`)
export default api
