import React, { useState } from 'react'
import { formatNumber, getAnomalyColor } from '../utils/formatter'
import './DeviceCardGrid.css'

const DeviceCardGrid = ({ devices, devicesInfo }) => {
  const [currentPage, setCurrentPage] = useState(1)
  const [searchId, setSearchId] = useState('')
  const pageSize = 12

  // 合并设备信息
  const merged = devices.map(device => {
    const info = devicesInfo.find(i => i.deviceId === device.deviceId)
    return {
      ...device,
      deviceType: info?.deviceType || '--',
      location: info?.location || '--'
    }
  })

  // 按设备ID筛选（不区分大小写）
  const filtered = searchId.trim() === ''
    ? merged
    : merged.filter(device => device.deviceId.toLowerCase().includes(searchId.toLowerCase()))

  // 分页计算
  const totalPages = Math.ceil(filtered.length / pageSize)
  const startIndex = (currentPage - 1) * pageSize
  const endIndex = startIndex + pageSize
  const currentDevices = filtered.slice(startIndex, endIndex)

  const goToPage = (page) => {
    setCurrentPage(Math.max(1, Math.min(page, totalPages)))
  }

  const handleSearchChange = (e) => {
    setSearchId(e.target.value)
    setCurrentPage(1) // 重置到第一页
  }

  if (merged.length === 0) {
    return <div className="no-devices">暂无设备数据</div>
  }

  return (
    <div className="device-card-container">
      <div className="device-search-bar">
        <input
          type="text"
          placeholder="🔍 按设备ID筛选..."
          value={searchId}
          onChange={handleSearchChange}
          className="device-search-input"
        />
      </div>

      <div className="device-card-grid">
        {currentDevices.map(device => (
          <div key={device.deviceId} className="device-card">
            <div className="device-card-header">
              <div className="device-id-area">
                <span className="device-icon">🖥️</span>
                <span className="device-id">{device.deviceId}</span>
              </div>
              <span className="device-type-badge">{device.deviceType}</span>
            </div>

            <div className="sensor-grid">
              <div className="sensor-item">
                <div className="sensor-icon">🌡️</div>
                <div className="sensor-info">
                  <div className="sensor-label">温度</div>
                  <div className="sensor-value">{formatNumber(device.temperature)} °C</div>
                </div>
              </div>
              <div className="sensor-item">
                <div className="sensor-icon">📳</div>
                <div className="sensor-info">
                  <div className="sensor-label">振动</div>
                  <div className="sensor-value">{formatNumber(device.vibration)} mm/s</div>
                </div>
              </div>
              <div className="sensor-item">
                <div className="sensor-icon">⚡</div>
                <div className="sensor-info">
                  <div className="sensor-label">电流</div>
                  <div className="sensor-value">{formatNumber(device.current)} A</div>
                </div>
              </div>
              <div className="sensor-item">
                <div className="sensor-icon">📈</div>
                <div className="sensor-info">
                  <div className="sensor-label">压力</div>
                  <div className="sensor-value">{formatNumber(device.pressure)} Pa</div>
                </div>
              </div>
            </div>

            <div className="device-card-footer">
              <div className="anomaly-container">
                <div className="anomaly-label">异常概率</div>
                <div className="anomaly-progress">
                  <div 
                    className="anomaly-progress-bar" 
                    style={{ width: `${device.anomalyScore * 100}%`, backgroundColor: getAnomalyColor(device.anomalyScore) }}
                  />
                </div>
                <div className="anomaly-percent">{formatNumber(device.anomalyScore * 100)}%</div>
              </div>
              <div className="location-info">
                <span className="location-icon">📍</span>
                <span className="location-text">{device.location}</span>
              </div>
            </div>
          </div>
        ))}
      </div>

      {totalPages > 1 && (
        <div className="pagination">
          <button onClick={() => goToPage(1)} disabled={currentPage === 1} className="page-btn">首页</button>
          <button onClick={() => goToPage(currentPage - 1)} disabled={currentPage === 1} className="page-btn">上一页</button>
          <span className="page-info">第 {currentPage} / {totalPages} 页</span>
          <button onClick={() => goToPage(currentPage + 1)} disabled={currentPage === totalPages} className="page-btn">下一页</button>
          <button onClick={() => goToPage(totalPages)} disabled={currentPage === totalPages} className="page-btn">末页</button>
        </div>
      )}
    </div>
  )
}

export default DeviceCardGrid
