package com.kojaeeon.dashboard.shared;

import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

  private final JdbcClient db;

  public SettingsController(JdbcClient db) {
    this.db = db;
  }

  @GetMapping
  Map<String, String> get() {
    var values = new java.util.HashMap<String, String>(
      Map.of("app_theme", "light", "mermaid_theme", "default")
    );
    db.sql("SELECT key,value FROM settings")
      .query((r, i) -> Map.entry(r.getString(1), r.getString(2)))
      .list()
      .forEach(e -> values.put(e.getKey(), e.getValue()));
    return values;
  }

  public record Setting(String value) {}

  @PutMapping("/{key}")
  void set(@PathVariable String key, @RequestBody Setting value) {
    Set<String> allowed = switch (key) {
      case "app_theme" -> Set.of("light", "dark");
      case "mermaid_theme" -> Set.of("default", "dark", "forest", "neutral", "base");
      default -> throw new IllegalArgumentException("지원하지 않는 설정입니다.");
    };
    if (
      value.value() == null || !allowed.contains(value.value())
    ) throw new IllegalArgumentException("지원하지 않는 테마입니다.");
    db.sql(
      "INSERT INTO settings(key,value) VALUES (:key,:value) ON CONFLICT(key) DO UPDATE SET value=excluded.value"
    )
      .param("key", key)
      .param("value", value.value())
      .update();
  }
}
