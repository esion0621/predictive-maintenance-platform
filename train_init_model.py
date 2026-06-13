#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模型训练脚本（状态机 + 故障驱动标签版）
- 复用生产者 DeviceState 状态机生成训练数据，保证训练/实时数据分布一致
- 标签由故障模式驱动（ground truth），不再硬编码 if-else
- 窗口内 ≥30% 异常点则标记为异常窗口
- class_weight='balanced' 自动平衡样本
- 训练后打印分类报告验证指标
"""

import json
import random
import numpy as np
import os
from datetime import datetime
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import classification_report
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
DAYS_BACK = 7
ABNORMAL_WINDOW_RATIO = 0.3  # 窗口内异常点占比阈值
# =============================================

random.seed(42)
np.random.seed(42)

# ==================== 复用生产者状态机 ====================
# 健康基线
BASE_TEMP = 45.0
BASE_VIB = 0.5
BASE_CURR = 1.5
BASE_PRES = 100.0

LIMITS = {
    'temperature': (20, 150),
    'vibration': (0.1, 10),
    'current': (0.5, 5),
    'pressure': (80, 130)
}

FAULT_PROB_R1 = 0.01
FAULT_PROB_R2 = 0.01
FAULT_PROB_R3 = 0.005
FAULT_PROB_COMBO = 0.005


class DeviceState:
    """设备状态机（与 producer.py 保持一致）"""

    def __init__(self, device_id, install_date):
        self.device_id = device_id
        self.install_date = install_date
        days_in_service = (datetime.now().date() - install_date).days
        aging = min(1.0, days_in_service / 365)
        self.prev_temp = BASE_TEMP + aging * random.uniform(0, 10) + random.gauss(0, 2)
        self.prev_vib = BASE_VIB + aging * random.uniform(0, 0.3) + random.gauss(0, 0.05)
        self.prev_curr = BASE_CURR + aging * random.uniform(0, 0.5) + random.gauss(0, 0.1)
        self.prev_pres = BASE_PRES + random.gauss(0, 2)
        self.fault_mode = None
        self.fault_remaining = 0
        self.is_abnormal = False

    def _aging_drift(self):
        days_in_service = (datetime.now().date() - self.install_date).days
        aging = min(1.0, days_in_service / 365)
        return {
            'temp': aging * 0.01,
            'vib': aging * 0.002,
            'curr': aging * 0.003,
            'pres': 0.0
        }

    def _mean_revert(self, value, baseline, strength=0.02):
        return (baseline - value) * strength

    def _clamp(self, value, key):
        lo, hi = LIMITS[key]
        return min(max(value, lo), hi)

    def generate(self):
        if self.fault_mode is None:
            r = random.random()
            if r < FAULT_PROB_R1:
                self.fault_mode = 'R1'
                self.fault_remaining = random.randint(5, 8)
            elif r < FAULT_PROB_R1 + FAULT_PROB_R2:
                self.fault_mode = 'R2'
                self.fault_remaining = random.randint(3, 5)
            elif r < FAULT_PROB_R1 + FAULT_PROB_R2 + FAULT_PROB_R3:
                self.fault_mode = 'R3'
                self.fault_remaining = random.randint(1, 2)
            elif r < FAULT_PROB_R1 + FAULT_PROB_R2 + FAULT_PROB_R3 + FAULT_PROB_COMBO:
                self.fault_mode = 'COMBO'
                self.fault_remaining = random.randint(5, 10)

        self.is_abnormal = self.fault_mode is not None

        if self.fault_mode == 'R1':
            temp, vib, curr, pres = self._generate_r1()
        elif self.fault_mode == 'R2':
            temp, vib, curr, pres = self._generate_r2()
        elif self.fault_mode == 'R3':
            temp, vib, curr, pres = self._generate_r3()
        elif self.fault_mode == 'COMBO':
            temp, vib, curr, pres = self._generate_combo()
        else:
            temp, vib, curr, pres = self._generate_normal()

        self.prev_temp = temp
        self.prev_vib = vib
        self.prev_curr = curr
        self.prev_pres = pres

        if self.fault_mode is not None:
            self.fault_remaining -= 1
            if self.fault_remaining <= 0:
                self.fault_mode = None

        return temp, vib, curr, pres

    def _generate_normal(self):
        drift = self._aging_drift()
        temp = self.prev_temp + random.gauss(0, 0.5) + drift['temp'] + self._mean_revert(self.prev_temp, BASE_TEMP, 0.02)
        vib = self.prev_vib + random.gauss(0, 0.02) + drift['vib'] + self._mean_revert(self.prev_vib, BASE_VIB, 0.03)
        curr = self.prev_curr + random.gauss(0, 0.03) + drift['curr'] + self._mean_revert(self.prev_curr, BASE_CURR, 0.03)
        pres = self.prev_pres + random.gauss(0, 0.3) + self._mean_revert(self.prev_pres, BASE_PRES, 0.02)
        return (
            self._clamp(temp, 'temperature'),
            self._clamp(vib, 'vibration'),
            self._clamp(curr, 'current'),
            self._clamp(pres, 'pressure')
        )

    def _generate_r1(self):
        temp = self.prev_temp + random.uniform(3, 5)
        vib = self.prev_vib + random.gauss(0, 0.02)
        curr = self.prev_curr + random.gauss(0, 0.03)
        pres = self.prev_pres + random.gauss(0, 0.3)
        return (
            self._clamp(temp, 'temperature'),
            self._clamp(vib, 'vibration'),
            self._clamp(curr, 'current'),
            self._clamp(pres, 'pressure')
        )

    def _generate_r2(self):
        temp = self.prev_temp + random.gauss(0, 0.5)
        vib = random.uniform(6, 8)
        curr = random.uniform(3.8, 4.5)
        pres = self.prev_pres + random.gauss(0, 0.3)
        return (
            self._clamp(temp, 'temperature'),
            self._clamp(vib, 'vibration'),
            self._clamp(curr, 'current'),
            self._clamp(pres, 'pressure')
        )

    def _generate_r3(self):
        temp = self.prev_temp + random.gauss(0, 0.5)
        vib = self.prev_vib + random.gauss(0, 0.02)
        curr = self.prev_curr + random.gauss(0, 0.03)
        pres = self.prev_pres - random.uniform(15, 25)
        return (
            self._clamp(temp, 'temperature'),
            self._clamp(vib, 'vibration'),
            self._clamp(curr, 'current'),
            self._clamp(pres, 'pressure')
        )

    def _generate_combo(self):
        temp = self.prev_temp + random.uniform(2, 4)
        vib = self.prev_vib + random.uniform(0.3, 0.8)
        curr = self.prev_curr + random.uniform(0.2, 0.5)
        pres = self.prev_pres + random.gauss(0, 1.0)
        return (
            self._clamp(temp, 'temperature'),
            self._clamp(vib, 'vibration'),
            self._clamp(curr, 'current'),
            self._clamp(pres, 'pressure')
        )


# ==================== 数据生成与特征提取 ====================

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
    使用状态机生成原始时间序列（每秒一个点）
    返回: list of (temp, vib, curr, pres, is_abnormal)
    """
    state = DeviceState(device_id, install_date)
    ts_data = []
    for _ in range(duration_seconds):
        temp, vib, curr, pres = state.generate()
        ts_data.append((temp, vib, curr, pres, state.is_abnormal))
    return ts_data


