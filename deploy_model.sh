#!/bin/bash
# 训练后清理HDFS旧数据 + 上传新模型 + 更新索引
# 用法: ./deploy_model.sh [model_dir]
# model_dir 默认 /home/hadoop/tmp

MODEL_DIR=${1:-/home/hadoop/tmp}
HDFS_MODEL_BASE="hdfs://master:9000/models"
HDFS_DATA_BASE="hdfs://master:9000/data/history/sensor"
KEEP_DAYS=7

echo "===== 1. 清理 HDFS 超过 ${KEEP_DAYS} 天的历史数据 ====="
CUTOFF_DATE=$(date -d "${KEEP_DAYS} days ago" +%Y-%m-%d)
echo "保留 ${CUTOFF_DATE} 之后的数据"

# 列出所有日期分区并删除过期数据
hdfs dfs -ls ${HDFS_DATA_BASE} | while read -r line; do
    dir_path=$(echo "$line" | awk '{print $NF}')
    dir_date=$(echo "$dir_path" | grep -oP '\d{4}-\d{2}-\d{2}')
    if [ -n "$dir_date" ] && [[ "$dir_date" < "$CUTOFF_DATE" ]]; then
        echo "删除过期数据: $dir_path"
        hdfs dfs -rm -r -skipTrash "$dir_path"
    fi
done
echo "HDFS 历史数据清理完成"

echo ""
echo "===== 2. 查找最新训练模型 ====="
LATEST_MODEL=$(ls -t ${MODEL_DIR}/anomaly_model_*.json 2>/dev/null | head -1)
if [ -z "$LATEST_MODEL" ]; then
    echo "错误: 未找到模型文件 ${MODEL_DIR}/anomaly_model_*.json"
    exit 1
fi
MODEL_FILENAME=$(basename "$LATEST_MODEL")
echo "最新模型: $LATEST_MODEL"

echo ""
echo "===== 3. 上传模型到 HDFS ====="
hdfs dfs -mkdir -p ${HDFS_MODEL_BASE}/anomaly
hdfs dfs -put -f "$LATEST_MODEL" ${HDFS_MODEL_BASE}/anomaly/
echo "模型已上传: ${HDFS_MODEL_BASE}/anomaly/${MODEL_FILENAME}"

echo ""
echo "===== 4. 更新模型索引文件 ====="
INDEX_PATH="${HDFS_MODEL_BASE}/anomaly_current_version.txt"
echo "${HDFS_MODEL_BASE}/anomaly/${MODEL_FILENAME}" | hdfs dfs -put -f - "$INDEX_PATH"
echo "索引已更新: $INDEX_PATH -> ${HDFS_MODEL_BASE}/anomaly/${MODEL_FILENAME}"

echo ""
echo "===== 5. 清理本地旧模型文件（保留最新3个） ====="
ls -t ${MODEL_DIR}/anomaly_model_*.json | tail -n +4 | xargs -r rm -f
ls -t ${MODEL_DIR}/anomaly_model_description_*.json | tail -n +4 | xargs -r rm -f
echo "本地旧文件清理完成"

echo ""
echo "===== 部署完成 ====="
echo "模型路径: ${HDFS_MODEL_BASE}/anomaly/${MODEL_FILENAME}"
echo "索引文件: $INDEX_PATH"

