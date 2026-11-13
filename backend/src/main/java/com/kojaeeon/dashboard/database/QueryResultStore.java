package com.kojaeeon.dashboard.database;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class QueryResultStore {

  private record Entry(QueryResult value, Instant expires) {}

  private final LinkedHashMap<String, Entry> values = new LinkedHashMap<>();

  public synchronized void put(QueryResult result) {
    prune();
    values.put(result.id(), new Entry(result, Instant.now().plusSeconds(1800)));
    while (values.size() > 20) values.remove(values.keySet().iterator().next());
  }

  public synchronized QueryResult get(String id) {
    prune();
    var entry = values.get(id);
    if (entry == null) throw new IllegalArgumentException(
      "조회 결과가 만료되었습니다. DB조회에서 다시 실행하세요."
    );
    return entry.value();
  }

  private void prune() {
    values.values().removeIf(value -> value.expires().isBefore(Instant.now()));
  }
}
