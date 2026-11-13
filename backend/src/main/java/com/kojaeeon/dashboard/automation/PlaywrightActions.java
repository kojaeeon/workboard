package com.kojaeeon.dashboard.automation;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class PlaywrightActions {

  private Page page;
  private FrameLocator frame;
  private final String baseUrl;
  private final Path output;
  private final int timeout;

  public PlaywrightActions(Page page, String baseUrl, Path output, int timeout) {
    this.page = page;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    this.output = output;
    this.timeout = timeout;
  }

  public Page page() {
    return page;
  }

  private Locator locator(String selector) {
    return frame == null ? page.locator(selector) : frame.locator(selector);
  }

  private String quoted(String value) {
    return (
      "\"" +
      value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\a ")
        .replace("\r", "\\d ") +
      "\""
    );
  }

  private record Candidate(String strategy, Locator locator) {}

  private List<Candidate> candidates(ScenarioParser.Step step) {
    if (!step.selector().isEmpty()) return List.of(
      new Candidate("selector", locator(step.selector()))
    );
    if (!step.id().isEmpty()) return List.of(
      new Candidate("id", locator("[id=" + quoted(step.id()) + "]"))
    );
    if (!step.attr().isEmpty()) {
      String selector = step
        .attr()
        .entrySet()
        .stream()
        .map(e -> "[" + e.getKey() + "=" + quoted(e.getValue()) + "]")
        .collect(java.util.stream.Collectors.joining());
      return List.of(new Candidate("attr", locator(selector)));
    }
    if (!step.syscode().isEmpty()) return List.of(
      new Candidate("syscode", locator("[syscode=" + quoted(step.syscode()) + "]"))
    );
    if (!step.role().isEmpty()) {
      AriaRole role;
      try {
        role = AriaRole.valueOf(step.role().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("지원하지 않는 role: " + step.role());
      }
      String name = step.name().isEmpty() ? step.label() : step.name();
      var options = new Page.GetByRoleOptions().setExact(step.exact());
      if (!name.isEmpty()) options.setName(name);
      var frameOptions = new FrameLocator.GetByRoleOptions().setExact(step.exact());
      if (!name.isEmpty()) frameOptions.setName(name);
      return List.of(
        new Candidate(
          "role",
          frame == null ? page.getByRole(role, options) : frame.getByRole(role, frameOptions)
        )
      );
    }
    var result = new ArrayList<Candidate>();
    if (!step.label().isEmpty()) result.add(
      new Candidate(
        "label",
        frame == null
          ? page.getByLabel(step.label(), new Page.GetByLabelOptions().setExact(step.exact()))
          : frame.getByLabel(
              step.label(),
              new FrameLocator.GetByLabelOptions().setExact(step.exact())
            )
      )
    );
    String text = step.text().isEmpty() ? step.label() : step.text();
    if (!text.isEmpty()) result.add(
      new Candidate(
        "text",
        frame == null
          ? page.getByText(text, new Page.GetByTextOptions().setExact(step.exact()))
          : frame.getByText(text, new FrameLocator.GetByTextOptions().setExact(step.exact()))
      )
    );
    return result;
  }

  private Candidate locate(ScenarioParser.Step step) {
    long deadline =
      System.nanoTime() + (long) (step.timeout() == null ? timeout : step.timeout()) * 1_000_000;
    while (true) {
      checkStop();
      for (var candidate : candidates(step)) {
        var visible = candidate.locator().filter(new Locator.FilterOptions().setVisible(true));
        int count = visible.count();
        if (step.nth() != null) {
          if (count > step.nth()) return new Candidate(
            candidate.strategy(),
            visible.nth(step.nth())
          );
        } else if (count == 1) return new Candidate(candidate.strategy(), visible);
        else if (count > 1) throw new IllegalArgumentException(
          candidate.strategy() +
            ": 보이는 대상이 " +
            count +
            "개입니다. selector / role / nth로 구분하세요."
        );
      }
      if (System.nanoTime() >= deadline) throw new IllegalArgumentException(
        "대상을 찾지 못했습니다: " + step.target()
      );
      page.waitForTimeout(100);
    }
  }

  public void checkStop() {
    if (Files.exists(output.resolve("stop"))) throw new Cancelled();
  }

  public static class Cancelled extends RuntimeException {}

  public String execute(ScenarioParser.Step step, int number) {
    checkStop();
    double limit = step.timeout() == null ? timeout : step.timeout();
    String action = step.action();
    if (action.equals("goto")) {
      String url = URI.create(baseUrl)
        .resolve(step.url().isEmpty() ? step.stringValue() : step.url())
        .toString();
      ScenarioParser.validateUrl(url);
      page.navigate(
        url,
        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(limit)
      );
      frame = null;
      return "url";
    }
    if (action.equals("frame")) {
      frame = page.frameLocator(step.selector().isEmpty() ? step.stringValue() : step.selector());
      return "frame";
    }
    if (action.equals("main_frame")) {
      frame = null;
      return "main_frame";
    }
    if (action.equals("screenshot")) {
      page.screenshot(
        new Page.ScreenshotOptions()
          .setPath(output.resolve("%03d-screenshot.png".formatted(number)))
          .setFullPage(true)
          .setTimeout(limit)
      );
      return "screenshot";
    }
    var found = locate(step);
    var target = found.locator();
    switch (action) {
      case "click", "menu" -> target.click(new Locator.ClickOptions().setTimeout(limit));
      case "dblclick" -> target.dblclick(new Locator.DblclickOptions().setTimeout(limit));
      case "fill", "text" -> target.fill(
        step.stringValue(),
        new Locator.FillOptions().setTimeout(limit)
      );
      case "select" -> target.selectOption(
        step.stringValue(),
        new Locator.SelectOptionOptions().setTimeout(limit)
      );
      case "check" -> target.check(new Locator.CheckOptions().setTimeout(limit));
      case "uncheck" -> target.uncheck(new Locator.UncheckOptions().setTimeout(limit));
      case "press" -> target.press(
        step.stringValue(),
        new Locator.PressOptions().setTimeout(limit)
      );
      case "wait", "assert_visible" -> assertThat(target).isVisible(
        new com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions().setTimeout(
          limit
        )
      );
      case "assert_text" -> assertThat(target).containsText(
        step.stringValue(),
        new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setTimeout(
          limit
        )
      );
      case "popup" -> {
        page = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(limit), () ->
          target.click(new Locator.ClickOptions().setTimeout(limit))
        );
        page.waitForLoadState(
          LoadState.DOMCONTENTLOADED,
          new Page.WaitForLoadStateOptions().setTimeout(limit)
        );
        frame = null;
      }
      default -> throw new IllegalArgumentException("지원하지 않는 action: " + action);
    }
    checkStop();
    return found.strategy();
  }
}
