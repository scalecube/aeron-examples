package io.scalecube.aeron.examples;

import static io.aeron.CommonContext.UDP_MEDIA;
import static io.scalecube.aeron.examples.AeronHelper.STREAM_ID;
import static io.scalecube.aeron.examples.AeronHelper.printPublication;

import io.aeron.Aeron;
import io.aeron.Aeron.Context;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.agrona.concurrent.SigInt;

public class SimplePublisher {

  public static final String ENDPOINT = "localhost:20121";

  private static MediaDriver mediaDriver;
  private static Aeron aeron;
  private static final AtomicBoolean running = new AtomicBoolean(true);

  /**
   * Main runner.
   *
   * @param args args
   */
  public static void main(String[] args) throws InterruptedException {
    SigInt.register(SimplePublisher::close);

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
    Counter requestBackpressureCounter = meterRegistry.counter("request.backpressure");

    String channel = new ChannelUriStringBuilder().media(UDP_MEDIA).endpoint(ENDPOINT).build();
    Publication publication = aeron.addPublication(channel, STREAM_ID); // logbuffer: 48M

    printPublication(publication);

    for (long i = 0; ; i++) {
      AeronHelper.sendMessageQuietly(publication, i, requestBackpressureCounter);
      requestCounter.increment();
    }
  }

  private static void close() {
    running.set(false);
    CloseHelper.close(aeron);
    CloseHelper.close(mediaDriver);
  }
}
