package io.scalecube.aeron.examples.meter;

import java.util.function.Function;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.DynamicCompositeAgent;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

public class MeterRegistry implements AutoCloseable {

  private final LatencyReporter latencyReporter;
  private final ThroughputReporter throughputReporter;
  private final Function<String, LatencyListener> latencyListenerFactory;
  private final Function<String, ThroughputListener> throughputListenerFactory;
  private final AgentRunner agentRunner;

  private MeterRegistry(LatencyReporter latencyReporter, ThroughputReporter throughputReporter) {
    this.latencyReporter = latencyReporter;
    this.throughputReporter = throughputReporter;
    this.latencyListenerFactory = FileReportingLatencyListener::new;
    this.throughputListenerFactory = FileReportingThroughputListener::new;

    agentRunner =
        new AgentRunner(
            new SleepingMillisIdleStrategy(1),
            System.err::println,
            null,
            new DynamicCompositeAgent("compositeAgent", latencyReporter, throughputReporter));
  }

  private void start() {
    AgentRunner.startOnThread(agentRunner);
  }

  public static MeterRegistry create() {
    final MeterRegistry meterRegistry =
        new MeterRegistry(new LatencyReporter(), new ThroughputReporter());
    meterRegistry.start();
    return meterRegistry;
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
    CloseHelper.quietCloseAll(agentRunner, latencyReporter, throughputReporter);
  }
}
