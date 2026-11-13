package com.kojaeeon.dashboard.automation;

import com.fasterxml.jackson.databind.*;
import com.kojaeeon.dashboard.shared.Frontmatter;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import java.nio.file.*;

/** Entrypoint used by the isolated child JVM, without starting Spring or SQLite. */
public final class UiTestWorker {

  private UiTestWorker() {}

  public static int run(Path output) throws Exception {
    var json = new ObjectMapper().findAndRegisterModules();
    var parser = new ScenarioParser(new Frontmatter(), json);
    var scenario = parser.parse(Files.readString(output.resolve("scenario.md")));
    var config = json.readValue(
      output.resolve("browser.json").toFile(),
      ScenarioParser.BrowserConfig.class
    );
    var report = new TestReport(scenario);
    report.status = "running";
    ReportFiles.write(json, output, report);
    try (
      var playwright = Playwright.create(
        new Playwright.CreateOptions().setEnv(
          java.util.Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
        )
      )
    ) {
      BrowserType type = switch (config.browser()) {
        case "firefox" -> playwright.firefox();
        case "webkit" -> playwright.webkit();
        default -> playwright.chromium();
      };
      var options = new BrowserType.LaunchOptions()
        .setHeadless(config.headless())
        .setTimeout(config.timeout());
      if (config.executable_path() != null) {
        Path executable = Path.of(config.executable_path());
        if (!Files.isRegularFile(executable)) throw new IllegalArgumentException(
          "브라우저 실행 파일이 없습니다: " + executable
        );
        options.setExecutablePath(executable);
      }
      try (
        var browser = type.launch(options);
        var context = browser.newContext(
          new Browser.NewContextOptions().setViewportSize(
            config.viewport().width(),
            config.viewport().height()
          )
        )
      ) {
        context.setDefaultTimeout(config.timeout());
        context.setDefaultNavigationTimeout(config.timeout());
        context
          .tracing()
          .start(
            new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true)
          );
        var actions = new PlaywrightActions(
          context.newPage(),
          config.base_url(),
          output,
          config.timeout()
        );
        try {
          actions.checkStop();
          actions
            .page()
            .navigate(
              config.base_url(),
              new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(config.timeout())
            );
          for (int i = 0; i < scenario.steps().size(); i++) {
            actions.checkStop();
            var current = report.steps.get(i);
            current.status = "running";
            ReportFiles.write(json, output, report);
            long start = System.nanoTime();
            try {
              current.strategy = actions.execute(scenario.steps().get(i), i + 1);
              current.status = "passed";
            } catch (PlaywrightActions.Cancelled e) {
              current.status = "cancelled";
              throw e;
            } catch (Throwable e) {
              current.status = "failed";
              current.error = message(e);
              try {
                actions
                  .page()
                  .screenshot(
                    new Page.ScreenshotOptions()
                      .setPath(output.resolve("failure.png"))
                      .setFullPage(true)
                      .setTimeout(5000)
                  );
              } catch (Exception ignored) {}
              throw e;
            } finally {
              current.duration_ms = Math.round((System.nanoTime() - start) / 1_000_000d);
              ReportFiles.write(json, output, report);
            }
          }
          actions
            .page()
            .screenshot(
              new Page.ScreenshotOptions()
                .setPath(output.resolve("final.png"))
                .setFullPage(true)
                .setTimeout(config.timeout())
            );
          report.status = "passed";
        } finally {
          context.tracing().stop(new Tracing.StopOptions().setPath(output.resolve("trace.zip")));
        }
      }
    } catch (PlaywrightActions.Cancelled e) {
      report.status = "cancelled";
    } catch (Throwable e) {
      report.status = Files.exists(output.resolve("stop")) ? "cancelled" : "failed";
      report.error = message(e);
    } finally {
      for (var step : report.steps) {
        if (step.status.equals("pending")) step.status = "skipped";
        else if (step.status.equals("running")) step.status = report.status.equals("cancelled")
          ? "cancelled"
          : "failed";
      }
      ReportFiles.write(json, output, report);
    }
    return report.status.equals("passed") ? 0 : 1;
  }

  private static String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }
}
