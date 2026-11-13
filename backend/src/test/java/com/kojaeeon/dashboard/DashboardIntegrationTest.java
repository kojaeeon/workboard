package com.kojaeeon.dashboard;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kojaeeon.dashboard.mindmap.Graph;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardIntegrationTest {

  static final Path directory;
  static final HttpServer ado;
  static final AtomicReference<String> detailsQuery = new AtomicReference<>();

  static {
    try {
      directory = Files.createTempDirectory("dashboard-integration-");
      ado = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      ado.createContext("/collection/_apis/wit/wiql", exchange -> {
        assertThat(exchange.getRequestMethod()).isEqualTo("POST");
        assertThat(exchange.getRequestHeaders().getFirst("Authorization")).startsWith("Basic ");
        JsonNode body = new ObjectMapper().readTree(exchange.getRequestBody());
        String response = body.path("query").asText().equals("tree")
          ? "{\"queryType\":\"tree\"}"
          : "{\"queryType\":\"flat\",\"asOf\":\"2026-11-13T00:00:00Z\",\"columns\":[{\"referenceName\":\"System.Id\",\"name\":\"ID\"},{\"referenceName\":\"System.Title\",\"name\":\"제목\"},{\"referenceName\":\"System.AssignedTo\"},{\"referenceName\":\"System.Description\"}],\"workItems\":[{\"id\":2},{\"id\":1}]}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      ado.createContext("/collection/_apis/wit/workitems", exchange -> {
        detailsQuery.set(exchange.getRequestURI().getRawQuery());
        byte[] bytes =
          "{\"value\":[{\"id\":1,\"fields\":{\"System.Title\":\"First\",\"System.AssignedTo\":{\"displayName\":\"담당자\"},\"System.Description\":\"<p>설명</p><script>bad()</script>\"}},{\"id\":2,\"fields\":{\"System.Title\":\"Second\"}}]}".getBytes(
            StandardCharsets.UTF_8
          );
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      ado.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("dashboard.db-path", () -> directory.resolve("dashboard.db").toString());
    registry.add(
      "ADO_BASE_URL",
      () -> "http://127.0.0.1:" + ado.getAddress().getPort() + "/collection"
    );
    registry.add("ADO_PROJECT", () -> "project");
    registry.add("ADO_PAT", () -> "test-only-pat");
  }

  @Autowired
  MockMvc mvc;

  @Autowired
  ObjectMapper json;

  String encode(Object value) throws Exception {
    return json.writeValueAsString(value);
  }

  JsonNode response(
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
  ) throws Exception {
    return json.readTree(
      mvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8)
    );
  }

  @Test
  void workCrudAndValidation() throws Exception {
    long id = response(
      post("/api/work")
        .contentType(MediaType.APPLICATION_JSON)
        .content(encode(Map.of("title", "작업", "status", "todo", "notes", "메모")))
    )
      .path("id")
      .asLong();
    mvc
      .perform(
        put("/api/work/" + id)
          .contentType(MediaType.APPLICATION_JSON)
          .content(encode(Map.of("title", "수정", "status", "doing", "notes", "진행")))
      )
      .andExpect(status().isOk());
    assertThat(response(get("/api/work")).toString()).contains("수정", "doing", "진행");
    mvc
      .perform(
        post("/api/work")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"title\":\"\",\"status\":\"bad\"}")
      )
      .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/work/" + id)).andExpect(status().isOk());
  }

  @Test
  void scheduleUsesSeoulAndExclusiveEnd() throws Exception {
    long id = response(
      post("/api/schedule")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
          encode(
            Map.of(
              "title",
              "하루 일정",
              "start",
              "2026-11-09",
              "end",
              "2026-11-10",
              "allDay",
              true,
              "category",
              "work",
              "notes",
              ""
            )
          )
        )
    )
      .path("id")
      .asLong();
    JsonNode row = response(get("/api/schedule/" + id));
    assertThat(row.path("start_ts").asLong()).isEqualTo(
      java.time.OffsetDateTime.parse("2026-11-09T00:00:00+09:00").toEpochSecond()
    );
    assertThat(
      response(get("/api/schedule").param("start", "2026-11-10").param("end", "2026-11-11"))
    ).isEmpty();
    assertThat(
      response(get("/api/schedule").param("start", "2026-11-09").param("end", "2026-11-10"))
        .get(0)
        .path("allDay")
        .asBoolean()
    ).isTrue();
    mvc
      .perform(
        patch("/api/schedule/" + id)
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"start\":\"2026-11-11\",\"end\":null,\"allDay\":true}")
      )
      .andExpect(status().isOk());
    assertThat(
      response(get("/api/schedule/" + id))
        .path("end")
        .asText()
    ).isEqualTo("2026-11-12");
    mvc
      .perform(
        post("/api/schedule")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"title\":\"bad\",\"start\":\"2026-11-10\",\"end\":\"2026-11-09\",\"allDay\":true,\"category\":\"work\"}"
          )
      )
      .andExpect(status().isBadRequest());
    mvc.perform(delete("/api/schedule/" + id)).andExpect(status().isOk());
  }

  @Test
  void timedEventsNormalizeOffsetToSeoul() throws Exception {
    long id = response(
      post("/api/schedule")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
          "{\"title\":\"회의\",\"start\":\"2026-11-09T00:00:00Z\",\"end\":\"2026-11-09T01:00:00Z\",\"allDay\":false,\"category\":\"meeting\"}"
        )
    )
      .path("id")
      .asLong();
    assertThat(
      response(get("/api/schedule/" + id))
        .path("start")
        .asText()
    ).isEqualTo("2026-11-09T09:00:00+09:00");
  }

  @Test
  void documentsKeepBomCrLfAndDetectExternalChanges() throws Exception {
    Path folder = Files.createDirectory(directory.resolve("markdown")),
      file = folder.resolve("test.md");
    Files.writeString(file, "\ufeff# 제목\r\n내용\r\n", StandardCharsets.UTF_8);
    mvc
      .perform(
        post("/api/documents/folders")
          .contentType(MediaType.APPLICATION_JSON)
          .content(encode(Map.of("path", folder.toString())))
      )
      .andExpect(status().isOk());
    JsonNode opened = response(get("/api/documents/local").param("path", file.toString()));
    assertThat(opened.path("content").asText()).isEqualTo("# 제목\n내용\n");
    var save = Map.of(
      "path",
      file.toString(),
      "revision",
      opened.path("revision").asText(),
      "content",
      "# 수정\n새 내용\n"
    );
    response(
      put("/api/documents/local").contentType(MediaType.APPLICATION_JSON).content(encode(save))
    );
    assertThat(Files.readString(file)).isEqualTo("\ufeff# 수정\r\n새 내용\r\n");
    Files.writeString(file, "외부 변경");
    mvc
      .perform(
        put("/api/documents/local").contentType(MediaType.APPLICATION_JSON).content(encode(save))
      )
      .andExpect(status().isConflict());
    assertThat(Files.readString(file)).isEqualTo("외부 변경");
    mvc
      .perform(
        get("/api/documents/local").param("path", directory.resolve("unregistered.md").toString())
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void sqliteDocumentAndSampleAreAvailable() throws Exception {
    long id = response(
      post("/api/documents")
        .contentType(MediaType.APPLICATION_JSON)
        .content(encode(Map.of("title", "테스트 문서", "content", "# 원본\n한글")))
    )
      .path("id")
      .asLong();
    assertThat(response(get("/api/documents")).toString()).contains("테스트 문서", "한글");
    assertThat(response(get("/api/documents/sample")).path("content").asText()).contains(
      "KJA_BOARD"
    );
    mvc.perform(delete("/api/documents/" + id)).andExpect(status().isOk());
  }

  @Test
  void mapsRoundTripAndRejectStaleUpdatesAndDeletes() throws Exception {
    JsonNode graph = response(get("/api/mindmaps/example"));
    long id = response(
      post("/api/mindmaps")
        .contentType(MediaType.APPLICATION_JSON)
        .content(encode(Map.of("title", "서비스 맵", "graph", graph)))
    )
      .path("id")
      .asLong();
    assertThat(response(get("/api/mindmaps/" + id)).get("graph")).isEqualTo(graph);
    String payload = encode(Map.of("title", "수정 맵", "graph", graph, "revision", 1));
    assertThat(
      response(
        put("/api/mindmaps/" + id)
          .contentType(MediaType.APPLICATION_JSON)
          .content(payload)
      )
        .path("revision")
        .asInt()
    ).isEqualTo(2);
    mvc
      .perform(
        put("/api/mindmaps/" + id)
          .contentType(MediaType.APPLICATION_JSON)
          .content(payload)
      )
      .andExpect(status().isConflict());
    mvc
      .perform(delete("/api/mindmaps/" + id).param("revision", "1"))
      .andExpect(status().isConflict());
    mvc.perform(delete("/api/mindmaps/" + id).param("revision", "2")).andExpect(status().isOk());
  }

  @Test
  void graphValidationRejectsBrokenHierarchyAndUnsafeStyles() throws Exception {
    var graph = response(get("/api/mindmaps/example")).deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) graph).put("root", "missing");
    mvc
      .perform(
        post("/api/mindmaps/validate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(graph.toString())
      )
      .andExpect(status().isBadRequest());
    assertThatThrownBy(() ->
      new Graph.Style("javascript:bad", null, null, null, null, null)
    ).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() ->
      new Graph.Style(null, null, null, null, null, "<script>")
    ).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Graph.View(1, Double.NaN, 0d, 0d, 0d, 0d)).isInstanceOf(
      IllegalArgumentException.class
    );
  }

  @Test
  void nodeTypesPersist() throws Exception {
    mvc
      .perform(
        put("/api/mindmaps/types")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            "{\"name\":\"사용자 유형\",\"style\":{\"background\":\"#123456\",\"shape\":\"hexagon\",\"icon\":\"★\"}}"
          )
      )
      .andExpect(status().isOk());
    assertThat(response(get("/api/mindmaps/types")).toString()).contains(
      "사용자 유형",
      "#123456",
      "hexagon"
    );
  }

  @Test
  void wiqlPreservesColumnAndIdOrderAndProjectsNoPath() throws Exception {
    JsonNode result = response(
      post("/api/ado/wiql")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"query\":\"SELECT [System.Id] FROM WorkItems\"}")
    );
    assertThat(result.path("rows").get(0).path("System.Id").asLong()).isEqualTo(2);
    assertThat(result.path("rows").get(1).path("System.AssignedTo").asText()).isEqualTo("담당자");
    assertThat(result.path("rows").get(1).path("System.Description").asText()).isEqualTo("설명");
    assertThat(detailsQuery.get()).contains("asOf=", "fields=", "ids=2%2C1", "api-version=7.1");
    mvc
      .perform(
        post("/api/ado/wiql")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"query\":\"tree\"}")
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void settingsValidateThemes() throws Exception {
    mvc
      .perform(
        put("/api/settings/app_theme")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"value\":\"dark\"}")
      )
      .andExpect(status().isOk());
    assertThat(response(get("/api/settings")).path("app_theme").asText()).isEqualTo("dark");
    mvc
      .perform(
        put("/api/settings/app_theme")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"value\":\"other\"}")
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void codeGenerationReadsSavedFrontmatterAndDownloadsPreview() throws Exception {
    String content =
      "---\nworkItem: {table: TB_PORT, service: Port, serviceNm: 포팅, package: com.example}\n---\n# 설명";
    long id = response(
      post("/api/documents")
        .contentType(MediaType.APPLICATION_JSON)
        .content(encode(Map.of("title", "생성 정보 MD", "content", content)))
    )
      .path("id")
      .asLong();
    assertThat(response(get("/api/codegen/sources")).path("sources").toString()).contains(
      "document:" + id
    );
    JsonNode input = response(get("/api/codegen/inputs").param("key", "document:" + id));
    assertThat(input.path("values").path("table_name").asText()).isEqualTo("TB_PORT");
    JsonNode preview = response(
      post("/api/codegen/preview")
        .contentType(MediaType.APPLICATION_JSON)
        .content(input.path("values").toString())
    );
    assertThat(preview.path("files")).hasSize(4);
    mvc
      .perform(get("/api/codegen/previews/" + preview.path("id").asText() + "/zip"))
      .andExpect(status().isOk())
      .andExpect(content().contentType("application/zip"));
    mvc.perform(delete("/api/documents/" + id)).andExpect(status().isOk());
  }

  @Test
  void uiTestValidatesPackagedExampleAndRejectsInvalidBrowser() throws Exception {
    JsonNode example = response(
      get("/api/ui-tests/source").param("key", "example:ui-test-smoke.md")
    );
    JsonNode plan = response(
      post("/api/ui-tests/validate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(encode(Map.of("markdown", example.path("content").asText())))
    );
    assertThat(plan.path("steps")).hasSize(9);
    mvc
      .perform(
        post("/api/ui-tests/validate")
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            encode(
              Map.of(
                "markdown",
                example.path("content").asText(),
                "browserMarkdown",
                "---\nbrowser: unknown\n---"
              )
            )
          )
      )
      .andExpect(status().isBadRequest());
    mvc.perform(get("/api/ui-tests/runs/invalid")).andExpect(status().isBadRequest());
  }

  @AfterAll
  static void stop() {
    ado.stop(0);
  }
}
