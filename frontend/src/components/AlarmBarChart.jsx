import React from 'react'
import ReactECharts from 'echarts-for-react'
import './AlarmBarChart.css'

const AlarmBarChart = ({ data }) => {
  const deviceNames = data.map(item => item.deviceId)
  const alarmCounts = data.map(item => item.alarmCount)

  const option = {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { type: 'category', data: deviceNames, axisLabel: { rotate: 45 } },
    yAxis: { type: 'value', name: '告警次数' },
    series: [{ type: 'bar', data: alarmCounts, itemStyle: { color: '#f59e0b', borderRadius: [4, 4, 0, 0] } }]
  }

  return <ReactECharts option={option} style={{ height: 400 }} />
}

export default AlarmBarChart
