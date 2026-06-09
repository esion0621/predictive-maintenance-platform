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
    val featureCols = Array("avg_temp", "max_vibration", "current_variance", "pressure_change_rate")

    val (mean, std) = calculateMeanStd(df, featureCols)

    val numClasses = model.numClasses
    val treesData = model.trees.map { tree =>
      flattenClassificationTree(tree.rootNode, numClasses)
    }

    val modelData = Map(
      "model_type" -> "random_forest",
      "version" -> version,
      "n_features" -> featureCols.length,
      "scaler" -> Map("mean" -> mean, "scale" -> std),
      "trees" -> treesData
    )

    val jsonString = Serialization.write(modelData)
    val outputPath = s"$outputBaseDir/anomaly/json/model_$version.json"
    writeToHdfs(outputPath, jsonString)
    outputPath
  }

  def exportGbtToJson(model: GBTRegressionModel, df: DataFrame, version: String, outputBaseDir: String): String = {
    val featureCols = Array("avg_temp", "max_vibration", "current_variance", "pressure_change_rate")

    val (mean, std) = calculateMeanStd(df, featureCols)

    val treesData = model.trees.map { tree =>
      flattenRegressionTree(tree.rootNode)
    }

    val modelData = Map(
      "model_type" -> "gbt_regression",
      "version" -> version,
      "n_features" -> featureCols.length,
      "scaler" -> Map("mean" -> mean, "scale" -> std),
      "trees" -> treesData
    )

    val jsonString = Serialization.write(modelData)
    val outputPath = s"$outputBaseDir/rul/json/model_$version.json"
    writeToHdfs(outputPath, jsonString)
    outputPath
  }

  private def calculateMeanStd(df: DataFrame, featureCols: Array[String]): (Array[Double], Array[Double]) = {
    val stats = df.select(featureCols.map(c => org.apache.spark.sql.functions.stddev_pop(c).alias(s"${c}_std")): _*)
      .crossJoin(df.select(featureCols.map(c => org.apache.spark.sql.functions.avg(c).alias(s"${c}_mean")): _*))
      .first()

    val mean = featureCols.map(c => stats.getDouble(stats.fieldIndex(s"${c}_mean")))
    val std = featureCols.map(c => stats.getDouble(stats.fieldIndex(s"${c}_std")))
    (mean, std)
  }

  private def flattenClassificationTree(rootNode: Node, numClasses: Int): Map[String, Any] = {
    val features      = scala.collection.mutable.ArrayBuffer[Int]()
    val thresholds    = scala.collection.mutable.ArrayBuffer[Double]()
    val leftChildren  = scala.collection.mutable.ArrayBuffer[Int]()
    val rightChildren = scala.collection.mutable.ArrayBuffer[Int]()
    val values        = scala.collection.mutable.ArrayBuffer[Array[Int]]()

    def dfs(node: Node): Int = {
      val idx = features.size
      node match {
        case leaf: LeafNode =>
          features.append(-2)
          thresholds.append(-2.0)
          leftChildren.append(-1)
          rightChildren.append(-1)
          val predictedClass = leaf.prediction.toInt
          val counts = Array.fill(numClasses)(0)
          counts(predictedClass) = 10
          values.append(counts)
        case internal: InternalNode =>
          // 占位，等子节点遍历完后再回填
          features.append(0)
          thresholds.append(0.0)
          leftChildren.append(-1)
          rightChildren.append(-1)
          values.append(Array.fill(numClasses)(0))

          val leftIdx  = dfs(internal.leftChild)
          val rightIdx = dfs(internal.rightChild)

          features(idx) = internal.split.featureIndex
          val thresholdVal = internal.split match {
            case s: ContinuousSplit => s.threshold
            case _ => 0.0
          }
          thresholds(idx) = thresholdVal
          leftChildren(idx) = leftIdx
          rightChildren(idx) = rightIdx
      }
      idx
    }

    dfs(rootNode)

    Map(
      "feature"        -> features.toArray,
      "threshold"      -> thresholds.toArray,
      "children_left"  -> leftChildren.toArray,
      "children_right" -> rightChildren.toArray,
      "value"          -> values.toArray
    )
  }

  private def flattenRegressionTree(rootNode: Node): Map[String, Any] = {
    val features      = scala.collection.mutable.ArrayBuffer[Int]()
    val thresholds    = scala.collection.mutable.ArrayBuffer[Double]()
    val leftChildren  = scala.collection.mutable.ArrayBuffer[Int]()
    val rightChildren = scala.collection.mutable.ArrayBuffer[Int]()
    val values        = scala.collection.mutable.ArrayBuffer[Array[Double]]()

    def dfs(node: Node): Int = {
      val idx = features.size
      node match {
        case leaf: LeafNode =>
          features.append(-2)
          thresholds.append(-2.0)
          leftChildren.append(-1)
          rightChildren.append(-1)
          values.append(Array(leaf.prediction))
        case internal: InternalNode =>
          features.append(0)
          thresholds.append(0.0)
          leftChildren.append(-1)
          rightChildren.append(-1)
          values.append(Array(0.0))

          val leftIdx  = dfs(internal.leftChild)
          val rightIdx = dfs(internal.rightChild)

          features(idx) = internal.split.featureIndex
          val thresholdVal = internal.split match {
            case s: ContinuousSplit => s.threshold
            case _ => 0.0
          }
          thresholds(idx) = thresholdVal
          leftChildren(idx) = leftIdx
          rightChildren(idx) = rightIdx
      }
      idx
    }

    dfs(rootNode)

    Map(
      "feature"        -> features.toArray,
      "threshold"      -> thresholds.toArray,
      "children_left"  -> leftChildren.toArray,
      "children_right" -> rightChildren.toArray,
      "value"          -> values.toArray
    )
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

