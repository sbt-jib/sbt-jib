package de.gccc.jib

import java.io.File

import com.google.cloud.tools.jib.configuration.LayerConfiguration
import com.google.cloud.tools.jib.filesystem.AbsoluteUnixPath

private[jib] object SbtJibHelper {

  def mappingsConverter(name: String, mappings: Seq[(File, String)]): LayerConfiguration = {
    val layerConfiguration = LayerConfiguration.builder()

    mappings
      .filter(_._1.isFile) // fixme resolve all directory files
      .map { case (file, fullPathOnImage) => (file.toPath, fullPathOnImage) }
      .toList
      .sortBy(_._2)
      .foreach {
        case (sourceFile, pathOnImage) =>
          layerConfiguration.addEntry(sourceFile, AbsoluteUnixPath.get(pathOnImage))
      }

    layerConfiguration.build()
  }

}
