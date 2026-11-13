package com.kojaeeon.dashboard.database;

/** SQL identifier rules and type differences used by generated code. */
public interface DatabaseDialect {
  String type();

  default String identifier(String name) {
    return "\"" + name + "\"";
  }

  default String qualifiedTable(String name) {
    return java.util.Arrays.stream(name.split("\\."))
      .map(this::identifier)
      .collect(java.util.stream.Collectors.joining("."));
  }

  default String integerJavaType(String type) {
    return "Integer";
  }
}
