package com.github.pbyrne84.zio2playground.http

import com.github.pbyrne84.zio2playground.tracing.{B3HTTPResponseTracing, B3Tracing}
import zhttp.http.{Http, Request, Response}
import zio.ZIO
import zio.telemetry.opentelemetry.Tracing

object TracingHttp {

  /** Method that creates a Http.collectZIO[Request] with tracing wrapping the routes, also adds the
    * tracing headers added to the response.
    */
  def b3TracingHttp[R, E](
      routes: PartialFunction[Request, ZIO[R, E, Response]]
  ): Http[R with Tracing with B3HTTPResponseTracing, E, Request, Response] =
    Http.collectZIO[Request] {
      initialiseB3Trace {
        routes
      }
    }

  /** extracts the headers and initialises the tracing from them if valid, else will generate a
    * different trace id. At the end will append the trace headers in the response.
    *
    * This could be generified further as B3Tracing and B3HTTPResponseTracing could all be layered
    * in generified version.
    */
  def initialiseB3Trace[R, E](
      routes: PartialFunction[Request, ZIO[R, E, Response]]
  ): PartialFunction[Request, ZIO[R with Tracing with B3HTTPResponseTracing, E, Response]] = {
    case request: Request =>
      B3Tracing
        .requestInitialisationSpan(
          s"new-${request.method.toString.toLowerCase}-request",
          request
        ) {
          for {
            _ <- ZIO.logInfo(s"executing request $request")
            result <- routes(request)
            currentHeaders = result.headers
            appendedHeaders <- B3Tracing.serverSpan("responding") {
              for {
                appendedHeaders <- B3HTTPResponseTracing.appendHeadersToResponse(
                  currentHeaders
                )
                _ <- ZIO.logInfo(s"response-status:${result.status}")

              } yield appendedHeaders
            }
          } yield result.copy(headers = appendedHeaders)
        }
  }

}
