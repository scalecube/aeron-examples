package io.scalecube.aeron.examples.meter;

public interface ThroughputListener extends AutoCloseable {

  /**
   * Called for a rate report.
   *
   * @param messages number of messages
   */
  void onReport(double messages);
}
