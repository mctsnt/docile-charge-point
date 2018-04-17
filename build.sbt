
scalacOptions ++= Seq("-Xlint:-nullary-unit")

enablePlugins(OssLibPlugin)

lazy val commonSettings = Seq(
  organization := "com.newmotion",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := tnm.ScalaVersion.prev,
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")
)

lazy val root = (project in file(".")).
  settings(
    commonSettings,
    name := "docile-charge-point",
    mainClass := Some("chargepoint.docile.Main"),
    assemblyJarName in assembly := "docile.jar",
    connectInput in run := true,
    libraryDependencies ++= deps(scalaVersion.value)
  )

lazy val lambda = (project in file("aws-lambda")).
  dependsOn(root).
  settings(
    commonSettings,
    name := "lambda-docile-charge-point",
    retrieveManaged := true,
    libraryDependencies ++= deps(scalaVersion.value),
    mainClass := Some("chargepoint.docile.Lambda"),
    assemblyJarName in assembly := "docile-lambda.jar"
  )

assemblyJarName in assembly := "docile.jar"

connectInput in run := true

def deps(scalaVersion: String) = Seq(
  "com.lihaoyi"                  % "ammonite"         % "1.1.2"    cross CrossVersion.full,
  "com.thenewmotion.ocpp"       %% "ocpp-j-api"       % "9.1.0",
  "org.rogach"                  %% "scallop"          % "3.1.3",
  "org.scala-lang"               % "scala-compiler"   % scalaVersion,

  "com.typesafe.scala-logging"  %% "scala-logging"    % "3.9.0",
  "org.slf4j"                    % "slf4j-api"        % "1.7.25",
  "ch.qos.logback"               % "logback-classic"  % "1.2.3",

  "org.specs2"                  %% "specs2-core"      % "4.3.4"    % "test"
)

