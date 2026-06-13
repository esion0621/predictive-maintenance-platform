import React, { useState } from 'react'
import ReactECharts from 'echarts-for-react'
import { getDeviceHistory } from '../api/client'
import './DeviceHistoryChart.css'

const DeviceHistoryChart = ({ devicesInfo }) => {
  const [deviceId, setDeviceId] = useState('')
  const [days, setDays] = useState(7)
  const [chartData, setChartData] = useState(null)
  const [loading, setLoading] = useState(false)

  const handleQuery = async () => {
    if (!deviceId) return
    setLoading(true)
    try {
      const endDate = new Date().toISOString().slice(0, 10)
      const startDate = new Date(Date.now() - days * 86400000).toISOString().slice(0, 10)
      const res = await getDeviceHistory(deviceId, startDate, endDate, 1, 100)
      const records = res.data?.records || []
      const dates = records.map(r => r.statDate)
      const temps = records.map(r => r.avgTemp)
      const vibs = records.map(r => r.maxVibration)
      setChartData({ dates, temps, vibs })
    } catch (error) {
      console.error('查询历史失败', error)
    } finally {
      setLoading(false)
    }
  }

  const option = chartData ? {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(10,14,39,0.9)', borderColor: 'rgba(0,212,255,0.2)', textStyle: { color: '#ccd6f6' } },
    legend: { data: ['平均温度(℃)', '最大振动(mm/s)'], textStyle: { color: '#8892b0' } },
    xAxis: { type: 'category', data: chartData.dates, axisLine: { lineStyle: { color: '#1a2555' } }, axisLabel: { color: '#5a6380' } },
    yAxis: [
      { type: 'value', name: '温度', nameTextStyle: { color: '#8892b0' }, axisLine: { lineStyle: { color: '#1a2555' } }, axisLabel: { color: '#5a6380' }, splitLine: { lineStyle: { color: 'rgba(26,37,85,0.3)' } } },
      { type: 'value', name: '振动', nameTextStyle: { color: '#8892b0' }, axisLine: { lineStyle: { color: '#1a2555' } }, axisLabel: { color: '#5a6380' }, splitLine: { show: false } }
    ],
    series: [
      { name: '平均温度(℃)', type: 'line', data: chartData.temps, smooth: true, lineStyle: { color: '#00d4ff' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(0,212,255,0.15)' }, { offset: 1, color: 'rgba(0,212,255,0)' }] } } },
      { name: '最大振动(mm/s)', type: 'line', data: chartData.vibs, smooth: true, lineStyle: { color: '#ff6b35' }, yAxisIndex: 1, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(255,107,53,0.15)' }, { offset: 1, color: 'rgba(255,107,53,0)' }] } } }
    ]
  } : null

  return (
    <div className="history-chart">
      <div className="history-controls">
        <select value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
          <option value="">选择设备</option>
          {devicesInfo.map(d => <option key={d.deviceId} value={d.deviceId}>{d.deviceId}</option>)}
        </select>
        <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={7}>最近7天</option>
          <option value={14}>最近14天</option>
          <option value={30}>最近30天</option>
        </select>
        <button onClick={handleQuery} disabled={!deviceId || loading}>查询</button>
      </div>
      <div className="chart-wrapper">
        {loading && <div className="loading">加载中...</div>}
        {!loading && chartData && <ReactECharts option={option} style={{ height: '350px' }} />}
        {!loading && !chartData && <div className="placeholder">请选择设备并点击查询</div>}
      </div>
    </div>
  )
}

export default DeviceHistoryChart
