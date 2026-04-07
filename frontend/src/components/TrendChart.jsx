import React, { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import './TrendChart.css'

const TrendChart = ({ devicesLatest }) => {
  const chartRef = useRef(null)
  let chartInstance = null

  useEffect(() => {
    if (!chartRef.current) return
    chartInstance = echarts.init(chartRef.current)
    const option = {
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: [] },
      yAxis: { type: 'value', name: '温度 (℃)' },
      series: [{ type: 'line', smooth: true, data: [], lineStyle: { color: '#3b82f6', width: 2 }, areaStyle: { opacity: 0.1 } }],
      grid: { top: 20, left: 50, right: 20, bottom: 20 }
    }
    chartInstance.setOption(option)
    return () => chartInstance?.dispose()
  }, [])

  useEffect(() => {
    if (!chartInstance || !devicesLatest.length) return
    // 模拟最近1小时平均温度（实际应从后端获取，这里使用设备平均温度）
    const now = new Date()
    const times = Array.from({ length: 12 }, (_, i) => {
      const d = new Date(now.getTime() - (11 - i) * 5 * 60000)
      return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    })
    const avgTemp = devicesLatest.reduce((sum, d) => sum + (d.temperature || 0), 0) / devicesLatest.length
    const data = Array(12).fill(avgTemp).map((v, i) => v + (Math.random() - 0.5) * 5) // 模拟波动
    chartInstance.setOption({
      xAxis: { data: times },
      series: [{ data }]
    })
  }, [devicesLatest])

  return <div ref={chartRef} className="trend-chart" />
}

export default TrendChart
