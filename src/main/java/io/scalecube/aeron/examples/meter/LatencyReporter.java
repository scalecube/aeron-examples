package io.scalecube.aeron.examples.meter;

import static io.aeron.Aeron.NULL_VALUE;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;

public class LatencyReporter implements Agent, AutoCloseable {

  public static final long LOWEST_TRACKABLE_VALUE = 1;
  public static final long HIGHEST_TRACKABLE_VALUE = TimeUnit.SECONDS.toNanos(3600);
  public static final int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 0;
  public static final int PERCENTILE_TICKS_PER_HALF_DISTANCE = 5;
  public static final double SCALING_RATIO = 1000;

  private final Map<String, LatencyMeter> meters = new ConcurrentHashMap<>();

  private long reportDeadline = NULL_VALUE;

  public LatencyMeter meter(String name, LatencyListener... listeners) {
    return meters.computeIfAbsent(
        name, s -> new LatencyMeter(name, new CompositeLatencyListener(listeners)));
  }

  public void remove(LatencyMeter meter) {
    if (meter != null) {
      final LatencyMeter r = meters.remove(meter.name());
      CloseHelper.quietClose(r);
    }
  }

  @Override
  public void close() {
    meters.values().forEach(LatencyMeter::close);
  }

  @Override
  public int doWork() {
    final long nanoTime = System.nanoTime();
    if (reportDeadline == NULL_VALUE) {
      reportDeadline = nanoTime + Duration.ofSeconds(1).toNanos();
    }

    if (nanoTime >= reportDeadline) {
      meters.values().forEach(LatencyMeter::run);
      reportDeadline = NULL_VALUE;
      return 0;
    }

    return 1;
  }

  @Override
  public String roleName() {
    return "latency-reporter";
  }
}
