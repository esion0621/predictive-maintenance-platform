# 工业设备预测维护平台

<img width="1612" height="752" alt="批注 2026-06-13 150045" src="https://github.com/user-attachments/assets/bf1c6a8f-b169-4376-bf5f-0b5f53ca52aa" />

<img width="1618" height="761" alt="批注 2026-06-13 150102" src="https://github.com/user-attachments/assets/99e3c26d-06e4-4ce9-969f-2ed31a05eba0" />

<img width="1622" height="777" alt="批注 2026-06-13 150250" src="https://github.com/user-attachments/assets/c1e0116f-a1e4-4b21-8c42-9b7a37a47a8f" />

<img width="1627" height="775" alt="批注 2026-06-13 150326" src="https://github.com/user-attachments/assets/43419388-ea15-42d9-b42d-b04cf4361103" />

<img width="1628" height="760" alt="批注 2026-06-13 150338" src="https://github.com/user-attachments/assets/aa305eaa-9d4c-457b-afb6-46142045b4b2" />

---

基于 Lambda 架构的工业物联网预测维护系统。平台模拟 100 台设备的传感器数据流，通过 Flink CEP 规则匹配 + 随机森林模型实时检测异常，利用 Spark 离线训练模型并热更新，提供完整的后端 API 与前端可视化界面。

## 🎯 核心特性

- **CEP 前置拦截**：Flink 消费 Kafka 数据后先经 CEP 规则匹配（温度连续上升 R1、振动+电流双超 R2、压力急降 R3），命中立即告警，未命中送模型推理，降低延迟。
- **模型实时推理**：随机森林模型热加载，窗口特征计算后实时输出异常概率。
- **状态机数据生成**：生产者基于 DeviceState 状态机，随机游走+均值回归，按概率注入故障模式，生成有时序关联的传感器数据。
- **离线模型训练**：Spark 每日读取 HDFS 历史数据，多条件组合标签训练异常检测与剩余寿命模型，2% 阈值自动更新。
- **完整数据服务**：Spring Boot REST API，聚合 Redis 实时数据与 MySQL 统计结果。
- **可视化监控**：React + ECharts 展示设备状态、CEP/模型告警、健康趋势与报表。

---

## 🏗️ 系统架构

<img width="6522" height="8413" alt="deepseek_mermaid_20260614_56adbe" src="https://github.com/user-attachments/assets/f08a181a-0c7d-45ae-8b11-e0cf12c3a73b" />


- **数据模拟层**：Python 状态机模拟 100 台设备，每秒发送 1 条传感器数据到 Kafka，支持 R1/R2/R3/综合故障注入。
- **消息队列**：Kafka + ZooKeeper，主题 `device-sensor`，2 分区 2 副本。
- **实时处理层**：Flink 1.15.4 on YARN，并行度 2，消费 Kafka → CEP 规则检测 → 窗口特征计算 → 模型推理 → 告警合并 → 写入 Redis/MySQL 与 HDFS Parquet。
- **离线训练层**：Spark 3.1.3 on YARN，按设备+天聚合特征，多条件组合标签，训练随机森林与 GBDT 模型，导出 JSON 并更新 HDFS 索引。
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
│       ├── FlinkJob.java       # 主入口：Kafka消费→CEP检测→窗口特征→模型推理→Redis/MySQL/HDFS
│       ├── config/JobConfig.java
│       ├── pojo/               # SensorData, FeatureWindow, AnomalyResult (含source/ruleId)
│       ├── source/             # 自定义反序列化
│       ├── process/            # CEPRuleDetector, FeatureExtractor, ModelLoader (热加载)
│       ├── model/              # RandomForestModel (JSON解析与推理)
│       ├── sink/               # RedisSink, MySQLSink
│       └── utils/              # HdfsUtils
├── spark-offline-job/          # Spark 离线作业 (Scala, Maven)
│   ├── pom.xml
│   ├── submit_daily_stats.sh
│   ├── submit_model_training.sh
│   └── src/main/scala/com/predict/
│       ├── DailyStatsJob.scala       # 每日设备统计，写入MySQL
│       ├── ModelTrainingJob.scala    # 按天聚合+多条件标签+RUL惩罚项，训练随机森林+GBDT
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
├── producer.py                 # Kafka 数据生产者 (DeviceState状态机+故障注入)
├── train_init_model.py         # 初始模型训练 (复用状态机，窗口标签，class_weight=balanced)
├── register_model.py           # 注册模型到MySQL (model_version + model_metrics)
├── deploy_model.sh             # 清理HDFS旧数据+上传模型+更新索引
├── create_tables.sql           # MySQL 建表语句
├── clean_old_data.sh           # 清理30天前告警与HDFS数据
└── screenshots/                # 存放项目截图
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

