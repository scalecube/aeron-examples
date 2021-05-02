package io.scalecube.aeron.examples.perftest;

import static io.aeron.CommonContext.MDC_CONTROL_MODE_DYNAMIC;
import static io.aeron.CommonContext.UDP_MEDIA;
import static io.scalecube.aeron.examples.AeronHelper.STREAM_ID;
import static io.scalecube.aeron.examples.AeronHelper.printPublication;

import io.aeron.Aeron;
import io.aeron.Aeron.Context;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.ExclusivePublication;
import io.aeron.driver.MediaDriver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.scalecube.aeron.examples.AeronHelper;
import io.scalecube.aeron.examples.MeterRegistries;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.CloseHelper;
import org.agrona.concurrent.SigInt;

public class MdcPublisher {

  public static final String CONTROL_ENDPOINT = "localhost:30121";

  private static MediaDriver mediaDriver;
  private static Aeron aeron;
  private static final AtomicBoolean running = new AtomicBoolean(true);

  /**
   * Main runner.
   *
   * @param args args
   */
  public static void main(String[] args) throws InterruptedException {
    SigInt.register(MdcPublisher::close);

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

    String controlEndpointChannel =
        new ChannelUriStringBuilder()
            .media(UDP_MEDIA)
            .controlMode(MDC_CONTROL_MODE_DYNAMIC)
            .controlEndpoint(CONTROL_ENDPOINT)
            .build();

    ExclusivePublication publication =
        aeron.addExclusivePublication(controlEndpointChannel, STREAM_ID);

    printPublication(publication);

    for (long i = 0; ; i++) {
      AeronHelper.sendMessageQuietly(publication, requestCounter, requestBackpressureCounter);
    }
  }

  private static void close() {
    running.set(false);
    CloseHelper.close(aeron);
    CloseHelper.close(mediaDriver);
  }
}
