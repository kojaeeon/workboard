package com.kojaeeon.dashboard.mindmap;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mindmaps")
public class MindMapController {

  private final MindMapRepository repository;
  private final ObjectMapper json;

  public MindMapController(MindMapRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  public record Input(String title, Graph graph, Integer revision) {}

  @GetMapping
  public List<MindMapRepository.Entry> list() {
    return repository.list();
  }

  @GetMapping("/example")
  public Graph example() throws IOException {
    try (var stream = new ClassPathResource("mindmap-example.json").getInputStream()) {
      return json.readValue(stream, Graph.class);
    }
  }

  @GetMapping("/{id}")
  public MindMapRepository.Saved get(@PathVariable long id) {
    return repository.get(id);
  }

  @PostMapping
  public MindMapRepository.Version create(@RequestBody Input input) {
    return repository.save(null, input.title(), input.graph(), null);
  }

  @PutMapping("/{id}")
  public MindMapRepository.Version update(@PathVariable long id, @RequestBody Input input) {
    return repository.save(id, input.title(), input.graph(), input.revision());
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable long id, @RequestParam int revision) {
    repository.delete(id, revision);
  }

  @GetMapping("/types")
  public List<Graph.NodeType> types() {
    return repository.types();
  }

  @PutMapping("/types")
  public void type(@RequestBody Graph.NodeType type) {
    repository.saveType(type);
  }

  @PostMapping("/validate")
  public Graph validate(@RequestBody Graph graph) {
    return graph;
  }
}
