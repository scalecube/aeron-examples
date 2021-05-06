package io.scalecube.aeron.examples.meter;

import org.HdrHistogram.Histogram;

public interface LatencyListener extends AutoCloseable {

  /**
   * Called for a latency report.
   *
   * @param intervalHistogram the histogram
   */
  void onReport(Histogram intervalHistogram);

  /**
   * Called for an accumulated result.
   *
   * @param accumulatedHistogram the histogram
   */
  void onTerminate(Histogram accumulatedHistogram);
}
