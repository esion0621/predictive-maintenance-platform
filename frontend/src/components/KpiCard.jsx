import React from 'react'
import './KpiCard.css'

const KpiCard = ({ title, value, icon, color }) => {
  return (
    <div className="kpi-card" style={{ borderLeftColor: color }}>
      <div className="kpi-icon" style={{ backgroundColor: color + '10', color }}>
        {icon}
      </div>
      <div className="kpi-content">
        <div className="kpi-title">{title}</div>
        <div className="kpi-value">{value}</div>
      </div>
    </div>
  )
}

export default KpiCard
