package com.kojaeeon.dashboard.database;

import org.springframework.stereotype.Component;

@Component
public class SqliteDialect implements DatabaseDialect {

  public String type() {
    return "sqlite";
  }

  @Override
  public String integerJavaType(String type) {
    return java.util.Set.of("INTEGER", "INT").contains(type) ? "Long" : "Integer";
  }
}
