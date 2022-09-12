package com.github.pbyrne84.zio2playground.logging

import zio.logging.LogAnnotation

object ExampleLogAnnotations {
  val stringTraceId: LogAnnotation[String] = LogAnnotation[String](
    name = "trace_id",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

  val stringSpanId: LogAnnotation[String] = LogAnnotation[String](
    name = "span_id",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

  val parentSpanId: LogAnnotation[String] = LogAnnotation[String](
    name = "parent_span_id",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

  val stringSpanName: LogAnnotation[String] = LogAnnotation[String](
    name = "span_name",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

  val kitty: LogAnnotation[String] = LogAnnotation[String](
    name = "kitty",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

}
