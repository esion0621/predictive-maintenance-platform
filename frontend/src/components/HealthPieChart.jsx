import React, { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import './HealthPieChart.css'

const HealthPieChart = ({ data }) => {
  const chartRef = useRef(null)

  useEffect(() => {
    if (!chartRef.current) return
    const chart = echarts.init(chartRef.current)
    const option = {
      tooltip: { trigger: 'item' },
      legend: { orient: 'vertical', left: 'left' },
      series: [
        {
          name: '健康等级',
          type: 'pie',
          radius: '50%',
          data: [
            { value: data.健康 || 0, name: '健康', itemStyle: { color: '#10b981' } },
            { value: data.注意 || 0, name: '注意', itemStyle: { color: '#f59e0b' } },
            { value: data.危险 || 0, name: '危险', itemStyle: { color: '#ef4444' } }
          ],
          emphasis: { scale: true },
          label: { show: true, formatter: '{b}: {d}%' }
        }
      ]
    }
    chart.setOption(option)
    return () => chart.dispose()
  }, [data])

  return <div ref={chartRef} className="health-pie-chart" />
}

export default HealthPieChart
