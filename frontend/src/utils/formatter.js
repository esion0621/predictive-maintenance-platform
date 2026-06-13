// 格式化时间戳
export const formatTimestamp = (timestamp) => {
  if (!timestamp) return '--'
  const date = new Date(timestamp)
  return date.toLocaleString('zh-CN')
}

// 格式化日期
export const formatDate = (dateStr) => {
  if (!dateStr) return '--'
  return dateStr
}

// 数值保留两位小数
export const formatNumber = (num, digits = 2) => {
  if (num === undefined || num === null) return '--'
  return Number(num).toFixed(digits)
}

// 异常分数颜色
export const getAnomalyColor = (score) => {
  if (score >= 0.8) return '#ff2d55'
  if (score >= 0.5) return '#ff6b35'
  return '#00e5a0'
}

// 健康等级
export const getHealthLevel = (score, rul) => {
  if (score > 0.8 || rul < 30) return '危险'
  if (score > 0.5 || rul < 90) return '注意'
  return '健康'
}
