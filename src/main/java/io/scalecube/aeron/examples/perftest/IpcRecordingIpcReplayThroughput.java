package io.scalecube.aeron.examples.perftest;

import static io.aeron.CommonContext.IPC_MEDIA;
import static io.aeron.CommonContext.MDC_CONTROL_MODE_DYNAMIC;
import static io.aeron.CommonContext.UDP_MEDIA;
import static io.scalecube.aeron.examples.AeronHelper.FRAGMENT_LIMIT;
import static io.scalecube.aeron.examples.AeronHelper.HEADER_LENGTH;
import static io.scalecube.aeron.examples.AeronHelper.MEGABYTE;
import static io.scalecube.aeron.examples.AeronHelper.MESSAGE_LENGTH;
import static io.scalecube.aeron.examples.AeronHelper.NUMBER_OF_MESSAGES;
import static io.scalecube.aeron.examples.AeronHelper.RUNS;
import static io.scalecube.aeron.examples.AeronHelper.STREAM_ID;
import static io.scalecube.aeron.examples.AeronHelper.printArchiveContext;
import static org.agrona.BitUtil.CACHE_LINE_LENGTH;
import static org.agrona.BufferUtil.allocateDirectAligned;

import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.Image;
import io.aeron.ImageFragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.codecs.SourceLocation;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.MediaDriver.Context;
import io.aeron.driver.ThreadingMode;
import io.aeron.driver.status.SystemCounterDescriptor;
import io.aeron.logbuffer.Header;
import io.scalecube.aeron.examples.AeronHelper;
import io.scalecube.aeron.examples.meter.LatencyMeter;
import io.scalecube.aeron.examples.meter.MeterRegistry;
import io.scalecube.aeron.examples.meter.ThroughputMeter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.AgentTerminationException;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.NoOpIdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersReader;

public class IpcRecordingIpcReplayThroughput implements AutoCloseable {

  private static final int REPLAY_STREAM_ID = 101;
  private static final String RECORDING_CHANNEL = CommonContext.IPC_CHANNEL;
  private static final String REPLAY_CHANNEL = CommonContext.IPC_CHANNEL;

  public static final String RECORDING_EVENTS_CHANNEL_ENDPOINT = "localhost:8030";
  public static final String CONTROL_CHANNEL_ENDPOINT = "localhost:8010";
  public static final String REPLICATION_CHANNEL_ENDPOINT = "localhost:0";

  private final Aeron aeron;
  private final AeronArchive aeronArchive;
  private final MediaDriver mediaDriver;
  private final Archive archive;
  private final UnsafeBuffer buffer =
      new UnsafeBuffer(allocateDirectAligned(MESSAGE_LENGTH, CACHE_LINE_LENGTH));
  private final MeterRegistry meterRegistry;

  /**
   * Main method for launching the process.
   *
   * @param args passed to the process.
   */
  public static void main(final String[] args) throws InterruptedException {
    try (IpcRecordingIpcReplayThroughput test = new IpcRecordingIpcReplayThroughput()) {
      for (int i = 0; i < RUNS; i++) {
        test.streamMessagesForRecording();
      }
    }
  }

  IpcRecordingIpcReplayThroughput() {
    Path aeronPath = Paths.get(CommonContext.generateRandomDirName());
    String instanceName = aeronPath.getFileName().toString();
    Path archivePath =
        AeronHelper.archivePath()
            .orElseGet(() -> Paths.get(String.join("-", instanceName, "archive")));

    meterRegistry = MeterRegistry.create();

    mediaDriver =
        MediaDriver.launch(
            new Context()
                .aeronDirectoryName(aeronPath.toString())
                .termBufferSparseFile(false)
                .useWindowsHighResTimer(true)
                .spiesSimulateConnection(true)
                .threadingMode(ThreadingMode.SHARED)
                .conductorIdleStrategy(BusySpinIdleStrategy.INSTANCE)
                .receiverIdleStrategy(NoOpIdleStrategy.INSTANCE)
                .senderIdleStrategy(NoOpIdleStrategy.INSTANCE));

    aeron =
        Aeron.connect(
            new Aeron.Context()
                .aeronDirectoryName(aeronPath.toString())
                .availableImageHandler(AeronHelper::printAvailableImage)
                .unavailableImageHandler(AeronHelper::printUnavailableImage));

    archive =
        Archive.launch(
            new Archive.Context()
                .aeron(aeron)
                .mediaDriverAgentInvoker(mediaDriver.sharedAgentInvoker())
                .errorCounter(
                    new AtomicCounter(
                        mediaDriver.context().countersValuesBuffer(),
                        SystemCounterDescriptor.ERRORS.id()))
                .errorHandler(mediaDriver.context().errorHandler())
                .localControlChannel(localControlChannel(instanceName))
                .controlChannel(controlChannel())
                .recordingEventsEnabled(false)
                .recordingEventsChannel(recordingEventsChannel())
                .replicationChannel(replicationChannel())
                .aeronDirectoryName(aeronPath.toString())
                .archiveDirectoryName(archivePath.toString())
                .threadingMode(ArchiveThreadingMode.SHARED));

    printArchiveContext(archive.context());

    aeronArchive = AeronArchive.connect(new AeronArchive.Context().aeron(aeron));
  }

