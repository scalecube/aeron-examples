package io.scalecube.aeron.examples.meter;

import org.HdrHistogram.Histogram;
import org.agrona.CloseHelper;

public class LatencyMeter implements AutoCloseable {

  private final String name;
  private final LatencyListener listener;
  private final org.HdrHistogram.Recorder histogram;

  private Histogram accumulatedHistogram;

  LatencyMeter(String name, LatencyListener listener) {
    this.name = name;
    this.listener = listener;
    this.histogram =
        new org.HdrHistogram.Recorder(
            LatencyReporter.HIGHEST_TRACKABLE_VALUE,
            LatencyReporter.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);
  }

  public String name() {
    return name;
  }

  public void record(long value) {
    histogram.recordValue(value);
  }

  void run() {
    Histogram intervalHistogram = histogram.getIntervalHistogram();
    if (accumulatedHistogram != null) {
      accumulatedHistogram.add(intervalHistogram);
    } else {
      accumulatedHistogram = intervalHistogram;
    }
    listener.onReport(intervalHistogram);
  }

  void onTerminate() {
    if (accumulatedHistogram != null) {
      listener.onTerminate(accumulatedHistogram);
    }
  }

  @Override
  public void close() {
    histogram.reset();
    CloseHelper.quietClose(listener);
  }
}
