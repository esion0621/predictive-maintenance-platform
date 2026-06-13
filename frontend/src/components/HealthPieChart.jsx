import React, { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import './HealthPieChart.css'

const HealthPieChart = ({ data }) => {
  const chartRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return
    const chart = echarts.init(chartRef.current)

    const pieData = [
      { value: data.健康 || 0, name: '健康', itemStyle: { color: '#00e5a0' } },
      { value: data.注意 || 0, name: '注意', itemStyle: { color: '#ff6b35' } },
      { value: data.危险 || 0, name: '危险', itemStyle: { color: '#ff2d55' } }
    ].filter(d => d.value > 0)

    const option = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(10,14,39,0.95)',
        borderColor: 'rgba(0,212,255,0.3)',
        textStyle: { color: '#ccd6f6' },
        formatter: p => `${p.name}<br/>数量: <b style="color:${p.color}">${p.value}</b><br/>占比: <b>${p.percent}%</b>`
      },
      legend: {
        orient: 'vertical',
        left: 'left',
        textStyle: { color: '#aabbcc' },
        itemWidth: 12,
        itemHeight: 12
      },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['60%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#0a0e27',
          borderWidth: 2
        },
        label: {
          show: true,
          color: '#ccd6f6',
          formatter: '{b}\n{d}%'
        },
        labelLine: {
          lineStyle: { color: '#3a4a88' }
        },
        emphasis: {
          label: { show: true, fontSize: 14, fontWeight: 'bold' },
          itemStyle: {
            shadowBlur: 20,
            shadowColor: 'rgba(255,255,255,0.2)'
          }
        },
        data: pieData
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

  return <div ref={chartRef} className="health-pie-chart" />
}

export default HealthPieChart