  @Override
  public void close() {
    CloseHelper.close(aeronArchive);
    CloseHelper.close(aeron);
    CloseHelper.close(archive);
    CloseHelper.close(mediaDriver);
  }

  private static long awaitRecordingStarted(
      CountersReader countersReader, IdleStrategy idleStrategy, int sessionId) {

    int counterId;
    long recordingId;

    System.out.printf("### Await recording started for session id: %s%n", sessionId);
    while ((counterId = RecordingPos.findCounterIdBySession(countersReader, sessionId)) == -1) {
      idleStrategy.idle();
    }
    while ((recordingId = RecordingPos.getRecordingId(countersReader, counterId)) == -1) {
      idleStrategy.idle();
    }
    while (!RecordingPos.isActive(countersReader, counterId, recordingId)) {
      idleStrategy.idle();
    }
    System.out.printf("### Recording has started for session id: %s%n", sessionId);

    return recordingId;
  }

  private static void awaitReplaySubscription(
      IdleStrategy idleStrategy, Subscription subscription) {
    System.out.println("### Await replay subscription gets connected");
    while (!subscription.isConnected()) {
      idleStrategy.idle();
    }
    System.out.println("### Replay subscription connected");
  }

  private static boolean isImageNotUsable(Image image) {
    return image.isClosed() || image.isEndOfStream();
  }

  private void streamMessagesForRecording() throws InterruptedException {
    try (Publication publication = aeron.addExclusivePublication(RECORDING_CHANNEL, STREAM_ID)) {

      final long subscriptionId =
          aeronArchive.startRecording(
              ChannelUri.addSessionId(RECORDING_CHANNEL, publication.sessionId()),
              STREAM_ID,
              SourceLocation.REMOTE,
              true);

      CountersReader countersReader = aeron.countersReader();
      SleepingMillisIdleStrategy idleStrategy = new SleepingMillisIdleStrategy(100);

      long recordingId =
          awaitRecordingStarted(countersReader, idleStrategy, publication.sessionId());
      Subscription subscription =
          aeronArchive.replay(recordingId, 0L, -1, REPLAY_CHANNEL, REPLAY_STREAM_ID);
      awaitReplaySubscription(idleStrategy, subscription);

      CountDownLatch recordingLatch = new CountDownLatch(1);

      final LatencyMeter latency = meterRegistry.latency("rec_ipc.rep_ipc.latency");
      final ThroughputMeter tps = meterRegistry.tps("rec_ipc.rep_ipc.tps");

      AgentRunner.startOnThread(
          new AgentRunner(
              NoOpIdleStrategy.INSTANCE,
              th -> {},
              null,
              new RecordingAgent(recordingLatch, publication)));

      AgentRunner replayAgentRunner =
          new AgentRunner(
              NoOpIdleStrategy.INSTANCE,
              th -> {},
              null,
              new ReplayAgent(subscription, latency, tps));
      replayAgentRunner.run();

      recordingLatch.await();

      CloseHelper.close(subscription);
      aeronArchive.stopRecording(subscriptionId);
      aeronArchive.truncateRecording(recordingId, 0);
    }
  }

  static String localControlChannel(String instanceName) {
    return new ChannelUriStringBuilder().media(IPC_MEDIA).endpoint(instanceName).build();
  }

