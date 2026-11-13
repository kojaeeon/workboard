package com.kojaeeon.dashboard.work;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class WorkRepository {

  public record Item(long id, String title, String status, String notes, String updated_at) {}

  private final JdbcClient db;

  public WorkRepository(JdbcClient db) {
    this.db = db;
  }

  public List<Item> list() {
    return db
      .sql("SELECT * FROM work_items ORDER BY updated_at DESC,id DESC")
      .query(Item.class)
      .list();
  }

  public long save(Long id, WorkController.Input input) {
    var query = db
      .sql(
        id == null
          ? "INSERT INTO work_items(title,status,notes) VALUES (:title,:status,:notes)"
          : "UPDATE work_items SET title=:title,status=:status,notes=:notes,updated_at=CURRENT_TIMESTAMP WHERE id=:id"
      )
      .param("title", input.title().strip())
      .param("status", input.status())
      .param("notes", input.notes() == null ? "" : input.notes());
    if (id != null) {
      if (query.param("id", id).update() != 1) throw new IllegalArgumentException(
        "작업을 찾을 수 없습니다."
      );
      return id;
    }
    var key = new GeneratedKeyHolder();
    query.update(key);
    return key.getKey().longValue();
  }

  public void delete(long id) {
    db.sql("DELETE FROM work_items WHERE id=:id").param("id", id).update();
  }
}
