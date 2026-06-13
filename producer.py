#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
传感器数据生产者（状态机 + 故障注入版）
- 每台设备维护状态机，数据点之间有时序关联（随机游走 + 均值回归）
- 按概率注入4种故障模式，确保 CEP 规则可触发
- 每秒每台设备发送1条数据
"""

import json
import random
import time
from datetime import datetime
from kafka import KafkaProducer
import pymysql

# ==================== 配置 ====================
KAFKA_SERVERS = ['master', 'slave1', 'slave2']
KAFKA_TOPIC = 'device-sensor'
MYSQL_CONFIG = {
    'host': 'master',
    'user': 'root',
    'password': '060201',
    'database': 'predictive_maintenance'
}

# 健康基线
BASE_TEMP = 45.0
BASE_VIB = 0.5
BASE_CURR = 1.5
BASE_PRES = 100.0

# 限幅范围
LIMITS = {
    'temperature': (20, 150),
    'vibration': (0.1, 10),
    'current': (0.5, 5),
    'pressure': (80, 130)
}

# 故障注入概率（每台设备每秒）
FAULT_PROB_R1 = 0.01       # 温度连续上升
FAULT_PROB_R2 = 0.01       # 振动+电流双超
FAULT_PROB_R3 = 0.005      # 压力急降
FAULT_PROB_COMBO = 0.005   # 综合故障
# =============================================


class DeviceState:
    """设备状态机：维护传感器上一条值和故障模式"""

    def __init__(self, device_id, install_date):
        self.device_id = device_id
        self.install_date = install_date

        # 上一条传感器值（从健康基线附近初始化）
        days_in_service = (datetime.now().date() - install_date).days
        aging = min(1.0, days_in_service / 365)
        self.prev_temp = BASE_TEMP + aging * random.uniform(0, 10) + random.gauss(0, 2)
        self.prev_vib = BASE_VIB + aging * random.uniform(0, 0.3) + random.gauss(0, 0.05)
        self.prev_curr = BASE_CURR + aging * random.uniform(0, 0.5) + random.gauss(0, 0.1)
        self.prev_pres = BASE_PRES + random.gauss(0, 2)

        # 故障模式
        self.fault_mode = None       # None / 'R1' / 'R2' / 'R3' / 'COMBO'
        self.fault_remaining = 0     # 故障剩余点数

    def _aging_drift(self):
        """老化漂移：设备越老，传感器值越容易偏高"""
        days_in_service = (datetime.now().date() - self.install_date).days
        aging = min(1.0, days_in_service / 365)
        return {
            'temp': aging * 0.01,
            'vib': aging * 0.002,
            'curr': aging * 0.003,
            'pres': 0.0
        }

    def _mean_revert(self, value, baseline, strength=0.02):
        """均值回归力：偏离基线越远，拉回越强"""
        return (baseline - value) * strength

    def _clamp(self, value, key):
        lo, hi = LIMITS[key]
        return min(max(value, lo), hi)

    def generate(self):
        """生成一条传感器数据"""
        # 检查是否需要进入故障模式
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

        # 故障模式生成
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

        # 更新上一条值
        self.prev_temp = temp
        self.prev_vib = vib
        self.prev_curr = curr
        self.prev_pres = pres

        # 故障计数递减
        if self.fault_mode is not None:
            self.fault_remaining -= 1
            if self.fault_remaining <= 0:
                self.fault_mode = None

        return {
            'temperature': round(temp, 1),
            'vibration': round(vib, 2),
            'current': round(curr, 2),
            'pressure': round(pres, 1)
        }

    def _generate_normal(self):
        """正常随机游走 + 均值回归"""
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
        """R1: 温度连续上升（每点 +3~5°C），其他正常微波动"""
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
        """R2: 振动+电流双超阈值，其他正常"""
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
        """R3: 压力急降（一次降 15~25），其他正常"""
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
        """综合故障：温度上升 + 振动增大 + 电流增大"""
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


def main():
    producer = KafkaProducer(
        bootstrap_servers=KAFKA_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode('utf-8')
    )

    db = pymysql.connect(**MYSQL_CONFIG)
    cursor = db.cursor()
    cursor.execute("SELECT device_id, install_date FROM device_info")
    devices = cursor.fetchall()
    cursor.close()

    # 初始化每台设备的状态机
    device_states = {}
    for device_id, install_date in devices:
        device_states[device_id] = DeviceState(device_id, install_date)

    print(f"已加载 {len(device_states)} 台设备，开始发送数据...")

    try:
        while True:
            for device_id, state in device_states.items():
                sensor_data = state.generate()
                message = {
                    'device_id': device_id,
                    'timestamp': int(time.time() * 1000),
                    **sensor_data
                }
                producer.send(KAFKA_TOPIC, value=message)
            time.sleep(1)  # 每秒一轮，每台设备1条
    except KeyboardInterrupt:
        print("生产者停止")
    finally:
        producer.close()
        db.close()


if __name__ == '__main__':
    main()

