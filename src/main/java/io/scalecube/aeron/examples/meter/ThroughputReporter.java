package io.scalecube.aeron.examples.meter;

import static io.aeron.Aeron.NULL_VALUE;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;

public class ThroughputReporter implements Agent, AutoCloseable {

  private final Map<String, ThroughputMeter> meters = new ConcurrentHashMap<>();

  private long reportDeadline = NULL_VALUE;

  public ThroughputMeter meter(String name, ThroughputListener... listeners) {
    return meters.computeIfAbsent(
        name,
        s ->
            new ThroughputMeter(
                name, Duration.ofSeconds(1).toNanos(), new CompositeThroughputListener(listeners)));
  }

  public void remove(ThroughputMeter meter) {
    if (meter != null) {
      final ThroughputMeter r = meters.remove(meter.name());
      CloseHelper.quietClose(r);
    }
  }

  @Override
  public void close() {
    meters.values().forEach(ThroughputMeter::close);
  }

  @Override
  public int doWork() {
    final long nanoTime = System.nanoTime();
    if (reportDeadline == NULL_VALUE) {
      reportDeadline = nanoTime + Duration.ofSeconds(1).toNanos();
    }

    if (nanoTime >= reportDeadline) {
      meters.values().forEach(ThroughputMeter::run);
      reportDeadline = NULL_VALUE;
      return 0;
    }

    return 1;
  }

  @Override
  public String roleName() {
    return "throughput-reporter";
  }
}
