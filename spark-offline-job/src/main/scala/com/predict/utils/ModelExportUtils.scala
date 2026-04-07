package com.predict.utils

import org.apache.spark.ml.classification.RandomForestClassificationModel
import org.apache.spark.ml.regression.GBTRegressionModel
import org.apache.spark.ml.tree.{ContinuousSplit, InternalNode, LeafNode, Node}
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.json4s._
import org.json4s.jackson.Serialization
import org.slf4j.LoggerFactory

object ModelExportUtils {

  private val logger = LoggerFactory.getLogger(this.getClass)
  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  def exportRandomForestToJson(model: RandomForestClassificationModel, df: DataFrame, version: String, outputBaseDir: String): String = {
    val featureCols = Array("avg_temp", "max_vibration", "avg_current", "avg_pressure",
      "temp_squared", "vib_squared", "temp_vib_interaction", "hour_of_day", "day_of_week")

    val (mean, std) = calculateMeanStd(df, featureCols)

    val treesData = model.trees.map { tree =>
      extractNodeJson(tree.rootNode)
    }

    val modelData = Map(
      "model_type" -> "random_forest",
      "version" -> version,
      "n_features" -> featureCols.length,
      "mean" -> mean,
      "std" -> std,
      "trees" -> treesData
    )

    val jsonString = Serialization.write(modelData)
    val outputPath = s"$outputBaseDir/anomaly/json/model_$version.json"
    writeToHdfs(outputPath, jsonString)
    outputPath
  }

  def exportGbtToJson(model: GBTRegressionModel, df: DataFrame, version: String, outputBaseDir: String): String = {
    val featureCols = Array("avg_temp", "max_vibration", "avg_current", "avg_pressure",
      "temp_squared", "vib_squared", "temp_vib_interaction", "hour_of_day", "day_of_week")

    val (mean, std) = calculateMeanStd(df, featureCols)

    val treesData = model.trees.map { tree =>
      extractNodeJson(tree.rootNode)
    }

    val modelData = Map(
      "model_type" -> "gbt_regression",
      "version" -> version,
      "n_features" -> featureCols.length,
      "mean" -> mean,
      "std" -> std,
      "trees" -> treesData
    )

    val jsonString = Serialization.write(modelData)
    val outputPath = s"$outputBaseDir/rul/json/model_$version.json"
    writeToHdfs(outputPath, jsonString)
    outputPath
  }

  private def calculateMeanStd(df: DataFrame, featureCols: Array[String]): (Array[Double], Array[Double]) = {
    val stats = df.select(featureCols.map(c => org.apache.spark.sql.functions.stddev(c).alias(s"${c}_std")): _*)
      .crossJoin(df.select(featureCols.map(c => org.apache.spark.sql.functions.avg(c).alias(s"${c}_mean")): _*))
      .first()

    val mean = featureCols.map(c => stats.getDouble(stats.fieldIndex(s"${c}_mean")))
    val std = featureCols.map(c => stats.getDouble(stats.fieldIndex(s"${c}_std")))
    (mean, std)
  }

  private def extractNodeJson(node: Node): Map[String, Any] = {
    node match {
      case leaf: LeafNode =>
        Map(
          "type" -> "leaf",
          "value" -> leaf.prediction
        )
      case internal: InternalNode =>
        // 安全获取 threshold，处理不同类型 Split 的差异
        val thresholdValue = internal.split match {
          case s: ContinuousSplit => s.threshold
          case _ => 0.0
        }
        Map(
          "type" -> "internal",
          "feature" -> internal.split.featureIndex,
          "threshold" -> thresholdValue,
          "left" -> extractNodeJson(internal.leftChild),
          "right" -> extractNodeJson(internal.rightChild)
        )
    }
  }

  private def writeToHdfs(path: String, content: String): Unit = {
    val fs = HdfsUtils.getFileSystem
    val outputPath = new org.apache.hadoop.fs.Path(path)
    val outputStream = fs.create(outputPath, true)
    outputStream.write(content.getBytes("UTF-8"))
    outputStream.close()
    logger.info(s"JSON model written to $path")
  }
}
