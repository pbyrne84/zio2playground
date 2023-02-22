package com.github.pbyrne84.zio2playground.fibres

import com.github.pbyrne84.zio2playground.BaseSpec
import com.github.pbyrne84.zio2playground.BaseSpec.Shared
import zio.{Fiber, FiberRefs, Runtime, RuntimeFlags, Scope, Unsafe, ZEnvironment, ZIO}
import zio.test._

import java.time.Duration

// Experimenting as I want to run something in parallel as a test utility with its own isolated fibre
// that can be shutdown separately
object ZIOCustomFibreSpec extends BaseSpec {
  override def spec: Spec[Shared with TestEnvironment with Scope, Any] = {
    suite(getClass.getSimpleName)(
      suite("")(
        test("") {

          for {
            backoundFork <- new CustomOperation().run()
            meow <- ZIO
              .succeed(println("should shut down"))
            _ = println("boop")
            _ <- ZIO.attempt(Thread.sleep(1000))
            _ <- ZIO.succeed(println("ssss"))
            _ <- backoundFork.interruptFork
          } yield assertTrue(true)
        }
      )
    )
  }
}

object CustomOperation {
  val customRuntime: Runtime[Any] =
    Runtime(ZEnvironment.empty, FiberRefs.empty, RuntimeFlags.default)

  def main(args: Array[String]): Unit = {
    new CustomOperation().run()
  }
}

//Just create something that will run in the background that cannot inhibit test
class CustomOperation {

  private val runtime = CustomOperation.customRuntime

  def run() = {
    val call = (for {
      counters <- ZIO.succeed((0 to 3000).toList)
      x <- ZIO.foreach(counters) { counter =>
        ZIO
          .blocking { // blocking as there is a Thread.sleep within.
            ZIO.succeed {
              Thread.sleep(100)
              println(counter)
              counter
            }
          }
      }
    } yield ()).fork

    call
  }
}
