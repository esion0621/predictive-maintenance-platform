#!/bin/bash

# ==================== 配置 ====================
# MySQL 连接信息
MYSQL_HOST="master"
MYSQL_USER="root"
MYSQL_PASSWORD="060201"
MYSQL_DB="predictive_maintenance"

# HDFS 原始数据路径
HDFS_DATA_BASE="/data/history/sensor"

# 保留天数（30天前及更早的数据将被删除）
RETENTION_DAYS=30

# 日志目录
LOG_DIR="/home/hadoop/logs/cleanup"
mkdir -p $LOG_DIR
LOG_FILE="$LOG_DIR/cleanup_$(date +%Y%m%d_%H%M%S).log"

# 是否启用安全模式（设置为 false 才会实际执行删除）
DRY_RUN=false  

# ==================== 函数 ====================
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a $LOG_FILE
}

# 计算30天前的日期（格式：yyyy-mm-dd）
CLEANUP_DATE=$(date -d "$RETENTION_DAYS days ago" +%Y-%m-%d)
log "清理日期阈值: $CLEANUP_DATE (删除此日期及之前的数据)"

# ==================== 1. 清理 MySQL 告警表 ====================
log "开始清理 MySQL 告警表 (alarm_event)..."
SQL_DELETE_ALARM="DELETE FROM alarm_event WHERE DATE(alarm_time) <= '$CLEANUP_DATE';"

if [ "$DRY_RUN" = true ]; then
    log "[试运行] 将执行 SQL: $SQL_DELETE_ALARM"
    COUNT_SQL="SELECT COUNT(*) FROM alarm_event WHERE DATE(alarm_time) <= '$CLEANUP_DATE';"
    count=$(mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DB -sN -e "$COUNT_SQL")
    log "[试运行] 预计删除 $count 条告警记录"
else
    mysql -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DB -e "$SQL_DELETE_ALARM"
    log "已执行告警表清理"
fi

# ==================== 2. 清理 HDFS 原始数据 ====================
log "开始清理 HDFS 原始数据 (路径: $HDFS_DATA_BASE)"

# 列出所有日期分区目录（格式：dt=yyyy-mm-dd）
partitions=$(hdfs dfs -ls $HDFS_DATA_BASE 2>/dev/null | grep 'dt=' | awk '{print $8}')

if [ -z "$partitions" ]; then
    log "未找到任何分区目录，跳过 HDFS 清理"
else
    for part in $partitions; do
        # 提取日期部分（dt=2026-03-01 -> 2026-03-01）
        part_date=$(echo $part | sed 's/.*dt=//')
        if [[ "$part_date" < "$CLEANUP_DATE" ]] || [[ "$part_date" == "$CLEANUP_DATE" ]]; then
            if [ "$DRY_RUN" = true ]; then
                log "[试运行] 将删除 HDFS 分区: $part"
            else
                hdfs dfs -rm -r -skipTrash $part
                log "已删除 HDFS 分区: $part"
            fi
        fi
    done
fi

log "清理任务完成（试运行=$DRY_RUN）"
