-- 创建数据库
CREATE DATABASE IF NOT EXISTS predictive_maintenance;
USE predictive_maintenance;

-- 设备维度表
CREATE TABLE IF NOT EXISTS device_info (
    device_id VARCHAR(20) PRIMARY KEY,
    device_type VARCHAR(50) NOT NULL,
    install_date DATE NOT NULL,
    location VARCHAR(100)
);

-- 告警事件表
CREATE TABLE IF NOT EXISTS alarm_event (
    alarm_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(20) NOT NULL,
    alarm_time DATETIME NOT NULL,
    anomaly_score DECIMAL(5,4) NOT NULL,
    feature_values JSON,
    is_handled TINYINT DEFAULT 0,
    INDEX idx_device_time (device_id, alarm_time)
);

-- 模型版本表
CREATE TABLE IF NOT EXISTS model_version (
    model_id INT AUTO_INCREMENT PRIMARY KEY,
    model_type VARCHAR(20) NOT NULL, -- 'anomaly' or 'rul'
    version VARCHAR(50) NOT NULL,
    hdfs_path VARCHAR(255) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_active TINYINT DEFAULT 0,
    UNIQUE KEY uniq_version_type (version, model_type)
);

-- 模型指标表
CREATE TABLE IF NOT EXISTS model_metrics (
    metric_id INT AUTO_INCREMENT PRIMARY KEY,
    model_id INT NOT NULL,
    rmse DECIMAL(10,6),
    accuracy DECIMAL(5,4),
    f1_score DECIMAL(5,4),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (model_id) REFERENCES model_version(model_id)
);

-- 设备每日统计表
CREATE TABLE IF NOT EXISTS device_daily_stats (
    stat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id VARCHAR(20) NOT NULL,
    stat_date DATE NOT NULL,
    avg_temp DECIMAL(6,2),
    max_vibration DECIMAL(6,2),
    avg_current DECIMAL(6,2),
    avg_pressure DECIMAL(8,2),
    UNIQUE KEY uniq_device_date (device_id, stat_date)
);
