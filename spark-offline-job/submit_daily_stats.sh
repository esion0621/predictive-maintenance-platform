#!/bin/bash

export HADOOP_CONF_DIR=/home/hadoop/hadoop/etc/hadoop
export YARN_CONF_DIR=/home/hadoop/hadoop/etc/hadoop
export SPARK_HOME=/home/hadoop/spark

JAR_PATH="/home/hadoop/job/spark-offline-job/target/spark-offline-job-1.0.0.jar"
LOG_DIR="/home/hadoop/logs/spark-jobs"
mkdir -p $LOG_DIR

# 可接受日期参数，默认为昨天
TARGET_DATE=$1
if [ -z "$TARGET_DATE" ]; then
    TARGET_DATE=$(date -d "yesterday" +%Y-%m-%d)
fi

$SPARK_HOME/bin/spark-submit \
  --master yarn \
  --deploy-mode cluster \
  --class com.predict.DailyStatsJob \
  --name "DailyStatsJob_${TARGET_DATE}" \
  --driver-memory 1g \
  --executor-memory 2g \
  --executor-cores 2 \
  --num-executors 2 \
  --conf "spark.sql.adaptive.enabled=true" \
  --conf "spark.sql.adaptive.coalescePartitions.enabled=true" \
  --conf "spark.dynamicAllocation.enabled=false" \
  $JAR_PATH $TARGET_DATE \
  >> $LOG_DIR/daily_stats_${TARGET_DATE}.log 2>&1
