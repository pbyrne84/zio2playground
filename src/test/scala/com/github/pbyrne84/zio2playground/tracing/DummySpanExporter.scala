package com.github.pbyrne84.zio2playground.tracing

import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala
class DummySpanExporter extends SpanExporter with StrictLogging {
  override def `export`(spans: util.Collection[SpanData]): CompletableResultCode = {
    spans.asScala.foreach { (span: SpanData) =>
      logger.info("DummySpanExporter.export exporting " + span)
    }

    CompletableResultCode.ofSuccess()
  }

  override def flush(): CompletableResultCode = ???

  override def shutdown(): CompletableResultCode = ???
}
