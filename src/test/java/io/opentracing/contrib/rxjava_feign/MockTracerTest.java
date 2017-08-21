package io.opentracing.contrib.rxjava_feign;

import static java.util.concurrent.TimeUnit.SECONDS;

import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Retryer;
import feign.Target;
import feign.hystrix.HystrixFeign;
import feign.okhttp.OkHttpClient;
import feign.opentracing.FeignSpanDecorator;
import feign.opentracing.TracingClient;
import feign.opentracing.hystrix.TracingConcurrencyStrategy;
import io.opentracing.Tracer;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.mock.MockTracer.Propagator;
import io.opentracing.rxjava.TracingRxJavaUtils;
import io.opentracing.util.GlobalTracer;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockTracerTest {

  private static final Logger logger = LoggerFactory.getLogger(MockTracerTest.class);

  private final MockTracer mockTracer = new MockTracer(new ThreadLocalActiveSpanSource(),
      Propagator.TEXT_MAP);
  private final MockWebServer mockWebServer = new MockWebServer();


  @Before
  public void before() throws IOException {
    mockTracer.reset();
    mockWebServer.start();

    GlobalTracer.register(mockTracer);
    TracingRxJavaUtils.enableTracing(mockTracer);
    TracingConcurrencyStrategy.register(mockTracer);
  }

  @After
  public void after() throws IOException {
    mockWebServer.close();
  }

  @Test
  public void test() throws Exception {
    Feign feign = getClient(mockTracer);

    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(200));

    StringEntityRequest
        entity = feign.newInstance(
        new Target.HardCodedTarget<>(StringEntityRequest.class,
            mockWebServer.url("/foo").toString()));
    entity.get();

    logger.info("finished spans: {}", mockTracer.finishedSpans().size());

    // let's wait and print finished spans in a loop
    for (int i = 0; i < 10; i++) {
      TimeUnit.MILLISECONDS.sleep(500);
      logger.info("finished spans: {}", mockTracer.finishedSpans().size());
    }

    List<MockSpan> spans = mockTracer.finishedSpans();
    Set<Long> traceIds = spans.stream().map(span -> span.context().traceId())
        .collect(Collectors.toSet());
    logger.info("there are {} traces with ids: {}", traceIds.size(), traceIds);
  }


  static Feign getClient(Tracer tracer) {
    return HystrixFeign.builder()
        .client(new TracingClient(new OkHttpClient(), tracer,
            Collections.singletonList(new FeignSpanDecorator.StandardTags())))
        .retryer(new Retryer.Default(100, SECONDS.toMillis(1), 2))
        .build();
  }

  interface StringEntityRequest {

    @RequestLine("GET")
    @Headers("Content-Type: application/json")
    String get();
  }
}
