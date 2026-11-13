package com.kojaeeon.dashboard.automation;

import com.kojaeeon.dashboard.documents.MarkdownSources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ui-tests")
public class UiTestController {

  public record ValidationInput(String markdown, String browserMarkdown) {}

  public record Validation(
    String title,
    java.util.List<ScenarioParser.Step> steps,
    ScenarioParser.BrowserConfig browser
  ) {}

  private final ScenarioParser parser;
  private final UiTestRuns runs;
  private final MarkdownSources sources;

  public UiTestController(ScenarioParser parser, UiTestRuns runs, MarkdownSources sources) {
    this.parser = parser;
    this.runs = runs;
    this.sources = sources;
  }

  @GetMapping("/sources")
  public MarkdownSources.Listing sources() {
    return sources.list(false, false, true);
  }

  @GetMapping("/source")
  public MarkdownSources.Content source(@RequestParam String key) throws IOException {
    return sources.read(key, false, false, true);
  }

  @PostMapping("/validate")
  public Validation validate(@RequestBody ValidationInput input) {
    var scenario = parser.parse(input.markdown());
    return new Validation(
      scenario.title(),
      scenario.steps(),
      parser.effective(scenario.browser(), input.browserMarkdown(), null, null)
    );
  }

  @PostMapping("/browser")
  public ScenarioParser.BrowserConfig browser(@RequestBody ValidationInput input) {
    var base = parser.parse(input.markdown()).browser();
    return parser.effective(base, input.browserMarkdown(), null, null);
  }

  @PostMapping("/runs")
  public UiTestRuns.Snapshot start(@RequestBody UiTestRuns.Input input) throws IOException {
    return runs.start(input);
  }

  @GetMapping("/runs/{id}")
  public UiTestRuns.Snapshot snapshot(@PathVariable String id) throws IOException {
    return runs.snapshot(id);
  }

  @PostMapping("/runs/{id}/stop")
  public void stop(@PathVariable String id) throws IOException {
    runs.stop(id);
  }

  @GetMapping("/runs/{id}/files/{name:.+}")
  public ResponseEntity<Resource> file(
    @PathVariable String id,
    @PathVariable String name,
    @RequestParam(defaultValue = "false") boolean preview
  ) {
    var path = runs.file(id, name);
    String type = name.endsWith(".png")
      ? "image/png"
      : name.endsWith(".json")
        ? "application/json"
        : name.endsWith(".zip")
          ? "application/zip"
          : "text/plain";
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(type))
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        (preview && name.endsWith(".png")
          ? ContentDisposition.inline()
          : ContentDisposition.attachment()
        )
          .filename(name, StandardCharsets.UTF_8)
          .build()
          .toString()
      )
      .body(new FileSystemResource(path));
  }
}
