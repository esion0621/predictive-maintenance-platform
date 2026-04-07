package com.predict.config;

import java.io.Serializable;

public class JobConfig implements Serializable {
    // Kafka配置
    public static final String KAFKA_BOOTSTRAP_SERVERS = "master:9092,slave1:9092,slave2:9092";
    public static final String KAFKA_TOPIC = "device-sensor";
    public static final String KAFKA_GROUP_ID = "flink-device-group";

    // MySQL配置
    public static final String MYSQL_URL = "jdbc:mysql://master:3306/predictive_maintenance?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    public static final String MYSQL_USER = "root";
    public static final String MYSQL_PASSWORD = "060201";

    // Redis配置
    public static final String REDIS_HOST = "slave1";
    public static final int REDIS_PORT = 6379;
    public static final String REDIS_PASSWORD = "123456";

    // HDFS路径
    public static final String HDFS_MODEL_INDEX_PATH = "/tmp/models/anomaly_current_version.txt";
    public static final String HDFS_CHECKPOINT_PATH = "/flink/checkpoints/";
    public static final String HDFS_SENSOR_OUTPUT_PATH = "hdfs://master:9000/data/history/sensor";

    // 窗口配置 (滑动窗口，30秒长度，10秒滑动步长)
    public static final long WINDOW_SIZE_MS = 30000;
    public static final long WINDOW_SLIDE_MS = 10000;

    // 告警阈值
    public static final double ALARM_THRESHOLD = 0.8;

    // 模型热更新检查间隔（毫秒）
    public static final long MODEL_RELOAD_INTERVAL_MS = 60000;
}
