# zio2playground

A repo that covers 

1. Service layering in tests including shared layering. As mentioned there are some gotchas.
2. Tracing through the application using OpenTelemetry, this enables Zipkin etc. This includes setting 
   current context from incoming headers. We want to keep the trace across system boundaries. 
   Not having this sort of stuff can make a fun day a much less than fun day.
3. How we log the trace in the logging, so we can get some kibana or similar goodness. This implementation uses logback 
   as not everything is likely to be pure ZIO.log in an application. There is an example of monkeying around
   with the MDC in **LoggingSL4JExample**. This handles java util and direct SL4J logging which probably simulates a lot 
   of production environments. For example, I don't think a functionally pure version of PAC4J is on anyone's todo list. 
   Anything security based should be implemented as few times as possible, unless you like crackers.
    
   <br/>MDC stuff does have its limitations due to issues with copying between threads, ideally async
   is being done by the effect system and the java stuff is not async by nature.

   It is a bit hacky but an idea of how to do it, I had to hijack the zio package to read the fiber ref to get the 
   **zio.logging.logContext** where this is held.

   **B3TracingOps.serverSpan** creates a span and add it to the logging context.
   
4. ZIO.log does add to the MDC but only for that call. The logback.xml config adds all MDC
   to the log hence number **LoggingSL4JExample** is doing something similar for the java logging calls.


## Testing
Currently, tests kind of work in Intellij. The shared layering works but the rendering of the test results only
works if all the tests are run. Also running multiple tests across files is ignored.

The instructions for sharing services across tests can be found in the following video.

**Zymposium - Sharing Expensive Services Across Specs**
https://www.youtube.com/watch?v=gzHStYNa6Og&t=878s

Instead of using the **zio.test.ZIOSpecDefault** base class create a custom one with a type that houses the common 
signature for what will be shared.

Taken from <https://github.com/pbyrne84/zio2playground/blob/main/src/test/scala/com/github/pbyrne84/zio2playground/BaseSpec.scala>

```scala
type Shared = AllTestBootstrap
  with ServerAWireMock
  with InitialisedParams
  with EnvironmentParamSetup
```

And then in the use that in the **ZIOSpec[R : EnvironmentTag]** signature

e.g.
```scala
abstract class BaseSpec extends ZIOSpec[BaseSpec.Shared]
```

The abstract **bootstrap** method will then have to fit that signature.

```scala
val bootstrap = BaseSpec.layerWithWireMock
```

## Logging and tracing
We need a **spanFrom**  operation to initialise tracing with a starting value such as an incoming headers or to 
generate one if there is the headers do not exist.

e.g.
### Example from LoggingSL4JExample
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

**B3PropagatorExtractorMultipleHeaders**

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

#### Output from RoutesSpec

***B3TracingOps.serverSpan*** creates a new span then makes sure those values are added to the logger.
There is no magic like in Kamon so need to be aware that we may be creating spans, but they can be out of sync
with what is logged. Less magic is good in the sense that we can observe things.

You will see that parent span id f9550d1c8f78300b on the second entry relates to the span id on the first. 

```json
{
  "@timestamp": "2022-09-13T11:50:39.571+01:00",
  "@version": "1",
  "message": "received a called traced service call with the id 433",
  "logger_name": "com.github.pbyrne84.zio2playground.routes.Routes",
  "thread_name": "zio-default-blocking-4",
  "level": "INFO",
  "level_value": 20000,
  "span_name": "callTracedService",
  "parent_span_id": "72f449d2d1e23744",
  "trace_id": "f63279dda3c01ef3ef3bc27e2bb5c206",
  "span_id": "f9550d1c8f78300b"
}
```

```json
{
  "@timestamp": "2022-09-13T11:50:39.571+01:00",
  "@version": "1",
  "message": "calling http://localhost:50828/downstream/433",
  "logger_name": "com.github.pbyrne84.zio2playground.client.ExternalApiService",
  "thread_name": "zio-default-blocking-4",
  "level": "INFO",
  "level_value": 20000,
  "span_name": "ExternalApiService.callApi",
  "parent_span_id": "f9550d1c8f78300b",
  "trace_id": "f63279dda3c01ef3ef3bc27e2bb5c206",
  "span_id": "482156f6269286ab"
}
```

#### TracingClientSpec

This examples reading the current context and adding it to a client calls headers using ZIO layering to achieve this.
We are allowing the ids to be autogenerated. The reporter will complain it cannot talk to a real service, this is ignorable.

#### LoggingSL4JExample

This reads from a dummy TextMapPropagator and TextMapGetter found in DummyTracing. The carrier is a list of tuples 
with field names matching the expected field names. The SL4J bridge is being used as we have things doing logging 
via java util which is being handled by the **jul-to-slf4j** library.


##### ZIOHack.attemptWithMdcLogging
As the name suggest this a hack. It pretends to live in the world of the zio package which gives us the ability to 
write a custom zio attempt operation. The FiberRuntime has LogContext in it with all our logging goodies which we 
would like applied to logging calls via MDC ZIO has no control over.

```scala
// needed for util->sl4j logging
SLF4JBridgeHandler.install()

for {
   _ <- ZIOHack.attemptWithMdcLogging {
      javaUtilLogger.severe(s"util meowWithMdc $getThreadName")
   }
   _ <- ZIO.attempt {
      javaUtilLogger.severe(s"util meowWithNoMdcMdc $getThreadName")
   }
} yield ()
```
produces
```sh
{
  "@timestamp": "2022-09-13T15:23:09.539+01:00",
  "@version": "1",
  "message": "util meowWithMdc ZScheduler-Worker-4",
  "logger_name": "com.github.pbyrne84.zio2playground.logging.LoggingSL4JExample",
  "thread_name": "ZScheduler-Worker-4",
  "level": "ERROR",
  "level_value": 40000,
  "span_name": "banana2",
  "parent_span_id": "c234dec741f08fcb",
  "trace_id": "01115d8eb7e102b505085969c4aca859",
  "user_id": "user-id",
  "span_id": "20f2acf6460baf0c",
  "kitty": "kitty"
}{
  "@timestamp": "2022-09-13T15:23:09.541+01:00",
  "@version": "1",
  "message": "util meowWithNoMdcMdc ZScheduler-Worker-4",
  "logger_name": "com.github.pbyrne84.zio2playground.logging.LoggingSL4JExample",
  "thread_name": "ZScheduler-Worker-4",
  "level": "ERROR",
  "level_value": 40000
}
```
Note the MDC is flushed after the first call.

**Code that does the magic**
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


#### Other project overviews
[https://pbyrne84.github.io/](https://pbyrne84.github.io/)





