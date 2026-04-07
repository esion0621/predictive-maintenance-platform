package com.predict

import java.sql.{Connection, DriverManager, PreparedStatement}
import java.time.LocalDate
import java.util.Properties

import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}
import org.apache.spark.sql.functions._
import com.predict.utils.HdfsUtils
import org.slf4j.LoggerFactory

object DailyStatsJob {

  private val logger = LoggerFactory.getLogger(this.getClass)

  val HDFS_DATA_BASE = "hdfs://master:9000/data/history/sensor"
  val MYSQL_URL = "jdbc:mysql://master:3306/predictive_maintenance?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8"
  val MYSQL_USER = "root"
  val MYSQL_PASSWORD = "060201"

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("DeviceDailyStatsJob")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
      .config("spark.sql.files.ignoreMissingFiles", "true")
      .config("spark.sql.files.ignoreCorruptFiles", "true")
      .getOrCreate()

    try {
      val targetDate = if (args.length > 0) LocalDate.parse(args(0)) else LocalDate.now().minusDays(1)
      val targetDateStr = targetDate.toString
      logger.info(s"Processing date: $targetDateStr")

      val hdfsPath = s"$HDFS_DATA_BASE/dt=$targetDateStr"
      logger.info(s"Reading HDFS path: $hdfsPath")

      if (!HdfsUtils.exists(hdfsPath)) {
        logger.warn(s"Directory $hdfsPath does not exist. No data to process. Exiting gracefully.")
        return
      }

      // 直接读取目录下所有文件（不指定扩展名过滤）
      val rawDF = spark.read.parquet(hdfsPath)

      if (rawDF.isEmpty) {
        logger.warn(s"No data found for date $targetDateStr. Exiting.")
        return
      }

      val df = rawDF
        .withColumnRenamed("deviceId", "device_id")
        .withColumnRenamed("temperature", "temperature")
        .withColumnRenamed("vibration", "vibration")
        .withColumnRenamed("current", "current")
        .withColumnRenamed("pressure", "pressure")

      val dailyStatsDF = df
        .groupBy("device_id")
        .agg(
          avg("temperature").alias("avg_temp"),
          max("vibration").alias("max_vibration"),
          avg("current").alias("avg_current"),
          avg("pressure").alias("avg_pressure")
        )
        .withColumn("stat_date", lit(targetDateStr))

      dailyStatsDF.show(20, truncate = false)
      logger.info(s"Calculated daily stats for ${dailyStatsDF.count()} devices.")

      deleteExistingData(targetDateStr)
      writeToMySQL(dailyStatsDF, targetDateStr)

      logger.info(s"Daily stats job completed successfully for date $targetDateStr.")

    } catch {
      case e: Exception =>
        logger.error("Daily stats job failed", e)
        sys.exit(1)
    } finally {
      spark.stop()
    }
  }

  def deleteExistingData(statDate: String): Unit = {
    var connection: Connection = null
    var pstmt: PreparedStatement = null
    try {
      Class.forName("com.mysql.cj.jdbc.Driver")
      connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)
      val deleteSql = "DELETE FROM device_daily_stats WHERE stat_date = ?"
      pstmt = connection.prepareStatement(deleteSql)
      pstmt.setString(1, statDate)
      val deletedCount = pstmt.executeUpdate()
      logger.info(s"Deleted $deletedCount old records for date $statDate.")
    } catch {
      case e: Exception =>
        logger.error(s"Failed to delete old data for date $statDate", e)
        throw e
    } finally {
      if (pstmt != null) pstmt.close()
      if (connection != null) connection.close()
    }
  }

  def writeToMySQL(df: DataFrame, statDate: String): Unit = {
    val props = new Properties()
    props.setProperty("user", MYSQL_USER)
    props.setProperty("password", MYSQL_PASSWORD)
    props.setProperty("driver", "com.mysql.cj.jdbc.Driver")
    df.write.mode(SaveMode.Append).jdbc(MYSQL_URL, "device_daily_stats", props)
    logger.info(s"Successfully wrote ${df.count()} records to device_daily_stats table.")
  }
}
