package com.kojaeeon.dashboard.schedule;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService {

  public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final Map<String, String> COLORS = Map.of(
    "work",
    "#51786b",
    "personal",
    "#647eae",
    "meeting",
    "#9b78ab",
    "deadline",
    "#be6654"
  );

  public record Input(
    String title,
    String start,
    String end,
    boolean allDay,
    String category,
    String notes
  ) {}

  public record Props(String category, String notes) {}

  public record CalendarEvent(
    String id,
    String title,
    String start,
    String end,
    boolean allDay,
    String backgroundColor,
    String borderColor,
    Props extendedProps
  ) {}

  private final ScheduleRepository repository;

  public ScheduleService(ScheduleRepository repository) {
    this.repository = repository;
  }

  public static ZonedDateTime datetime(String text) {
    try {
      if (text.length() == 10) return LocalDate.parse(text).atStartOfDay(SEOUL);
      try {
        return OffsetDateTime.parse(text).atZoneSameInstant(SEOUL);
      } catch (DateTimeParseException e) {
        return LocalDateTime.parse(text).atZone(SEOUL);
      }
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("올바른 날짜와 시간을 입력하세요.");
    }
  }

  public long save(Long id, Input input) {
    if (input.title() == null || input.title().isBlank()) throw new IllegalArgumentException(
      "일정 제목을 입력하세요."
    );
    if (
      input.category() == null || !COLORS.containsKey(input.category())
    ) throw new IllegalArgumentException("일정 분류를 선택하세요.");
    var start = datetime(input.start());
    var end = datetime(input.end());
    if (!end.isAfter(start)) throw new IllegalArgumentException("종료는 시작보다 늦어야 합니다.");
    String begin = input.allDay()
      ? LocalDate.parse(input.start()).toString()
      : timestamp(start);
    String finish = input.allDay()
      ? LocalDate.parse(input.end()).toString()
      : timestamp(end);
    return repository.save(
      id,
      new Input(input.title(), begin, finish, input.allDay(), input.category(), input.notes()),
      start.toEpochSecond(),
      end.toEpochSecond()
    );
  }

  private static String timestamp(ZonedDateTime value) {
    String pattern = value.getNano() == 0
      ? "uuuu-MM-dd'T'HH:mm:ssxxx"
      : "uuuu-MM-dd'T'HH:mm:ss.SSSSSSxxx";
    return java.time.format.DateTimeFormatter.ofPattern(pattern).format(value);
  }

  public List<CalendarEvent> list(String start, String end) {
    var begin = datetime(start);
    var finish = datetime(end);
    if (!finish.isAfter(begin)) throw new IllegalArgumentException(
      "조회 기간이 올바르지 않습니다."
    );
    return repository
      .list(begin.toEpochSecond(), finish.toEpochSecond())
      .stream()
      .map(e ->
        new CalendarEvent(
          "" + e.id(),
          e.title(),
          e.start(),
          e.end(),
          e.all_day() == 1,
          COLORS.get(e.category()),
          COLORS.get(e.category()),
          new Props(e.category(), e.notes())
        )
      )
      .toList();
  }

  public void move(long id, String start, String end, boolean allDay) {
    var old = repository.get(id);
    if (end == null || end.isBlank()) end = allDay
      ? LocalDate.parse(start).plusDays(1).toString()
      : datetime(start).plusHours(1).toOffsetDateTime().toString();
    save(id, new Input(old.title(), start, end, allDay, old.category(), old.notes()));
  }
}
