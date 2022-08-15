package com.github.pbyrne84.zio2playground.tracing

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.`export`.SpanExporter
import io.opentelemetry.sdk.trace.data.SpanData

import java.util
import scala.jdk.CollectionConverters.CollectionHasAsScala
class DummySpanExporter extends SpanExporter {
  override def `export`(spans: util.Collection[SpanData]): CompletableResultCode = {
    spans.asScala.foreach { (span: SpanData) =>
      println("DummySpanExporter.export " + span)

      span.getParentSpanId
      println(span.getClass)
    }

    new CompletableResultCode()
  }

  override def flush(): CompletableResultCode = ???

  override def shutdown(): CompletableResultCode = ???
}
