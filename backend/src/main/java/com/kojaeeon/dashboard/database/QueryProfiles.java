package com.kojaeeon.dashboard.database;

import com.kojaeeon.dashboard.documents.*;
import com.kojaeeon.dashboard.shared.ApiErrors.Conflict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class QueryProfiles {

  public record ProfileDocument(String key, String location, String markdown, String revision) {}

  public record Save(String key, String markdown, String revision) {}

  private final MarkdownSources sources;
  private final DocumentRepository documents;
  private final LocalDocumentService files;
  private final QueryProfileParser parser;

  public QueryProfiles(
    MarkdownSources sources,
    DocumentRepository documents,
    LocalDocumentService files,
    QueryProfileParser parser
  ) {
    this.sources = sources;
    this.documents = documents;
    this.files = files;
    this.parser = parser;
  }

  public MarkdownSources.Listing list() {
    var list = sources.list(true, true, false);
    var all = new ArrayList<MarkdownSources.Source>();
    all.add(
      new MarkdownSources.Source(
        "example:db-query-sqlite.md",
        "SQLite · 컬럼 조회 예시",
        "내장 예시"
      )
    );
    all.add(
      new MarkdownSources.Source("example:db-query-db2.md", "DB2 LUW · 컬럼 조회 예시", "내장 예시")
    );
    all.addAll(list.sources());
    return new MarkdownSources.Listing(all, list.warnings());
  }

  public ProfileDocument open(String key) throws IOException {
    if (Set.of("example:db-query-sqlite.md", "example:db-query-db2.md").contains(key)) try (
      var input = new ClassPathResource("examples/" + key.substring(8)).getInputStream()
    ) {
      return new ProfileDocument(
        key,
        "내장 예시 · 저장하면 SQLite 문서로 복사",
        LocalDocumentService.decode(input.readAllBytes()),
        null
      );
    }
    if (key != null && key.startsWith("document:")) {
      var document = documents.get(Long.parseLong(key.substring(9)));
      return new ProfileDocument(key, "SQLite 문서", document.content(), hash(document.content()));
    }
    if (key != null && key.startsWith("file:")) {
      var file = files.openRecursive(key.substring(5));
      return new ProfileDocument(key, file.path(), file.content(), file.revision());
    }
    throw new IllegalArgumentException("MD 목록에서 문서를 선택하세요.");
  }

  public ProfileDocument save(Save input) throws IOException {
    var profile = parser.parse(input.markdown());
    String key = input.key();
    if (key == null || key.startsWith("example:")) {
      long id = documents.save(null, profile.title(), input.markdown());
      return open("document:" + id);
    }
    if (key.startsWith("document:")) {
      var original = documents.get(Long.parseLong(key.substring(9)));
      if (!hash(original.content()).equals(input.revision())) throw new Conflict(
        "다른 화면에서 MD가 변경되었습니다. 다시 열어 확인하세요."
      );
      documents.saveIfUnchanged(
        original.id(),
        profile.title(),
        input.markdown(),
        original.content()
      );
      return open(key);
    }
    if (key.startsWith("file:")) {
      files.saveRecursive(key.substring(5), input.revision(), input.markdown());
      return open(key);
    }
    throw new IllegalArgumentException("MD 저장 위치를 확인하세요.");
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
