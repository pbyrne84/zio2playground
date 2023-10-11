package zio

import org.slf4j.MDC
import zio.ZIO.ZIOError
import zio.logging.LogContext

import scala.jdk.CollectionConverters.MapHasAsJava

object ZIOHack {

  def attemptWithMdcLogging[A](code: => A)(implicit trace: Trace): Task[A] =
    ZIO.withFiberRuntime[Any, Throwable, A] { (fiberState: Fiber.Runtime[Throwable, A], _) =>
      // Logback implementation can return null
      val mdcAtStart = Option(MDC.getCopyOfContextMap)

      try {
        // Follows similar logic to FiberRuntime.log
        val logContext: LogContext = fiberState.getFiberRef(zio.logging.logContext)(Unsafe.unsafe)
        MDC.setContextMap(logContext.asMap.asJava)
        val result = code
        ZIO.succeedNow(result)
      } catch {
        case t: Throwable if !fiberState.isFatal(t)(Unsafe.unsafe) =>
          throw ZIOError.Traced(Cause.fail(t))
      } finally {
        MDC.setContextMap(mdcAtStart.orNull)
      }
    }

  // version that uses a generified version to get things out the fiber but doesn't need to know all the dirty
  // details
  def attemptWithMdcLogging2[A](code: => A): Task[A] = {
    attemptWithFiberRef(zio.logging.logContext) { (logContext: LogContext) =>
      val mdcAtStart = Option(MDC.getCopyOfContextMap)
      try {
        MDC.setContextMap(logContext.asMap.asJava)
        code
      } finally {
        MDC.setContextMap(mdcAtStart.orNull)
      }
    }
  }

  // Something like this could be added as it is generic and doesn't require exposing internals
  def attemptWithFiberRef[A, B](fiberRef: FiberRef[A])(code: A => B): Task[B] = {
    ZIO.withFiberRuntime[Any, Throwable, B] { (fiberState, _) =>
      try {
        val refValue = fiberState.getFiberRef(fiberRef)(Unsafe.unsafe)
        val result = code(refValue)

        ZIO.succeedNow(result)
      } catch {
        case t: Throwable if !fiberState.isFatal(t)(Unsafe.unsafe) =>
          throw ZIOError.Traced(Cause.fail(t))
      }
    }
  }
}
