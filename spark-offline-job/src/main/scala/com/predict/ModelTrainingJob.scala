package com.predict

import java.sql.{Connection, DriverManager, PreparedStatement}
import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.apache.spark.sql.functions.{avg, col, current_date, datediff, lit, max, when}
import org.apache.spark.ml.classification.{RandomForestClassificationModel, RandomForestClassifier}
import org.apache.spark.ml.evaluation.{MulticlassClassificationEvaluator, RegressionEvaluator}
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.{GBTRegressionModel, GBTRegressor}
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import com.predict.utils.{HdfsUtils, ModelExportUtils}
import org.slf4j.LoggerFactory

object ModelTrainingJob {

  private val logger = LoggerFactory.getLogger(this.getClass)

  val HDFS_DATA_BASE = "hdfs://master:9000/data/history/sensor"
  val HDFS_MODEL_BASE = "hdfs://master:9000/models"
  val MYSQL_URL = "jdbc:mysql://master:3306/predictive_maintenance?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf-8"
  val MYSQL_USER = "root"
  val MYSQL_PASSWORD = "060201"

  val featureCols = Array(
    "avg_temp", "max_vibration", "avg_current", "avg_pressure",
    "temp_squared", "vib_squared", "temp_vib_interaction", "hour_of_day", "day_of_week"
  )

  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("ModelTrainingJob")
      .config("spark.sql.adaptive.enabled", "true")
      .config("spark.sql.adaptive.coalescePartitions.enabled", "true")
      .config("spark.sql.files.ignoreMissingFiles", "true")
      .config("spark.sql.files.ignoreCorruptFiles", "true")
      .getOrCreate()

