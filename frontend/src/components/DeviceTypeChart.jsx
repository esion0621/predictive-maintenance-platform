import React, { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { getDevicesLatest, getDevicesInfo } from '../api/client'
import './DeviceTypeChart.css'

const DeviceTypeChart = () => {
  const chartRef = useRef(null)
  const chartInstanceRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return
    const chart = echarts.init(chartRef.current)
    chartInstanceRef.current = chart

    const option = {
      backgroundColor: 'transparent',
      tooltip: {
        backgroundColor: 'rgba(10,14,39,0.95)',
        borderColor: 'rgba(0,212,255,0.4)',
        textStyle: { color: '#ccd6f6' }
      },
      grid: {
        left: '3%',
        right: '8%',
        bottom: '3%',
        top: '10%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLine: { lineStyle: { color: '#3a4a88' } },
        axisLabel: { color: '#aabbcc', fontSize: 11 },
        axisTick: { lineStyle: { color: '#3a4a88' } },
        splitLine: { show: false }
      },
      yAxis: {
        type: 'value',
        name: '异常率(%)',
        nameTextStyle: { color: '#8892b0' },
        max: 100,
        axisLine: { lineStyle: { color: '#3a4a88' } },
        axisLabel: { color: '#aabbcc', formatter: '{value}%' },
        splitLine: { lineStyle: { color: 'rgba(58,74,136,0.25)' } }
      },
      series: [{
        type: 'scatter',
        data: [],
        symbolSize: d => Math.max(30, d[2] * 4),
        itemStyle: {
          color: 'rgba(0,212,255,0.85)',
          borderWidth: 2,
          borderColor: 'rgba(255,255,255,0.8)',
          shadowBlur: 15,
          shadowColor: 'rgba(0,212,255,0.3)'
        },
        emphasis: {
          itemStyle: {
            color: '#ff6b35',
            borderColor: '#fff',
            borderWidth: 3,
            shadowBlur: 20,
            shadowColor: 'rgba(255,107,53,0.5)'
          }
        }
      }]
    }

    chart.setOption(option)

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
        const scatterData = types.map((t, i) => ({
          value: [t, (typeMap[t].abnormal / typeMap[t].total) * 100, typeMap[t].total],
          itemStyle: {
            color: `rgba(0,212,255,0.85)`
          }
        }))

        chart.setOption({
          tooltip: {
            formatter: p => {
              return `<b style="color:#00d4ff">${p.value[0]}</b><br/>异常率: <b>${p.value[1].toFixed(1)}%</b><br/>设备数: <b>${p.value[2]}</b>`
            }
          },
          xAxis: { data: types },
          series: [{ data: scatterData }]
        })
      } catch (error) {
        console.error('获取设备类型统计失败', error)
      }
    }

    fetchData()

    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      chart.dispose()
    }
  }, [])

  return <div ref={chartRef} className="device-type-chart" />
}

export default DeviceTypeChart

