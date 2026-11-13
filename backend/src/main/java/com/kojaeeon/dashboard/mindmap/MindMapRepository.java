package com.kojaeeon.dashboard.mindmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kojaeeon.dashboard.shared.ApiErrors.Conflict;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MindMapRepository {

  public record Entry(long id, String title, int revision, String updated_at) {}

  public record Saved(long id, String title, int revision, Graph graph) {}

  public record Version(long id, int revision) {}

  private final JdbcClient db;
  private final ObjectMapper json;

  public MindMapRepository(JdbcClient db, ObjectMapper json) {
    this.db = db;
    this.json = json;
    String[][] types = {
      { "서비스", "◇", "#cbb788" },
      { "화면", "▣", "#a9c7b1" },
      { "API", "⇄", "#abc5dc" },
      { "Controller", "C", "#abc5dc" },
      { "Service", "S", "#b9c5a3" },
      { "Mapper", "M", "#d4b9a1" },
      { "테이블", "▤", "#c2b7d7" },
      { "테스트", "✓", "#a6c9c3" },
      { "그룹", "▧", "#cbb788" },
    };
    for (var type : types)
      db.sql("INSERT OR IGNORE INTO mindmap_types(name,style_json) VALUES (:name,:style)")
        .param("name", type[0])
        .param(
          "style",
          encode(new Graph.Style("#222b24", "#eee9de", type[2], 15, "rounded", type[1]))
        )
        .update();
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private <T> T decode(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (Exception e) {
      throw new IllegalArgumentException("저장 데이터 형식을 확인하세요.", e);
    }
  }

  public List<Entry> list() {
    return db
      .sql("SELECT id,title,revision,updated_at FROM mind_maps ORDER BY updated_at DESC,id DESC")
      .query(Entry.class)
      .list();
  }

  public Saved get(long id) {
    return db
      .sql("SELECT * FROM mind_maps WHERE id=:id")
      .param("id", id)
      .query((r, i) ->
        new Saved(
          r.getLong("id"),
          r.getString("title"),
          r.getInt("revision"),
          decode(r.getString("graph_json"), Graph.class)
        )
      )
      .optional()
      .orElseThrow(() -> new IllegalArgumentException("저장된 맵이 없습니다."));
  }

  public Version save(Long id, String title, Graph graph, Integer revision) {
    if (title == null || title.isBlank()) throw new IllegalArgumentException(
      "맵 제목을 입력하세요."
    );
    if (graph == null) throw new IllegalArgumentException("맵 데이터가 필요합니다.");
    if (id == null) {
      var key = new GeneratedKeyHolder();
      db.sql("INSERT INTO mind_maps(title,graph_json) VALUES (:title,:graph)")
        .param("title", title.strip())
        .param("graph", encode(graph))
        .update(key);
      return new Version(key.getKey().longValue(), 1);
    }
    if (revision == null) throw new IllegalArgumentException("맵 버전이 필요합니다.");
    int changed = db
      .sql(
        "UPDATE mind_maps SET title=:title,graph_json=:graph,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND revision=:revision"
      )
      .param("title", title.strip())
      .param("graph", encode(graph))
      .param("id", id)
      .param("revision", revision)
      .update();
    if (changed != 1) throw new Conflict(
      "다른 화면에서 맵이 변경되거나 삭제되었습니다. 다시 열어 확인하세요."
    );
    return new Version(id, revision + 1);
  }

  public void delete(long id, int revision) {
    if (
      db
        .sql("DELETE FROM mind_maps WHERE id=:id AND revision=:revision")
        .param("id", id)
        .param("revision", revision)
        .update() != 1
    ) throw new Conflict("맵이 변경되거나 삭제되었습니다. 목록을 다시 확인하세요.");
  }

  public List<Graph.NodeType> types() {
    return db
      .sql("SELECT * FROM mindmap_types ORDER BY name")
      .query((r, i) ->
        new Graph.NodeType(
          r.getString("name"),
          decode(r.getString("style_json"), Graph.Style.class)
        )
      )
      .list();
  }

  public void saveType(Graph.NodeType type) {
    db.sql(
      "INSERT INTO mindmap_types(name,style_json) VALUES (:name,:style) ON CONFLICT(name) DO UPDATE SET style_json=excluded.style_json"
    )
      .param("name", type.name().strip())
      .param("style", encode(type.style()))
      .update();
  }
}