    try {
      val endDate = LocalDate.now().minusDays(1)
      val startDate = endDate.minusDays(6)
      logger.info(s"Training data range: $startDate to $endDate")

      val df = readHistoricalData(spark, startDate, endDate)
      if (df.isEmpty) {
        logger.error("No historical data found. Exiting.")
        sys.exit(1)
      }
      logger.info(s"Loaded ${df.count()} records.")

      val featureDF = engineerFeatures(df)
      featureDF.cache()
      logger.info("Feature engineering completed.")

      val labeledDF = buildLabels(featureDF)(spark)
      labeledDF.cache()
      logger.info("Label and RUL construction completed.")

      // 训练异常检测模型
      logger.info("Training anomaly detection model (Random Forest)...")
      val (anomalyModel, anomalyMetrics) = trainAnomalyDetectionModel(labeledDF)

      // 训练RUL模型
      logger.info("Training RUL prediction model (GBDT)...")
      val (rulModel, rulMetrics) = trainRulModel(labeledDF)

      // 获取上一版本指标
      val prevAnomalyMetrics = getPreviousModelMetrics("anomaly")
      val prevRulMetrics = getPreviousModelMetrics("rul")

      val anomalyImproved = shouldUpdateModel(anomalyMetrics.accuracy, prevAnomalyMetrics.accuracy, isLowerBetter = false)
      val rulImproved = shouldUpdateModel(rulMetrics.rmse, prevRulMetrics.rmse, isLowerBetter = true)

      if (!anomalyImproved && !rulImproved) {
        logger.warn("No improvement over previous models. Skipping model update.")
        sys.exit(0)
      }

      val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

      if (anomalyImproved) {
        val anomalyJsonPath = ModelExportUtils.exportRandomForestToJson(anomalyModel, labeledDF, timestamp, HDFS_MODEL_BASE)
        updateModelMetadata("anomaly", timestamp, anomalyJsonPath, anomalyMetrics, spark)
        updateModelIndex("anomaly", anomalyJsonPath)
        logger.info(s"Anomaly model exported (accuracy=${anomalyMetrics.accuracy})")
      }

      if (rulImproved) {
        val rulJsonPath = ModelExportUtils.exportGbtToJson(rulModel, labeledDF, timestamp, HDFS_MODEL_BASE)
        updateModelMetadata("rul", timestamp, rulJsonPath, rulMetrics, spark)
        updateModelIndex("rul", rulJsonPath)
        logger.info(s"RUL model exported (rmse=${rulMetrics.rmse})")
      }

      logger.info("Model training job completed successfully.")
    } catch {
      case e: Exception =>
        logger.error("Model training job failed", e)
        sys.exit(1)
    } finally {
      spark.stop()
    }
  }

  def readHistoricalData(spark: SparkSession, startDate: LocalDate, endDate: LocalDate): DataFrame = {
    val days = ChronoUnit.DAYS.between(startDate, endDate).toInt + 1
    val paths = (0 until days).map(i => s"$HDFS_DATA_BASE/dt=${startDate.plusDays(i)}").toArray
    spark.read.parquet(paths: _*)
  }

  def engineerFeatures(df: DataFrame): DataFrame = {
    val dailyStats = df.groupBy("deviceId")
      .agg(
        avg("temperature").alias("avg_temp"),
        max("vibration").alias("max_vibration"),
        avg("current").alias("avg_current"),
        avg("pressure").alias("avg_pressure")
      )
    dailyStats
      .withColumn("temp_squared", col("avg_temp") * col("avg_temp"))
      .withColumn("vib_squared", col("max_vibration") * col("max_vibration"))
      .withColumn("temp_vib_interaction", col("avg_temp") * col("max_vibration"))
      .withColumn("hour_of_day", lit(0))
      .withColumn("day_of_week", lit(0))
  }

  def buildLabels(df: DataFrame)(implicit spark: SparkSession): DataFrame = {
    val deviceInfoDF = spark.read
      .format("jdbc")
      .option("url", MYSQL_URL)
      .option("dbtable", "device_info")
      .option("user", MYSQL_USER)
      .option("password", MYSQL_PASSWORD)
      .load()
      .select(col("device_id").alias("deviceId"), col("install_date"))

    val withDeviceInfo = df.join(deviceInfoDF, Seq("deviceId"), "left_outer")

    val withAge = withDeviceInfo
      .withColumn("days_in_service", datediff(current_date(), col("install_date")))
      .withColumn("aging_factor", when(col("days_in_service") > 365, 1.0)
        .otherwise(col("days_in_service") / 365))

    val withLabel = withAge
      .withColumn("label",
        when(col("aging_factor") > 0.7 &&
          (col("avg_temp") > 70 || col("max_vibration") > 1.5 ||
            col("avg_current") > 2.5 || abs(col("avg_pressure") - 100) > 10), 1)
          .otherwise(0))

    val withRul = withLabel
      .withColumn("rul", when(col("days_in_service") > 365, 0)
        .otherwise(lit(365) - col("days_in_service")))

    withRul.select(
      col("deviceId"),
      col("avg_temp"), col("max_vibration"), col("avg_current"), col("avg_pressure"),
      col("temp_squared"), col("vib_squared"), col("temp_vib_interaction"),
      col("hour_of_day"), col("day_of_week"),
      col("label"), col("rul")
    )
  }

  def trainAnomalyDetectionModel(df: DataFrame): (RandomForestClassificationModel, ModelMetrics) = {
    val assembler = new VectorAssembler()
      .setInputCols(featureCols)
      .setOutputCol("features")

    val classifier = new RandomForestClassifier()
      .setLabelCol("label")
      .setFeaturesCol("features")
      .setNumTrees(50)
      .setMaxDepth(10)
      .setSeed(42)

    val pipeline = new Pipeline().setStages(Array(assembler, classifier))
    val Array(trainData, testData) = df.randomSplit(Array(0.8, 0.2), seed = 42)

    val model = pipeline.fit(trainData)
    val predictions = model.transform(testData)

    val evaluator = new MulticlassClassificationEvaluator()
      .setLabelCol("label")
      .setPredictionCol("prediction")
    val accuracy = evaluator.setMetricName("accuracy").evaluate(predictions)
    val f1 = evaluator.setMetricName("f1").evaluate(predictions)

    val rfModel = model.stages(1).asInstanceOf[RandomForestClassificationModel]
    (rfModel, ModelMetrics(rmse = 0.0, accuracy = accuracy, f1Score = f1))
  }

  def trainRulModel(df: DataFrame): (GBTRegressionModel, ModelMetrics) = {
    val assembler = new VectorAssembler()
      .setInputCols(featureCols)
      .setOutputCol("features")

    val gbt = new GBTRegressor()
      .setLabelCol("rul")
      .setFeaturesCol("features")
      .setMaxIter(50)
      .setMaxDepth(10)
      .setSeed(42)

    val pipeline = new Pipeline().setStages(Array(assembler, gbt))
    val Array(trainData, testData) = df.randomSplit(Array(0.8, 0.2), seed = 42)

    val model = pipeline.fit(trainData)
    val predictions = model.transform(testData)

    val evaluator = new RegressionEvaluator()
      .setLabelCol("rul")
      .setPredictionCol("prediction")
      .setMetricName("rmse")
    val rmse = evaluator.evaluate(predictions)

    val gbtModel = model.stages(1).asInstanceOf[GBTRegressionModel]
    (gbtModel, ModelMetrics(rmse = rmse, accuracy = 0.0, f1Score = 0.0))
  }

  def getPreviousModelMetrics(modelType: String): ModelMetrics = {
    var connection: Connection = null
    var pstmt: PreparedStatement = null
    var rs: java.sql.ResultSet = null
    try {
      Class.forName("com.mysql.cj.jdbc.Driver")
      connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)
      val sql = """
        SELECT m.accuracy, m.f1_score, m.rmse
        FROM model_metrics m
        JOIN model_version v ON m.model_id = v.model_id
        WHERE v.model_type = ? AND v.is_active = 0
        ORDER BY v.create_time DESC
        LIMIT 1
      """
      pstmt = connection.prepareStatement(sql)
      pstmt.setString(1, modelType)
      rs = pstmt.executeQuery()
      if (rs.next()) {
        ModelMetrics(
          rmse = rs.getDouble("rmse"),
          accuracy = rs.getDouble("accuracy"),
          f1Score = rs.getDouble("f1_score")
        )
      } else {
        ModelMetrics(rmse = Double.MaxValue, accuracy = 0.0, f1Score = 0.0)
      }
    } catch {
      case e: Exception =>
        logger.warn(s"Failed to get previous metrics for $modelType", e)
        ModelMetrics(rmse = Double.MaxValue, accuracy = 0.0, f1Score = 0.0)
    } finally {
      if (rs != null) rs.close()
      if (pstmt != null) pstmt.close()
      if (connection != null) connection.close()
    }
  }

  def shouldUpdateModel(current: Double, previous: Double, isLowerBetter: Boolean): Boolean = {
    if (isLowerBetter) {
      current < previous * 0.95
    } else {
      current > previous * 1.05
    }
  }

  def updateModelMetadata(modelType: String, version: String, modelPath: String, metrics: ModelMetrics, spark: SparkSession): Unit = {
    var connection: Connection = null
    var pstmt: PreparedStatement = null
    var metricsPstmt: PreparedStatement = null
    try {
      Class.forName("com.mysql.cj.jdbc.Driver")
      connection = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)

      val deactivateSql = "UPDATE model_version SET is_active = 0 WHERE model_type = ?"
      pstmt = connection.prepareStatement(deactivateSql)
      pstmt.setString(1, modelType)
      pstmt.executeUpdate()
      pstmt.close()

      val insertSql = "INSERT INTO model_version (model_type, version, hdfs_path, create_time, is_active) VALUES (?, ?, ?, ?, 1)"
      pstmt = connection.prepareStatement(insertSql, java.sql.Statement.RETURN_GENERATED_KEYS)
      pstmt.setString(1, modelType)
      pstmt.setString(2, version)
      pstmt.setString(3, modelPath)
      pstmt.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now()))
      pstmt.executeUpdate()

      val rs = pstmt.getGeneratedKeys
      var modelId = -1
      if (rs.next()) modelId = rs.getInt(1)
      pstmt.close()

      val metricsSql = "INSERT INTO model_metrics (model_id, rmse, accuracy, f1_score, created_at) VALUES (?, ?, ?, ?, ?)"
      metricsPstmt = connection.prepareStatement(metricsSql)
      metricsPstmt.setInt(1, modelId)
      metricsPstmt.setDouble(2, metrics.rmse)
      metricsPstmt.setDouble(3, metrics.accuracy)
      metricsPstmt.setDouble(4, metrics.f1Score)
      metricsPstmt.setTimestamp(5, java.sql.Timestamp.valueOf(LocalDateTime.now()))
      metricsPstmt.executeUpdate()

      logger.info(s"Model metadata updated for $modelType, version: $version")
    } catch {
      case e: Exception =>
        logger.error("Failed to update model metadata", e)
        throw e
    } finally {
      if (pstmt != null) pstmt.close()
      if (metricsPstmt != null) metricsPstmt.close()
      if (connection != null) connection.close()
    }
  }

  def updateModelIndex(modelType: String, modelJsonPath: String): Unit = {
    val indexFile = s"$HDFS_MODEL_BASE/${modelType}_current_version.txt"
    val fs = HdfsUtils.getFileSystem
    val path = new org.apache.hadoop.fs.Path(indexFile)
    val outputStream = fs.create(path, true)
    outputStream.writeBytes(modelJsonPath)
    outputStream.close()
    logger.info(s"Updated model index: $indexFile -> $modelJsonPath")
  }

  case class ModelMetrics(rmse: Double, accuracy: Double, f1Score: Double)
}
