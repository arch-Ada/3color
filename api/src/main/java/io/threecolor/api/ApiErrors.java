package io.threecolor.api;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrors {
  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class
  })
  public ResponseEntity<Dtos.ErrorResponse> invalid(Exception e) {
    String message =
        e instanceof IllegalArgumentException
            ? e.getMessage()
            : "Request fields are missing, malformed, or outside allowed bounds.";
    return ResponseEntity.badRequest()
        .body(new Dtos.ErrorResponse("INVALID_REQUEST", message, Map.of()));
  }
}
