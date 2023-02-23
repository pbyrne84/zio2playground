scalaVersion := "2.13.8"
version := "0.1"
name := "zio2playground"

val zioConfigVersion = "3.0.6"
val zioVersion = "2.0.5"
val zioLoggingVersion = "2.1.6"
val openTelemetryVersion = "1.21.0"

libraryDependencies ++= List(
  "dev.zio" %% "zio" % zioVersion,
  "io.d11" %% "zhttp" % "2.0.0-RC10",
  "org.scalaz" %% "scalaz-core" % "7.3.7",
  "org.slf4j" % "jul-to-slf4j" % "2.0.5",
  "dev.zio" %% "zio-logging-slf4j" % zioLoggingVersion,
  "dev.zio" %% "zio-logging-slf4j-bridge" % zioLoggingVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "io.opentracing" % "opentracing-util" % "0.33.0",
  "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % openTelemetryVersion,
  "io.opentelemetry" % "opentelemetry-exporter-zipkin" % openTelemetryVersion,
  "io.opentelemetry" % "opentelemetry-exporter-jaeger" % openTelemetryVersion,
  "net.logstash.logback" % "logstash-logback-encoder" % "7.2",
  "io.opentelemetry" % "opentelemetry-sdk" % openTelemetryVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio-config-refined" % zioConfigVersion,
  "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
  "dev.zio" %% "zio-opentelemetry" % "2.0.3",
  "dev.zio" %% "zio-opentracing" % "2.0.3",
  "org.flywaydb" % "flyway-core" % "9.10.2",
  "org.postgresql" % "postgresql" % "42.5.1",
  "io.getquill" %% "quill-jdbc-zio" % "4.6.0",
  "io.getquill" %% "quill-jasync-postgres" % "4.6.0",
  "io.opentracing" % "opentracing-mock" % "0.33.0" % Test,
  "com.h2database" % "h2" % "2.1.214",
  "io.d11" %% "zhttp-test" % "2.0.0-RC9" % Test,
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "org.mockito" % "mockito-all" % "1.10.19" % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "com.github.tomakehurst" % "wiremock" % "2.27.2" % Test,
  "org.typelevel" %% "cats-effect" % "3.4.8" % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
  "org.http4s" %% "http4s-blaze-server" % "0.23.13", // compat with zio and io (last time I checked anyway)
  "org.http4s" %% "http4s-server" % "0.23.18" % Test,
  "dev.zio" %% "zio-http" % "0.0.4" % Test,
  "com.softwaremill.sttp.client3" %% "zio" % "3.8.11" % Test
)

Test / parallelExecution := false

Test / test := (Test / test)
  .dependsOn(Compile / scalafmtCheck)
  .dependsOn(Test / scalafmtCheck)
  .value

testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
