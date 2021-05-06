package io.scalecube.aeron.examples.meter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class LatencyReporter implements AutoCloseable {

  public static final Logger LOGGER = LoggerFactory.getLogger(LatencyReporter.class);

  public static final Duration DEFAULT_REPORT_INTERVAL = Duration.ofSeconds(1);

  public static final long HIGHEST_TRACKABLE_VALUE = TimeUnit.SECONDS.toNanos(60);
  public static final int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 3;
  public static final int PERCENTILE_TICKS_PER_HALF_DISTANCE = 5;
  public static final double SCALING_RATIO = 1000.0;

  private final Duration reportInterval;

  private final Map<String, LatencyMeter> meters = new ConcurrentHashMap<>();

  private Disposable disposable;

  public LatencyReporter() {
    this(DEFAULT_REPORT_INTERVAL);
  }

  public LatencyReporter(Duration reportInterval) {
    this.reportInterval = reportInterval;
  }

  public LatencyReporter start() {
    disposable =
        Flux.interval(reportInterval)
            .doFinally(s -> onTerminate())
            .subscribe(i -> run(), throwable -> LOGGER.error("[reportInterval] Error:", throwable));
    return this;
  }

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

  private void run() {
    meters.values().forEach(LatencyMeter::run);
  }

  private void onTerminate() {
    meters.values().forEach(LatencyMeter::onTerminate);
  }

  @Override
  public void close() {
    disposable.dispose();
    meters.values().forEach(LatencyMeter::close);
  }
}
