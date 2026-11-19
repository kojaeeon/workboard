package com.kojaeeon.dashboard.documents;

import com.kojaeeon.dashboard.shared.ApiErrors;
import com.microsoft.playwright.PlaywrightException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

  private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
  private final DocumentRepository repository;
  private final LocalDocumentService files;
  private final DocumentPdf pdf;
  private final Path examples;

  public DocumentController(
    DocumentRepository repository,
    LocalDocumentService files,
    DocumentPdf pdf,
    @Value("${dashboard.examples-path}") String examples
  ) {
    this.repository = repository;
    this.files = files;
    this.pdf = pdf;
    this.examples = Path.of(examples);
  }

  public record Input(String title, String content) {}

  public record FolderInput(String path) {}

  public record SaveFile(String path, String revision, String content) {}

  public record PdfInput(String title, String html) {}

  @GetMapping
  public List<DocumentRepository.DocumentSummary> list() {
    return repository.summaries();
  }

  @GetMapping("/{id}")
  public DocumentRepository.Document get(@PathVariable long id) {
    return repository.get(id);
  }

  @PostMapping("/pdf")
  public ResponseEntity<?> exportPdf(@RequestBody PdfInput input) {
    try {
      byte[] content = pdf.render(input.title(), input.html());
      return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore())
        .header(
          HttpHeaders.CONTENT_DISPOSITION,
          ContentDisposition.attachment()
            .filename(DocumentPdf.filename(input.title()), StandardCharsets.UTF_8)
            .build()
            .toString()
        )
        .body(content);
    } catch (PlaywrightException failure) {
      log.warn("PDF rendering failed", failure);
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiErrors.Error(
        "PDF 출력에 실패했습니다. Chromium 설치 또는 DASHBOARD_PDF_CHROMIUM_PATH 설정을 확인하세요."
      ));
    }
  }

  @PostMapping
  public Map<String, Long> create(@RequestBody Input input) {
    return Map.of("id", repository.save(null, input.title(), input.content()));
  }

  @PutMapping("/{id}")
  public void update(@PathVariable long id, @RequestBody Input input) {
    repository.save(id, input.title(), input.content());
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable long id) {
    repository.delete(id);
  }

  @GetMapping("/sample")
  public Input sample() throws IOException {
    Path source = examples.resolve("main-test.md");
    if (Files.isRegularFile(source)) return new Input("시작하기", Files.readString(source));
    try (
      var stream = new org.springframework.core.io.ClassPathResource(
        "markdown-sample.md"
      ).getInputStream()
    ) {
      return new Input(
        "시작하기",
        new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
      );
    }
  }

  @GetMapping("/folders")
  public List<LocalDocumentService.FolderFiles> folders() {
    return files.folders();
  }

  @PostMapping("/folders")
  public void folder(@RequestBody FolderInput input) throws IOException {
    files.register(input.path());
  }

  @DeleteMapping("/folders/{id}")
  public void remove(@PathVariable long id) {
    repository.removeFolder(id);
  }

  @GetMapping("/local")
  public LocalDocumentService.LocalFile open(@RequestParam String path) throws IOException {
    return files.open(path);
  }

  @PutMapping("/local")
  public LocalDocumentService.LocalFile save(@RequestBody SaveFile input) throws IOException {
    return files.save(input.path(), input.revision(), input.content());
  }

  @PostMapping("/upload")
  public Input upload(@RequestParam MultipartFile file) throws IOException {
    String name = Objects.requireNonNullElse(file.getOriginalFilename(), "문서.md");
    return new Input(
      name.replaceFirst("\\.[^.]*$", ""),
      LocalDocumentService.decode(file.getBytes())
    );
  }
}
