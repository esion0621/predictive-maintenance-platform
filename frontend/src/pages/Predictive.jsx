import React, { useEffect, useState } from 'react'
import HealthPieChart from '../components/HealthPieChart'
import RulTable from '../components/RulTable'
import DeviceHistoryChart from '../components/DeviceHistoryChart'
import { getDevicesLatest, getDevicesInfo, getDeviceRul } from '../api/client'
import './Predictive.css'

const Predictive = () => {
  const [devicesLatest, setDevicesLatest] = useState([])
  const [devicesInfo, setDevicesInfo] = useState([])
  const [rulData, setRulData] = useState([])
  const [loading, setLoading] = useState(true)

  const fetchData = async () => {
    try {
      const [latestRes, infoRes] = await Promise.all([
        getDevicesLatest(),
        getDevicesInfo()
      ])
      setDevicesLatest(latestRes.data || [])
      setDevicesInfo(infoRes.data || [])
      
      // 获取所有设备的RUL（并发请求）
      const devices = infoRes.data || []
      const rulPromises = devices.map(device => getDeviceRul(device.deviceId).catch(() => ({ data: null })))
      const rulResults = await Promise.all(rulPromises)
      const rulList = rulResults.map(res => res.data).filter(d => d !== null)
      setRulData(rulList)
    } catch (error) {
      console.error('获取预测数据失败', error)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchData()
    const interval = setInterval(fetchData, 30000) // 30秒刷新一次
    return () => clearInterval(interval)
  }, [])

  // 健康等级统计
  const healthStats = {
    健康: rulData.filter(r => r.healthLevel === '健康').length,
    注意: rulData.filter(r => r.healthLevel === '注意').length,
    危险: rulData.filter(r => r.healthLevel === '危险').length,
  }

  return (
    <div className="predictive">
      <h1 className="page-title">预测维护</h1>
      <div className="predictive-grid">
        <div className="card pie-card">
          <h3>设备健康等级分布</h3>
          <HealthPieChart data={healthStats} />
        </div>
        <div className="card table-card">
          <h3>剩余寿命预测 (RUL)</h3>
          <RulTable rulData={rulData} devicesLatest={devicesLatest} />
        </div>
        <div className="card history-card full-width">
          <h3>设备历史趋势查询</h3>
          <DeviceHistoryChart devicesInfo={devicesInfo} />
        </div>
      </div>
    </div>
  )
}

export default Predictive
