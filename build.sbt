name := """reactive-twitter-stream-withslick"""

version := "1.0"

resolvers += "nightlies" at "https://scala-ci.typesafe.com/artifactory/scala-release-temp/"

//scalaVersion := "2.11.8"
scalaVersion := "2.11.9-264cc5f-nightly"

scalaBinaryVersion := "2.11" // or "2.11"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.4.14"
  val sprayV = "2.4.11"
  val slickV = "3.1.1"
  val akkaHttpV = "10.0.0"
  Seq(
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.typesafe.akka" %% "akka-stream-kafka" % "0.11-M3",
    "com.sksamuel.avro4s" %% "avro4s-core" % "1.4.3",
    "com.hunorkovacs" %% "koauth" % "1.1.0",
    "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.6.2",
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    "com.typesafe" % "config" % "1.2.1",
    "com.h2database" % "h2" % "1.3.175",
    "org.postgresql" % "postgresql" % "9.3-1100-jdbc41",
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % sprayV,
    "org.json4s" %% "json4s-native" % "3.3.0",
    "junit" % "junit" % "4.11" % "test",
    "commons-daemon" % "commons-daemon" % "1.0.15",
  
    "io.atlassian.aws-scala" %% "aws-scala-core"  % "7.0.2",
    "io.atlassian.aws-scala" %% "aws-scala-s3"  % "7.0.2",
    "io.atlassian.aws-scala" %% "aws-scala-sqs"  % "7.0.2",
    "io.atlassian.aws-scala" %% "aws-scala-core"  % "7.0.2"  % "test" classifier "tests",
    "io.atlassian.aws-scala" %% "aws-scala-s3"  % "7.0.2"  % "test" classifier "tests",
    "io.atlassian.aws-scala" %% "aws-scala-sqs"  % "7.0.2"  % "test" classifier "tests",
  
    "ch.megard" %% "akka-http-cors" % "0.1.10",
    
    "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    //    "org.slf4j" % "slf4j-nop" % "1.6.4",
    "org.specs2" %% "specs2-core" % "2.3.11" % "test",
    "org.specs2" %% "specs2-mock" % "2.3.11",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "org.scalactic" % "scalactic_2.11" % "3.0.1",
    "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  )
}
fork in run := true