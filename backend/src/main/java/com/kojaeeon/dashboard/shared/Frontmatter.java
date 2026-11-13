package com.kojaeeon.dashboard.shared;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

@Component
public class Frontmatter {

  private static final Pattern HEADER = Pattern.compile(
    "\\A\\uFEFF?---[ \\t]*\\r?\\n(.*?)\\r?\\n(?:---|\\.\\.\\.)[ \\t]*(?:\\r?\\n|$)",
    Pattern.DOTALL
  );

  public Map<String, Object> read(String markdown) {
    if (
      markdown == null || markdown.getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024
    ) throw new IllegalArgumentException("MD는 2MB 이하로 작성하세요.");
    var match = HEADER.matcher(markdown);
    if (!match.find()) throw new IllegalArgumentException(
      "문서 맨 위에 YAML 프론트매터(---)가 필요합니다."
    );
    var options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(50);
    options.setNestingDepthLimit(50);
    options.setCodePointLimit(2 * 1024 * 1024);
    try {
      Object value = new Yaml(new SafeConstructor(options)).load(match.group(1));
      if (
        !(value instanceof Map<?, ?> raw) ||
        raw
          .keySet()
          .stream()
          .anyMatch(k -> !(k instanceof String))
      ) throw new IllegalArgumentException("프론트매터는 key: value 형식이어야 합니다.");
      @SuppressWarnings("unchecked")
      var metadata = (Map<String, Object>) raw;
      return metadata;
    } catch (org.yaml.snakeyaml.error.YAMLException e) {
      throw new IllegalArgumentException("프론트매터 YAML 형식을 확인하세요.", e);
    }
  }
}
