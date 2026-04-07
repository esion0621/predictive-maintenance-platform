# 工业设备预测维护平台

基于实时流处理与机器学习的工业物联网预测维护系统。平台模拟100台设备的传感器数据流，通过 Flink 实时检测异常并告警，利用 Spark 离线训练寿命预测模型，并提供完整的后端 API 与前端可视化界面。

---

## 📸 项目截图

> 以下为项目运行效果示意图，实际界面以部署为准。



## 🎯 项目背景与目标

在工业物联网场景下，设备传感器数据实时流入，需要快速识别异常并预测剩余寿命。本项目构建了一套完整的 Lambda 架构数据处理平台，涵盖数据采集、实时流处理、离线模型训练、API 服务与前端展示，实现了：

- **实时异常检测**：Flink 消费 Kafka 数据，结合随机森林模型实时计算异常概率，告警写入 Redis 与 MySQL。
- **离线模型训练**：Spark 每日读取 HDFS 历史数据，训练异常检测与剩余寿命模型，并热更新至 Flink。
- **完整数据服务**：Spring Boot 提供 REST API，聚合 Redis 实时数据与 MySQL 统计结果。
- **可视化监控**：React + ECharts 展示设备状态、告警列表、健康趋势与报表。

---

## 🏗️ 系统架构
<img width="8922" height="2762" alt="123456" src="https://github.com/user-attachments/assets/470ca4a9-126a-4bfc-a312-31be3cc07589" />




- **数据模拟层**：Python 脚本模拟 100 台设备，每秒发送 1 条传感器数据到 Kafka。
- **消息队列**：Kafka + ZooKeeper，主题 `device-sensor`，2 分区 2 副本。
- **实时处理层**：Flink 1.15.4 on YARN，并行度 2，消费 Kafka → 窗口特征计算 → 模型推理 → 告警 → 写入 Redis/MySQL 与 HDFS Parquet。
- **离线训练层**：Spark 3.1.3 on YARN，每日统计设备指标，每周训练随机森林与 GBDT 模型，导出 JSON 并更新 HDFS 索引。
- **存储层**：HDFS（原始 Parquet + 模型文件），MySQL（设备信息、告警、统计、模型元数据），Redis（设备实时状态、告警列表）。
- **服务层**：Spring Boot 3.x，提供统一 REST API。
- **可视化层**：React 18 + Vite + ECharts，三页面：实时监控、预测维护、报表分析。

---

## 📦 技术栈

| 组件 | 技术 | 版本 |
|------|------|------|
| 数据模拟 | Python + kafka-python + pymysql | 3.8+ |
| 消息队列 | Kafka + ZooKeeper | 3.2.0 / 3.7.1 |
| 实时计算 | Flink on YARN | 1.15.4 |
| 离线计算 | Spark on YARN | 3.1.3 (Scala 2.12) |
| 存储 | HDFS (Hadoop) + MySQL 8.0 + Redis 5.0.7 | 3.2.4 |
| 后端 | Spring Boot + MyBatis Plus + RedisTemplate | 3.x |
| 前端 | React 18 + Vite + ECharts + Axios | 18.2.0 |
| 部署 | 三节点虚拟机 (master, slave1, slave2) | Ubuntu 20.04 |

---

## 📁 项目目录结构

```
.
├── backend/                    # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/predict/
│       ├── config/             # MyBatisPlus, Redis, WebMvc 配置
│       ├── controller/         # DeviceController (8个API)
│       ├── dto/                # 数据传输对象
│       ├── entity/             # 实体类 (device_info, alarm_event, device_daily_stats, model_version, model_metrics)
│       ├── mapper/             # MyBatis Plus Mapper
│       ├── service/            # 业务逻辑
│       └── utils/              # RedisKeyUtils
├── flink-job/                  # Flink 实时作业 (Java, Maven)
│   ├── pom.xml
│   └── src/main/java/com/predict/
│       ├── FlinkJob.java       # 主入口：Kafka消费→清洗→窗口特征→模型推理→Redis/MySQL/HDFS
│       ├── config/JobConfig.java
│       ├── pojo/               # SensorData, FeatureWindow, AnomalyResult
│       ├── source/             # 自定义反序列化
│       ├── process/            # FeatureExtractor, ModelLoader (热加载)
│       ├── model/              # RandomForestModel (JSON解析与推理)
│       ├── sink/               # RedisSink, MySQLSink
│       └── utils/              # HdfsUtils
├── spark-offline-job/          # Spark 离线作业 (Scala, Maven)
│   ├── pom.xml
│   ├── submit_daily_stats.sh
│   ├── submit_model_training.sh
│   └── src/main/scala/com/predict/
│       ├── DailyStatsJob.scala       # 每日设备统计，写入MySQL
│       ├── ModelTrainingJob.scala    # 读取7天数据，训练随机森林+GBDT，导出JSON，更新MySQL与HDFS索引
│       └── utils/                    # HdfsUtils, ModelExportUtils
├── frontend/                   # React 前端 (Vite)
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/client.js       # Axios封装
│       ├── pages/              # Dashboard, Predictive, Report
│       ├── components/         # KpiCard, DeviceCardGrid, TrendChart, AlarmList, HealthPieChart, RulTable, DeviceHistoryChart, AlarmBarChart, ModelMetricsCard, DeviceTypeChart
│       └── utils/formatter.js
├── insert_devices.py           # 初始化100台设备到MySQL
├── producer.py                 # Kafka 数据生产者 (模拟传感器)
├── train_init_model.py         # 初始模型训练 (生成JSON并上传HDFS)
├── create_tables.sql           # MySQL 建表语句
├── clean_old_data.sh           # 清理30天前告警与HDFS数据
└── screenshots/                # 存放项目截图 (需自行创建)
```

