organization in ThisBuild := "com.auction"
version in ThisBuild := "1.0-SNAPSHOT"

// the Scala version that will be used for cross-compiled libraries
scalaVersion in ThisBuild := "2.13.0"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.3.3" % "provided"
val scalaTest = "org.scalatest" %% "scalatest" % "3.0.8" % Test

lazy val `bidding-service` = (project in file("."))
  .aggregate(`bidding-api`, `bidding-impl`)

lazy val `bidding-api` = (project in file("bidding-api"))
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslApi
    )
  )

lazy val `bidding-impl` = (project in file("bidding-impl"))
  .enablePlugins(LagomScala)
  .settings(
    libraryDependencies ++= Seq(
      lagomScaladslPersistenceCassandra,
      lagomScaladslKafkaBroker,
      lagomScaladslTestKit,
      macwire,
      scalaTest
    )
  )
  .settings(lagomForkedTestSettings)
  .dependsOn(`bidding-api`)


lagomKafkaEnabled in ThisBuild := false
lagomKafkaAddress in ThisBuild := "192.168.1.102:32770"
