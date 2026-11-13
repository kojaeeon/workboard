package com.kojaeeon.dashboard.database;

import java.util.*;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.springframework.stereotype.Component;

@Component
public class SelectQueryPolicy {

  private static final Set<String> WRITE = Set.of(
    "INSERT",
    "UPDATE",
    "DELETE",
    "MERGE",
    "DROP",
    "CREATE",
    "ALTER",
    "TRUNCATE",
    "CALL",
    "GRANT",
    "REVOKE",
    "ATTACH",
    "DETACH",
    "PRAGMA",
    "VACUUM",
    "REPLACE",
    "INTO"
  );

  public void validate(String sql) {
    try {
      var statements = CCJSqlParserUtil.parseStatements(sql).getStatements();
      if (
        statements.size() != 1 || !(statements.getFirst() instanceof Select)
      ) throw new IllegalArgumentException(
        "하나의 SELECT 또는 WITH ... SELECT만 실행할 수 있습니다."
      );
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException(
        "조회용 SQL 문법을 확인하세요. 하나의 SELECT 또는 WITH ... SELECT를 지원합니다."
      );
    }
    // Ignore strings, quoted identifiers, and comments when checking data-changing clauses.
    var token = new StringBuilder();
    for (int i = 0; i < sql.length(); ) {
      char c = sql.charAt(i);
      if (c == '\'' || c == '"' || c == '`' || c == '[') {
        check(token);
        char end = c == '[' ? ']' : c;
        i++;
        while (i < sql.length()) {
          if (sql.charAt(i++) == end) {
            if (i < sql.length() && sql.charAt(i) == end) {
              i++;
              continue;
            }
            break;
          }
        }
      } else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
        check(token);
        while (i < sql.length() && sql.charAt(i) != '\n') i++;
      } else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
        check(token);
        int depth = 1;
        i += 2;
        while (i < sql.length() && depth > 0) {
          if (i + 1 < sql.length() && sql.startsWith("/*", i)) {
            depth++;
            i += 2;
          } else if (i + 1 < sql.length() && sql.startsWith("*/", i)) {
            depth--;
            i += 2;
          } else i++;
        }
      } else if (Character.isLetterOrDigit(c) || c == '_') {
        token.append(c);
        i++;
      } else {
        check(token);
        i++;
      }
    }
    check(token);
  }

  private void check(StringBuilder token) {
    if (
      WRITE.contains(token.toString().toUpperCase(Locale.ROOT))
    ) throw new IllegalArgumentException(
      "DB조회에서는 데이터를 변경하는 SQL을 실행할 수 없습니다."
    );
    token.setLength(0);
  }
}
