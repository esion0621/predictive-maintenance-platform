package com.predict.utils

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.slf4j.LoggerFactory

object HdfsUtils {

  private val logger = LoggerFactory.getLogger(this.getClass)
  private val conf = new Configuration()
  conf.set("fs.defaultFS", "hdfs://master:9000")

  def getFileSystem: FileSystem = FileSystem.get(conf)

  def exists(pathStr: String): Boolean = {
    val fs = getFileSystem
    fs.exists(new Path(pathStr))
  }

  def deleteAndCreateDir(pathStr: String): Unit = {
    val fs = getFileSystem
    val path = new Path(pathStr)
    try {
      if (fs.exists(path)) {
        logger.info(s"Deleting existing path: $pathStr")
        fs.delete(path, true)
      }
      logger.info(s"Creating directory: $pathStr")
      fs.mkdirs(path)
    } catch {
      case e: Exception =>
        logger.error(s"Failed to delete/create directory: $pathStr", e)
        throw e
    }
  }
}
