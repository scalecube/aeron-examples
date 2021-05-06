package io.scalecube.aeron.examples.meter;

import static io.scalecube.aeron.examples.meter.LatencyReporter.PERCENTILE_TICKS_PER_HALF_DISTANCE;
import static io.scalecube.aeron.examples.meter.LatencyReporter.SCALING_RATIO;

import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.HdrHistogram.Histogram;
import org.agrona.CloseHelper;

public class FileReportingLatencyListener implements LatencyListener {

  private final PrintStream printStream;

  public FileReportingLatencyListener(String name) {
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
  public void onReport(Histogram histogram) {
    histogram.outputPercentileDistribution(
        printStream, PERCENTILE_TICKS_PER_HALF_DISTANCE, SCALING_RATIO, false);
  }

  @Override
  public void onTerminate(Histogram histogram) {
    histogram.outputPercentileDistribution(
        printStream, PERCENTILE_TICKS_PER_HALF_DISTANCE, SCALING_RATIO, false);
  }

  @Override
  public void close() {
    CloseHelper.quietClose(printStream);
  }
}
