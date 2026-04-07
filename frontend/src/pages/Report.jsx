import React, { useEffect, useState } from 'react'
import AlarmBarChart from '../components/AlarmBarChart'
import ModelMetricsCard from '../components/ModelMetricsCard'
import DeviceTypeChart from '../components/DeviceTypeChart'
import { getAlarmStats, getCurrentModelVersion, getLatestModelMetrics } from '../api/client'
import './Report.css'

const Report = () => {
  const [alarmStats, setAlarmStats] = useState([])
  const [modelVersion, setModelVersion] = useState(null)
  const [modelMetrics, setModelMetrics] = useState(null)
  const [loading, setLoading] = useState(true)

  const fetchData = async () => {
    try {
      const [statsRes, versionRes, metricsRes] = await Promise.all([
        getAlarmStats(7),
        getCurrentModelVersion('anomaly'),
        getLatestModelMetrics(1).catch(() => ({ data: null }))
      ])
      setAlarmStats(statsRes.data || [])
      setModelVersion(versionRes.data)
      setModelMetrics(metricsRes.data)
    } catch (error) {
      console.error('获取报表数据失败', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
  }, [])

  return (
    <div className="report">
      <h1 className="page-title">报表分析</h1>
      <div className="report-grid">
        <div className="card bar-chart-card">
          <h3>近7天设备告警数量排行</h3>
          <AlarmBarChart data={alarmStats} />
        </div>
        <div className="card metrics-card">
          <h3>模型效果指标</h3>
          <ModelMetricsCard modelVersion={modelVersion} modelMetrics={modelMetrics} />
        </div>
        <div className="card type-card full-width">
          <h3>设备类型与异常率关联分析</h3>
          <DeviceTypeChart />
        </div>
      </div>
    </div>
  )
}

export default Report
