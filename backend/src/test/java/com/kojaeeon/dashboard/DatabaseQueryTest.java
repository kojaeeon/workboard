package com.kojaeeon.dashboard;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kojaeeon.dashboard.codegen.*;
import com.kojaeeon.dashboard.database.*;
import com.kojaeeon.dashboard.documents.*;
import com.kojaeeon.dashboard.shared.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;

@SpringBootTest
class DatabaseQueryTest {

  static final Path dashboard;

  static {
    try {
      dashboard = Files.createTempDirectory("query-dashboard-").resolve("dashboard.db");
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("dashboard.db-path", () -> dashboard.toString());
  }

  @TempDir
  Path directory;

  @Autowired
  DatabaseQueryService queries;

  @Autowired
  QueryProfileParser parser;

  @Autowired
  SelectQueryPolicy policy;

  @Autowired
  QueryResultStore store;

  @Autowired
  QueryCodeGenerator generator;

  @Autowired
  CodeGenService codegen;

  @Autowired
  QueryProfiles profiles;

  @Autowired
  DocumentRepository documents;

  @Autowired
  LocalDocumentService localFiles;

  private Path fixture() throws Exception {
    Path file = directory.resolve("customer.db");
    try (
      var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
      var statement = connection.createStatement()
    ) {
      statement.execute(
        "CREATE TABLE customer(ID INTEGER PRIMARY KEY, NAME VARCHAR(100) NOT NULL, BALANCE DECIMAL(12,2), CREATED_AT TIMESTAMP, TENANT_ID INTEGER, CALCULATED TEXT GENERATED ALWAYS AS (NAME || '-x') VIRTUAL)"
      );
      statement.execute("INSERT INTO customer(ID,NAME) VALUES(1,'고객'),(2,'두 번째')");
    }
    return file;
  }

  private String metadata(Path file) throws Exception {
    return Files.readString(Path.of("src/main/resources/examples/db-query-sqlite.md"))
      .replace("data/dashboard.db", file.toString().replace('\\', '/'))
      .replace("table: work_items", "table: customer")
      .replace("service: WorkItem", "service: Customer");
  }

  private GenerationRequest request() {
    return new GenerationRequest("customer", "Customer", "고객", "com.example.customer", null);
  }

  @Test
  void sqliteConnectionAndBoundMetadataQueryAreReal() throws Exception {
    Path file = fixture();
    var markdown = metadata(file);
    assertThat(queries.test(markdown).product()).containsIgnoringCase("SQLite");
    var result = queries.execute(markdown);
    assertThat(result.rows()).hasSize(6);
    assertThat(result.rows().getFirst().get("COLUMN_NAME")).isEqualTo("ID");
    assertThat(result.truncated()).isFalse();
    assertThat(store.get(result.id())).isEqualTo(result);
    String serialized = new ObjectMapper().writeValueAsString(result);
    assertThat(serialized).doesNotContain("password_env", "jdbc_url", "username_env");
    var model = new QueryMetadata().read(result, request());
    assertThat(model.keys()).extracting(QueryMetadata.Column::name).containsExactly("ID");
    assertThat(model.insertable())
      .extracting(QueryMetadata.Column::name)
      .doesNotContain("CALCULATED");
    assertThat(model.columns().getFirst().javaType()).isEqualTo("Long");
    assertThat(model.imports()).contains("java.math.BigDecimal", "java.time.LocalDateTime");
  }

  @Test
  void connectorCannotWriteOrCreateDatabase() throws Exception {
    Path file = fixture();
    var connector = new SqliteConnector();
    try (
      var connection = connector.open(parser.parse(metadata(file)).connection());
      var statement = connection.createStatement()
    ) {
      assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM customer")).isInstanceOf(
        SQLException.class
      );
    }
    Path missing = directory.resolve("missing.db");
    assertThatThrownBy(() -> queries.test(metadata(missing))).isInstanceOf(
      IllegalArgumentException.class
    );
    assertThat(missing).doesNotExist();
  }

