import json
import random
import time
from datetime import datetime
from kafka import KafkaProducer
import pymysql

# Kafka配置
producer = KafkaProducer(
    bootstrap_servers=['master', 'slave1', 'slave2'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# MySQL连接（读取设备信息）
db = pymysql.connect(
    host='master',
    user='root',
    password='060201',
    database='predictive_maintenance'
)

# 获取所有设备ID及安装日期
cursor = db.cursor()
cursor.execute("SELECT device_id, install_date FROM device_info")
devices = cursor.fetchall()
cursor.close()

# 基础传感器范围（健康状态）
base_ranges = {
    'temperature': (30, 70),
    'vibration': (0.1, 1.0),
    'current': (0.5, 2.5),
    'pressure': (90, 110)
}

def get_sensor_values(device_id, install_date):
    # 计算已运行天数（老化因子）
    days_in_service = (datetime.now().date() - install_date).days
    aging_factor = min(1.0, days_in_service / 365)  # 一年后完全老化

    # 随机波动
    temp = random.uniform(*base_ranges['temperature'])
    vib = random.uniform(*base_ranges['vibration'])
    curr = random.uniform(*base_ranges['current'])
    pres = random.uniform(*base_ranges['pressure'])

    # 老化影响：温度升高、振动加剧、电流增大、压力波动
    temp += aging_factor * random.uniform(5, 15)
    vib += aging_factor * random.uniform(0.2, 0.5)
    curr += aging_factor * random.uniform(0.2, 0.8)
    pres += random.uniform(-5, 5) * aging_factor

    # 限幅
    temp = min(max(temp, 0), 150)
    vib = min(max(vib, 0), 10)
    curr = min(max(curr, 0), 5)
    pres = min(max(pres, 80), 130)

    return {
        'temperature': round(temp, 1),
        'vibration': round(vib, 2),
        'current': round(curr, 2),
        'pressure': round(pres, 1)
    }

# 持续发送
try:
    while True:
        for device_id, install_date in devices:
            sensor_data = get_sensor_values(device_id, install_date)
            message = {
                'device_id': device_id,
                'timestamp': int(time.time() * 1000),
                **sensor_data
            }
            producer.send('device-sensor', value=message)
            # 模拟100台每秒发送一次，每台间隔约0.01秒（实际取决于循环）
            time.sleep(0.01)  # 100台*0.01=1秒，即每秒100条
        # 也可在每次循环后短暂休息，但上面的sleep已控制速率
except KeyboardInterrupt:
    print("生产者停止")
finally:
    producer.close()
    db.close()
