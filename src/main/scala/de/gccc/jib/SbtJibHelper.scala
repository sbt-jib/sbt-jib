package de.gccc.jib

import java.io.File
import com.google.cloud.tools.jib.api.buildplan.{ AbsoluteUnixPath, FileEntriesLayer }
import com.google.cloud.tools.jib.api.JibContainer

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

private[jib] object SbtJibHelper {

  def mappingsConverter(name: String, mappings: Seq[(File, String)]): FileEntriesLayer = {
    val layerBuilder = FileEntriesLayer.builder()

    mappings
      .filter(_._1.isFile) // fixme resolve all directory files
      .map { case (file, fullPathOnImage) => (file.toPath, fullPathOnImage) }
      .toList
      .sortBy(_._2)
      .foreach { case (sourceFile, pathOnImage) =>
        layerBuilder.addEntry(sourceFile, AbsoluteUnixPath.get(pathOnImage))
      }

    layerBuilder.build()
  }

}
