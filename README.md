# zio2playground

## Testing

Currently, tests kind of work in Intellij. They run as a main class which means things like shared 
services across tests behave very weird.

The instructions for doing this can be found in the following video.

**Zymposium - Sharing Expensive Services Across Specs**
https://www.youtube.com/watch?v=gzHStYNa6Og&t=878s

What happens when run as a main class is the shared service instantiates 3 times. This is easily 
verifiable in the **SharedLayerBaseSpec** where this is all detailed. Without a specific test 
runner intellij is doing the equivalent of :- 

```shell
sbt "Test/runMain com.github.pbyrne84.zio2playground.sharedlayer.TestA"
```

which outputs 

```shell
created ExpensiveService class com.github.pbyrne84.zio2playground.sharedlayer.ExpensiveService T
hread[ZScheduler-Worker-11,5,main]
created ExpensiveService class com.github.pbyrne84.zio2playground.sharedlayer.ExpensiveService T
hread[ZScheduler-Worker-8,5,main]
created ExpensiveService class com.github.pbyrne84.zio2playground.sharedlayer.ExpensiveService T
hread[ZScheduler-Worker-9,5,main]
```

while running 

```shell
sbt "Test/testOnly com.github.pbyrne84.zio2playground.sharedlayer.TestA"
```

outputs

```shell
created ExpensiveService class com.github.pbyrne84.zio2playground.sharedlayer.ExpensiveService T
hread[ZScheduler-Worker-11,5,main]
```

meaning the expensive service is only created once.

This can be a bit of a gotcha as it can be a bit confusing. It is nice to be able to run tests in isolation as it 
enables a debugger to be easily used as inheriting tests can be a variable affair. Worth keeping an eye on the 
GitHub projects

https://github.com/zio/zio-intellij/
and
https://github.com/zio/zio-test-intellij


### Using an intellij external tool to run tests
https://www.jetbrains.com/help/idea/settings-tools-external-tools.html

External tools allow you to set up custom operations to run from Intellij. They have a set of macros that can be 
passed to the external tool such as the line number $LineNumber$ or the file path relative to source path $FilePathRelativeToSourcepath$.
$FilePathRelativeToSourcepath$ is useful as we can calculate the test class to run from that and call sbt run that tests.

![external_tool.png](pythonTestRunner\external_tool.png)

#### test_runner.py
This takes the argument passed from the external tool and if ends in Test or Spec runs that test. Else it runs the 
previous test. This is so you can write the tests first (like children who will get more than coal at Christmas), run the 
tests proving they fail, then switch to the implementation while running the tests without having to go back to the test
or command line. 

(CMD|CTRL)+shift+a opens the run action allowing you to type the name of the external tool and run it that way,
it remembers the last thing you typed, you can just open it again and press enter to retry. This is not as elegant 
as the usual shift+f10 or CMD+R which runs the last run action but still it is better than flicking around in 
panels or tabs running things. 

This is just an example, it is tied to being in a subfolder in this project purely because I needed to set up a python
sdk in a scala project as a submodule.

If you were really adventurous you could get the line number and only run that test case. 


#### problems
This does not solve the problem of easily debugging though that could probably be done.



## Logging and tracing
We need a **spanFrom** an operation to enable tracing

e.g.
```scala
// extension methods for ZIO (spanFrom in this case)
import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps

val operations : ZIO[A,B,C]= ???

operations.spanFrom(
  propagator = DummyTracing.basicPropagator,
  carrier = List(
    DummyTracing.traceIdField -> "01115d8eb7e102b505085969c4aca859",
    DummyTracing.spanIdField -> "40ce80b7c43f2884"
  ),
  getter = DummyTracing.headerTextMapGetter,
  spanName = "span-name",
  spanKind = SpanKind.SERVER
)

```


### Initialisation of trace ids
Using open telemetry we have the following parts 

1. Carrier
2. TextMapPropagator
3. TextMapGetter

The carrier can be the source values that can potentially initialise the values. For example a list of headers. The
TextMapGetter is called by the TextMapPropagator to interface with the values and locate a possible value.

An example implementation of the TextMapPropagator is 

//B3PropagatorExtractorMultipleHeaders

