package io.scalecube.aeron.examples.meter;

import java.util.concurrent.atomic.LongAdder;
import org.agrona.CloseHelper;

public class ThroughputMeter implements AutoCloseable {

  private final String name;
  private final long reportIntervalNs;
  private final ThroughputListener listener;

  private final LongAdder totalMessages = new LongAdder();

  private long lastTotalBytes;
  private long lastTotalMessages;
  private long lastTimestamp;

  ThroughputMeter(String name, long reportIntervalNs, ThroughputListener listener) {
    this.name = name;
    this.reportIntervalNs = reportIntervalNs;
    this.listener = listener;
  }

  public String name() {
    return name;
  }

  public void record() {
    record(1);
  }

  public void record(long messages) {
    totalMessages.add(messages);
  }

  void run() {
    long currentTotalMessages = totalMessages.longValue();
    long currentTimestamp = System.nanoTime();

    long timeSpanNs = currentTimestamp - lastTimestamp;
    double messagesPerSec =
        ((currentTotalMessages - lastTotalMessages) * (double) reportIntervalNs)
            / (double) timeSpanNs;

    lastTotalMessages = currentTotalMessages;
    lastTimestamp = currentTimestamp;

    listener.onReport(messagesPerSec);
  }

  @Override
  public void close() {
    CloseHelper.quietClose(listener);
  }
}
