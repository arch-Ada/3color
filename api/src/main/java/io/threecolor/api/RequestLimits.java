package io.threecolor.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds both declared and chunked bodies before JSON allocation; limits concurrent CPU work. */
@Component
public class RequestLimits extends OncePerRequestFilter {
  private static final int MAX_BYTES = 131072;
  private final Semaphore work = new Semaphore(2);

  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean post = request.getMethod().equals("POST");
    boolean replay =
        request.getMethod().equals("GET")
            && request.getRequestURI().matches("/api/v1/puzzles/supply/[0-9a-f]{64}");
    if (!request.getRequestURI().startsWith("/api/v1/") || !(post || replay)) {
      chain.doFilter(request, response);
      return;
    }
    if (!work.tryAcquire()) {
      error(response, 429, "BUSY", "Two analyses are already running. Try again shortly.");
      return;
    }
    try {
      if (!post) {
        chain.doFilter(request, response);
        return;
      }
      if (request.getContentLengthLong() > MAX_BYTES) {
        error(response, 413, "BODY_TOO_LARGE", "Maximum request size is 128 KiB.");
        return;
      }
      byte[] bytes = request.getInputStream().readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) {
        error(response, 413, "BODY_TOO_LARGE", "Maximum request size is 128 KiB.");
        return;
      }
      chain.doFilter(
          new HttpServletRequestWrapper(request) {
            public ServletInputStream getInputStream() {
              var input = new ByteArrayInputStream(bytes);
              return new ServletInputStream() {
                public int read() {
                  return input.read();
                }

                public int read(byte[] b, int off, int len) {
                  return input.read(b, off, len);
                }

                public boolean isFinished() {
                  return input.available() == 0;
                }

                public boolean isReady() {
                  return true;
                }

                public void setReadListener(ReadListener listener) {
                  throw new UnsupportedOperationException("Synchronous MVC only");
                }
              };
            }

            public BufferedReader getReader() {
              return new BufferedReader(
                  new InputStreamReader(getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            }
          },
          response);
    } finally {
      work.release();
    }
  }

  private void error(HttpServletResponse r, int status, String code, String message)
      throws IOException {
    r.setStatus(status);
    r.setContentType("application/json");
    r.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
  }
}
