import React, { useRef } from 'react'
import './KpiCard.css'

const KpiCard = ({ title, value, icon, color }) => {
  const cardRef = useRef(null)

  const handleMouseMove = (e) => {
    const card = cardRef.current
    if (!card) return
    const rect = card.getBoundingClientRect()
    const x = e.clientX - rect.left
    const y = e.clientY - rect.top
    const cx = rect.width / 2
    const cy = rect.height / 2
    const rotateX = ((y - cy) / cy) * -5
    const rotateY = ((x - cx) / cx) * 5
    card.style.transform = `perspective(600px) rotateX(${rotateX}deg) rotateY(${rotateY}deg) translateY(-2px)`
  }

  const handleMouseLeave = () => {
    const card = cardRef.current
    if (!card) return
    card.style.transform = 'perspective(600px) rotateX(0deg) rotateY(0deg) translateY(0)'
  }

  return (
    <div
      className="kpi-card"
      ref={cardRef}
      style={{ borderLeftColor: color, transition: 'transform 0.15s ease' }}
      onMouseMove={handleMouseMove}
      onMouseLeave={handleMouseLeave}
    >
      <div className="kpi-icon" style={{ backgroundColor: color + '15', color }}>
        {icon}
      </div>
      <div className="kpi-content">
        <div className="kpi-title">{title}</div>
        <div className="kpi-value" style={{ color }}>{value}</div>
      </div>
    </div>
  )
}

export default KpiCard
