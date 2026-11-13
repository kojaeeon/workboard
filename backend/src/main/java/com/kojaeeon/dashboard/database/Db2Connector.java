package com.kojaeeon.dashboard.database;

import java.sql.*;
import java.util.Properties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class Db2Connector implements DatabaseConnector {

  private final Environment environment;

  public Db2Connector(Environment environment) {
    this.environment = environment;
  }

  public String type() {
    return "db2";
  }

  private String secret(String name) {
    if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException(
      "접속정보의 환경변수 이름을 확인하세요."
    );
    String value = environment.getProperty(name);
    if (value == null || value.isEmpty()) throw new IllegalArgumentException(
      "환경변수 " + name + "이 설정되지 않았습니다."
    );
    return value;
  }

  public Connection open(QueryProfile.ConnectionSettings settings) throws SQLException {
    if (
      settings.jdbc_url() == null || !settings.jdbc_url().startsWith("jdbc:db2://")
    ) throw new IllegalArgumentException(
      "DB2 JDBC URL은 jdbc:db2://host:port/database 형식이어야 합니다."
    );
    var properties = new Properties();
    properties.putAll(settings.properties());
    settings.properties_env().forEach((name, env) -> properties.setProperty(name, secret(env)));
    String username =
      settings.username_env() == null ? settings.username() : secret(settings.username_env());
    if (username == null || username.isBlank()) throw new IllegalArgumentException(
      "DB2 username 또는 username_env를 지정하세요."
    );
    properties.setProperty("user", username);
    properties.setProperty("password", secret(settings.password_env()));
    properties.putIfAbsent("loginTimeout", "10");
    Connection connection = new com.ibm.db2.jcc.DB2Driver().connect(
      settings.jdbc_url(),
      properties
    );
    if (connection == null) throw new SQLException("DB2 JDBC URL을 처리하지 못했습니다.");
    try {
      connection.setReadOnly(true);
    } catch (SQLFeatureNotSupportedException ignored) {
      /* SQL policy and the DB account still apply. */
    } catch (SQLException error) {
      try {
        connection.close();
      } catch (SQLException closeError) {
        error.addSuppressed(closeError);
      }
      throw error;
    }
    return connection;
  }
}
