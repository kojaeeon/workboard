package com.kojaeeon.dashboard.work;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/work")
public class WorkController {

  public record Input(
    @NotBlank(message = "제목을 입력하세요.") String title,
    @NotBlank
    @Pattern(regexp = "todo|doing|done", message = "올바른 상태를 선택하세요.")
    String status,
    String notes
  ) {}

  private final WorkRepository repository;

  public WorkController(WorkRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<WorkRepository.Item> list() {
    return repository.list();
  }

  @PostMapping
  public Map<String, Long> create(@Valid @RequestBody Input input) {
    return Map.of("id", repository.save(null, input));
  }

  @PutMapping("/{id}")
  public void update(@PathVariable long id, @Valid @RequestBody Input input) {
    repository.save(id, input);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable long id) {
    repository.delete(id);
  }
}
