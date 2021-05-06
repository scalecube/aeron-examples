package io.scalecube.aeron.examples.meter;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FileReportingThroughputListener implements ThroughputListener {

  private final PrintStream printStream;

  private long totalMessages;
  private long seconds;

  public FileReportingThroughputListener(String name) {
    try {
      final String fileName =
          String.join(
              "-",
              name,
              LocalDateTime.now(ZoneId.of("UTC"))
                  .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH.mm.ss.SSS")));
      printStream = new PrintStream(fileName);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void onReport(double messages) {
    totalMessages += messages;
    seconds++;
    printStream.println(
        String.format("%.07g", messages) + " msgs/sec, " + totalMessages + " messages");
  }

  @Override
  public void close() {
    printStream.println("Throughput average: ");
    printStream.println(
        String.format("%.07g", (double) totalMessages / seconds)
            + " msgs/sec, "
            + totalMessages
            + " messages");
  }
}
