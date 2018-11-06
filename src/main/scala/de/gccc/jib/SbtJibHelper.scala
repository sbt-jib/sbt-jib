package de.gccc.jib

import java.io.File

import com.google.cloud.tools.jib.configuration.LayerConfiguration
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath
import com.google.common.collect.ImmutableList

import scala.collection.JavaConverters._

private[jib] object SbtJibHelper {

  def mappingsConverter(mappings: Seq[(File, String)]): LayerConfiguration = {
    val layerConfiguration = LayerConfiguration.builder()

    mappings.map {
      case (file, fullPathOnImage) =>
        if (file.isFile) {
          val fileName = file.getName
          val index    = fullPathOnImage.indexOf(fileName)
          val pathOnImage = {
            if (index != -1) fullPathOnImage.substring(0, index).stripSuffix("/")
            else fullPathOnImage
          }
          (file.toPath, pathOnImage)
        } else {
          (file.toPath, fullPathOnImage)
        }
    }.groupBy(_._2)
      .map {
        case (pathOnImage, sourceFileWithPath) =>
          val files = sourceFileWithPath.map(_._1).asJava
          (pathOnImage, ImmutableList.sortedCopyOf(files))
      }
      .toList
      .sortBy(_._1)
      .foreach {
        case (pathOnImage, sourceFiles) =>
          sourceFiles.forEach { path =>
            layerConfiguration.addEntryRecursive(path, AbsoluteUnixPath.get(pathOnImage))
          }
      }

    layerConfiguration.build()
  }

}
