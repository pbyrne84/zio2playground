package com.github.pbyrne84.zio2playground.logging

import com.typesafe.scalalogging.StrictLogging
import io.opentelemetry.api.internal.StringUtils
import io.opentelemetry.api.trace._
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapPropagator, TextMapSetter}

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

object DummyTracing extends StrictLogging {
  val traceIdField = "traceId"
  val spanIdField = "spanId"

  // Hard coded to the constants above. In reality we would use
  // B3 or something
  val basicPropagator: TextMapPropagator = new TextMapPropagator {
    override def fields(): util.Collection[String] = {
      List(traceIdField, spanIdField).asJava
    }

    override def inject[C](context: Context, carrier: C, setter: TextMapSetter[C]): Unit = {}

    override def extract[C](context: Context, carrier: C, getter: TextMapGetter[C]): Context = {
      val traceId = getter.get(carrier, traceIdField)
      val spanId = getter.get(carrier, spanIdField)
      val span = SpanContext.createFromRemoteParent(
        StringUtils.padLeft(traceId, TraceId.getLength),
        spanId,
        TraceFlags.getSampled,
        TraceState.getDefault
      )

      context.`with`(Span.wrap(span))
    }
  }

}
