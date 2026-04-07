import React from 'react'
import { formatNumber } from '../utils/formatter'
import './ModelMetricsCard.css'

const ModelMetricsCard = ({ modelVersion, modelMetrics }) => {
  return (
    <div className="metrics-content">
      {modelVersion ? (
        <div className="metrics-item">
          <span className="metrics-label">当前模型版本:</span>
          <span className="metrics-value">{modelVersion.version}</span>
        </div>
      ) : <div className="metrics-item">暂无模型版本信息</div>}
      {modelMetrics ? (
        <>
          <div className="metrics-item">
            <span className="metrics-label">RMSE:</span>
            <span className="metrics-value">{formatNumber(modelMetrics.rmse)}</span>
          </div>
          <div className="metrics-item">
            <span className="metrics-label">准确率:</span>
            <span className="metrics-value">{formatNumber(modelMetrics.accuracy * 100)}%</span>
          </div>
          <div className="metrics-item">
            <span className="metrics-label">F1分数:</span>
            <span className="metrics-value">{formatNumber(modelMetrics.f1Score)}</span>
          </div>
          <div className="metrics-item">
            <span className="metrics-label">创建时间:</span>
            <span className="metrics-value">{modelMetrics.createdAt?.replace('T', ' ')}</span>
          </div>
        </>
      ) : (
        <div className="metrics-item">暂无模型指标数据</div>
      )}
    </div>
  )
}

export default ModelMetricsCard
