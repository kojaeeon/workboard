package com.kojaeeon.dashboard.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kojaeeon.dashboard.DashboardApplication;
import com.kojaeeon.dashboard.shared.ApiErrors.Conflict;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

@Service
public class UiTestRuns {

  public record Input(
    String markdown,
    String browserMarkdown,
    String baseUrl,
    Boolean headed,
    String clientId
  ) {}

  public record Snapshot(
    String id,
    String title,
    String status,
    String error,
    List<TestReport.StepResult> steps,
    boolean running,
    List<String> files,
    String output
  ) {}

  private record Run(String owner, Path directory, Process process) {}

  private final Path root;
  private final ScenarioParser parser;
  private final ObjectMapper json;
  private final Map<String, Run> runs = new ConcurrentHashMap<>();
  private final ScheduledExecutorService canceller = Executors.newSingleThreadScheduledExecutor(
    r -> {
      var thread = new Thread(r, "ui-test-cleanup");
      thread.setDaemon(true);
      return thread;
    }
  );

  public UiTestRuns(
    ScenarioParser parser,
    ObjectMapper json,
    @Value("${DASHBOARD_UI_TEST_PATH:data/ui_tests}") String root
  ) {
    this.parser = parser;
    this.json = json;
    this.root = Path.of(root).toAbsolutePath().normalize();
  }

  public synchronized Snapshot start(Input input) throws IOException {
    if (
      input.clientId() == null || !input.clientId().matches("[A-Za-z0-9_-]{1,100}")
    ) throw new IllegalArgumentException("실행 세션 ID가 필요합니다.");
    if (
      runs
        .values()
        .stream()
        .anyMatch(run -> run.owner().equals(input.clientId()) && run.process().isAlive())
    ) throw new Conflict("이미 실행 중입니다.");
    var scenario = parser.parse(input.markdown());
    var config = parser.effective(
      scenario.browser(),
      input.browserMarkdown(),
      input.baseUrl(),
      input.headed()
    );
    String id =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now()) +
      "-" +
      UUID.randomUUID().toString().substring(0, 8);
    Path directory = root.resolve(id);
    Files.createDirectories(directory);
    Files.writeString(directory.resolve("scenario.md"), input.markdown());
    json
      .writerWithDefaultPrettyPrinter()
      .writeValue(directory.resolve("browser.json").toFile(), config);
    ReportFiles.write(json, directory, new TestReport(scenario));
    var command = new ArrayList<String>();
    command.add(
      Path.of(
        System.getProperty("java.home"),
        "bin",
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java"
      ).toString()
    );
    command.add("--enable-native-access=ALL-UNNAMED");
    var source = new ApplicationHome(DashboardApplication.class).getSource();
    if (source != null && source.isFile() && source.getName().endsWith(".jar")) {
      command.add("-jar");
      command.add(source.getAbsolutePath());
    } else {
      command.add("-cp");
      command.add(System.getProperty("java.class.path"));
      command.add(DashboardApplication.class.getName());
    }
    command.add("--ui-test-worker");
    command.add(directory.toString());
    try {
      var process = new ProcessBuilder(command)
        .directory(Path.of("").toAbsolutePath().toFile())
        .redirectErrorStream(true)
        .redirectOutput(directory.resolve("worker.log").toFile())
        .start();
      runs.put(id, new Run(input.clientId(), directory, process));
      process.onExit().thenRun(() -> {
        try {
          snapshot(id);
        } catch (IOException ignored) {}
      });
    } catch (IOException e) {
      var report = new TestReport(scenario);
      report.status = "failed";
      report.error = "실행 프로세스를 시작하지 못했습니다.";
      ReportFiles.write(json, directory, report);
      throw e;
    }
    return snapshot(id);
  }

  private Path directory(String id) {
    if (id == null || !id.matches("\\d{8}T\\d{6}Z-[a-f0-9]{8}")) throw new IllegalArgumentException(
      "실행 ID를 확인하세요."
    );
    Path directory = root.resolve(id).normalize();
    if (
      !directory.getParent().equals(root) || !Files.isDirectory(directory)
    ) throw new IllegalArgumentException("실행 결과를 찾을 수 없습니다.");
    return directory;
  }

  public synchronized Snapshot snapshot(String id) throws IOException {
    Path directory = directory(id);
    var run = runs.get(id);
    boolean running = run != null && run.process().isAlive();
    var report = json.readValue(directory.resolve("result.json").toFile(), TestReport.class);
    if (!running && Set.of("starting", "running").contains(report.status)) {
      report.status = Files.exists(directory.resolve("stop")) ? "cancelled" : "failed";
      report.error = report.status.equals("cancelled")
        ? ""
        : "실행 프로세스가 종료되었습니다. worker.log를 확인하세요.";
      for (var step : report.steps) {
        if (step.status.equals("pending")) step.status = "skipped";
        else if (step.status.equals("running")) step.status = report.status.equals("cancelled")
          ? "cancelled"
          : "failed";
      }
      ReportFiles.write(json, directory, report);
    }
    List<String> files;
    try (var paths = Files.list(directory)) {
      files = paths
        .filter(Files::isRegularFile)
        .map(p -> p.getFileName().toString())
        .filter(this::artifact)
        .sorted()
        .toList();
    }
    return new Snapshot(
      id,
      report.title,
      report.status,
      report.error,
      report.steps,
      running,
      files,
      directory.toString()
    );
  }

  public synchronized void stop(String id) throws IOException {
    Path directory = directory(id);
    var run = runs.get(id);
    if (run != null && run.process().isAlive()) {
      Files.writeString(directory.resolve("stop"), "");
      canceller.schedule(
        () -> {
          if (run.process().isAlive()) kill(run.process());
        },
        3,
        TimeUnit.SECONDS
      );
    }
  }

  private void kill(Process process) {
    var children = process.descendants().toList();
    children.reversed().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
  }

  private boolean artifact(String name) {
    return name.matches("[A-Za-z0-9_.-]+\\.(json|zip|png|log)");
  }

  public Path file(String id, String name) {
    Path directory = directory(id);
    if (!artifact(name)) throw new IllegalArgumentException("다운로드할 결과 파일을 확인하세요.");
    Path path = directory.resolve(name);
    if (!Files.isRegularFile(path)) throw new IllegalArgumentException("결과 파일이 없습니다.");
    return path;
  }

  @PreDestroy
  public void close() {
    runs
      .values()
      .stream()
      .filter(r -> r.process().isAlive())
      .forEach(r -> kill(r.process()));
    canceller.shutdownNow();
  }
}
