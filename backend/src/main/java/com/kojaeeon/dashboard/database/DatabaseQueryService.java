package com.kojaeeon.dashboard.database;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

@Service
public class DatabaseQueryService {

  public record Description(
    String title,
    String databaseType,
    int maxRows,
    int timeoutSeconds,
    QueryProfile.WorkItem workItem,
    QueryProfile.GenerationSettings codegen
  ) {}

  public record ConnectionInfo(String databaseType, String product, String version) {}

  private final QueryProfileParser parser;
  private final SelectQueryPolicy policy;
  private final QueryResultStore results;
  private final Map<String, DatabaseConnector> connectors;
  private final ScheduledExecutorService timer = Executors.newScheduledThreadPool(2, r -> {
    var thread = new Thread(r, "database-query-timeout");
    thread.setDaemon(true);
    return thread;
  });

  public DatabaseQueryService(
    QueryProfileParser parser,
    SelectQueryPolicy policy,
    QueryResultStore results,
    List<DatabaseConnector> connectors
  ) {
    this.parser = parser;
    this.policy = policy;
    this.results = results;
    var map = new LinkedHashMap<String, DatabaseConnector>();
    for (var connector : connectors)
      if (map.put(connector.type(), connector) != null) throw new IllegalStateException(
        "중복 DB 연결 유형: " + connector.type()
      );
    this.connectors = Map.copyOf(map);
  }

  public Description describe(String markdown) {
    var profile = parser.parse(markdown);
    connector(profile);
    policy.validate(profile.query().sql());
    return new Description(
      profile.title(),
      profile.connection().type(),
      profile.query().max_rows(),
      profile.query().timeout_seconds(),
      profile.workItem(),
      profile.codegen()
    );
  }

  private DatabaseConnector connector(QueryProfile profile) {
    var connector = connectors.get(profile.connection().type());
    if (connector == null) throw new IllegalArgumentException(
      "지원하지 않는 DB 유형입니다: " + profile.connection().type()
    );
    return connector;
  }

  public ConnectionInfo test(String markdown) throws IOException {
    var profile = parser.parse(markdown);
    try (var connection = connector(profile).open(profile.connection())) {
      var metadata = connection.getMetaData();
      return new ConnectionInfo(
        profile.connection().type(),
        metadata.getDatabaseProductName(),
        metadata.getDatabaseProductVersion()
      );
    } catch (SQLException error) {
      throw sqlError(error, false);
    }
  }

