package com.github.pbyrne84.zio2playground.tracing

import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.{SimpleSpanProcessor, SpanExporter}
import io.opentelemetry.sdk.trace.data.SpanData
import zio.{ZIO, ZLayer}

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala

/** We are not going to send the span details anywhere, we are just going to manage the creation of
  * child spans etc.
  *
  * TracerProvider.noop().tracerBuilder("").build() build something that does not create child span
  * ids and it makes things confusing if we are trying to see child spans etc.
  */
object NonExportingTracer extends StrictLogging {

  private val spanExporter = new SpanExporter {
    override def `export`(spans: util.Collection[SpanData]): CompletableResultCode = {

      spans.asScala.foreach(span => logger.info(s"exporting span $span"))

      CompletableResultCode.ofSuccess()
    }

    override def flush(): CompletableResultCode =
      CompletableResultCode.ofSuccess()

    override def shutdown(): CompletableResultCode =
      CompletableResultCode.ofSuccess()
  }

  val live: ZLayer[Any, Throwable, Tracer] = ZLayer(
    for {
      spanProcessor <- ZIO.succeed(SimpleSpanProcessor.create(spanExporter))
      tracerProvider <- ZIO.succeed(
        SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build()
      )
      openTelemetry <- ZIO.succeed(
        OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build()
      )
      tracer <- ZIO.succeed(
        openTelemetry.getTracer(getClass.getName)
      )
    } yield tracer
  )

}
