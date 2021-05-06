package io.scalecube.aeron.examples.meter;

import org.agrona.CloseHelper;

public class CompositeThroughputListener implements ThroughputListener {

  private final ThroughputListener[] listeners;

  public CompositeThroughputListener(ThroughputListener... listeners) {
    this.listeners = listeners;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(listeners);
  }

  @Override
  public void onReport(double messages) {
    for (ThroughputListener listener : listeners) {
      listener.onReport(messages);
    }
  }
}