  @Test
  void rejectsMutationMultipleStatementsAndSelectInto() {
    for (String sql : List.of(
      "DELETE FROM customer",
      "SELECT 1; SELECT 2",
      "WITH x AS (SELECT 1) DELETE FROM customer",
      "SELECT * INTO other FROM customer",
      "SELECT * FROM customer FOR UPDATE"
    ))
      assertThatThrownBy(() -> policy.validate(sql))
        .as(sql)
        .isInstanceOf(IllegalArgumentException.class);
    policy.validate(
      "WITH x AS (SELECT 'DELETE; DROP TABLE customer' AS value) SELECT value FROM x -- UPDATE"
    );
  }

  @Test
  void normalResultColumnsAndNullsGenerateSchemaWithoutInferringPk() throws Exception {
    Path file = fixture();
    String markdown = metadata(file).replace("mode: metadata", "mode: result_columns");
    int begin = markdown.indexOf("  sql: |"),
      end = markdown.indexOf("  parameters:", begin);
    markdown =
      markdown.substring(0, begin) +
      "  sql: |\n    SELECT ID, NAME, BALANCE FROM customer WHERE ID = :id\n" +
      markdown.substring(end);
    markdown = markdown.replace("table: customer\n  max_rows", "id: 1\n  max_rows");
    var result = queries.execute(markdown);
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().getFirst()).containsEntry("BALANCE", null);
    var output = generator.generate(request(), result);
    assertThat(output.warnings()).anyMatch(w -> w.contains("PK 정보"));
    String xml = output
      .files()
      .stream()
      .filter(f -> f.language().equals("xml"))
      .findFirst()
      .orElseThrow()
      .content();
    assertThat(xml).doesNotContain("<update", "<delete", "findById");
  }

  @Test
  void rowCapPreventsPartialMetadataGeneration() throws Exception {
    var result = queries.execute(metadata(fixture()).replace("max_rows: 1000", "max_rows: 1"));
    assertThat(result.rows()).hasSize(1);
    assertThat(result.truncated()).isTrue();
    assertThatThrownBy(() -> generator.generate(request(), result))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("잘린");
  }

  @Test
  void querySnapshotGeneratesCompilableJavaAndBoundMapperXml() throws Exception {
    Path file = fixture();
    var result = queries.execute(metadata(file));
    try (
      var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
      var statement = connection.createStatement()
    ) {
      statement.execute("ALTER TABLE customer ADD COLUMN LATER TEXT");
    }
    var preview = codegen.previewFromQuery(result.id(), request());
    assertThat(preview.files()).hasSize(5);
    assertThat(preview.status()).contains("DB 조회 결과");
    String xml = preview
      .files()
      .stream()
      .filter(f -> f.language().equals("xml"))
      .findFirst()
      .orElseThrow()
      .content();
    assertThat(xml)
      .contains("#{id,jdbcType=INTEGER}", "WHERE \"ID\" =", "<resultMap")
      .doesNotContain("LATER", "${");
    compile(preview.files());
    var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory
      .newDocumentBuilder()
      .parse(
        new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))
      );
  }

  private Path compile(List<CodeGenerator.GeneratedFile> files) throws Exception {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
    var paths = new ArrayList<java.io.File>();
    for (var file : files)
      if (file.language().equals("java")) {
        Path path = directory.resolve(file.path());
        Files.createDirectories(path.getParent());
        Files.writeString(path, file.content());
        paths.add(path.toFile());
      }
    Path classes = Files.createDirectory(directory.resolve("classes"));
    try (
      var manager = compiler.getStandardFileManager(
        diagnostics,
        null,
        java.nio.charset.StandardCharsets.UTF_8
      )
    ) {
      boolean passed = compiler
        .getTask(
          null,
          manager,
          diagnostics,
          List.of(
            "-proc:none",
            "-classpath",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            "-d",
            classes.toString()
          ),
          null,
          manager.getJavaFileObjectsFromFiles(paths)
        )
        .call();
      assertThat(passed).withFailMessage(diagnostics.getDiagnostics().toString()).isTrue();
    }
    return classes;
  }

  @Test
  void generatedMapperExecutesCrudOnSqlite() throws Exception {
    Path file = fixture();
    var output = generator.generate(request(), queries.execute(metadata(file)));
    Path classes = compile(output.files());
    String xml = output
      .files()
      .stream()
      .filter(f -> f.language().equals("xml"))
      .findFirst()
      .orElseThrow()
      .content();
    ClassLoader old = Thread.currentThread().getContextClassLoader();
    try (
      var loader = new java.net.URLClassLoader(new java.net.URL[] { classes.toUri().toURL() }, old)
    ) {
      Thread.currentThread().setContextClassLoader(loader);
      var dataSource = new org.apache.ibatis.datasource.unpooled.UnpooledDataSource();
      dataSource.setDriver("org.sqlite.JDBC");
      dataSource.setUrl("jdbc:sqlite:" + file);
      var config = new org.apache.ibatis.session.Configuration(
        new org.apache.ibatis.mapping.Environment(
          "test",
          new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
          dataSource
        )
      );
      new org.apache.ibatis.builder.xml.XMLMapperBuilder(
        new java.io.StringReader(xml),
        config,
        "generated.xml",
        config.getSqlFragments()
      ).parse();
      try (
        var session = new org.apache.ibatis.session.SqlSessionFactoryBuilder()
          .build(config)
          .openSession()
      ) {
        String namespace = "com.example.customer.CustomerMapper.";
        assertThat(session.selectList(namespace + "findAll")).hasSize(2);
        Class<?> type = loader.loadClass("com.example.customer.CustomerVO");
        Object value = type.getConstructor().newInstance();
        type.getMethod("setId", Long.class).invoke(value, 3L);
        type.getMethod("setName", String.class).invoke(value, "생성 코드");
        assertThat(session.insert(namespace + "insert", value)).isEqualTo(1);
        Object inserted = session.selectOne(namespace + "findById", Map.of("id", 3L));
        assertThat(type.getMethod("getCalculated").invoke(inserted)).isEqualTo("생성 코드-x");
        type.getMethod("setName", String.class).invoke(value, "수정");
        assertThat(session.update(namespace + "update", value)).isEqualTo(1);
        Object updated = session.selectOne(namespace + "findById", Map.of("id", 3L));
        assertThat(type.getMethod("getName").invoke(updated)).isEqualTo("수정");
        assertThat(session.delete(namespace + "delete", Map.of("id", 3L))).isEqualTo(1);
        session.commit();
      }
    } finally {
      Thread.currentThread().setContextClassLoader(old);
    }
  }

  @Test
  void sqliteLongRunningQueryIsCancelledAtDeadline() throws Exception {
    String markdown = metadata(fixture());
    int begin = markdown.indexOf("  sql: |"),
      end = markdown.indexOf("  parameters:", begin);
    markdown =
      markdown.substring(0, begin) +
      "  sql: |\n    WITH RECURSIVE numbers(x) AS (VALUES(1) UNION ALL SELECT x+1 FROM numbers WHERE x<1000000000) SELECT sum(x) FROM numbers\n" +
      markdown.substring(end);
    String sql = markdown.replace("timeout_seconds: 30", "timeout_seconds: 1");
    long start = System.nanoTime();
    assertThatThrownBy(() -> queries.execute(sql))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("제한시간");
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5000);
  }

  @Test
  void db2DialectCompositeKeysAndTypeOverrides() throws Exception {
    var columns = List.of(
      new QueryResult.Column("COLUMN_NAME", "COLUMN_NAME", Types.VARCHAR, "VARCHAR", false, 128, 0),
      new QueryResult.Column("DATA_TYPE", "DATA_TYPE", Types.VARCHAR, "VARCHAR", false, 128, 0)
    );
    var rows = List.of(
      Map.<String, Object>of("COLUMN_NAME", "TENANT_ID", "DATA_TYPE", "INTEGER", "PK_POSITION", 1),
      Map.<String, Object>of(
        "COLUMN_NAME",
        "ID",
        "DATA_TYPE",
        "BIGINT",
        "PK_POSITION",
        2,
        "IS_GENERATED",
        1
      ),
      Map.<String, Object>of("COLUMN_NAME", "AMOUNT", "DATA_TYPE", "DECFLOAT")
    );
    var settings = new QueryProfile.GenerationSettings(
      "metadata",
      null,
      null,
      null,
      Map.of("AMOUNT", "java.math.BigDecimal"),
      null
    );
    var result = new QueryResult(
      "db2-fixture",
      "DB2 LUW",
      "db2",
      columns,
      rows,
      false,
      1,
      new QueryProfile.WorkItem("app.customer", "Customer", "고객", "com.example.customer"),
      settings
    );
    var output = generator.generate(
      new GenerationRequest("app.customer", "Customer", "고객", "com.example.customer", null),
      result
    );
    String xml = output
      .files()
      .stream()
      .filter(f -> f.language().equals("xml"))
      .findFirst()
      .orElseThrow()
      .content();
    assertThat(xml).contains(
      "\"APP\".\"CUSTOMER\"",
      "WHERE \"TENANT_ID\" = #{tenantId,jdbcType=INTEGER} AND \"ID\" = #{id,jdbcType=BIGINT}"
    );
    assertThat(xml.substring(xml.indexOf("<insert"), xml.indexOf("</insert>"))).doesNotContain(
      "\"ID\""
    );
    compile(output.files());
  }

  @Test
  void profilesSaveCopiesAndDetectConcurrentChanges() throws Exception {
    var original = profiles.open("example:db-query-sqlite.md");
    var saved = profiles.save(new QueryProfiles.Save(original.key(), original.markdown(), null));
    assertThat(saved.key()).startsWith("document:");
    documents.save(
      Long.parseLong(saved.key().substring(9)),
      "changed",
      saved.markdown() + "\nexternal"
    );
    assertThatThrownBy(() ->
      profiles.save(new QueryProfiles.Save(saved.key(), saved.markdown(), saved.revision()))
    ).isInstanceOf(ApiErrors.Conflict.class);
    documents.delete(Long.parseLong(saved.key().substring(9)));
  }

  @Test
  void nestedProfileFileSaveKeepsBomAndCrLf() throws Exception {
    Path root = Files.createDirectory(directory.resolve("md"));
    Path nested = Files.createDirectory(root.resolve("nested"));
    Path file = nested.resolve("db.md");
    String content = metadata(fixture());
    Files.writeString(file, "\ufeff" + content.replace("\n", "\r\n"));
    localFiles.register(root.toString());
    var opened = profiles.open("file:" + file.toRealPath());
    profiles.save(
      new QueryProfiles.Save(opened.key(), opened.markdown() + "\nchanged\n", opened.revision())
    );
    assertThat(Files.readString(file)).startsWith("\ufeff").endsWith("changed\r\n");
  }

  @Test
  void rejectsInlinePasswordsAndRetainsOnlyEnvironmentReferences() throws Exception {
    String db2 = Files.readString(Path.of("src/main/resources/examples/db-query-db2.md"));
    var profile = parser.parse(db2);
    assertThat(profile.connection().password_env()).isEqualTo("DB2_PASSWORD");
    policy.validate(profile.query().sql());
    assertThatThrownBy(() ->
      parser.parse(db2.replace("password_env: DB2_PASSWORD", "password: do-not-store"))
    ).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
      parser.parse(db2.replace("DATABASE", "DATABASE:password=do-not-store;"))
    ).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> queries.test(db2.replace("DB2_USER", "MISSING_DB_USER_123")))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("MISSING_DB_USER_123");
  }
}
