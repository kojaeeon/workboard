package com.kojaeeon.dashboard.database;

import java.util.*;

/** Contains only query data and generation settings; never retains credentials. */
public record QueryResult(
  String id,
  String title,
  String databaseType,
  List<Column> columns,
  List<Map<String, Object>> rows,
  boolean truncated,
  long elapsedMs,
  QueryProfile.WorkItem workItem,
  QueryProfile.GenerationSettings codegen
) {
  public record Column(
    String key,
    String label,
    int jdbcType,
    String typeName,
    boolean nullable,
    int precision,
    int scale
  ) {}

  public QueryResult {
    columns = List.copyOf(columns);
    rows = rows
      .stream()
      .map(row -> Collections.unmodifiableMap(new LinkedHashMap<>(row)))
      .toList();
  }
}
