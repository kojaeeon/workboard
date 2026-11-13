package com.kojaeeon.dashboard.automation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kojaeeon.dashboard.shared.Frontmatter;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ScenarioParser {

  public record Viewport(Integer width, Integer height) {
    public Viewport {
      width = width == null ? 1440 : width;
      height = height == null ? 1000 : height;
      if (
        width < 1 || width > 16384 || height < 1 || height > 16384
      ) throw new IllegalArgumentException("브라우저 크기는 1~16384입니다.");
    }
  }

  public record BrowserConfig(
    String browser,
    String executable_path,
    Viewport viewport,
    String base_url,
    Integer timeout,
    Boolean headless
  ) {
    public BrowserConfig {
      browser = browser == null ? "chromium" : browser;
      viewport = viewport == null ? new Viewport(null, null) : viewport;
      base_url = base_url == null ? "http://127.0.0.1:8080" : base_url;
      timeout = timeout == null ? 10000 : timeout;
      headless = headless == null ? false : headless;
      if (
        !Set.of("chromium", "firefox", "webkit").contains(browser)
      ) throw new IllegalArgumentException("지원하는 브라우저는 chromium, firefox, webkit입니다.");
      validateUrl(base_url);
      checkTimeout(timeout);
      if (executable_path != null) executable_path = executable_path.strip().isEmpty()
        ? null
        : executable_path.strip();
    }
  }

  public record Step(
    String action,
    String label,
    String selector,
    String id,
    String role,
    String name,
    String text,
    Map<String, String> attr,
    String syscode,
    Boolean exact,
    Integer nth,
    JsonNode value,
    String url,
    Integer timeout
  ) {
    public Step {
      label = empty(label);
      selector = empty(selector);
      id = empty(id);
      role = empty(role);
      name = empty(name);
      text = empty(text);
      syscode = empty(syscode);
      url = empty(url);
      attr = attr == null ? Map.of() : Map.copyOf(attr);
      exact = exact == null ? true : exact;
      if (
        action == null ||
        !Set.of(
          "goto",
          "menu",
          "click",
          "dblclick",
          "text",
          "fill",
          "select",
          "check",
          "uncheck",
          "press",
          "wait",
          "assert_text",
          "assert_visible",
          "screenshot",
          "popup",
          "frame",
          "main_frame"
        ).contains(action)
      ) throw new IllegalArgumentException("지원하지 않는 action: " + action);
      if (value != null && !value.isValueNode()) throw new IllegalArgumentException(
        "value는 문자열·숫자·불리언이어야 합니다."
      );
      boolean hasValue = value != null && !value.isNull();
      if (action.equals("goto") && url.isEmpty() && !hasValue) throw new IllegalArgumentException(
        "goto: url 또는 value가 필요합니다."
      );
      if (
        action.equals("frame") && selector.isEmpty() && !hasValue
      ) throw new IllegalArgumentException("frame: iframe selector 또는 value가 필요합니다.");
      if (
        !Set.of("goto", "frame", "screenshot", "main_frame").contains(action) &&
        selector.isEmpty() &&
        id.isEmpty() &&
        role.isEmpty() &&
        label.isEmpty() &&
        text.isEmpty() &&
        attr.isEmpty() &&
        syscode.isEmpty()
      ) throw new IllegalArgumentException(action + ": 대상을 지정하세요.");
      if (
        Set.of("text", "fill", "select", "press", "assert_text").contains(action) && !hasValue
      ) throw new IllegalArgumentException(action + ": value가 필요합니다.");
      if (nth != null && nth < 0) throw new IllegalArgumentException("nth는 0 이상이어야 합니다.");
      if (timeout != null) checkTimeout(timeout);
      if (
        attr
          .keySet()
          .stream()
          .anyMatch(key -> !key.matches("[A-Za-z_][\\w:.-]*"))
      ) throw new IllegalArgumentException("attr 키는 HTML 속성 이름이어야 합니다.");
    }

    public String target() {
      for (String s : List.of(label, name, selector, id, text, url, syscode))
        if (!s.isEmpty()) return s;
      return "";
    }

    public String stringValue() {
      return value == null || value.isNull()
        ? ""
        : value.isBoolean()
          ? value.asBoolean()
            ? "True"
            : "False"
          : value.asText();
    }
  }

  public record Scenario(
    String title,
    Map<String, Object> work_item,
    BrowserConfig browser,
    List<Step> steps
  ) {}

  private final Frontmatter frontmatter;
  private final ObjectMapper json;

  public ScenarioParser(Frontmatter frontmatter, ObjectMapper json) {
    this.frontmatter = frontmatter;
    this.json = json;
  }

  private static String empty(String value) {
    return Objects.requireNonNullElse(value, "");
  }

  static void checkTimeout(int value) {
    if (value < 100 || value > 120000) throw new IllegalArgumentException(
      "timeout은 100~120000ms입니다."
    );
  }

  public static void validateUrl(String value) {
    try {
      var uri = URI.create(value);
      if (
        !Set.of("http", "https").contains(empty(uri.getScheme())) ||
        uri.getRawAuthority() == null ||
        uri.getRawAuthority().isBlank()
      ) throw new IllegalArgumentException();
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("시작 URL은 http:// 또는 https:// 주소여야 합니다.");
    }
  }

  private <T> T convert(Object value, Class<T> type) {
    try {
      return json.convertValue(value, type);
    } catch (IllegalArgumentException e) {
      Throwable cause = e;
      while (cause.getCause() != null) cause = cause.getCause();
      throw new IllegalArgumentException("프론트매터 검증 실패: " + cause.getMessage());
    }
  }

  public Scenario parse(String markdown) {
    var metadata = frontmatter.read(markdown);
    if (
      !(metadata.get("step") instanceof List<?> raw) || raw.isEmpty() || raw.size() > 500
    ) throw new IllegalArgumentException("step에 1~500개 단계가 필요합니다.");
    if (
      !(metadata.getOrDefault("workItem", Map.of()) instanceof Map<?, ?>)
    ) throw new IllegalArgumentException("workItem은 key: value 형식이어야 합니다.");
    @SuppressWarnings("unchecked")
    var work = (Map<String, Object>) metadata.getOrDefault("workItem", Map.of());
    var browser = convert(metadata.getOrDefault("browser", Map.of()), BrowserConfig.class);
    if (browser == null) throw new IllegalArgumentException(
      "browser는 key: value 형식이어야 합니다."
    );
    String title = String.valueOf(
      metadata.getOrDefault(
        "title",
        work.getOrDefault("serviceNm", work.getOrDefault("menu", "UI 시나리오"))
      )
    );
    return new Scenario(
      title,
      work,
      browser,
      raw
        .stream()
        .map(value -> {
          var step = convert(value, Step.class);
          if (step == null) throw new IllegalArgumentException(
            "step은 key: value 형식이어야 합니다."
          );
          return step;
        })
        .toList()
    );
  }

  public ObjectNode browserOverride(String markdown) {
    var metadata = frontmatter.read(markdown);
    if (!metadata.containsKey("browser")) throw new IllegalArgumentException(
      "browser 항목이 필요합니다."
    );
    Object raw = metadata.get("browser") instanceof Map<?, ?> ? metadata.get("browser") : metadata;
    convert(raw, BrowserConfig.class);
    return json.valueToTree(raw);
  }

  public BrowserConfig effective(
    BrowserConfig base,
    String overrideMarkdown,
    String baseUrl,
    Boolean headed
  ) {
    ObjectNode result = json.valueToTree(base);
    if (overrideMarkdown != null && !overrideMarkdown.isBlank()) {
      ObjectNode override = browserOverride(overrideMarkdown);
      if (override.has("viewport") && override.get("viewport").isObject()) {
        ObjectNode viewport = (ObjectNode) result.get("viewport");
        viewport.setAll((ObjectNode) override.get("viewport"));
        override.set("viewport", viewport);
      }
      result.setAll(override);
    }
    if (baseUrl != null) result.put("base_url", baseUrl);
    if (headed != null) result.put("headless", !headed);
    return convert(result, BrowserConfig.class);
  }
}