```scala
val propagator: TextMapPropagator = B3Propagator.injectingMultiHeaders()
```

accessed by the 

```java 
  public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
    return Stream.<Supplier<Optional<Context>>>of(
            () -> singleHeaderExtractor.extract(context, carrier, getter),
            () -> multipleHeadersExtractor.extract(context, carrier, getter),
            () -> Optional.of(context))
        .map(Supplier::get)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst()
        .get();
  }
```

call in the 
```java
B3Propagator
```

As we are using the multi header version this will scan the headers in the carrier looking for the following headers.
```scala
object B3 {
  object header {
    val traceId: String = "X-B3-TraceId"
    val spanId: String = "X-B3-SpanId"
    val sampled: String = "X-B3-Sampled"
  }
}
```

The actual constants are private so the ones above are versions local to the project. When looking for header values we 
should not care about case-sensitivity on names.

### Examples

#### TracingClientSpec

This examples reading the current context and adding it to a client calls headers using layering to achieve this.
We are allowing the ids to be autogenerated. The reporter will complain it cannot talk to a real service, this is ignorable.

#### LoggingSL4JExample

This reads from a dummy TextMapPropagator and TextMapGetter found in DummyTracing. The carrier is a list of tuples 
with field names matching the expected field names. The sl4j bridge is being used as we have things doing logging 
via java util which is being handled by the **jul-to-slf4j** library.

The limitations of going to sl4j from zio is the trace id is missing from these values so less than ideal. As we 
have an environment with logback.xml etc. in the environment is hard to debug. Playing with the Slf4jBridgeSpec in the 
logging repo custom annotations seem to not appear there either. We can add things to the MDC with 

```scala
_ <- ZIO.succeed(MDC.put("trace_id", traceId))
_ <- ZIO.succeed(MDC.put("span_name", snapName))
_ <- ZIO.succeed(MDC.put("span_id", spanId))
```

Which will ensure things get put onto direct sl4j and util logging calls. It appears closeLogEntry gets called and
that shifts the MDC about

```sh
appending slf4j_logger_name - zio.logging.example.UserOperation - ZScheduler-Worker-2
appending kitty - kitty - ZScheduler-Worker-2
appending trace_id - 01115d8eb7e102b505085969c4aca859 - ZScheduler-Worker-2
appending span_name - banana1 - ZScheduler-Worker-2
appending user_id - user-id - ZScheduler-Worker-2
appending parent_span_id - b2ccda77dae84661 - ZScheduler-Worker-2
appending span_id - 275c55e516c0cdca - ZScheduler-Worker-2
closeLogEntry - ZScheduler-Worker-2
```

The closeLogEntry has the following code
https://github.com/zio/zio-logging/blob/8e64f982e501635be7dda8b27de07dc4e99491a2/slf4j/src/main/scala/zio/logging/slf4j/SLF4J.scala#L106
```scala
val previous =
  if (!mdc.isEmpty) {
    val previous =
      Some(Option(MDC.getCopyOfContextMap).getOrElse(java.util.Collections.emptyMap[String, String]()))
    MDC.setContextMap(mdc)
    previous
  } else None
```

**previous** is set in the MDC after the ZIO logging call meaning it is not available to non ZIO logging calls. 

##### ZIOHack.attemptWithMdcLogging
As the name suggest this a hack. It pretends to live in the world of the zio package which gives us the ability to 
write a custom zio attempt operation. The FiberRuntime has LogContext in it with all our logging goodies which we 
would like applied to logging calls ZIO has no interaction with.

```scala
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
```
A more generified version that could be used.

```scala
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
        val refValue: A = fiberState.getFiberRef(fiberRef)(Unsafe.unsafe)

        val result = code(refValue)

        ZIO.succeedNow(result)
      } catch {
        case t: Throwable if !fiberState.isFatal(t)(Unsafe.unsafe) =>
          throw ZIOError.Traced(Cause.fail(t))
      }
    }
  }
```



**LogbackMDCAdapter** (There ain't no party like a null club party)

```java 
public Map<String, String> getCopyOfContextMap() {
    Map<String, String> hashMap = copyOnThreadLocal.get();
    if (hashMap == null) {
        return null;
    } else {
        return new HashMap<String, String>(hashMap);
    }
}
```








