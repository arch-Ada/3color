package io.threecolor.worker;

import static org.junit.jupiter.api.Assertions.*;

import io.threecolor.difficulty.PlayDifficulty;
import io.threecolor.generation.*;
import io.threecolor.supply.DiscoveryQueue;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class DiscoveryWorkerTest {
  static class Work implements DiscoveryWorker.Operations {
    final DiscoveryQueue.Lease lease =
        new DiscoveryQueue.Lease(
            "test", PlayDifficulty.Category.EASY, PuzzleSize.SMALL, 1, UUID.randomUUID());
    final CountDownLatch searching = new CountDownLatch(1), released = new CountDownLatch(1);
    final AtomicInteger completed = new AtomicInteger(), renewals = new AtomicInteger();
    boolean fail, loseLease, failHeartbeat, failRelease, block = true;
    final AtomicBoolean claimed = new AtomicBoolean();

    public void initialize() {}

    public Optional<DiscoveryQueue.Lease> claim() {
      return claimed.compareAndSet(false, true) ? Optional.of(lease) : Optional.empty();
    }

    public boolean heartbeat(DiscoveryQueue.Lease lease) {
      renewals.incrementAndGet();
      if (failHeartbeat) throw new IllegalStateException("heartbeat bug");
      return !loseLease;
    }

    public DeletionGenerator.Outcome search(DiscoveryQueue.Lease lease) {
      searching.countDown();
      if (fail) throw new IllegalStateException("search bug");
      if (block)
        try {
          new CountDownLatch(1).await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      return new DeletionGenerator.Outcome(null, null, null, "NO_MATCH");
    }

    public void complete(
        DiscoveryQueue.Lease lease, DeletionGenerator.Outcome result, long millis) {
      completed.incrementAndGet();
    }

    public void release(DiscoveryQueue.Lease lease) throws SQLException {
      released.countDown();
      if (failRelease) throw new SQLException("release unavailable");
    }
  }

  @Test
  void shutdownInterruptsSearchReleasesLeaseAndDoesNotPublish() throws Exception {
    var work = new Work();
    var fatal = new AtomicReference<Throwable>();
    var worker = new DiscoveryWorker(work, 10, 60000, fatal::set);
    worker.start();
    try {
      assertTrue(work.searching.await(5, TimeUnit.SECONDS));
    } finally {
      worker.stop();
    }
    assertTrue(work.released.await(5, TimeUnit.SECONDS));
    assertEquals(0, work.completed.get());
    assertFalse(worker.isRunning());
    assertNull(fatal.get());
  }

  @Test
  void lostLeaseInterruptsAttemptWithoutKillingWorkerOrPublishing() throws Exception {
    var work = new Work();
    work.loseLease = true;
    var fatal = new AtomicReference<Throwable>();
    var worker = new DiscoveryWorker(work, 10, 10, fatal::set);
    worker.start();
    try {
      assertTrue(work.released.await(5, TimeUnit.SECONDS));
      assertTrue(worker.isRunning());
      assertEquals(0, work.completed.get());
      assertNull(fatal.get());
    } finally {
      worker.stop();
    }
  }

  @Test
  void unexpectedSearchAndHeartbeatFailuresReleaseAndSignalFatalExit() throws Exception {
    for (boolean heartbeat : List.of(false, true)) {
      var work = new Work();
      work.fail = !heartbeat;
      work.failHeartbeat = heartbeat;
      var fatal = new CountDownLatch(1);
      var worker = new DiscoveryWorker(work, 10, 10, failure -> fatal.countDown());
      worker.start();
      try {
        assertTrue(fatal.await(5, TimeUnit.SECONDS));
        assertTrue(work.released.await(5, TimeUnit.SECONDS));
        assertFalse(worker.isRunning());
        assertEquals(0, work.completed.get());
      } finally {
        worker.stop();
      }
    }
  }

  @Test
  void releaseFailureDoesNotHideUnexpectedSearchFailure() throws Exception {
    var work = new Work();
    work.fail = true;
    work.failRelease = true;
    var fatal = new CountDownLatch(1);
    var failure = new AtomicReference<Throwable>();
    var worker =
        new DiscoveryWorker(
            work,
            10,
            60000,
            error -> {
              failure.set(error);
              fatal.countDown();
            });
    worker.start();
    try {
      assertTrue(fatal.await(5, TimeUnit.SECONDS));
      assertEquals("search bug", failure.get().getCause().getMessage());
      assertEquals(1, failure.get().getCause().getSuppressed().length);
    } finally {
      worker.stop();
    }
  }

  @Test
  void completedAttemptReleasesLease() throws Exception {
    var work = new Work();
    work.block = false;
    var worker = new DiscoveryWorker(work, 10, 60000, failure -> fail(failure));
    worker.start();
    try {
      assertTrue(work.released.await(5, TimeUnit.SECONDS));
      assertEquals(1, work.completed.get());
      assertEquals(0, work.renewals.get());
    } finally {
      worker.stop();
    }
  }
}
