package com.github.pbyrne84.zio2playground.logging

import io.opentelemetry.api.trace._
import io.opentelemetry.sdk.trace.SdkTracerProvider
import org.slf4j
import org.slf4j.LoggerFactory
import zio.logging.backend.SLF4J
import zio.telemetry.opentelemetry.Tracing
import zio.{Cause, ZIO, ZIOAppDefault, ZIOHack, ZLayer}

import java.util.logging.{Level, Logger}

object LoggingSL4JExample extends ZIOAppDefault {
  override def run = {

    // too many things called Tracer makes things hard

    // builder builder builder to get around inaccessibility of things
    val tracer =
      SdkTracerProvider.builder().build().tracerBuilder(this.getClass.toString).build()

    val tracerLayer = ZLayer(ZIO.succeed(tracer))

    val logger = zio.Runtime.removeDefaultLoggers >>> SLF4J.slf4j

    new LoggingSL4JExample().run.provide(
      tracerLayer,
      Tracing.live,
      logger
    )
  }
}

class LoggingSL4JExample {

  import org.slf4j.bridge.SLF4JBridgeHandler
  import zio.logging.LogAnnotation

  // needed for util->sl4j logging
  SLF4JBridgeHandler.install()

  private val javaUtilLogger: Logger = Logger.getLogger(getClass.getName)
  javaUtilLogger.setLevel(Level.INFO)

  val sl4jLogger: slf4j.Logger = LoggerFactory.getLogger(getClass)

  def run = {

    import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps

    val operations = (for {
      op1 <- createOperation("banana1").fork
      op2 <- createOperation("banana2").fork
      _ <- op1.join
      _ <- op2.join
    } yield ()) @@ LogAnnotation.UserId("user-id")

    // The span id will actually be the parent span id so we will never see that except for the reporters thaT
    // talk to zipkin etc.
    operations.spanFrom(
      propagator = DummyTracing.basicPropagator,
      carrier = List(
        DummyTracing.traceIdField -> "01115d8eb7e102b505085969c4aca859",
        DummyTracing.spanIdField -> "40ce80b7c43f2884"
      ),
      getter = DummyTracing.headerTextMapGetter,
      spanName = "span-name",
      spanKind = SpanKind.SERVER
    )
  }

  val errorMapper: PartialFunction[Any, StatusCode] = { case a =>
    StatusCode.UNSET
  }

  private def createOperation(spanName: String) = {
    // Wrap like this to get child spans
    Tracing
      .span(spanName, SpanKind.SERVER, errorMapper) {
        for {
          _ <- wrapOperationInTrace {
            for {
              _ <- ZIO.logInfo("I am the devil")
              _ <- ZIO.attempt {
                javaUtilLogger.severe(s"meow $getThreadName")
              }
              // this will set the mdc from the logging context and then reset it back after
              // similarly to how the zio.logging.backend.SL4J.closeLogEntry operates.
              // ZIOHack as the name implies is a hack. It abuses package name to get access.
              // Definitely a less than ideal solution however adult anyone feels.
              _ <- ZIOHack.attemptWithMdcLogging(sl4jLogger.info(s"woofWithMdc $getThreadName"))
              _ <- ZIOHack.attemptWithMdcLogging2(sl4jLogger.info(s"woofWithMdc2 $getThreadName"))
              _ <- ZIO.attempt(sl4jLogger.info(s"woofNoMdc $getThreadName"))
              a = new RuntimeException("I had problems and the ice cream didn't help")
              _ <- ZIO.logCause("dying for a cause", Cause.die(a))
            } yield ()
          }.@@(ExampleLogAnnotations.kitty("kitty"))
        } yield {
          ()
        }
      }

    // will only have kitty added to the 2 native zio logging calls. Not really debuggable as the annotation code does
    // call the MDC stuff in SL4J scala
//    override def appendKeyValue( key: String, value: String ): Unit = {
//      mdc.put( key, value )
//      ()
//    }

  }

  private def getThreadName =
    Thread.currentThread().getName

  private def wrapOperationInTrace[A, B, C](call: => ZIO[A, B, C]) = {
    import TracingOps._

    for {
      span <- Tracing.getCurrentSpan
      spanContext = span.getSpanContext
      traceId = spanContext.getTraceId
      spanId = spanContext.getSpanId
      parentSpanId = span.maybeParentSpanId.getOrElse("???")
      snapName = span.maybeName.getOrElse("unknown")

      result <- call @@ ExampleLogAnnotations.stringTraceId(traceId) @@
        ExampleLogAnnotations.parentSpanId(parentSpanId) @@
        ExampleLogAnnotations.stringSpanId(spanId) @@
        ExampleLogAnnotations.stringSpanName(snapName)
    } yield result
  }

}