def extract_window_features_and_labels(ts_data):
    """
    从原始时间序列中提取滑动窗口特征 + 故障驱动标签
    窗口内 ≥30% 的点标记为异常，则窗口标签为 1
    """
    features = []
    labels = []
    for start in range(0, len(ts_data) - WINDOW_SIZE_SEC + 1, WINDOW_SLIDE_SEC):
        window = ts_data[start:start + WINDOW_SIZE_SEC]
        temps = [p[0] for p in window]
        vibs = [p[1] for p in window]
        currents = [p[2] for p in window]
        pressures = [p[3] for p in window]
        abnormal_flags = [p[4] for p in window]

        avg_temp = np.mean(temps)
        max_vib = np.max(vibs)
        curr_var = np.var(currents)
        press_change_rate = pressures[-1] - pressures[0]

        # 故障驱动标签：窗口内异常点占比 ≥ 阈值
        abnormal_ratio = sum(abnormal_flags) / len(abnormal_flags)
        window_label = 1 if abnormal_ratio >= ABNORMAL_WINDOW_RATIO else 0

        features.append([avg_temp, max_vib, curr_var, press_change_rate])
        labels.append(window_label)
    return features, labels


def generate_all_data(devices, days_back):
    """逐设备生成特征和标签"""
    total_seconds = days_back * 24 * 3600
    X_list = []
    y_list = []
    for device_id, install_date in devices:
        print(f"处理设备 {device_id}...")
        raw_ts = generate_raw_ts(device_id, install_date, total_seconds)
        feats, labs = extract_window_features_and_labels(raw_ts)
        X_list.extend(feats)
        y_list.extend(labs)
    return X_list, y_list


