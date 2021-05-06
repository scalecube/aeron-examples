package io.scalecube.aeron.examples.meter;

import org.HdrHistogram.Histogram;
import org.agrona.CloseHelper;

public class CompositeLatencyListener implements LatencyListener {

  private final LatencyListener[] listeners;

  public CompositeLatencyListener(LatencyListener... listeners) {
    this.listeners = listeners;
  }

  @Override
  public void onReport(Histogram intervalHistogram) {
    for (LatencyListener latencyListener : listeners) {
      latencyListener.onReport(intervalHistogram);
    }
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(listeners);
  }

  @Override
  public void onTerminate(Histogram accumulatedHistogram) {
    for (LatencyListener latencyListener : listeners) {
      latencyListener.onTerminate(accumulatedHistogram);
    }
  }
}
