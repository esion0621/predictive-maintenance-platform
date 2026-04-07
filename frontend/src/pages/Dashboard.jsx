import React, { useEffect, useState } from 'react'
import KpiCard from '../components/KpiCard'
import DeviceCardGrid from '../components/DeviceCardGrid'   
import TrendChart from '../components/TrendChart'
import AlarmList from '../components/AlarmList'
import { getDevicesLatest, getDevicesInfo, getRecentAlarms } from '../api/client'
import './Dashboard.css'

const Dashboard = () => {
  const [devicesLatest, setDevicesLatest] = useState([])
  const [devicesInfo, setDevicesInfo] = useState([])
  const [alarms, setAlarms] = useState([])
  const [loading, setLoading] = useState(true)

  const fetchData = async () => {
    try {
      const [latestRes, infoRes, alarmsRes] = await Promise.all([
        getDevicesLatest(),
        getDevicesInfo(),
        getRecentAlarms(20)
      ])
      setDevicesLatest(latestRes.data || [])
      setDevicesInfo(infoRes.data || [])
      setAlarms(alarmsRes.data || [])
    } catch (error) {
      console.error('获取数据失败', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
    const interval = setInterval(fetchData, 5000)
    return () => clearInterval(interval)
  }, [])

  const totalDevices = devicesInfo.length
  const abnormalDevices = devicesLatest.filter(d => d.anomalyScore > 0.8).length
  const todayAlarms = alarms.length

  return (
    <div className="dashboard">
      <h1 className="page-title">实时监控</h1>
      <div className="kpi-row">
        <KpiCard title="总设备数" value={totalDevices} icon="📊" color="#3b82f6" />
        <KpiCard title="异常设备数" value={abnormalDevices} icon="⚠️" color="#ef4444" />
        <KpiCard title="今日告警数" value={todayAlarms} icon="🔔" color="#f59e0b" />
      </div>
      <div className="dashboard-grid">
        <div className="card device-grid-card">
          <h3>设备状态卡片</h3>
          <DeviceCardGrid devices={devicesLatest} devicesInfo={devicesInfo} />
        </div>
        <div className="card trend-chart-card">
          <h3>最近1小时平均温度趋势</h3>
          <TrendChart devicesLatest={devicesLatest} />
        </div>
        <div className="card alarm-list-card">
          <h3>最新告警</h3>
          <AlarmList alarms={alarms} />
        </div>
      </div>
    </div>
  )
}

export default Dashboard
