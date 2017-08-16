package io.opentracing.contrib.rxjava_feign;

import static io.opentracing.contrib.rxjava_feign.MockTracerTest.getClient;

import com.lightstep.tracer.jre.JRETracer;
import com.lightstep.tracer.shared.Options.OptionsBuilder;
import feign.Feign;
import feign.Target;
import feign.opentracing.hystrix.TracingConcurrencyStrategy;
import io.opentracing.rxjava.TracingRxJavaUtils;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LightStepTest {

  private final JRETracer tracer;
  private final MockWebServer mockWebServer = new MockWebServer();

  public LightStepTest() throws MalformedURLException {
    tracer = new JRETracer(
        new OptionsBuilder()
            .withAccessToken("bla-bla-bla")
            .withComponentName("feign-hystrix-rxjava")
            .build());
  }

  @Before
  public void before() throws IOException {
    mockWebServer.start();

    GlobalTracer.register(tracer);
    TracingRxJavaUtils.enableTracing(tracer);
    TracingConcurrencyStrategy.register(tracer);
  }

  @After
  public void after() throws IOException {
    mockWebServer.close();
  }

  @Test
  public void test() throws Exception {
    Feign feign = getClient(tracer);

    mockWebServer.enqueue(new MockResponse()
        .setResponseCode(200));

    MockTracerTest.StringEntityRequest
        entity = feign.newInstance(
        new Target.HardCodedTarget<>(MockTracerTest.StringEntityRequest.class,
            mockWebServer.url("/foo").toString()));
    entity.get();

    // let's wait
    TimeUnit.MILLISECONDS.sleep(1000);
  }
}
