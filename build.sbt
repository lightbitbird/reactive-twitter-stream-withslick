name := """reactive-twitter-stream-withslick"""

version := "1.0"

resolvers += "nightlies" at "https://scala-ci.typesafe.com/artifactory/scala-release-temp/"
resolvers += "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"

scalaVersion := "2.12.1"

scalaBinaryVersion := "2.12"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.5.3"
  val sprayV = "2.5.3"
  val slickV = "3.2.1"
  val akkaHttpV = "10.0.10"
  Seq(
    "com.typesafe.akka" %% "akka-http-core" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-jackson" % akkaHttpV,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpV,
    "com.typesafe.akka" %% "akka-actor" % akkaV,
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-stream" % akkaV,
    "com.hunorkovacs" %% "koauth" % "2.0.0",
    "com.typesafe.slick" %% "slick" % slickV,
    "com.typesafe.slick" %% "slick-hikaricp" % slickV,
    "com.typesafe" % "config" % "1.2.1",
    "com.h2database" % "h2" % "1.3.175",
    "org.postgresql" % "postgresql" % "9.3-1100-jdbc41",
    "com.typesafe.akka" %% "akka-http-spray-json" % sprayV,
    "org.json4s" %% "json4s-native" % "3.5.3",
    "junit" % "junit" % "4.11" % "test",
    "commons-daemon" % "commons-daemon" % "1.0.15",
  
    "io.atlassian.aws-scala" %% "aws-scala-core"  % "8.0.3",
    "io.atlassian.aws-scala" %% "aws-scala-s3"  % "8.0.3",
    "io.atlassian.aws-scala" %% "aws-scala-sqs"  % "8.0.3",
    "io.atlassian.aws-scala" %% "aws-scala-core"  % "8.0.3"  % "test" classifier "tests",
    "io.atlassian.aws-scala" %% "aws-scala-s3"  % "8.0.3"  % "test" classifier "tests",
    "io.atlassian.aws-scala" %% "aws-scala-sqs"  % "8.0.3"  % "test" classifier "tests",
  
    "ch.megard" %% "akka-http-cors" % "0.1.10",
    
    "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2",
    "ch.qos.logback" % "logback-classic" % "1.1.3",
    "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "org.scalactic" % "scalactic_2.12" % "3.0.4",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )
}
fork in run := true