### 4. 训练初始模型、注册并上传 HDFS

```bash
python3 train_init_model.py
python3 register_model.py
./deploy_model.sh
```

### 5. 启动 Flink 作业

```bash
flink run -m yarn-cluster -yjm 1024m -ytm 2048m -ys 1 -p 2 \
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

### 9. 配置定时任务

```bash
crontab -e

# 每日凌晨2点：训练+注册+部署
0 2 * * * cd /home/hadoop && python3 train_init_model.py && python3 register_model.py && ./deploy_model.sh >> /home/hadoop/logs/deploy_model.log 2>&1

# 每日凌晨1点：每日统计
0 1 * * * /home/hadoop/job/spark-offline-job/submit_daily_stats.sh

# 每周日凌晨3点：Spark模型训练
0 3 * * 0 /home/hadoop/job/spark-offline-job/submit_model_training.sh
```

### 10. 清理历史数据（每月）

```bash
chmod +x clean_old_data.sh
./clean_old_data.sh
```

---

## 📊 核心功能实现清单

| 模块 | 功能 | 状态 |
|------|------|------|
| **数据模拟** | 100设备×1条/秒，DeviceState状态机，R1/R2/R3/综合故障注入 | ✅ |
| **CEP检测** | 温度连续上升(R1)、振动+电流双超(R2)、压力急降(R3)，毫秒级告警 | ✅ |
| **实时处理** | Kafka消费、CEP分流、窗口特征、模型热加载、推理、告警合并 | ✅ |
| **存储** | HDFS Parquet分区，Redis Hash/List，MySQL 5张表（含source/rule_id） | ✅ |
| **离线训练** | 按天聚合，多条件组合标签，RUL异常惩罚，2%更新阈值，版本管理 | ✅ |
| **后端API** | 8个接口（最新状态、告警、历史、RUL、统计、模型信息） | ✅ |
| **前端** | 实时监控、预测维护、报表分析，CEP/MODEL告警标签区分 | ✅ |
| **运维** | 自动训练部署脚本，清理脚本，调度脚本，日志记录 | ✅ |

---

## 🧪 测试验证

- **CEP 实时性**：逐条检测，命中规则毫秒级告警，标记 source=CEP。
- **模型准确性**：异常检测模型准确率≥85%，RUL模型RMSE<30天。
- **吞吐量**：Flink 并行度2，处理100条/秒平稳运行。
- **容错**：启用 Checkpoint + RocksDB 状态后端，作业失败可从保存点恢复。

---

## 📝 注意事项

- Flink 作业中模型热更新间隔为60秒，修改 `JobConfig.MODEL_RELOAD_INTERVAL_MS` 可调整。
- CEP 规则阈值在 `JobConfig` 中配置，与生产者故障注入参数对应。
- HDFS 路径需确保 `hdfs://master:9000` 与集群配置一致。
- 前端默认代理后端 `http://master:8080`，可在 `vite.config.js` 中修改。
- 生产环境建议开启 Kerberos 认证，并调整日志级别。

---

## 🤝 贡献与许可

本项目为模拟工业预测维护的完整实现，仅供学习交流。

---

## 📧 联系方式

项目作者：esion
如有问题，欢迎提 Issue 或邮件联系。
