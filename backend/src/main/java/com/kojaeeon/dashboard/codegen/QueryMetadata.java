package com.kojaeeon.dashboard.codegen;

import com.kojaeeon.dashboard.database.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Converts either metadata rows or JDBC result columns into one DB-neutral model. */
@Component
public class QueryMetadata {

  private final Map<String, DatabaseDialect> dialects;

  public QueryMetadata() {
    this(List.of(new SqliteDialect(), new Db2Dialect()));
  }

  @org.springframework.beans.factory.annotation.Autowired
  public QueryMetadata(List<DatabaseDialect> dialects) {
    var map = new HashMap<String, DatabaseDialect>();
    for (var dialect : dialects)
      if (map.put(dialect.type(), dialect) != null) throw new IllegalStateException(
        "중복 DB 방언: " + dialect.type()
      );
    this.dialects = Map.copyOf(map);
  }

  private DatabaseDialect dialect(String type) {
    var dialect = dialects.get(type);
    if (dialect == null) throw new IllegalArgumentException(
      "코드 생성용 DatabaseDialect 구현이 필요합니다: " + type
    );
    return dialect;
  }

  public record Column(
    String name,
    String property,
    String javaType,
    String jdbcType,
    String comment,
    int pkPosition,
    boolean generated,
    boolean nullable,
    String quoted
  ) {}

  public record Model(
    List<Column> columns,
    List<Column> keys,
    List<Column> insertable,
    List<Column> updatable,
    List<String> imports,
    List<String> warnings,
    String quotedTable
  ) {}

  private static final Set<String> KEYWORDS = Set.of(
    "abstract",
    "assert",
    "boolean",
    "break",
    "byte",
    "case",
    "catch",
    "char",
    "class",
    "const",
    "continue",
    "default",
    "do",
    "double",
    "else",
    "enum",
    "extends",
    "final",
    "finally",
    "float",
    "for",
    "goto",
    "if",
    "implements",
    "import",
    "instanceof",
    "int",
    "interface",
    "long",
    "native",
    "new",
    "package",
    "private",
    "protected",
    "public",
    "return",
    "short",
    "static",
    "strictfp",
    "super",
    "switch",
    "synchronized",
    "this",
    "throw",
    "throws",
    "transient",
    "try",
    "void",
    "volatile",
    "while",
    "true",
    "false",
    "null",
    "record",
    "var",
    "yield",
    "sealed",
    "permits",
    "_"
  );

  public Model read(QueryResult result, GenerationRequest request) {
    if (result.truncated()) throw new IllegalArgumentException(
      "잘린 조회 결과로는 코드를 생성할 수 없습니다. 행 제한을 늘리거나 조회 범위를 줄여 다시 실행하세요."
    );
    var settings = result.codegen();
    var columns = new ArrayList<Column>();
    var warnings = new ArrayList<String>();
    var seenNames = new HashSet<String>();
    var seenProperties = new HashSet<String>();
    var imports = new TreeSet<String>();
    if (settings.mode().equals("metadata")) {
      if (result.rows().isEmpty()) throw new IllegalArgumentException(
        "컬럼 메타데이터 조회 결과가 없습니다."
      );
      for (var row : result.rows()) {
        String name = text(field(row, settings, "name", "COLUMN_NAME", "name"));
        String type = text(field(row, settings, "type", "DATA_TYPE", "type"));
        if (name.isBlank() || type.isBlank()) throw new IllegalArgumentException(
          "조회 결과의 컬럼명·타입이 필요합니다. codegen.mapping의 name과 type을 확인하세요."
        );
        Object pk = field(row, settings, "pk_position", "PK_POSITION", "pk");
        int position =
          pk == null
            ? truth(field(row, settings, "primary_key", "PRIMARY_KEY", "is_pk"))
              ? 1
              : 0
            : position(pk);
        Object nullable = field(row, settings, "nullable", "NULLABLE");
        boolean canBeNull = nullable == null || truth(nullable);
        add(
          columns,
          imports,
          warnings,
          result,
          settings,
          name,
          type,
          position,
          truth(field(row, settings, "generated", "IS_GENERATED", "generated")),
          canBeNull,
          text(field(row, settings, "comment", "COMMENT", "REMARKS")),
          seenNames,
          seenProperties
        );
      }
    } else {
      for (var column : result.columns())
        add(
          columns,
          imports,
          warnings,
          result,
          settings,
          column.label(),
          column.typeName(),
          0,
          false,
          column.nullable(),
          "",
          seenNames,
          seenProperties
        );
    }
    if (columns.isEmpty()) throw new IllegalArgumentException("생성할 컬럼이 없습니다.");
    for (String configured : settings.primary_keys())
      if (
        columns.stream().noneMatch(c -> c.name().equalsIgnoreCase(configured))
      ) throw new IllegalArgumentException(
        "primary_keys의 컬럼을 조회 결과에서 찾을 수 없습니다: " + configured
      );
    for (String configured : settings.generated_columns())
      if (
        columns.stream().noneMatch(c -> c.name().equalsIgnoreCase(configured))
      ) throw new IllegalArgumentException(
        "generated_columns의 컬럼을 조회 결과에서 찾을 수 없습니다: " + configured
      );
    var keys = columns
      .stream()
      .filter(c -> c.pkPosition() > 0)
      .sorted(Comparator.comparingInt(Column::pkPosition))
      .toList();
    var insertable = columns
      .stream()
      .filter(c -> !c.generated())
      .toList();
    var updatable = columns
      .stream()
      .filter(c -> c.pkPosition() == 0 && !c.generated())
      .toList();
    if (keys.isEmpty()) warnings.add(
      "PK 정보가 없어 단건 조회·수정·삭제 코드는 생성하지 않았습니다."
    );
    if (insertable.isEmpty()) warnings.add(
      "입력 가능한 컬럼이 없어 INSERT 코드는 생성하지 않았습니다."
    );
    if (updatable.isEmpty()) warnings.add(
      "수정 가능한 컬럼이 없어 UPDATE 코드는 생성하지 않았습니다."
    );
    String table = dialect(result.databaseType()).qualifiedTable(request.table_name());
    return new Model(
      List.copyOf(columns),
      keys,
      insertable,
      updatable,
      List.copyOf(imports),
      List.copyOf(warnings),
      table
    );
  }

