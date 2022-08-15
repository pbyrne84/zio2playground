package zio

import org.slf4j.MDC
import zio.ZIO.ZIOError
import zio.internal.FiberRuntime
import zio.logging.LogContext

import scala.jdk.CollectionConverters.MapHasAsJava

object ZIOHack {

  def attemptWithMdcLogging[A](code: => A)(implicit trace: Trace): Task[A] =
    ZIO.withFiberRuntime[Any, Throwable, A] { (fiberState: FiberRuntime[Throwable, A], _) =>
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

}
