package com.github.pbyrne84.zio2playground.logging

import zio.http._
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

  val trace: LogAnnotation[String] = LogAnnotation[String](
    name = "trace",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

  // This will be added to all the entries if added at the entrance point.
  // This can be handy if you want to reproduce the the call.
  // Logging booboos without being able to reproduce said booboos without
  // human communication can be tiring for the inheritor.
  //
  // Also keeping track of incoming payloads can be worth though possibly expensive,
  // but not as expensive as trying to work out how a particular boundary is being hit
  // in production. humanTme > machineTime for now.
  //
  // System support should also require minimum level of expertise by design with minimum
  // number of people being involved. Logging things clearly helps this.
  //
  // Also juniors being able to fix things on their own helps boost their confidence etc.
  val incomingRequest: LogAnnotation[Request] = LogAnnotation[Request](
    name = "incoming_request",
    combine = (_: Request, r: Request) => r,
    render = (request: Request) => s"${request.method.toString}-${request.url.encode}"
  )

  // May be worth having a log entry for sent payload if there is one.
  // Helps with finger pointing. If not an expected status code we should log
  // everything sent.
  // Same as any json issues in their responses. OpenAPi specs can be creatures of hope.
  // Hope should be kept for ice cream.
  val clientRequest: LogAnnotation[(Method, String)] = LogAnnotation[(Method, String)](
    name = "client_request",
    combine = (_: (Method, String), r: (Method, String)) => r,
    render = (request: (Method, String)) => s"${request._1}-${request._2}"
  )

  // always need more kitties
  val kitty: LogAnnotation[String] = LogAnnotation[String](
    name = "kitty",
    combine = (_: String, r: String) => r,
    render = _.toString
  )

}
