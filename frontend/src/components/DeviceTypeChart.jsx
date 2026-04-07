import React, { useEffect, useState } from 'react'
import ReactECharts from 'echarts-for-react'
import { getDevicesLatest, getDevicesInfo } from '../api/client'
import './DeviceTypeChart.css'

const DeviceTypeChart = () => {
  const [chartData, setChartData] = useState([])

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [latestRes, infoRes] = await Promise.all([getDevicesLatest(), getDevicesInfo()])
        const devices = latestRes.data || []
        const infos = infoRes.data || []
        const typeMap = {}
        devices.forEach(device => {
          const info = infos.find(i => i.deviceId === device.deviceId)
          const type = info?.deviceType || '未知'
          if (!typeMap[type]) typeMap[type] = { total: 0, abnormal: 0 }
          typeMap[type].total++
          if (device.anomalyScore > 0.8) typeMap[type].abnormal++
        })
        const types = Object.keys(typeMap)
        const abnormalRates = types.map(t => (typeMap[t].abnormal / typeMap[t].total) * 100)
        setChartData({ types, abnormalRates })
      } catch (error) {
        console.error('获取设备类型统计失败', error)
      }
    }
    fetchData()
  }, [])

  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}%' },
    xAxis: { type: 'category', data: chartData.types || [] },
    yAxis: { type: 'value', name: '异常率 (%)', max: 100 },
    series: [{ type: 'bar', data: chartData.abnormalRates || [], itemStyle: { color: '#ef4444', borderRadius: [4, 4, 0, 0] } }]
  }

  return <ReactECharts option={option} style={{ height: 400 }} />
}

export default DeviceTypeChart
