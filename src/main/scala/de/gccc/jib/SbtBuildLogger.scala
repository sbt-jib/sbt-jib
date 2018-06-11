package de.gccc.jib

import com.google.cloud.tools.jib.builder.BuildLogger
import sbt.util.Logger

// FIXME: disable org.apache logs
class SbtBuildLogger(logger: Logger) extends BuildLogger {

  override def error(message: CharSequence): Unit = logger.err(message.toString)
  /**
   * Logs messages as part of normal execution (default log level).
   *
   * @param message the message to log
   */
  override def lifecycle(message: CharSequence): Unit = logger.out(message.toString)
  override def warn(message: CharSequence): Unit = logger.warn(message.toString)
  override def info(message: CharSequence): Unit = logger.info(message.toString)
  override def debug(message: CharSequence): Unit = logger.debug(message.toString)
}