  private static String replicationChannel() {
    return new ChannelUriStringBuilder()
        .media(UDP_MEDIA)
        .endpoint(REPLICATION_CHANNEL_ENDPOINT)
        .build();
  }

  private static String recordingEventsChannel() {
    return new ChannelUriStringBuilder()
        .media(UDP_MEDIA)
        .controlMode(MDC_CONTROL_MODE_DYNAMIC)
        .controlEndpoint(RECORDING_EVENTS_CHANNEL_ENDPOINT)
        .build();
  }

  private static String controlChannel() {
    return new ChannelUriStringBuilder()
        .media(UDP_MEDIA)
        .endpoint(CONTROL_CHANNEL_ENDPOINT)
        .build();
  }

  private class RecordingAgent implements Agent {

    private final CountDownLatch recordingLatch;
    private final Publication publication;

    private long startNs;
    private long counter;

    private RecordingAgent(CountDownLatch recordingLatch, Publication publication) {
      this.recordingLatch = recordingLatch;
      this.publication = publication;
    }

    @Override
    public void onStart() {
      awaitPublicationConnected();
      startNs = System.nanoTime();
    }

    private void awaitPublicationConnected() {
      System.out.println("### Await recorded publication gets connected");
      SleepingMillisIdleStrategy idleStrategy = new SleepingMillisIdleStrategy(100);
      while (!publication.isConnected()) {
        idleStrategy.idle();
      }
      System.out.println("### Recorded publication connected");
    }

    @Override
    public void onClose() {
      final long stopPosition = publication.position();

      final long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
      final double dataRate = (stopPosition * 1000.0d / durationMs) / MEGABYTE;
      final double recordingMb = stopPosition / MEGABYTE;
      final long msgRate = (NUMBER_OF_MESSAGES / durationMs) * 1000L;

      System.err.printf(
          "Recorded %.02f MB @ %.02f MB/s - %,d msg/sec - %d byte payload + 32 byte header%n",
          recordingMb, dataRate, msgRate, MESSAGE_LENGTH);

      recordingLatch.countDown();
    }

    @Override
    public int doWork() {
      buffer.putLong(0, counter);
      buffer.putLong(8, System.nanoTime());

      int offer = (int) publication.offer(buffer, 0, MESSAGE_LENGTH);

      if (offer > 0) {
        counter++;
      }

      return offer;
    }

    @Override
    public String roleName() {
      return "recorder";
    }
  }

  private static class ReplayAgent implements Agent {

    private final Subscription subscription;
    private final LatencyMeter latency;
    private final ThroughputMeter tps;

    private Image image;
    private ImageFragmentAssembler fragmentAssembler;
    private long counter;
    private long totalLength;
    private long startNs;

    public ReplayAgent(Subscription subscription, LatencyMeter latency, ThroughputMeter tps) {
      this.subscription = subscription;
      this.latency = latency;
      this.tps = tps;
    }

    @Override
    public void onStart() {
      counter = 0;
      image = subscription.imageAtIndex(0);
      fragmentAssembler = new ImageFragmentAssembler(this::onMessage);
      startNs = System.nanoTime();
    }

    void onMessage(DirectBuffer buffer, int offset, int length, Header header) {
      counter = buffer.getLong(offset);

      final long nanoTime = buffer.getLong(offset + 8);
      latency.record(System.nanoTime() - nanoTime);

      tps.record();

      totalLength += length + HEADER_LENGTH;
    }

    @Override
    public void onClose() {
      long totalReceived = totalLength;

      final long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
      final double dataRate = (totalReceived * 1000.0d / durationMs) / MEGABYTE;
      final double recordingMb = totalReceived / MEGABYTE;
      final long msgRate = (NUMBER_OF_MESSAGES / durationMs) * 1000L;

      System.err.printf(
          "Replayed %.02f MB @ %.02f MB/s - %,d msg/sec - %d byte payload + 32 byte header%n",
          recordingMb, dataRate, msgRate, MESSAGE_LENGTH);
    }

    @Override
    public int doWork() {
      final int fragments = image.poll(fragmentAssembler, FRAGMENT_LIMIT);

      if (0 == fragments && isImageNotUsable(image)) {
        System.err.println("\n*** unexpected end of stream at message count: " + counter);
        throw new AgentTerminationException("good bye");
      }

      return fragments;
    }

    @Override
    public String roleName() {
      return "replayer";
    }
  }
}
