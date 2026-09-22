package io.threecolor.worker;

import io.threecolor.supply.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@SpringBootApplication
@Import(SupplyConfiguration.class)
public class WorkerApplication {
  @Bean
  DiscoveryWorker discoveryWorker(DiscoveryQueue queue, PuzzleStore store, Environment env) {
    return new DiscoveryWorker(
        queue,
        store,
        env.getProperty("threecolor.worker.interval-seconds", Integer.class, 10),
        env.getProperty("threecolor.worker.target-per-cell", Integer.class, 0),
        env.getProperty("threecolor.worker.first-seed", Long.class, 1000000L),
        env.getProperty("threecolor.worker.lease-seconds", Integer.class, 600),
        failure -> {
          LoggerFactory.getLogger(DiscoveryWorker.class)
              .error("Discovery failed; exiting for supervisor restart", failure);
          // Exit off the discovery thread so Spring shutdown can join it safely.
          new Thread(() -> System.exit(1), "discovery-fatal-exit").start();
        });
  }
}
