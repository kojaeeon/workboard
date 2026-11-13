package com.kojaeeon.dashboard.database;

import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class SqliteConnector implements DatabaseConnector {

  public String type() {
    return "sqlite";
  }

  public Connection open(QueryProfile.ConnectionSettings settings)
    throws SQLException, java.io.IOException {
    if (settings.path() == null || settings.path().isBlank()) throw new IllegalArgumentException(
      "SQLite connection.path를 입력하세요."
    );
    Path path = Path.of(settings.path()).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path)) throw new IllegalArgumentException(
      "SQLite DB 파일이 없습니다. 상대 경로는 대시보드 실행 폴더 기준입니다."
    );
    var properties = new Properties();
    properties.setProperty("busy_timeout", "5000");
    Connection connection = new org.sqlite.JDBC().connect(
      "jdbc:sqlite:" + path.toUri() + "?mode=ro",
      properties
    );
    try (var statement = connection.createStatement()) {
      statement.execute("PRAGMA query_only=ON");
    } catch (SQLException error) {
      connection.close();
      throw error;
    }
    return connection;
  }
}
