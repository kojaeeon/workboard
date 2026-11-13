package com.kojaeeon.dashboard.documents;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

  public record Document(long id, String title, String content, String updated_at) {}

  public record Folder(long id, String path) {}

  private final JdbcClient db;

  public DocumentRepository(JdbcClient db) {
    this.db = db;
  }

  public List<Document> list() {
    return db
      .sql("SELECT * FROM documents ORDER BY updated_at DESC,id DESC")
      .query(Document.class)
      .list();
  }

  public long save(Long id, String title, String content) {
    if (title == null || title.isBlank()) throw new IllegalArgumentException(
      "문서 제목을 입력하세요."
    );
    var query = db
      .sql(
        id == null
          ? "INSERT INTO documents(title,content) VALUES (:title,:content)"
          : "UPDATE documents SET title=:title,content=:content,updated_at=CURRENT_TIMESTAMP WHERE id=:id"
      )
      .param("title", title.strip())
      .param("content", content);
    if (id != null) {
      if (query.param("id", id).update() != 1) throw new IllegalArgumentException(
        "문서를 찾을 수 없습니다."
      );
      return id;
    }
    var key = new GeneratedKeyHolder();
    query.update(key);
    return key.getKey().longValue();
  }

  public void delete(long id) {
    db.sql("DELETE FROM documents WHERE id=:id").param("id", id).update();
  }

  public List<Folder> folders() {
    return db.sql("SELECT id,path FROM folders ORDER BY path").query(Folder.class).list();
  }

  public void addFolder(String path) {
    db.sql("INSERT OR IGNORE INTO folders(path) VALUES (:path)").param("path", path).update();
  }

  public void removeFolder(long id) {
    db.sql("DELETE FROM folders WHERE id=:id").param("id", id).update();
  }
}
