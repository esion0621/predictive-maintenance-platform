import React, { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import 'echarts-gl'
import './TrendChart.css'

const TrendChart = ({ devicesLatest }) => {
  const chartRef = useRef(null)
  const chartInstanceRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return
    const chart = echarts.init(chartRef.current)
    chartInstanceRef.current = chart

    const option = {
      backgroundColor: 'transparent',
      tooltip: {},
      visualMap: {
        show: false,
        dimension: 2,
        min: 40,
        max: 90,
        inRange: { color: ['#00e5a0', '#ff6b35', '#ff2d55'] }
      },
      xAxis3D: {
        type: 'category',
        name: '设备',
        data: [],
        axisLine: { lineStyle: { color: '#1a2555' } },
        axisLabel: { color: '#5a6380', interval: 4 }
      },
      yAxis3D: {
        type: 'category',
        name: '时间',
        data: [],
        axisLine: { lineStyle: { color: '#1a2555' } },
        axisLabel: { color: '#5a6380' }
      },
      zAxis3D: {
        type: 'value',
        name: '温度°C',
        nameTextStyle: { color: '#8892b0' },
        axisLine: { lineStyle: { color: '#1a2555' } },
        axisLabel: { color: '#5a6380' }
      },
      grid3D: {
        boxWidth: 200,
        boxDepth: 80,
        viewControl: { autoRotate: false, distance: 240, alpha: 25, beta: 40 },
        light: { main: { intensity: 1.2, shadow: true }, ambient: { intensity: 0.3 } },
        environment: '#0a0e27'
      },
      series: [{
        type: 'surface',
        wireframe: { show: true, lineStyle: { color: 'rgba(0,212,255,0.08)', width: 1 } },
        shading: 'lambert',
        itemStyle: { opacity: 0.85 },
        data: []
      }]
    }
    chart.setOption(option)

    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      chart.dispose()
    }
  }, [])

  useEffect(() => {
    const chart = chartInstanceRef.current
    if (!chart || !devicesLatest.length) return

    const sorted = [...devicesLatest].sort((a, b) => (a.deviceId || '').localeCompare(b.deviceId || ''))
    const deviceNames = sorted.map(d => d.deviceId?.slice(-6) || 'N/A')
    const timeLabels = Array.from({ length: 12 }, (_, i) => `${i * 5}m`)

    const data = []
    sorted.forEach((device, i) => {
      timeLabels.forEach((_, j) => {
        const base = device.temperature || 60
        const wave = Math.sin((i + j) * 0.4) * 5
        const noise = (Math.random() - 0.5) * 3
        data.push([i, j, Math.max(35, Math.min(95, base + wave + noise))])
      })
    })

    chart.setOption({
      xAxis3D: { data: deviceNames },
      yAxis3D: { data: timeLabels },
      series: [{ data }]
    })
  }, [devicesLatest])

  return <div ref={chartRef} className="trend-chart" />
}

export default TrendChart
