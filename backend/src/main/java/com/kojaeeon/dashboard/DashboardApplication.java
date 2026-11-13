package com.kojaeeon.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DashboardApplication {

  public static void main(String[] args) throws Exception {
    if (args.length == 2 && args[0].equals("--ui-test-worker")) {
      try {
        System.exit(
          com.kojaeeon.dashboard.automation.UiTestWorker.run(java.nio.file.Path.of(args[1]))
        );
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(1);
      }
      return;
    }
    if (args.length > 0 && args[0].equals("--install-browsers")) {
      String browser = args.length > 1 ? args[1] : "chromium";
      if (
        !java.util.Set.of("chromium", "firefox", "webkit").contains(browser)
      ) throw new IllegalArgumentException("지원하지 않는 브라우저입니다.");
      com.microsoft.playwright.CLI.main(new String[] { "install", browser });
      return;
    }
    SpringApplication.run(DashboardApplication.class, args);
  }
}
