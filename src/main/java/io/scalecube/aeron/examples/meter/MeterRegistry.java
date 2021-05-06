package io.scalecube.aeron.examples.meter;

import java.util.function.Function;
import org.agrona.CloseHelper;

public class MeterRegistry implements AutoCloseable {

  private final LatencyReporter latencyReporter;
  private final ThroughputReporter throughputReporter;
  private final Function<String, LatencyListener> latencyListenerFactory;
  private final Function<String, ThroughputListener> throughputListenerFactory;

  private MeterRegistry(LatencyReporter latencyReporter, ThroughputReporter throughputReporter) {
    this.latencyReporter = latencyReporter;
    this.throughputReporter = throughputReporter;
    this.latencyListenerFactory = FileReportingLatencyListener::new;
    this.throughputListenerFactory = FileReportingThroughputListener::new;
  }

  public static MeterRegistry create() {
    return new MeterRegistry(new LatencyReporter().start(), new ThroughputReporter().start());
  }

  public LatencyMeter latency(String name) {
    return latencyReporter.meter(name, latencyListenerFactory.apply(name));
  }

  public ThroughputMeter tps(String name) {
    return throughputReporter.meter(name, throughputListenerFactory.apply(name));
  }

  public void remove(LatencyMeter meter) {
    latencyReporter.remove(meter);
  }

  public void remove(ThroughputMeter meter) {
    throughputReporter.remove(meter);
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(latencyReporter, throughputReporter);
  }
}
