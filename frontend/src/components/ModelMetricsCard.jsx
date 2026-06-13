import React, { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { formatNumber } from '../utils/formatter'
import './ModelMetricsCard.css'

const ModelMetricsCard = ({ modelVersion, modelMetrics }) => {
  const rmseRef = useRef(null)
  const accRef = useRef(null)
  const f1Ref = useRef(null)

  const gaugeBase = {
    backgroundColor: 'transparent',
    series: [{
      type: 'gauge',
      startAngle: 210,
      endAngle: -30,
      radius: '85%',
      center: ['50%', '55%'],
      axisLine: {
        lineStyle: {
          width: 12,
          color: [[0.3, '#ff2d55'], [0.7, '#ff6b35'], [1, '#00e5a0']]
        }
      },
      pointer: { width: 4, length: '60%', itemStyle: { color: '#00d4ff' } },
      axisTick: { show: false },
      splitLine: { length: 10, lineStyle: { width: 2, color: '#1a2555' } },
      axisLabel: { distance: 15, color: '#5a6380', fontSize: 10 },
      detail: { offsetCenter: [0, '70%'], fontSize: 18, fontWeight: 700, color: '#00d4ff', formatter: '{value}' },
      title: { show: false }
    }]
  }

  useEffect(() => {
    if (!rmseRef.current || !accRef.current || !f1Ref.current) return

    const rmseChart = echarts.init(rmseRef.current)
    const accChart = echarts.init(accRef.current)
    const f1Chart = echarts.init(f1Ref.current)

    rmseChart.setOption({
      ...gaugeBase,
      series: [{ ...gaugeBase.series[0], min: 0, max: 1, detail: { ...gaugeBase.series[0].detail, formatter: '{value}' } }]
    })
    accChart.setOption({
      ...gaugeBase,
      series: [{ ...gaugeBase.series[0], min: 0, max: 100, detail: { ...gaugeBase.series[0].detail, formatter: '{value}%' } }]
    })
    f1Chart.setOption({
      ...gaugeBase,
      series: [{ ...gaugeBase.series[0], min: 0, max: 1, detail: { ...gaugeBase.series[0].detail, formatter: '{value}' } }]
    })

    if (modelMetrics) {
      rmseChart.setOption({ series: [{ data: [{ value: formatNumber(modelMetrics.rmse) }] }] })
      accChart.setOption({ series: [{ data: [{ value: formatNumber(modelMetrics.accuracy * 100) }] }] })
      f1Chart.setOption({ series: [{ data: [{ value: formatNumber(modelMetrics.f1Score) }] }] })
    } else {
      rmseChart.setOption({ series: [{ data: [{ value: 0.15 }] }] })
      accChart.setOption({ series: [{ data: [{ value: 92.5 }] }] })
      f1Chart.setOption({ series: [{ data: [{ value: 0.89 }] }] })
    }

    const handleResize = () => {
      rmseChart.resize()
      accChart.resize()
      f1Chart.resize()
    }
    window.addEventListener('resize', handleResize)
    return () => {
      window.removeEventListener('resize', handleResize)
      rmseChart.dispose()
      accChart.dispose()
      f1Chart.dispose()
    }
  }, [modelMetrics])

  return (
    <div className="metrics-content">
      {modelVersion ? (
        <div className="metrics-version">
          <span className="metrics-label">当前模型版本:</span>
          <span className="metrics-value">{modelVersion.version}</span>
        </div>
      ) : <div className="metrics-version">暂无模型版本信息</div>}
      <div className="metrics-gauges">
        <div className="gauge-item">
          <div ref={rmseRef} className="gauge-chart" />
          <div className="gauge-title">RMSE</div>
        </div>
        <div className="gauge-item">
          <div ref={accRef} className="gauge-chart" />
          <div className="gauge-title">准确率</div>
        </div>
        <div className="gauge-item">
          <div ref={f1Ref} className="gauge-chart" />
          <div className="gauge-title">F1 分数</div>
        </div>
      </div>
      {modelMetrics && (
        <div className="metrics-time">
          创建时间: {modelMetrics.createdAt?.replace('T', ' ')}
        </div>
      )}
    </div>
  )
}

export default ModelMetricsCard
