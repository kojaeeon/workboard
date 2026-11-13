package com.kojaeeon.dashboard.documents;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class MarkdownSources {

  public record Source(String key, String label, String location) {}

  public record Listing(List<Source> sources, List<String> warnings) {}

  public record Content(String location, String content) {}

  private final DocumentRepository repository;

  public MarkdownSources(DocumentRepository repository) {
    this.repository = repository;
  }

  private boolean markdown(Path path) {
    return path.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(md|markdown)$");
  }

  public Listing list(boolean recursive, boolean saved, boolean examples) {
    var sources = new ArrayList<Source>();
    var warnings = new ArrayList<String>();
    var seen = new HashSet<Path>();
    if (examples) {
      sources.add(
        new Source(
          "example:ui-test-smoke.md",
          "기본 예시 · ui-test-smoke.md",
          "examples/ui-test-smoke.md"
        )
      );
      sources.add(
        new Source("example:browser.md", "브라우저 설정 예시 · browser.md", "examples/browser.md")
      );
    }
    if (saved) repository
      .list()
      .forEach(d ->
        sources.add(
          new Source("document:" + d.id(), "[저장 문서] " + d.title(), "MD 보기 · 저장 문서")
        )
      );
    for (var folder : repository.folders()) {
      Path root = Path.of(folder.path());
      try (var paths = recursive ? Files.walk(root) : Files.list(root)) {
        Path realRoot = root.toRealPath();
        for (Path file : paths
          .filter(Files::isRegularFile)
          .filter(this::markdown)
          .sorted()
          .toList()) {
          Path real = file.toRealPath();
          if (!real.startsWith(realRoot) || !seen.add(real)) continue;
          sources.add(
            new Source(
              "file:" + real,
              "[" + root.getFileName() + "] " + root.relativize(file),
              real.toString()
            )
          );
        }
      } catch (IOException e) {
        warnings.add(root + ": 폴더가 없거나 접근할 수 없습니다.");
      }
    }
    return new Listing(sources, warnings);
  }

  public Content read(String key, boolean recursive, boolean saved, boolean examples)
    throws IOException {
    if (key == null) throw new IllegalArgumentException("MD를 선택하세요.");
    if (examples && Set.of("example:ui-test-smoke.md", "example:browser.md").contains(key)) {
      String name = key.substring(8);
      try (var stream = new ClassPathResource("examples/" + name).getInputStream()) {
        return new Content("examples/" + name, LocalDocumentService.decode(stream.readAllBytes()));
      }
    }
    if (saved && key.startsWith("document:")) {
      var document = repository
        .list()
        .stream()
        .filter(d -> key.equals("document:" + d.id()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("저장 문서를 찾을 수 없습니다."));
      return new Content("MD 보기 · 저장 문서", document.content());
    }
    if (!key.startsWith("file:")) throw new IllegalArgumentException(
      "목록을 새로고침하고 MD를 다시 선택하세요."
    );
    Path file = Path.of(key.substring(5)).toRealPath();
    boolean allowed = false;
    for (var folder : repository.folders()) {
      try {
        Path root = Path.of(folder.path()).toRealPath();
        if (recursive ? file.startsWith(root) : file.getParent().equals(root)) {
          allowed = true;
          break;
        }
      } catch (IOException ignored) {
        /* Other registered folders can still resolve this file. */
      }
    }
    if (
      !allowed || !markdown(file) || !Files.isRegularFile(file)
    ) throw new IllegalArgumentException("등록 폴더 안의 MD 파일만 열 수 있습니다.");
    if (Files.size(file) > LocalDocumentService.MAX_BYTES) throw new IllegalArgumentException(
      "2MB 이하 MD 파일만 지원합니다."
    );
    return new Content(file.toString(), LocalDocumentService.decode(Files.readAllBytes(file)));
  }
}
