package io.scalecube.aeron.examples;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import java.util.concurrent.TimeUnit;

public class MeterRegistries {

  public static DropwizardMeterRegistry createMeterRegistry() {
    MetricRegistry metricRegistry = new MetricRegistry();

    ConsoleReporter reporter =
        ConsoleReporter.forRegistry(metricRegistry)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.NANOSECONDS)
            .shutdownExecutorOnStop(false)
            .build();

    reporter.start(1, TimeUnit.SECONDS);

    return new DropwizardMeterRegistry(
        new DropwizardConfig() {
          @Override
          public String prefix() {
            return "console";
          }

          @Override
          public String get(String key) {
            return null;
          }
        },
        metricRegistry,
        HierarchicalNameMapper.DEFAULT,
        Clock.SYSTEM) {

      @Override
      protected Double nullGaugeValue() {
        return null;
      }
    };
  }
}
