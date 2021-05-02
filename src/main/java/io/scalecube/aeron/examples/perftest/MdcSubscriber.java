package io.scalecube.aeron.examples.perftest;

import static io.aeron.CommonContext.MDC_CONTROL_MODE_DYNAMIC;
import static io.aeron.CommonContext.UDP_MEDIA;
import static io.scalecube.aeron.examples.AeronHelper.STREAM_ID;
import static io.scalecube.aeron.examples.AeronHelper.printSubscription;

import io.aeron.Aeron;
import io.aeron.Aeron.Context;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.FragmentAssembler;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.scalecube.aeron.examples.AeronHelper;
import io.scalecube.aeron.examples.MeterRegistries;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;

public class MdcSubscriber {

  public static final String CONTROL_ENDPOINT = "localhost:30121";

  private static MediaDriver mediaDriver;
  private static Aeron aeron;
  private static final AtomicBoolean running = new AtomicBoolean(true);

  /**
   * Main runner.
   *
   * @param args args
   */
  public static void main(String[] args) {
    SigInt.register(MdcSubscriber::close);

    mediaDriver = MediaDriver.launchEmbedded();
    String aeronDirectoryName = mediaDriver.aeronDirectoryName();

    Context context =
        new Context()
            .aeronDirectoryName(aeronDirectoryName)
            .availableImageHandler(AeronHelper::printAvailableImage)
            .unavailableImageHandler(AeronHelper::printUnavailableImage);

    aeron = Aeron.connect(context);
    System.out.println("hello, " + context.aeronDirectoryName());

    final DropwizardMeterRegistry meterRegistry = MeterRegistries.createMeterRegistry();

    Counter requestCounter = meterRegistry.counter("request");

    DistributionSummary latencyDistributionSummary =
        DistributionSummary.builder("latency")
            .minimumExpectedValue((double) (10 * 1000))
            .maximumExpectedValue((double) Duration.ofSeconds(10).toNanos())
            .publishPercentiles(0.1, 0.2, 0.5, 0.75, 0.9, 0.99)
            .baseUnit("ns")
            .register(meterRegistry);

    String channel =
        new ChannelUriStringBuilder()
            .media(UDP_MEDIA)
            .controlMode(MDC_CONTROL_MODE_DYNAMIC)
            .controlEndpoint(CONTROL_ENDPOINT)
            .endpoint("localhost:0")
            .build();

    Subscription subscription =
        aeron.addSubscription(channel, STREAM_ID); // conn: 20121 / logbuffer: 48M

    printSubscription(subscription);

    final FragmentHandler fragmentHandler =
        (buffer, offset, length, header) -> {
          final long l = buffer.getLong(offset);
          latencyDistributionSummary.record(System.nanoTime() - l);
          requestCounter.increment();
        };
    FragmentAssembler fragmentAssembler = new FragmentAssembler(fragmentHandler);
    IdleStrategy idleStrategy = new BusySpinIdleStrategy();

    while (running.get()) {
      idleStrategy.idle(subscription.poll(fragmentAssembler, 100));
    }

    System.out.println("Shutting down...");

    close();
  }

  private static void close() {
    running.set(false);
    CloseHelper.close(aeron);
    CloseHelper.close(mediaDriver);
  }
}
