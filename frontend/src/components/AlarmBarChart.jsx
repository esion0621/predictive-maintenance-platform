import React, { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import './AlarmBarChart.css'

const AlarmBarChart = ({ data }) => {
  const chartRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return
    const chart = echarts.init(chartRef.current)

    const deviceNames = data.map(item => item.deviceId?.slice(-6) || 'N/A')
    const alarmCounts = data.map(item => item.alarmCount || 0)

    const option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(10,14,39,0.95)',
        borderColor: 'rgba(255,107,53,0.4)',
        textStyle: { color: '#ccd6f6' },
        axisPointer: { type: 'shadow', shadowStyle: { color: 'rgba(255,107,53,0.1)' } },
        formatter: p => {
          const d = p[0]
          return `${d.name}<br/>告警次数: <b style="color:#ff6b35">${d.value}</b>`
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        top: '10%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: deviceNames,
        axisLine: { lineStyle: { color: '#3a4a88' } },
        axisLabel: { color: '#aabbcc', interval: 0, rotate: 30, fontSize: 10 },
        axisTick: { alignWithLabel: true, lineStyle: { color: '#3a4a88' } }
      },
      yAxis: {
        type: 'value',
        name: '次数',
        nameTextStyle: { color: '#8892b0' },
        axisLine: { lineStyle: { color: '#3a4a88' } },
        axisLabel: { color: '#aabbcc' },
        splitLine: { lineStyle: { color: 'rgba(58,74,136,0.25)' } }
      },
      series: [{
        type: 'bar',
        barWidth: '50%',
        data: alarmCounts,
        itemStyle: {
          borderRadius: [4, 4, 0, 0],
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: '#ffaa55' },
              { offset: 1, color: '#ff2d55' }
            ]
          }
        },
        label: {
          show: true,
          position: 'top',
          color: '#ff6b35',
          fontSize: 11,
          fontWeight: 'bold'
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 15,
            shadowColor: 'rgba(255,107,53,0.4)'
          }
        }
      }]
    }

    chart.setOption(option)
    const handleResize = () => chart.resize()
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      chart.dispose()
    }
  }, [data])

  return <div ref={chartRef} className="alarm-bar-chart" />
}

export default AlarmBarChart

