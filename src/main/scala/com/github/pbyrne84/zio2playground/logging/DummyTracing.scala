package com.github.pbyrne84.zio2playground.logging

import io.opentelemetry.api.internal.StringUtils
import io.opentelemetry.api.trace.{Span, SpanContext, TraceFlags, TraceId, TraceState}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapGetter, TextMapPropagator, TextMapSetter}

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

object DummyTracing {
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

  // case insensitive on keys as headers should not be case sensitive on those
  // people have monkey hands
  val headerTextMapGetter: TextMapGetter[List[(String, String)]] =
    new TextMapGetter[List[(String, String)]] {
      override def keys(carrier: List[(String, String)]) = {
        println(s"$carrier")
        carrier.map(_._1.toLowerCase).asJava
      }

      override def get(carrier: List[(String, String)], key: String): String = {
        println(s"get $carrier -  $key")
        // headers should be case insensitive
        carrier.find(_._1.toLowerCase == key.toLowerCase).map(_._2).orNull
      }
    }

}
