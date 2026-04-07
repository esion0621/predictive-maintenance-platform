#!/bin/bash

export HADOOP_CONF_DIR=/home/hadoop/hadoop/etc/hadoop
export YARN_CONF_DIR=/home/hadoop/hadoop/etc/hadoop
export SPARK_HOME=/home/hadoop/spark

JAR_PATH="/home/hadoop/job/spark-offline-job/target/spark-offline-job-1.0.0.jar"
LOG_DIR="/home/hadoop/logs/spark-jobs"
mkdir -p $LOG_DIR

$SPARK_HOME/bin/spark-submit \
  --master yarn \
  --deploy-mode cluster \
  --class com.predict.ModelTrainingJob \
  --name "ModelTrainingJob_$(date +%Y%m%d)" \
  --driver-memory 2g \
  --executor-memory 4g \
  --executor-cores 2 \
  --num-executors 4 \
  --conf "spark.sql.adaptive.enabled=true" \
  --conf "spark.dynamicAllocation.enabled=false" \
  --conf "spark.memory.fraction=0.8" \
  $JAR_PATH \
  >> $LOG_DIR/model_training_$(date +%Y%m%d_%H%M%S).log 2>&1
