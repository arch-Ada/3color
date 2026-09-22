package io.threecolor.api;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RequestLimitsTest {
  private MockHttpServletRequest request(String method, String uri) {
    return new MockHttpServletRequest(method, uri);
  }

  @Test
  void replayAndPostShareSlotsOrdinaryGetsBypassAndFailuresRelease() throws Exception {
    var filter = new RequestLimits();
    var replay = "/api/v1/puzzles/supply/" + "a".repeat(64);
    for (String method : new String[] {"GET", "POST"}) {
      assertThrows(
          ServletException.class,
          () ->
              filter.doFilter(
                  request(method, method.equals("GET") ? replay : "/api/v1/hints"),
                  new MockHttpServletResponse(),
                  (a, b) -> {
                    throw new ServletException("test");
                  }));
    }
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    FilterChain hold =
        (a, b) -> {
          entered.countDown();
          try {
            if (!release.await(10, TimeUnit.SECONDS)) throw new ServletException("Timed out");
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServletException(e);
          }
        };
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                filter.doFilter(
                    request("POST", "/api/v1/hints"), new MockHttpServletResponse(), hold);
                return null;
              });
      var second =
          pool.submit(
              () -> {
                filter.doFilter(request("GET", replay), new MockHttpServletResponse(), hold);
                return null;
              });
      try {
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        for (String method : new String[] {"GET", "POST"}) {
          var response = new MockHttpServletResponse();
          filter.doFilter(
              request(method, method.equals("GET") ? replay : "/api/v1/hints"),
              response,
              (a, b) -> fail("Should be limited"));
          assertEquals(429, response.getStatus());
          assertTrue(response.getContentAsString().contains("BUSY"));
        }
        var health = new MockHttpServletResponse();
        filter.doFilter(
            request("GET", "/actuator/health"), health, (a, b) -> b.getWriter().write("healthy"));
        assertEquals("healthy", health.getContentAsString());
      } finally {
        release.countDown();
      }
      first.get(5, TimeUnit.SECONDS);
      second.get(5, TimeUnit.SECONDS);
    }
    var response = new MockHttpServletResponse();
    filter.doFilter(request("GET", replay), response, (a, b) -> b.getWriter().write("accepted"));
    assertEquals("accepted", response.getContentAsString());
  }

  @Test
  void oversizedBodiesStillReleaseTheSlot() throws Exception {
    var filter = new RequestLimits();
    for (int i = 0; i < 3; i++) {
      var request = request("POST", "/api/v1/hints");
      request.setContent(new byte[131073]);
      var response = new MockHttpServletResponse();
      filter.doFilter(request, response, (a, b) -> fail("Oversized body admitted"));
      assertEquals(413, response.getStatus());
    }
  }
}
