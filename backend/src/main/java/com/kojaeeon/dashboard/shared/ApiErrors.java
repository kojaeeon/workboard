package com.kojaeeon.dashboard.shared;

import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {

  public record Error(String message) {}

  public static class Conflict extends RuntimeException {

    public Conflict(String message) {
      super(message);
    }
  }

  @ExceptionHandler(Conflict.class)
  ResponseEntity<Error> conflict(Conflict e) {
    return ResponseEntity.status(409).body(new Error(e.getMessage()));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    java.time.DateTimeException.class,
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
  })
  ResponseEntity<Error> invalid(Exception e) {
    String message = e.getMessage();
    if (e instanceof MethodArgumentNotValidException v) {
      message = v.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
    } else if (e instanceof HttpMessageNotReadableException) {
      Throwable cause = e;
      while (cause.getCause() != null) cause = cause.getCause();
      message =
        cause instanceof IllegalArgumentException
          ? cause.getMessage()
          : "요청 데이터 형식을 확인하세요.";
    } else if (e instanceof java.time.DateTimeException) {
      message = "올바른 날짜와 시간을 입력하세요.";
    }
    return ResponseEntity.badRequest().body(new Error(message));
  }

  @ExceptionHandler(IOException.class)
  ResponseEntity<Error> io(IOException e) {
    return ResponseEntity.badRequest().body(
      new Error("파일을 읽거나 저장하지 못했습니다: " + e.getMessage())
    );
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Error> database(DataAccessException e) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
      new Error("데이터를 저장하거나 불러오지 못했습니다. 다시 시도해 주세요.")
    );
  }
}
