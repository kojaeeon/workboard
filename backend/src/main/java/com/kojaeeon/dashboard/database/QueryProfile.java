package com.kojaeeon.dashboard.database;

import com.kojaeeon.dashboard.codegen.GenerationRequest;
import java.util.*;

/** MD format: connection, query, codegen, and the existing workItem fields. */
public record QueryProfile(
  String title,
  ConnectionSettings connection,
  QuerySettings query,
  GenerationSettings codegen,
  WorkItem workItem
) {
  public QueryProfile {
    title = title == null || title.isBlank() ? "DB 조회" : title;
    if (connection == null || query == null) throw new IllegalArgumentException(
      "connection과 query 항목이 필요합니다."
    );
    codegen =
      codegen == null ? new GenerationSettings(null, null, null, null, null, null) : codegen;
    workItem = workItem == null ? new WorkItem(null, null, null, null) : workItem;
  }

  public record ConnectionSettings(
    String type,
    String path,
    String jdbc_url,
    String username,
    String username_env,
    String password_env,
    Map<String, String> properties,
    Map<String, String> properties_env
  ) {
    public ConnectionSettings {
      if (type == null || type.isBlank()) throw new IllegalArgumentException(
        "connection.type을 입력하세요."
      );
      type = type.toLowerCase(Locale.ROOT);
      properties = properties == null ? Map.of() : Map.copyOf(properties);
      properties_env = properties_env == null ? Map.of() : Map.copyOf(properties_env);
      for (var key : properties.keySet())
        if (key.toLowerCase(Locale.ROOT).contains("password")) throw new IllegalArgumentException(
          "비밀번호 속성은 properties_env로 지정하세요."
        );
      for (var key : properties.keySet())
        if (!key.matches("[A-Za-z][A-Za-z0-9_.]*")) throw new IllegalArgumentException(
          "접속 속성 이름을 확인하세요."
        );
      for (var key : properties_env.keySet())
        if (!key.matches("[A-Za-z][A-Za-z0-9_.]*")) throw new IllegalArgumentException(
          "접속 속성 이름을 확인하세요."
        );
      if (
        jdbc_url != null &&
        jdbc_url.matches(
          "(?is).*(?:[;:])(?:password|sslTrustStorePassword|sslKeyStorePassword)\\s*=.*"
        )
      ) throw new IllegalArgumentException("JDBC URL의 비밀번호를 제거하고 환경변수로 지정하세요.");
    }
  }

  public record QuerySettings(
    String sql,
    Map<String, Object> parameters,
    Integer max_rows,
    Integer timeout_seconds
  ) {
    public QuerySettings {
      if (sql == null || sql.isBlank()) throw new IllegalArgumentException(
        "query.sql을 입력하세요."
      );
      if (sql.length() > 65536) throw new IllegalArgumentException("SQL은 64KB 이하로 작성하세요.");
      parameters =
        parameters == null
          ? Map.of()
          : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
      for (var value : parameters.values())
        if (
          value != null &&
          !(value instanceof String || value instanceof Number || value instanceof Boolean)
        ) throw new IllegalArgumentException(
          "쿼리 매개변수는 문자열·숫자·불리언·null만 지원합니다."
        );
      max_rows = max_rows == null ? 1000 : max_rows;
      timeout_seconds = timeout_seconds == null ? 30 : timeout_seconds;
      if (max_rows < 1 || max_rows > 5000) throw new IllegalArgumentException(
        "max_rows는 1~5000입니다."
      );
      if (timeout_seconds < 1 || timeout_seconds > 120) throw new IllegalArgumentException(
        "timeout_seconds는 1~120입니다."
      );
    }
  }

  public record GenerationSettings(
    String mode,
    Map<String, String> mapping,
    List<String> primary_keys,
    List<String> generated_columns,
    Map<String, String> type_overrides,
    Map<String, String> property_overrides
  ) {
    public GenerationSettings {
      mode = mode == null ? "metadata" : mode;
      if (!Set.of("metadata", "result_columns").contains(mode)) throw new IllegalArgumentException(
        "codegen.mode는 metadata 또는 result_columns입니다."
      );
      mapping = mapping == null ? Map.of() : Map.copyOf(mapping);
      primary_keys = primary_keys == null ? List.of() : List.copyOf(primary_keys);
      generated_columns = generated_columns == null ? List.of() : List.copyOf(generated_columns);
      type_overrides = type_overrides == null ? Map.of() : Map.copyOf(type_overrides);
      property_overrides = property_overrides == null ? Map.of() : Map.copyOf(property_overrides);
      if (
        !Set.of(
          "name",
          "type",
          "nullable",
          "primary_key",
          "pk_position",
          "generated",
          "comment"
        ).containsAll(mapping.keySet())
      ) throw new IllegalArgumentException("codegen.mapping의 필드 이름을 확인하세요.");
    }
  }

  public record WorkItem(String table, String service, String serviceNm, String package_path) {
    public GenerationRequest generationRequest() {
      return new GenerationRequest(table, service, serviceNm, package_path, null);
    }
  }
}
