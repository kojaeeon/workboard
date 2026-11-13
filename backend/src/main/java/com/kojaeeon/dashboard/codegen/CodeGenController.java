package com.kojaeeon.dashboard.codegen;

import com.kojaeeon.dashboard.documents.MarkdownSources;
import java.io.IOException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/codegen")
public class CodeGenController {

  private final CodeGenService service;
  private final MarkdownSources sources;

  public CodeGenController(CodeGenService service, MarkdownSources sources) {
    this.service = service;
    this.sources = sources;
  }

  @GetMapping("/sources")
  public MarkdownSources.Listing sources() {
    return sources.list(true, true, false);
  }

  @GetMapping("/inputs")
  public CodeGenService.Inputs inputs(@RequestParam String key) throws IOException {
    return service.read(sources.read(key, true, true, false).content());
  }

  @PostMapping("/preview")
  public CodeGenService.Preview preview(@RequestBody GenerationRequest request) {
    return service.preview(request);
  }

  @GetMapping("/previews/{id}/zip")
  public ResponseEntity<byte[]> zip(@PathVariable String id) {
    var preview = service.get(id);
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType("application/zip"))
      .header(
        HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(preview.archiveName()).build().toString()
      )
      .body(CodeGenService.buildZip(preview.files()));
  }
}
