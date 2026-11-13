package com.kojaeeon.dashboard.database;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kojaeeon.dashboard.shared.Frontmatter;
import org.springframework.stereotype.Component;

@Component
public class QueryProfileParser {

  private final Frontmatter frontmatter;
  private final ObjectMapper json;

  public QueryProfileParser(Frontmatter frontmatter, ObjectMapper json) {
    this.frontmatter = frontmatter;
    this.json = json;
  }

  public QueryProfile parse(String markdown) {
    ObjectNode value = json.valueToTree(frontmatter.read(markdown));
    if (value.get("workItem") instanceof ObjectNode item && item.has("package")) item.set(
      "package_path",
      item.remove("package")
    );
    try {
      return json.treeToValue(value, QueryProfile.class);
    } catch (Exception error) {
      Throwable cause = error;
      while (cause.getCause() != null) cause = cause.getCause();
      throw new IllegalArgumentException(
        cause instanceof IllegalArgumentException
          ? cause.getMessage()
          : "DB 조회 프론트매터 형식을 확인하세요. 지원 항목은 title, connection, query, codegen, workItem입니다."
      );
    }
  }
}
