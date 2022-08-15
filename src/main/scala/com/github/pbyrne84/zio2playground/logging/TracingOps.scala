package com.github.pbyrne84.zio2playground.logging

import io.opentelemetry.api.trace.{Span, SpanContext, SpanKind}
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.data.SpanData

object TracingOps {

  // get the parent info out etc as it is not on the default span interface
  implicit class SpanOps(span: Span) {

    val asMaybeReadWriteSpan: Option[ReadWriteSpan] = span match {
      case readWriteSpan: ReadWriteSpan => Some(readWriteSpan)
      case _ => None
    }

    def maybeName: Option[String] =
      asMaybeReadWriteSpan.map(_.getName)

    def maybeParentSpanContext: Option[SpanContext] =
      asMaybeReadWriteSpan.map(_.getParentSpanContext)

    def maybeParentSpanId: Option[String] =
      maybeParentSpanContext.map(_.getSpanId)

    def maybeParentTraceId: Option[String] =
      maybeParentSpanContext.map(_.getTraceId)

    def maybeParentSpanIds: Option[(String, String)] =
      for {
        traceId <- maybeParentTraceId
        spanId <- maybeParentSpanId
      } yield (traceId -> spanId)

    def maybeToSpanData: Option[SpanData] =
      asMaybeReadWriteSpan.map(_.toSpanData)

    def maybeKind: Option[SpanKind] =
      asMaybeReadWriteSpan.map(_.getKind)

  }

}
