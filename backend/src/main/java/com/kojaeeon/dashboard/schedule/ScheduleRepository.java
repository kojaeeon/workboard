package com.kojaeeon.dashboard.schedule;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleRepository {

  public record Event(
    long id,
    String title,
    String start,
    String end,
    int all_day,
    String category,
    String notes,
    long start_ts,
    long end_ts,
    String updated_at
  ) {}

  private final JdbcClient db;

  public ScheduleRepository(JdbcClient db) {
    this.db = db;
  }

  public List<Event> list(long start, long end) {
    return db
      .sql(
        "SELECT * FROM schedule_events WHERE start_ts < :end AND end_ts > :start ORDER BY start_ts,id"
      )
      .param("start", start)
      .param("end", end)
      .query(Event.class)
      .list();
  }

  public Event get(long id) {
    return db
      .sql("SELECT * FROM schedule_events WHERE id=:id")
      .param("id", id)
      .query(Event.class)
      .optional()
      .orElseThrow(() ->
        new IllegalArgumentException("일정을 찾을 수 없습니다. 새로고침해 주세요.")
      );
  }

  public long save(Long id, ScheduleService.Input e, long start, long end) {
    var query = db
      .sql(
        id == null
          ? "INSERT INTO schedule_events(title,start,end,all_day,category,notes,start_ts,end_ts) VALUES (:title,:start,:end,:allDay,:category,:notes,:startTs,:endTs)"
          : "UPDATE schedule_events SET title=:title,start=:start,end=:end,all_day=:allDay,category=:category,notes=:notes,start_ts=:startTs,end_ts=:endTs,updated_at=CURRENT_TIMESTAMP WHERE id=:id"
      )
      .param("title", e.title().strip())
      .param("start", e.start())
      .param("end", e.end())
      .param("allDay", e.allDay() ? 1 : 0)
      .param("category", e.category())
      .param("notes", e.notes() == null ? "" : e.notes())
      .param("startTs", start)
      .param("endTs", end);
    if (id != null) {
      if (query.param("id", id).update() != 1) throw new IllegalArgumentException(
        "일정을 찾을 수 없습니다."
      );
      return id;
    }
    var key = new GeneratedKeyHolder();
    query.update(key);
    return key.getKey().longValue();
  }

  public void delete(long id) {
    if (
      db.sql("DELETE FROM schedule_events WHERE id=:id").param("id", id).update() != 1
    ) throw new IllegalArgumentException("일정을 찾을 수 없습니다.");
  }
}
