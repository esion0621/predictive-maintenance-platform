import React from 'react'
import { formatTimestamp } from '../utils/formatter'
import './AlarmList.css'

const AlarmList = ({ alarms }) => {
  return (
    <div className="alarm-list">
      {alarms.length === 0 ? (
        <div className="alarm-empty">暂无告警</div>
      ) : (
        <ul>
          {alarms.map((alarm, idx) => {
            let alarmObj
            try {
              alarmObj = typeof alarm === 'string' ? JSON.parse(alarm) : alarm
            } catch {
              alarmObj = { deviceId: 'unknown', anomalyScore: 0 }
            }
            return (
              <li key={idx} className="alarm-item">
                <span className="alarm-device">{alarmObj.deviceId}</span>
                <span className="alarm-score">异常概率 {(alarmObj.anomalyScore * 100).toFixed(0)}%</span>
                <span className="alarm-time">{formatTimestamp(alarmObj.timestamp)}</span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

export default AlarmList
