package com.kojaeeon.dashboard.integration.ado;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.net.ssl.*;
import org.jsoup.Jsoup;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class AdoService {

  public record Column(String key, String label, int width, String type) {}

  public record Result(List<Column> columns, List<Map<String, Object>> rows, int total) {}

  private static final Set<String> HTML = Set.of(
    "System.Description",
    "Microsoft.VSTS.Common.AcceptanceCriteria",
    "Microsoft.VSTS.TCM.ReproSteps"
  );
  private static final Set<String> NUMERIC = Set.of(
    "System.Id",
    "System.Rev",
    "Microsoft.VSTS.Common.Priority",
    "Microsoft.VSTS.Scheduling.StoryPoints",
    "Microsoft.VSTS.Scheduling.Effort",
    "Microsoft.VSTS.Scheduling.OriginalEstimate",
    "Microsoft.VSTS.Scheduling.RemainingWork",
    "Microsoft.VSTS.Scheduling.CompletedWork"
  );
  private final Environment environment;
  private final ObjectMapper json;

  public AdoService(Environment environment, ObjectMapper json) {
    this.environment = environment;
    this.json = json;
  }

  private String setting(String key) {
    return environment.getProperty(key, "").strip();
  }

  private String escape(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private JsonNode request(String method, String path, Map<String, String> params, Object body) {
    String base = setting("ADO_BASE_URL"),
      project = setting("ADO_PROJECT"),
      pat = setting("ADO_PAT");
    if (base.isBlank() || project.isBlank() || pat.isBlank()) throw new IllegalArgumentException(
      "ADO_BASE_URL, ADO_PROJECT, ADO_PAT 환경변수가 필요합니다."
    );
    URI uri;
    try {
      uri = URI.create(base);
    } catch (Exception e) {
      throw new IllegalArgumentException("ADO_BASE_URL을 확인하세요.");
    }
    if (
      !Set.of("http", "https").contains(Objects.toString(uri.getScheme(), "")) ||
      uri.getHost() == null ||
      uri.getUserInfo() != null ||
      uri.getQuery() != null ||
      uri.getFragment() != null
    ) throw new IllegalArgumentException(
      "ADO_BASE_URL은 HTTP(S) 조직/collection URL이어야 합니다."
    );
    var query = new LinkedHashMap<>(params);
    query.put("api-version", environment.getProperty("ADO_API_VERSION", "7.1"));
    String queryString = query
      .entrySet()
      .stream()
      .map(e -> escape(e.getKey()) + "=" + escape(e.getValue()))
      .collect(java.util.stream.Collectors.joining("&"));
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) URI.create(
        base.replaceAll("/+$", "") + "/_apis/" + path + "?" + queryString
      )
        .toURL()
        .openConnection();
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(30000);
      connection.setReadTimeout(30000);
      if (
        connection instanceof HttpsURLConnection secure &&
        !Boolean.parseBoolean(environment.getProperty("ADO_VERIFY_SSL", "false"))
      ) {
        var context = SSLContext.getInstance("TLS");
        context.init(
          null,
          new TrustManager[] {
            new X509TrustManager() {
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }

              public void checkClientTrusted(X509Certificate[] chain, String auth) {}

              public void checkServerTrusted(X509Certificate[] chain, String auth) {}
            },
          },
          new SecureRandom()
        );
        secure.setSSLSocketFactory(context.getSocketFactory());
        secure.setHostnameVerifier((host, session) -> true);
      }
      connection.setRequestMethod(method);
      connection.setRequestProperty("Accept", "application/json");
      connection.setRequestProperty(
        "Authorization",
        "Basic " + Base64.getEncoder().encodeToString((":" + pat).getBytes(StandardCharsets.UTF_8))
      );
      if (body != null) {
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        try (var output = connection.getOutputStream()) {
          json.writeValue(output, body);
        }
      }
      int status = connection.getResponseCode();
      if (status < 200 || status >= 300) throw new AdoException(
        "ADO 요청 실패 (HTTP " + status + ")"
      );
      try (var stream = connection.getInputStream()) {
        return json.readTree(stream);
      }
    } catch (AdoException e) {
      throw e;
    } catch (IOException e) {
      throw new AdoException(
        "ADO 서버에 연결할 수 없거나 JSON 응답이 올바르지 않습니다. URL과 네트워크를 확인하세요."
      );
    } catch (Exception e) {
      throw new AdoException("ADO 연결 설정을 확인하세요.");
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  public Result query(String wiql) {
    JsonNode result = request(
      "POST",
      "wit/wiql",
      Map.of(),
      Map.of("query", Objects.requireNonNullElse(wiql, ""))
    );
    if (!result.path("queryType").asText().equals("flat")) throw new IllegalArgumentException(
      "현재 화면은 Flat WIQL 쿼리를 지원합니다."
    );
    var columns = new ArrayList<Column>();
    for (var column : result.path("columns")) {
      String key = column.path("referenceName").asText();
      columns.add(
        new Column(
          key,
          column.path("name").asText(key),
          key.equals("System.Title") ? 400 : 180,
          NUMERIC.contains(key) ? "number" : HTML.contains(key) ? "html" : "text"
        )
      );
    }
    var ids = new ArrayList<Long>();
    int total = result.path("workItems").size();
    for (var item : result.path("workItems")) {
      if (ids.size() >= 200) break;
      ids.add(item.path("id").asLong());
    }
    if (ids.isEmpty()) return new Result(columns, List.of(), total);
    var params = new LinkedHashMap<String, String>();
    params.put(
      "ids",
      ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
    );
    params.put(
      "fields",
      columns.stream().map(Column::key).collect(java.util.stream.Collectors.joining(","))
    );
    if (result.hasNonNull("asOf")) params.put("asOf", result.get("asOf").asText());
    var byId = new HashMap<Long, Map<String, Object>>();
    for (var item : request("GET", "wit/workitems", params, null).path("value")) {
      var row = new LinkedHashMap<String, Object>();
      for (var column : columns)
        row.put(column.key(), value(item.path("fields").path(column.key()), column.type()));
      row.put("System.Id", item.path("id").asLong());
      byId.put(item.path("id").asLong(), row);
    }
    return new Result(
      columns,
      ids.stream().filter(byId::containsKey).map(byId::get).toList(),
      total
    );
  }

  private Object value(JsonNode node, String type) {
    if (node.isMissingNode() || node.isNull()) return null;
    if (node.isObject()) return node.hasNonNull("displayName")
      ? node.get("displayName").asText()
      : node.hasNonNull("name")
        ? node.get("name").asText()
        : node.toString();
    if (node.isArray()) {
      var parts = new ArrayList<String>();
      node.forEach(n -> parts.add(String.valueOf(value(n, type))));
      return String.join("; ", parts);
    }
    if (node.isNumber()) return node.numberValue();
    return type.equals("html") ? Jsoup.parse(node.asText()).text() : node.asText();
  }

  public static class AdoException extends RuntimeException {

    public AdoException(String message) {
      super(message);
    }
  }
}