# ==================== 模型导出 ====================

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
        feature = tree_obj.feature.tolist()
        threshold = tree_obj.threshold.tolist()
        children_left = tree_obj.children_left.tolist()
        children_right = tree_obj.children_right.tolist()
        n_node_samples = tree_obj.n_node_samples.tolist()
        values = []
        for i in range(tree_obj.node_count):
            val = tree_obj.value[i][0].tolist()
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


# ==================== 主流程 ====================

def main():
    print("开始初始化模型训练（状态机 + 故障驱动标签版）...")
    devices = get_devices()
    print(f"从MySQL读取到 {len(devices)} 台设备")

    X_list, y_list = generate_all_data(devices, DAYS_BACK)

    X = np.array(X_list, dtype=np.float32)
    y = np.array(y_list, dtype=np.int32)
    abnormal_ratio = y.mean()
    print(f"生成窗口样本数: {X.shape[0]}, 异常比例: {abnormal_ratio:.2%}")

    if abnormal_ratio < 0.01:
        print("警告: 异常样本比例过低 (<1%)，模型可能无法有效学习异常模式")

    # 划分训练集和测试集
    indices = np.arange(len(X))
    np.random.shuffle(indices)
    split = int(len(X) * 0.8)
    train_idx, test_idx = indices[:split], indices[split:]
    X_train, X_test = X[train_idx], X[test_idx]
    y_train, y_test = y[train_idx], y[test_idx]

    # 标准化
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    # 训练随机森林（class_weight='balanced' 自动平衡样本）
    print("训练随机森林模型 (n_estimators=50, max_depth=12, class_weight=balanced)...")
    model = RandomForestClassifier(
        n_estimators=50,
        max_depth=12,
        min_samples_leaf=5,
        class_weight='balanced',
        random_state=42,
        n_jobs=1
    )
    model.fit(X_train_scaled, y_train)
    print("训练完成")

    # 验证指标
    y_pred = model.predict(X_test_scaled)
    report = classification_report(y_test, y_pred, target_names=['正常', '异常'], digits=4, output_dict=True)
    print("\n===== 分类报告 =====")
    print(classification_report(y_test, y_pred, target_names=['正常', '异常'], digits=4))

    # 提取关键指标
    accuracy = report['accuracy']
    f1_anomaly = report['异常']['f1-score']
    precision_anomaly = report['异常']['precision']
    recall_anomaly = report['异常']['recall']

    # 保存模型
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
        'abnormal_ratio': float(abnormal_ratio),
        'n_estimators': 50,
        'max_depth': 12,
        'class_weight': 'balanced',
        'feature_names': ['avg_temperature', 'max_vibration', 'current_variance', 'pressure_change_rate'],
        'metrics': {
            'accuracy': float(accuracy),
            'f1_score': float(f1_anomaly),
            'precision': float(precision_anomaly),
            'recall': float(recall_anomaly)
        }
    }
    desc_path = os.path.join(OUTPUT_DIR, f"{MODEL_TYPE}_model_description_{timestamp}.json")
    with open(desc_path, 'w') as f:
        json.dump(description, f, indent=2)
    print(f"模型描述文件已保存至: {desc_path}")

    # 更新本地索引
    index_path = os.path.join(OUTPUT_DIR, f"{MODEL_TYPE}_current_version.txt")
    with open(index_path, 'w') as f:
        f.write(model_path)
    print(f"当前版本索引已更新: {index_path}")


if __name__ == '__main__':
    main()

