scalaVersion := "2.13.8"
version := "0.1"
name := "zio2playground"

val zioConfigVersion = "3.0.2"
val zioVersion = "2.0.0"
val zioLoggingVersion = "2.1.0"
//val zioLoggingVersion = "2.1.0+3-a733f542+20220908-1603-SNAPSHOT"

libraryDependencies ++= List(
  "dev.zio" %% "zio" % zioVersion,
  "io.d11" %% "zhttp" % "2.0.0-RC10",
  "org.scalaz" %% "scalaz-core" % "7.3.6",
  "ch.qos.logback" % "logback-classic" % "1.4.0",
  "org.slf4j" % "jul-to-slf4j" % "1.7.36",
  "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,
  "dev.zio" %% "zio-logging-slf4j-bridge" % zioLoggingVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "io.opentracing" % "opentracing-util" % "0.33.0",
  "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % "1.17.0",
  "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % "1.17.0-alpha",
  "io.opentelemetry" % "opentelemetry-exporter-zipkin" % "1.17.0",
  "io.opentelemetry" % "opentelemetry-exporter-jaeger" % "1.17.0",
  "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
  "io.opentelemetry" % "opentelemetry-sdk" % "1.17.0",
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio-config-refined" % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-opentelemetry" % "2.0.1",
  "dev.zio" %% "zio-opentracing" % "2.0.1",
  "org.flywaydb" % "flyway-core" % "9.1.4",
  "org.postgresql" % "postgresql" % "42.4.1",
  // Or Postgres Async
  "io.getquill" %% "quill-jdbc-zio" % "4.3.0",
  "io.getquill" %% "quill-jasync-postgres" % "4.3.0",
  "io.opentracing" % "opentracing-mock" % "0.33.0" % Test,
  "com.h2database" % "h2" % "2.1.214",
  "io.d11" %% "zhttp-test" % "2.0.0-RC9" % Test,
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "org.mockito" % "mockito-all" % "1.10.19" % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "com.github.tomakehurst" % "wiremock" % "2.27.2" % Test
)

Test / parallelExecution := false

Test / test := (Test / test)
  .dependsOn(Compile / scalafmtCheck)
  .dependsOn(Test / scalafmtCheck)
  .value

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
