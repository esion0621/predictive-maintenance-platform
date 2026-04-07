import pymysql
import random
from datetime import date, timedelta

# MySQL连接配置
conn = pymysql.connect(
    host='master',
    user='root',
    password='060201',
    database='predictive_maintenance'
)

cursor = conn.cursor()

# 生成100台设备
devices = []
device_types = ['Pump', 'Compressor', 'Fan', 'Motor']
locations = ['Workshop_A', 'Workshop_B', 'Storage_C']

for i in range(1, 101):
    device_id = f'device_{i:03d}'
    device_type = random.choice(device_types)
    # 安装日期：过去1-3年之间
    install_date = date.today() - timedelta(days=random.randint(365, 1095))
    location = random.choice(locations)
    devices.append((device_id, device_type, install_date, location))

# 插入数据
insert_sql = "INSERT INTO device_info (device_id, device_type, install_date, location) VALUES (%s, %s, %s, %s)"
cursor.executemany(insert_sql, devices)
conn.commit()

print(f"插入 {len(devices)} 条设备信息成功。")

cursor.close()
conn.close()
