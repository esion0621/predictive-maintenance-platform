import React from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import Dashboard from './pages/Dashboard'
import Predictive from './pages/Predictive'
import Report from './pages/Report'
import './App.css'

function App() {
  return (
    <div className="app">
      <nav className="navbar">
        <div className="nav-brand">
          <span className="logo">⚙️</span>
          <span>工业设备预测维护平台</span>
        </div>
        <div className="nav-links">
          <Link to="/" className="nav-link">实时监控</Link>
          <Link to="/predictive" className="nav-link">预测维护</Link>
          <Link to="/report" className="nav-link">报表分析</Link>
        </div>
      </nav>
      <main className="main-content">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/predictive" element={<Predictive />} />
          <Route path="/report" element={<Report />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