---

## 🚀 快速开始

### 1. 环境准备（三节点）

| 节点 | IP | 部署组件 |
|------|----|----------|
| master | 192.168.1.10 | Hadoop NameNode/DataNode, YARN RM/NM, ZooKeeper, Kafka Broker, MySQL, Spring Boot, Node.js |
| slave1 | 192.168.1.11 | Hadoop DataNode, YARN NM, ZooKeeper, Kafka Broker, Redis |
| slave2 | 192.168.1.12 | Hadoop DataNode, YARN NM, ZooKeeper, Kafka Broker |

所有服务安装完毕，Hadoop、Spark、Flink 配置 YARN 模式。

### 2. 数据库初始化

```bash
mysql -h master -u root -p060201 < create_tables.sql
python3 insert_devices.py
```

### 3. 启动基础服务

- ZooKeeper, Kafka, HDFS, YARN, MySQL, Redis

### 4. 训练初始模型并上传 HDFS

```bash
python3 train_init_model.py
hdfs dfs -mkdir -p /models/anomaly /models/rul
hdfs dfs -put /home/hadoop/tmp/anomaly_model_*.json /models/anomaly/json/
hdfs dfs -put /home/hadoop/tmp/anomaly_current_version.txt /models/
```

### 5. 启动 Flink 作业

```bash
flink run -m yarn-cluster -yjm 1024m -ytm 1024m -ys 1 -p 2 \
  -c com.predict.FlinkJob /home/hadoop/job/flink-job/target/flink-job-1.0-SNAPSHOT.jar
```

### 6. 启动数据生产者

```bash
python3 producer.py
```

### 7. 启动后端服务

```bash
cd backend
mvn clean package
java -jar target/predictive-maintenance-backend-1.0.0.jar
```

### 8. 启动前端

```bash
cd frontend
npm install
npm run dev   # 访问 http://master:3000
```

### 9. 配置离线调度（可选）

```bash
crontab -e
0 1 * * * /home/hadoop/job/spark-offline-job/submit_daily_stats.sh
0 3 * * 0 /home/hadoop/job/spark-offline-job/submit_model_training.sh
```

### 10. 清理历史数据（每月）

```bash
chmod +x clean_old_data.sh
# 编辑脚本，设置 DRY_RUN=false 后执行
./clean_old_data.sh
```

---

## 📊 核心功能实现清单

| 模块 | 功能 | 状态 |
|------|------|------|
| **数据模拟** | 100设备×1条/秒，老化模拟 | ✅ |
| **实时处理** | Kafka消费、清洗、窗口特征、模型热加载、推理、告警 | ✅ |
| **存储** | HDFS Parquet分区，Redis Hash/List，MySQL 5张表 | ✅ |
| **离线训练** | 每日聚合统计，每周训练随机森林+GBDT，JSON导出，效果对比，版本管理 | ✅ |
| **后端API** | 8个接口（最新状态、告警、历史、RUL、统计、模型信息） | ✅ |
| **前端** | 实时监控（KPI卡片、设备卡片、趋势图、告警列表）、预测维护（健康分布、RUL表、历史查询）、报表（告警排行、模型指标、类型异常率） | ✅ |
| **运维** | 清理脚本，调度脚本，日志记录 | ✅ |

---

## 🧪 测试验证

- **实时性**：窗口特征每10秒触发，告警延迟小于1秒。
- **准确性**：异常检测模型准确率≥85%，RUL模型RMSE<30天。
- **吞吐量**：Flink 并行度2，处理100条/秒平稳运行。
- **容错**：启用Checkpoint，作业失败可从保存点恢复。

---

## 📝 注意事项

- Flink 作业中模型热更新间隔为60秒，修改 `JobConfig.MODEL_RELOAD_INTERVAL_MS` 可调整。
- HDFS 路径需确保 `hdfs://master:9000` 与您的集群配置一致。
- 前端默认代理后端 `http://master:8080`，可在 `vite.config.js` 中修改。
- 生产环境建议开启 Kerberos 认证，并调整日志级别。

---

## 🤝 贡献与许可

本项目为工业预测维护的完整实现，仅供学习交流。如需商业使用，请遵守 Apache 2.0 许可证。

---

## 📧 联系方式

项目作者：[您的名字]  
GitHub：[您的仓库地址]  
如有问题，欢迎提 Issue 或邮件联系。

---

> 感谢使用本平台，希望对您的大数据与 AI 工程化实践有所帮助！
