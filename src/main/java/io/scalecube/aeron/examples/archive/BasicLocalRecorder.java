package io.scalecube.aeron.examples.archive;

import static io.aeron.CommonContext.IPC_MEDIA;
import static io.aeron.CommonContext.MDC_CONTROL_MODE_DYNAMIC;
import static io.aeron.CommonContext.UDP_MEDIA;
import static io.scalecube.aeron.examples.AeronHelper.STREAM_ID;
import static io.scalecube.aeron.examples.AeronHelper.awaitRecordingPosCounter;
import static io.scalecube.aeron.examples.AeronHelper.controlResponseChannel;
import static io.scalecube.aeron.examples.AeronHelper.localControlChannel;
import static io.scalecube.aeron.examples.AeronHelper.printArchiveContext;
import static io.scalecube.aeron.examples.AeronHelper.printRecordedPublication;

import io.aeron.Aeron;
import io.aeron.ChannelUri;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.CommonContext;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.agent.EventLogAgent;
import io.aeron.archive.Archive;
import io.aeron.archive.ArchiveThreadingMode;
import io.aeron.archive.client.AeronArchive;
import io.aeron.archive.status.RecordingPos;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.status.SystemCounterDescriptor;
import io.aeron.logbuffer.LogBufferDescriptor;
import io.scalecube.aeron.examples.AeronHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.agrona.CloseHelper;
import org.agrona.concurrent.SigInt;
import org.agrona.concurrent.status.AtomicCounter;
import org.agrona.concurrent.status.CountersReader;

public class BasicLocalRecorder {

  public static final String CHANNEL = new ChannelUriStringBuilder().media(IPC_MEDIA).build();

  public static final String RECORDING_EVENTS_CHANNEL_ENDPOINT = "localhost:8030";
  public static final String CONTROL_CHANNEL_ENDPOINT = "localhost:8010";
  public static final String REPLICATION_CHANNEL_ENDPOINT = "localhost:0";

  private static MediaDriver mediaDriver;
  private static Aeron aeron;
  private static Archive archive;
  private static AeronArchive aeronArchive;
  private static Publication publication;
  private static final AtomicBoolean running = new AtomicBoolean(true);

  /**
   * Main runner.
   *
   * @param args args
   */
  public static void main(String[] args) throws InterruptedException {
    System.setProperty("aeron.archive.max.concurrent.replays", "1");
    System.setProperty("aeron.event.log", "admin");
    System.setProperty("aeron.event.archive.log", "all");
    EventLogAgent.agentmain("", ByteBuddyAgent.install());

    SigInt.register(BasicLocalRecorder::close);

    Path aeronPath = Paths.get(CommonContext.generateRandomDirName());
    String instanceName = aeronPath.getFileName().toString();
    Path archivePath =
        AeronHelper.archivePath()
            .orElseGet(() -> Paths.get(String.join("-", instanceName, "archive")));

    mediaDriver =
        MediaDriver.launch(
            new MediaDriver.Context()
                .aeronDirectoryName(aeronPath.toString())
                .spiesSimulateConnection(true)
                .ipcTermBufferLength(LogBufferDescriptor.TERM_MIN_LENGTH));

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
                .recordingEventsChannel(recordingEventsChannel())
                .replicationChannel(replicationChannel())
                .aeronDirectoryName(aeronPath.toString())
                .archiveDirectoryName(archivePath.toString())
                .threadingMode(ArchiveThreadingMode.SHARED));

    printArchiveContext(archive.context());

    CountersReader counters = aeron.countersReader();

    aeronArchive =
        AeronArchive.connect(
            new AeronArchive.Context()
                .aeron(aeron)
                .controlResponseChannel(controlResponseChannel()));

    long controlSessionId = aeronArchive.controlSessionId();
    System.out.printf("### controlSessionId: %s%n", controlSessionId);

    // start recorded publication
    publication = aeronArchive.addRecordedPublication(CHANNEL, STREAM_ID);
    int recordingPosCounterId = awaitRecordingPosCounter(counters, publication.sessionId());
    long recordingId = RecordingPos.getRecordingId(counters, recordingPosCounterId);

    printRecordedPublication(aeronArchive, publication, recordingPosCounterId, recordingId);

    long i = 0;
    AeronHelper.recordMessage(counters, publication, i);
    AeronHelper.recordMessage(counters, publication, i);
    AeronHelper.recordMessage(counters, publication, i);

    for (int k = 0; ; k++) {
      RecordingDescriptor rd =
          AeronArchiveUtil.findLastRecording(aeronArchive, rd1 -> rd1.streamId == STREAM_ID);

      final long replaySessionId =
          aeronArchive.startReplay(rd.recordingId, 0, Long.MAX_VALUE, CHANNEL, STREAM_ID);

      final Subscription subscription =
          aeron.addSubscription(ChannelUri.addSessionId(CHANNEL, (int) replaySessionId), STREAM_ID);

      while (!subscription.isConnected()) {
        LockSupport.parkNanos(1);
      }

      System.out.println(k + " | ### subscription : " + subscription);
      System.out.println(k + " | ### closing subscription : " + subscription);

      int finalK = k;
      CloseHelper.close(
          throwable -> System.err.println(finalK + " | [close][error] throwable : " + throwable),
          subscription);

      TimeUnit.SECONDS.sleep(1);
    }
  }

  private static void close() {
    running.set(false);
    CloseHelper.close(publication);
    if (aeronArchive != null) {
      aeronArchive.stopRecording(publication);
    }
    CloseHelper.close(aeron);
    CloseHelper.close(aeronArchive);
    CloseHelper.close(archive);
    CloseHelper.close(mediaDriver);
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
}
