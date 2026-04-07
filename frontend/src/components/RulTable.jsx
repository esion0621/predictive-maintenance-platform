import React, { useState, useMemo } from 'react'
import { formatNumber } from '../utils/formatter'
import './RulTable.css'

const RulTable = ({ rulData, devicesLatest }) => {
  const [currentPage, setCurrentPage] = useState(1)
  const [sortBy, setSortBy] = useState('rul_asc') // rul_asc, rul_desc, device_asc
  const pageSize = 5

  // 合并数据
  const merged = rulData.map(rul => {
    const latest = devicesLatest.find(d => d.deviceId === rul.deviceId)
    return { ...rul, anomalyScore: latest?.anomalyScore || 0 }
  })

  // 排序
  const sortedData = useMemo(() => {
    const data = [...merged]
    if (sortBy === 'rul_asc') {
      data.sort((a, b) => a.predictedRul - b.predictedRul)
    } else if (sortBy === 'rul_desc') {
      data.sort((a, b) => b.predictedRul - a.predictedRul)
    } else if (sortBy === 'device_asc') {
      data.sort((a, b) => a.deviceId.localeCompare(b.deviceId))
    }
    return data
  }, [merged, sortBy])

  // 分页
  const totalPages = Math.ceil(sortedData.length / pageSize)
  const startIndex = (currentPage - 1) * pageSize
  const endIndex = startIndex + pageSize
  const currentData = sortedData.slice(startIndex, endIndex)

  const goToPage = (page) => {
    setCurrentPage(Math.max(1, Math.min(page, totalPages)))
  }

  const handleSortChange = (e) => {
    setSortBy(e.target.value)
    setCurrentPage(1) // 重置到第一页
  }

  if (merged.length === 0) {
    return <div className="no-rul-data">暂无剩余寿命数据</div>
  }

  return (
    <div className="rul-table-container">
      <div className="rul-toolbar">
        <div className="sort-control">
          <label>排序方式：</label>
          <select value={sortBy} onChange={handleSortChange} className="sort-select">
            <option value="rul_asc">剩余寿命 ↑ (升序)</option>
            <option value="rul_desc">剩余寿命 ↓ (降序)</option>
            <option value="device_asc">设备ID ↑ (A-Z)</option>
          </select>
        </div>
      </div>

      <table className="rul-table">
        <thead>
          <tr>
            <th>设备ID</th>
            <th>已运行天数</th>
            <th>预测剩余寿命(天)</th>
            <th>异常概率</th>
            <th>健康等级</th>
          </tr>
        </thead>
        <tbody>
          {currentData.map(item => (
            <tr key={item.deviceId}>
              <td className="left-align">{item.deviceId}</td>
              <td className="left-align">{item.daysInService}</td>
              <td className="left-align">{item.predictedRul}</td>
              <td className="left-align">{formatNumber(item.anomalyScore * 100)}%</td>
              <td className="left-align">
                <span className={`health-badge ${item.healthLevel === '健康' ? 'healthy' : item.healthLevel === '注意' ? 'caution' : 'danger'}`}>
                  {item.healthLevel}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

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

export default RulTable
