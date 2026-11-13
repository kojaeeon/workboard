package com.kojaeeon.dashboard.database;

import org.springframework.stereotype.Component;

@Component
public class Db2Dialect implements DatabaseDialect {

  public String type() {
    return "db2";
  }

  @Override
  public String identifier(String name) {
    return DatabaseDialect.super.identifier(name.toUpperCase(java.util.Locale.ROOT));
  }
}
