package com.kojaeeon.dashboard.schedule;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

  private final ScheduleService service;
  private final ScheduleRepository repository;

  public ScheduleController(ScheduleService service, ScheduleRepository repository) {
    this.service = service;
    this.repository = repository;
  }

  @GetMapping
  public List<ScheduleService.CalendarEvent> list(
    @RequestParam String start,
    @RequestParam String end
  ) {
    return service.list(start, end);
  }

  @GetMapping("/{id}")
  public ScheduleRepository.Event get(@PathVariable long id) {
    return repository.get(id);
  }

  @PostMapping
  public Map<String, Long> create(@RequestBody ScheduleService.Input e) {
    return Map.of("id", service.save(null, e));
  }

  @PutMapping("/{id}")
  public void update(@PathVariable long id, @RequestBody ScheduleService.Input e) {
    service.save(id, e);
  }

  public record Move(String start, String end, boolean allDay) {}

  @PatchMapping("/{id}")
  public void move(@PathVariable long id, @RequestBody Move m) {
    service.move(id, m.start(), m.end(), m.allDay());
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable long id) {
    repository.delete(id);
  }
}
