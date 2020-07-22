name := "docile-examples"

version := "0.1"

scalaVersion := "2.12.8"

resolvers += "TNM" at "https://nexus.thenewmotion.com/content/groups/public"

libraryDependencies += "com.newmotion" %% "docile-charge-point" % "0.5.1"
libraryDependencies += "com.typesafe.scala-logging"  %% "scala-logging"    % "3.9.0"
libraryDependencies +=  "ch.qos.logback"               % "logback-classic"  % "1.2.3"