  public QueryResult execute(String markdown) throws IOException {
    var profile = parser.parse(markdown);
    policy.validate(profile.query().sql());
    long start = System.nanoTime();
    var expired = new AtomicBoolean(false);
    try (var connection = connector(profile).open(profile.connection())) {
      connection.setAutoCommit(false);
      try {
        var jdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
        QueryResult result = jdbc.execute(
          profile.query().sql(),
          profile.query().parameters(),
          statement -> {
            statement.setMaxRows(profile.query().max_rows() + 1);
            statement.setQueryTimeout(profile.query().timeout_seconds());
            var timeout = timer.schedule(
              () -> {
                expired.set(true);
                try {
                  statement.cancel();
                } catch (SQLException ignored) {}
              },
              profile.query().timeout_seconds(),
              TimeUnit.SECONDS
            );
            try (var rows = statement.executeQuery()) {
              var metadata = rows.getMetaData();
              var columns = new ArrayList<QueryResult.Column>();
              var keys = new HashSet<String>();
              for (int index = 1; index <= metadata.getColumnCount(); index++) {
                String label = metadata.getColumnLabel(index),
                  key = label;
                int suffix = 2;
                while (!keys.add(key)) key = label + "_" + suffix++;
                columns.add(
                  new QueryResult.Column(
                    key,
                    label,
                    metadata.getColumnType(index),
                    metadata.getColumnTypeName(index),
                    metadata.isNullable(index) != ResultSetMetaData.columnNoNulls,
                    metadata.getPrecision(index),
                    metadata.getScale(index)
                  )
                );
              }
              var records = new ArrayList<Map<String, Object>>();
              boolean truncated = false;
              long characters = 0;
              while (rows.next()) {
                if (records.size() >= profile.query().max_rows()) {
                  truncated = true;
                  break;
                }
                var record = new LinkedHashMap<String, Object>();
                for (int index = 1; index <= columns.size(); index++) {
                  Object value = rows.getObject(index);
                  if (value instanceof java.sql.Blob blob) {
                    try (var input = blob.getBinaryStream()) {
                      byte[] bytes = input.readNBytes(65537);
                      if (bytes.length > 65536) {
                        truncated = true;
                        bytes = Arrays.copyOf(bytes, 65536);
                      }
                      value = Base64.getEncoder().encodeToString(bytes);
                    } catch (IOException e) {
                      throw new SQLException("BLOB 조회 실패", e);
                    } finally {
                      blob.free();
                    }
                  } else if (value instanceof java.sql.Clob clob) {
                    try (var reader = clob.getCharacterStream()) {
                      var buffer = new char[65537];
                      int count = 0,
                        read;
                      while (
                          count < buffer.length &&
                          (read = reader.read(buffer, count, buffer.length - count)) != -1
                        )
                        count += read;
                      if (count > 65536) truncated = true;
                      value = new String(buffer, 0, Math.min(count, 65536));
                    } catch (IOException e) {
                      throw new SQLException("CLOB 조회 실패", e);
                    } finally {
                      clob.free();
                    }
                  } else if (value instanceof byte[] bytes) {
                    if (bytes.length > 65536) {
                      truncated = true;
                      bytes = Arrays.copyOf(bytes, 65536);
                    }
                    value = Base64.getEncoder().encodeToString(bytes);
                  } else if (
                    value != null && !(value instanceof Number || value instanceof Boolean)
                  ) value = value.toString();
                  if (value instanceof String text && text.length() > 65536) {
                    value = text.substring(0, 65536);
                    truncated = true;
                  }
                  characters += value == null ? 4 : value.toString().length();
                  record.put(columns.get(index - 1).key(), value);
                }
                if (characters > 5_000_000) {
                  truncated = true;
                  break;
                }
                records.add(record);
              }
              if (expired.get()) throw new SQLException("조회 시간 초과");
              return new QueryResult(
                UUID.randomUUID().toString(),
                profile.title(),
                profile.connection().type(),
                columns,
                records,
                truncated,
                Math.round((System.nanoTime() - start) / 1_000_000d),
                profile.workItem(),
                profile.codegen()
              );
            } finally {
              timeout.cancel(false);
            }
          }
        );
        if (result == null) throw new IllegalArgumentException("조회 결과를 읽지 못했습니다.");
        results.put(result);
        return result;
      } finally {
        try {
          connection.rollback();
        } catch (SQLException ignored) {}
      }
    } catch (org.springframework.dao.DataAccessException error) {
      Throwable cause = error;
      while (cause.getCause() != null && !(cause instanceof SQLException)) cause = cause.getCause();
      throw cause instanceof SQLException sql
        ? sqlError(sql, expired.get())
        : new IllegalArgumentException("쿼리 매개변수와 SQL을 확인하세요.");
    } catch (SQLException error) {
      throw sqlError(error, expired.get());
    }
  }

  private IllegalArgumentException sqlError(SQLException error, boolean timeout) {
    return new IllegalArgumentException(
      timeout
        ? "조회 제한시간을 초과했습니다."
        : "DB 연결 또는 조회 실패 (SQLState=" +
            Objects.toString(error.getSQLState(), "없음") +
            ", code=" +
            error.getErrorCode() +
            "). 접속정보·SQL·조회 권한을 확인하세요."
    );
  }

  @PreDestroy
  public void close() {
    timer.shutdownNow();
  }
}
