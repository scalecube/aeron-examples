package io.scalecube.aeron.examples.meter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.agrona.CloseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

public class ThroughputReporter implements AutoCloseable {

  public static final Logger LOGGER = LoggerFactory.getLogger(ThroughputReporter.class);

  public static final Duration DEFAULT_REPORT_INTERVAL = Duration.ofSeconds(1);

  private final Duration reportInterval;

  private final Map<String, ThroughputMeter> meters = new ConcurrentHashMap<>();

  private Disposable disposable;

  public ThroughputReporter() {
    this(DEFAULT_REPORT_INTERVAL);
  }

  public ThroughputReporter(Duration reportInterval) {
    this.reportInterval = reportInterval;
  }

  public ThroughputReporter start() {
    disposable =
        Flux.interval(reportInterval)
            .subscribe(i -> run(), throwable -> LOGGER.error("[reportInterval] Error:", throwable));
    return this;
  }

  public ThroughputMeter meter(String name, ThroughputListener... listeners) {
    return meters.computeIfAbsent(
        name,
        s ->
            new ThroughputMeter(
                name, reportInterval.toNanos(), new CompositeThroughputListener(listeners)));
  }

  public void remove(ThroughputMeter meter) {
    if (meter != null) {
      final ThroughputMeter r = meters.remove(meter.name());
      CloseHelper.quietClose(r);
    }
  }

  private void run() {
    meters.values().forEach(ThroughputMeter::run);
  }

  @Override
  public void close() {
    disposable.dispose();
    meters.values().forEach(ThroughputMeter::close);
  }
}
