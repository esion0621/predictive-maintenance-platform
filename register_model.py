#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
将本地训练好的模型信息写入 MySQL model_version + model_metrics 表
用法: python register_model.py [description_json_path]
如果不传参数，自动查找 /home/hadoop/tmp 下最新的 anomaly_model_description_*.json
"""

import json
import glob
import os
import sys
import pymysql

MYSQL_CONFIG = {
    'host': 'master',
    'user': 'root',
    'password': '060201',
    'database': 'predictive_maintenance'
}
DESCRIPTION_DIR = '/home/hadoop/tmp'


def find_latest_description():
    pattern = os.path.join(DESCRIPTION_DIR, 'anomaly_model_description_*.json')
    files = sorted(glob.glob(pattern))
    if not files:
        print(f"未找到描述文件: {pattern}")
        sys.exit(1)
    return files[-1]


def register(desc_path):
    with open(desc_path, 'r') as f:
        desc = json.load(f)

    model_type = desc['model_type']
    version = desc['version']
    hdfs_path = desc['hdfs_path']
    metrics = desc.get('metrics', {})

    conn = pymysql.connect(**MYSQL_CONFIG)
    cursor = conn.cursor()

    # 将同类型的旧模型设为非活跃
    cursor.execute(
        "UPDATE model_version SET is_active = 0 WHERE model_type = %s",
        (model_type,)
    )

    # 插入新模型记录，设为活跃
    cursor.execute(
        "INSERT INTO model_version (model_type, version, hdfs_path, is_active) VALUES (%s, %s, %s, 1)",
        (model_type, version, hdfs_path)
    )
    model_id = cursor.lastrowid

    # 插入模型指标
    if metrics:
        cursor.execute(
            "INSERT INTO model_metrics (model_id, accuracy, f1_score) VALUES (%s, %s, %s)",
            (model_id, metrics.get('accuracy'), metrics.get('f1_score'))
        )
        print(f"模型指标已写入 model_metrics:")
        print(f"  accuracy = {metrics.get('accuracy')}")
        print(f"  f1_score = {metrics.get('f1_score')}")

    conn.commit()
    cursor.close()
    conn.close()

    print(f"模型已注册到 MySQL:")
    print(f"  model_id  = {model_id}")
    print(f"  model_type= {model_type}")
    print(f"  version   = {version}")
    print(f"  hdfs_path = {hdfs_path}")
    print(f"  is_active = 1")


if __name__ == '__main__':
    if len(sys.argv) > 1:
        path = sys.argv[1]
    else:
        path = find_latest_description()
    print(f"读取描述文件: {path}")
    register(path)

