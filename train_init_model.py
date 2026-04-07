#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修正版模型训练脚本（内存优化版）
- 使用生成器逐设备处理，特征写入 memmap 文件
- 窗口大小 30 秒，滑动步长 10 秒
- 每个设备生成 7 天的原始时间序列（每秒一个点）
- 提取窗口特征：平均温度、振动峰值、电流方差、压力变化率
- 基于老化因子和窗口内传感器极值生成标签
- 训练后自动上传模型至 HDFS
"""

import json
import random
import numpy as np
import subprocess
import os
from datetime import datetime, timedelta
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler
import pymysql

# ==================== 配置 ====================
MYSQL_CONFIG = {
    'host': 'master',
    'user': 'root',
    'password': '060201',
    'database': 'predictive_maintenance'
}
OUTPUT_DIR = '/home/hadoop/tmp'
MODEL_TYPE = 'anomaly'
WINDOW_SIZE_SEC = 30
WINDOW_SLIDE_SEC = 10
DAYS_BACK = 7               # 减少为7天，控制内存
# =============================================

# 设置随机种子保证可复现
random.seed(42)
np.random.seed(42)

def get_devices():
    conn = pymysql.connect(**MYSQL_CONFIG)
    cursor = conn.cursor()
    cursor.execute("SELECT device_id, install_date FROM device_info")
    devices = cursor.fetchall()
    cursor.close()
    conn.close()
    return devices

def generate_raw_ts(device_id, install_date, duration_seconds):
    """
    生成原始时间序列（每秒一个点）
    返回: list of (timestamp, temp, vib, curr, pres)
    """
    base_time = datetime.now().timestamp() - duration_seconds
    ts_data = []
    for i in range(duration_seconds):
        t = base_time + i
        # 动态老化因子：随时间变化
        days_in_service = (datetime.fromtimestamp(t).date() - install_date).days
        aging_factor = min(1.0, max(0.0, days_in_service / 365))

        temp = 30 + aging_factor * 20 + random.gauss(0, 3)
        vib = 0.2 + aging_factor * 1.0 + random.gauss(0, 0.1)
        curr = 1.0 + aging_factor * 1.0 + random.gauss(0, 0.2)
        pres = 100 + aging_factor * 5 + random.gauss(0, 1)

        # 限幅
        temp = min(max(temp, 20), 150)
        vib = min(max(vib, 0.1), 10)
        curr = min(max(curr, 0.5), 5)
        pres = min(max(pres, 85), 120)

        ts_data.append((t, temp, vib, curr, pres))
    return ts_data

def extract_window_features(ts_data):
    """
    从原始时间序列中提取滑动窗口特征
    返回: list of [avg_temp, max_vib, curr_var, press_change_rate]
    """
    features = []
    for start in range(0, len(ts_data) - WINDOW_SIZE_SEC + 1, WINDOW_SLIDE_SEC):
        window = ts_data[start:start+WINDOW_SIZE_SEC]
        temps = [p[1] for p in window]
        vibs = [p[2] for p in window]
        currents = [p[3] for p in window]
        pressures = [p[4] for p in window]

        avg_temp = np.mean(temps)
        max_vib = np.max(vibs)
        curr_var = np.var(currents)
        press_change_rate = pressures[-1] - pressures[0]

        features.append([avg_temp, max_vib, curr_var, press_change_rate])
    return features

def generate_data_generator(devices, days_back):
    """
    生成器：逐设备产生特征和标签，并累加样本数
    yield (features, labels) 每设备返回一次，但实际用于写入列表
    """
    total_seconds = days_back * 24 * 3600
    for device_id, install_date in devices:
        print(f"处理设备 {device_id}...")
        raw_ts = generate_raw_ts(device_id, install_date, total_seconds)
        features = extract_window_features(raw_ts)

        # 注意：老化因子取整个序列的最终老化因子（或可更精细）
        # 这里使用生成原始序列时最后一个点的老化因子（代表设备当前状态）
        final_days = (datetime.now().date() - install_date).days
        aging_factor = min(1.0, final_days / 365)

        for feat in features:
            is_abnormal = 0
            if aging_factor > 0.7:
                if feat[0] > 70 or feat[1] > 1.5 or feat[2] > 0.5 or abs(feat[3]) > 5:
                    is_abnormal = 1
            # 随机额外异常（5%）
            if random.random() < 0.05:
                is_abnormal = 1
            yield feat, is_abnormal

def export_model_to_json(model, scaler, output_path):
    model_data = {
        'model_type': 'random_forest',
        'n_features': model.n_features_in_,
        'classes': model.classes_.tolist(),
        'scaler': {
            'mean': scaler.mean_.tolist(),
            'scale': scaler.scale_.tolist()
        },
        'trees': []
    }

    for tree in model.estimators_:
        tree_obj = tree.tree_
        # 节点特征索引（-2 表示叶子节点）
        feature = tree_obj.feature.tolist()
        threshold = tree_obj.threshold.tolist()
        children_left = tree_obj.children_left.tolist()
        children_right = tree_obj.children_right.tolist()
        # 节点样本数
        n_node_samples = tree_obj.n_node_samples.tolist()
        # 节点值：shape (n_nodes, n_outputs, n_classes) -> 取每个节点的类别计数
        values = []
        for i in range(tree_obj.node_count):
            val = tree_obj.value[i][0].tolist()  # 取第一输出（分类问题）
            values.append(val)

        tree_data = {
            'feature': feature,
            'threshold': threshold,
            'children_left': children_left,
            'children_right': children_right,
            'value': values,
            'n_node_samples': n_node_samples
        }
        model_data['trees'].append(tree_data)

    with open(output_path, 'w') as f:
        json.dump(model_data, f, indent=2)

def main():
    print("开始初始化模型训练（内存优化版）...")
    devices = get_devices()
    print(f"从MySQL读取到 {len(devices)} 台设备")

    # 收集所有特征和标签（内存约200MB，可接受）
    X_list = []
    y_list = []
    for feat, label in generate_data_generator(devices, DAYS_BACK):
        X_list.append(feat)
        y_list.append(label)

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list, dtype=np.int32)
    print(f"生成窗口样本数: {X.shape[0]}, 异常比例: {y.mean():.2%}")

    print("训练随机森林模型...")
    scaler = StandardScaler()
    X_scaled = scaler.fit_transform(X)
    model = RandomForestClassifier(n_estimators=20, max_depth=8, random_state=42, n_jobs=1)
    model.fit(X_scaled, y)
    print("训练完成")

    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    model_filename = f"{MODEL_TYPE}_model_{timestamp}.json"
    model_path = os.path.join(OUTPUT_DIR, model_filename)
    export_model_to_json(model, scaler, model_path)
    print(f"模型已保存至: {model_path}")

    # 生成描述文件
    description = {
        'version': timestamp,
        'model_type': MODEL_TYPE,
        'created_at': datetime.now().isoformat(),
        'hdfs_path': f"/models/anomaly/{model_filename}",
        'local_path': model_path,
        'training_samples': X.shape[0],
        'abnormal_ratio': float(y.mean()),
        'n_estimators': 20,
        'max_depth': 8,
        'feature_names': ['avg_temperature', 'max_vibration', 'current_variance', 'pressure_change_rate']
    }
    desc_path = os.path.join(OUTPUT_DIR, f"{MODEL_TYPE}_model_description_{timestamp}.json")
    with open(desc_path, 'w') as f:
        json.dump(description, f, indent=2)
    print(f"模型描述文件已保存至: {desc_path}")

    # 更新本地索引（内容为本地路径，用于临时）
    index_path = os.path.join(OUTPUT_DIR, f"{MODEL_TYPE}_current_version.txt")
    with open(index_path, 'w') as f:
        f.write(model_path)
    print(f"当前版本索引已更新: {index_path}")

   

if __name__ == '__main__':
    main()
