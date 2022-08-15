package com.github.pbyrne84.zio2playground.tracing

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import zio.{ZIO, ZLayer}

object TestZipkinTracer {

  // this is a very good example of error handling being inconsistent across implementations
  // JaegerGrpcSpanExporter does not error if it cannot communicate whereas zipkin does
  // This could be less than ideal as the server will fail when it cannot report
  private val spanExporter = new DummySpanExporter()
  private val jaegerExporter =
    JaegerGrpcSpanExporter.builder().setEndpoint("http://localhost:90/").build()

  private val zipkinSpanExporter: ZipkinSpanExporter =
    ZipkinSpanExporter.builder().setEndpoint("http://localhost:90/").build()

  def live: ZLayer[Any, Throwable, Tracer] = ZLayer(
    for {
      _ <- ZIO.attempt(
        ZipkinSpanExporter.builder().setEndpoint("http://localhost:90/").build()
      )

      spanProcessor <- ZIO.succeed(SimpleSpanProcessor.create(jaegerExporter))
      tracerProvider <- ZIO.succeed(
        SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build()
      )
      openTelemetry <- ZIO.succeed(
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
      )
      tracer <- ZIO.succeed(
        openTelemetry.getTracer(TestZipkinTracer.getClass.getName)
      )
    } yield tracer
  )

}
