package io.threecolor.worker;

import io.threecolor.generation.*;
import io.threecolor.supply.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/** Owns one discovery thread and its lease heartbeat; queue operations retain database fencing. */
public final class DiscoveryWorker implements SmartLifecycle {
  interface Operations {
    void initialize() throws SQLException;

    Optional<DiscoveryQueue.Lease> claim() throws SQLException;

    boolean heartbeat(DiscoveryQueue.Lease lease) throws SQLException;

    DeletionGenerator.Outcome search(DiscoveryQueue.Lease lease) throws SQLException;

    void complete(DiscoveryQueue.Lease lease, DeletionGenerator.Outcome result, long millis)
        throws SQLException;

    void release(DiscoveryQueue.Lease lease) throws SQLException;
  }

  private static final org.slf4j.Logger log = LoggerFactory.getLogger(DiscoveryWorker.class);
  private final Operations operations;
  private final long intervalMillis, heartbeatMillis;
  private final Consumer<Throwable> fatal;
  private volatile boolean running;
  private Thread thread;

  DiscoveryWorker(
      Operations operations, long intervalMillis, long heartbeatMillis, Consumer<Throwable> fatal) {
    this.operations = operations;
    this.intervalMillis = intervalMillis;
    this.heartbeatMillis = heartbeatMillis;
    this.fatal = fatal;
  }

  public DiscoveryWorker(
      DiscoveryQueue queue,
      PuzzleStore store,
      int interval,
      int target,
      long first,
      int seconds,
      Consumer<Throwable> fatal) {
    this(
        new Operations() {
          private PuzzleBank bank;
          private String version;

          public void initialize() throws SQLException {
            bank = PuzzleBank.bundled();
            store.importBank(bank, SupplyIdentity.bankHash());
            version = SupplyIdentity.searchVersion();
            queue.initialize(version, first);
          }

          public Optional<DiscoveryQueue.Lease> claim() throws SQLException {
            return queue.claim(version, target, Duration.ofSeconds(seconds));
          }

          public boolean heartbeat(DiscoveryQueue.Lease lease) throws SQLException {
            return queue.heartbeat(lease, Duration.ofSeconds(seconds));
          }

          public DeletionGenerator.Outcome search(DiscoveryQueue.Lease lease) throws SQLException {
            var excluded = store.topologies(lease.size().minimum(), lease.size().maximum());
            return new RangeGenerator()
                .search(
                    lease.category(),
                    lease.size().minimum(),
                    lease.size().maximum(),
                    lease.seed(),
                    bank,
                    excluded);
          }

          public void complete(
              DiscoveryQueue.Lease lease, DeletionGenerator.Outcome result, long millis)
              throws SQLException {
            String outcome = queue.complete(lease, result, millis);
            log.info(
                "{} {} seed={} outcome={}", lease.category(), lease.size(), lease.seed(), outcome);
          }

          public void release(DiscoveryQueue.Lease lease) throws SQLException {
            queue.release(lease);
          }
        },
        interval * 1000L,
        seconds / 3 * 1000L,
        fatal);
    if (interval < 1 || target < 0 || seconds < 60)
      throw new IllegalArgumentException("Invalid worker limits");
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public synchronized void start() {
    if (running) return;
    running = true;
    thread = new Thread(this::run, "puzzle-discovery");
    thread.setUncaughtExceptionHandler((ignored, failure) -> fatal.accept(failure));
    thread.start();
  }

  @Override
  public void stop() {
    running = false;
    var worker = thread;
    if (worker == null) return;
    worker.interrupt();
    if (worker == Thread.currentThread()) return;
    try {
      worker.join(25000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (worker.isAlive()) log.warn("Discovery did not stop within the shutdown allowance");
  }

  private void run() {
    var heartbeat = Executors.newSingleThreadScheduledExecutor();
    try {
      operations.initialize();
      while (running) {
        try {
          var lease = operations.claim();
          if (lease.isPresent()) runAttempt(lease.get(), heartbeat);
        } catch (SQLException | DiscoveryQueue.LostLease e) {
          log.warn("Discovery deferred: {}", e.getClass().getSimpleName());
        }
        if (running) Thread.sleep(intervalMillis);
      }
    } catch (InterruptedException e) {
      if (running) throw new IllegalStateException("Discovery interrupted unexpectedly", e);
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      throw new IllegalStateException("Discovery stopped unexpectedly", e);
    } finally {
      running = false;
      heartbeat.shutdownNow();
    }
  }

  private void runAttempt(DiscoveryQueue.Lease lease, ScheduledExecutorService scheduler)
      throws SQLException {
    var guard = new LeaseGuard(lease);
    ScheduledFuture<?> pulse = null;
    long start = System.nanoTime();
    Throwable failure = null;
    try {
      pulse =
          scheduler.scheduleAtFixedRate(
              guard::renew, heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
      var result = operations.search(lease);
      // Database completion additionally checks lease ownership atomically.
      if (guard.canPublish()
          && running
          && !Thread.currentThread().isInterrupted()
          && !"INTERRUPTED".equals(result.failure()))
        operations.complete(
            lease, result, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
    } catch (SQLException | RuntimeException | Error e) {
      failure = e;
      throw e;
    } finally {
      guard.close();
      if (pulse != null) pulse.cancel(false);
      // A lost lease interrupts this attempt, not the next one. Shutdown is tracked by running.
      Thread.interrupted();
      try {
        operations.release(lease);
      } catch (SQLException cleanup) {
        if (failure != null) failure.addSuppressed(cleanup);
        else {
          guard.rethrowFailure();
          throw cleanup;
        }
      }
      guard.rethrowFailure();
    }
  }

  private final class LeaseGuard {
    private final DiscoveryQueue.Lease lease;
    private boolean active = true, lost;
    private RuntimeException failure;

    LeaseGuard(DiscoveryQueue.Lease lease) {
      this.lease = lease;
    }

    synchronized void renew() {
      if (!active) return;
      try {
        if (operations.heartbeat(lease)) return;
      } catch (SQLException e) {
        log.warn("Discovery heartbeat unavailable");
      } catch (RuntimeException e) {
        failure = e;
      }
      lost = true;
      thread.interrupt();
    }

    synchronized boolean canPublish() {
      return !lost;
    }

    synchronized void close() {
      active = false;
    }

    synchronized void rethrowFailure() {
      if (failure != null) throw failure;
    }
  }
}
