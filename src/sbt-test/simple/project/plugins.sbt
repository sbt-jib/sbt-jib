addSbtPlugin("com.typesafe.sbt" % "sbt-twirl" % "1.3.13")


val pluginVersion = Option(System.getProperty("plugin.version")).getOrElse("0.0.9-SNAPSHOT")
if(pluginVersion == null)
  throw new RuntimeException("""|The system property 'plugin.version' is not defined.
                                |Specify this property using the scriptedLaunchOpts -D.""".stripMargin)
else addSbtPlugin("de.gccc.sbt" % "sbt-jib" % pluginVersion)
