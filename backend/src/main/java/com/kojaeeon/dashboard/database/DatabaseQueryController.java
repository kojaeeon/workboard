package com.kojaeeon.dashboard.database;

import com.kojaeeon.dashboard.documents.MarkdownSources;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/database")
public class DatabaseQueryController {

  public record Input(String markdown) {}

  private final DatabaseQueryService service;
  private final QueryProfiles profiles;
  private final QueryResultStore results;

  public DatabaseQueryController(
    DatabaseQueryService service,
    QueryProfiles profiles,
    QueryResultStore results
  ) {
    this.service = service;
    this.profiles = profiles;
    this.results = results;
  }

  @GetMapping("/profiles")
  public MarkdownSources.Listing profiles() {
    return profiles.list();
  }

  @GetMapping("/profile")
  public QueryProfiles.ProfileDocument profile(@RequestParam String key) throws IOException {
    return profiles.open(key);
  }

  @PostMapping("/profile")
  public QueryProfiles.ProfileDocument save(@RequestBody QueryProfiles.Save input)
    throws IOException {
    return profiles.save(input);
  }

  @PostMapping("/validate")
  public DatabaseQueryService.Description validate(@RequestBody Input input) {
    return service.describe(input.markdown());
  }

  @PostMapping("/connect")
  public DatabaseQueryService.ConnectionInfo connect(@RequestBody Input input) throws IOException {
    return service.test(input.markdown());
  }

  @PostMapping("/query")
  public QueryResult query(@RequestBody Input input) throws IOException {
    return service.execute(input.markdown());
  }

  @GetMapping("/results/{id}")
  public QueryResult result(@PathVariable String id) {
    return results.get(id);
  }
}
