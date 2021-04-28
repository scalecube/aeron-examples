package io.scalecube.aeron.examples;

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
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

public class SimpleSubscriber {

  public static final String ENDPOINT = "localhost:20121";

  private static Aeron aeron;
  private static MediaDriver mediaDriver;
  private static final AtomicBoolean running = new AtomicBoolean(true);

  /**
   * Main runner.
   *
   * @param args args
   */
  public static void main(String[] args) {
    SigInt.register(SimpleSubscriber::close);

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

    String channel = new ChannelUriStringBuilder().media(UDP_MEDIA).endpoint(ENDPOINT).build();
    Subscription subscription =
        aeron.addSubscription(channel, STREAM_ID); // conn: 20121 / logbuffer: 48M

    printSubscription(subscription);

    final FragmentHandler fragmentHandler =
        (buffer, offset, length, header) -> requestCounter.increment();
    FragmentAssembler fragmentAssembler = new FragmentAssembler(fragmentHandler);
    IdleStrategy idleStrategy = new SleepingMillisIdleStrategy(1);

    while (running.get()) {
      subscription.poll(fragmentAssembler, 100);
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
