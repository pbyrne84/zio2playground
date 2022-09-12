package com.github.pbyrne84.zio2playground.tracing

import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import zio.{ZIO, ZLayer}

object TestZipkinTracer {

  private val jaegerExporter =
    JaegerGrpcSpanExporter.builder().setEndpoint("http://localhost:90/").build()

  def live: ZLayer[Any, Throwable, Tracer] = ZLayer(
    for {
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