  private void add(
    List<Column> columns,
    Set<String> imports,
    List<String> warnings,
    QueryResult result,
    QueryProfile.GenerationSettings settings,
    String name,
    String sqlType,
    int pk,
    boolean generated,
    boolean nullable,
    String comment,
    Set<String> names,
    Set<String> properties
  ) {
    if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException(
      "코드 생성 컬럼명은 영문·숫자·밑줄 형식이어야 합니다: " + name
    );
    if (!names.add(name.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException(
      "중복 컬럼 별칭을 구분하세요: " + name
    );
    if (!settings.primary_keys().isEmpty()) pk = index(settings.primary_keys(), name) + 1;
    if (index(settings.generated_columns(), name) >= 0) generated = true;
    String property = lookup(settings.property_overrides(), name);
    if (property == null) property = camel(name);
    if (
      !property.matches("[A-Za-z_][A-Za-z0-9_]*") || KEYWORDS.contains(property)
    ) throw new IllegalArgumentException(
      "property_overrides의 Java 필드명을 확인하세요: " + property
    );
    if (!properties.add(property)) throw new IllegalArgumentException(
      "Java 필드명이 중복됩니다. property_overrides를 지정하세요: " + property
    );
    String base = sqlType.toUpperCase(Locale.ROOT).replaceFirst("\\(.*", "").strip();
    String jdbc = jdbcType(base);
    String type = lookup(settings.type_overrides(), name);
    if (type == null) {
      type = javaType(base, result.databaseType());
      if (type == null) {
        type = "String";
        warnings.add(
          name +
            "의 자료형 " +
            sqlType +
            "을 String으로 매핑했습니다. type_overrides와 TypeHandler를 검토하세요."
        );
      }
    }
    if (
      !type.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*(?:\\[\\])?")
    ) throw new IllegalArgumentException("Java 자료형 형식을 확인하세요: " + type);
    if (type.contains(".")) {
      imports.add(type.replace("[]", ""));
      type = type.substring(type.lastIndexOf('.') + 1);
    }
    comment = comment.replace("*/", "* /").replaceAll("\\R", " ");
    columns.add(
      new Column(
        name,
        property,
        type,
        jdbc,
        comment,
        pk,
        generated,
        nullable,
        dialect(result.databaseType()).identifier(name)
      )
    );
  }

  private Object field(
    Map<String, Object> row,
    QueryProfile.GenerationSettings settings,
    String field,
    String... defaults
  ) {
    String alias = settings.mapping().get(field);
    if (alias != null) return entry(row, alias);
    for (var candidate : defaults) {
      Object value = entry(row, candidate);
      if (value != null) return value;
    }
    return null;
  }

  private Object entry(Map<String, Object> row, String key) {
    return row
      .entrySet()
      .stream()
      .filter(e -> e.getKey().equalsIgnoreCase(key))
      .map(Map.Entry::getValue)
      .filter(Objects::nonNull)
      .findFirst()
      .orElse(null);
  }

  private String text(Object value) {
    return value == null ? "" : value.toString().strip();
  }

  private int position(Object value) {
    try {
      int position = Integer.parseInt(text(value));
      if (position < 0) throw new NumberFormatException();
      return position;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("PK 순서는 0 이상의 정수여야 합니다.");
    }
  }

  private boolean truth(Object value) {
    if (value instanceof Boolean bool) return bool;
    if (value instanceof Number number) return number.doubleValue() != 0;
    return Set.of("true", "1", "y", "yes", "t", "pk", "p").contains(
      text(value).toLowerCase(Locale.ROOT)
    );
  }

  private int index(List<String> values, String name) {
    for (int i = 0; i < values.size(); i++) if (values.get(i).equalsIgnoreCase(name)) return i;
    return -1;
  }

  private String lookup(Map<String, String> map, String name) {
    return map
      .entrySet()
      .stream()
      .filter(e -> e.getKey().equalsIgnoreCase(name))
      .map(Map.Entry::getValue)
      .findFirst()
      .orElse(null);
  }

  public static String quote(String value) {
    return "\"" + value + "\"";
  }

  private String camel(String name) {
    String[] parts = (
      name.equals(name.toUpperCase(Locale.ROOT)) ? name.toLowerCase(Locale.ROOT) : name
    ).split("_+");
    var value = new StringBuilder();
    for (String part : parts)
      if (!part.isEmpty()) value
        .append(
          value.isEmpty()
            ? Character.toLowerCase(part.charAt(0))
            : Character.toUpperCase(part.charAt(0))
        )
        .append(part.substring(1));
    String result = value.toString();
    return KEYWORDS.contains(result) ? result + "Value" : result;
  }

  private String javaType(String type, String database) {
    return switch (type) {
      case "INTEGER", "INT", "INT4", "SMALLINT", "TINYINT" -> dialect(database).integerJavaType(
        type
      );
      case "BIGINT", "INT8" -> "Long";
      case "DECIMAL", "NUMERIC", "NUMBER", "DECFLOAT" -> "java.math.BigDecimal";
      case "REAL" -> "Float";
      case "FLOAT", "DOUBLE", "DOUBLE PRECISION" -> "Double";
      case "BOOLEAN", "BOOL", "BIT" -> "Boolean";
      case "DATE" -> "java.time.LocalDate";
      case "TIME" -> "java.time.LocalTime";
      case "TIMESTAMP", "DATETIME" -> "java.time.LocalDateTime";
      case "BLOB", "BINARY", "VARBINARY", "LONG VARBINARY" -> "byte[]";
      case
        "CHAR",
        "CHARACTER",
        "VARCHAR",
        "CHARACTER VARYING",
        "TEXT",
        "CLOB",
        "GRAPHIC",
        "VARGRAPHIC",
        "DBCLOB",
        "LONG VARCHAR",
        "LONG VARGRAPHIC" -> "String";
      default -> null;
    };
  }

  private String jdbcType(String type) {
    return switch (type) {
      case "INTEGER", "INT", "INT4" -> "INTEGER";
      case "SMALLINT", "TINYINT" -> "SMALLINT";
      case "BIGINT", "INT8" -> "BIGINT";
      case "DECIMAL", "NUMERIC", "NUMBER", "DECFLOAT" -> "DECIMAL";
      case "REAL" -> "REAL";
      case "FLOAT", "DOUBLE", "DOUBLE PRECISION" -> "DOUBLE";
      case "BOOLEAN", "BOOL", "BIT" -> "BOOLEAN";
      case "DATE" -> "DATE";
      case "TIME" -> "TIME";
      case "TIMESTAMP", "DATETIME" -> "TIMESTAMP";
      case "BLOB", "BINARY", "VARBINARY", "LONG VARBINARY" -> "BLOB";
      default -> "VARCHAR";
    };
  }
}
