package com.kojaeeon.dashboard.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;

public final class ReportFiles {

  private ReportFiles() {}

  public static void write(ObjectMapper json, Path output, TestReport report) throws IOException {
    Path temporary = Files.createTempFile(output, ".result-", ".tmp");
    try {
      json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), report);
      // Windows can briefly lock result.json while the dashboard reads it.
      // Keep replacing the complete file; never truncate a report being polled.
      for (int attempt = 0; attempt < 20; attempt++) {
        try {
          try {
            Files.move(
              temporary,
              output.resolve("result.json"),
              StandardCopyOption.REPLACE_EXISTING,
              StandardCopyOption.ATOMIC_MOVE
            );
          } catch (AtomicMoveNotSupportedException e) {
            Files.move(
              temporary,
              output.resolve("result.json"),
              StandardCopyOption.REPLACE_EXISTING
            );
          }
          return;
        } catch (IOException e) {
          if (attempt == 19) throw e;
          try {
            Thread.sleep(25);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("결과 저장이 중단되었습니다.", interrupted);
          }
        }
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
