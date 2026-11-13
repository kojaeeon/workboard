package com.kojaeeon.dashboard.codegen;

import com.kojaeeon.dashboard.database.QueryResultStore;
import com.kojaeeon.dashboard.shared.Frontmatter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;

@Service
public class CodeGenService {

  public record Preview(
    String id,
    String archiveName,
    List<CodeGenerator.GeneratedFile> files,
    String status,
    List<String> warnings,
    String queryId
  ) {}

  public record Inputs(GenerationRequest values, String yaml) {}

  private final CodeGenerator generator;
  private final Frontmatter frontmatter;
  private QueryCodeGenerator queryGenerator;
  private QueryResultStore queryResults;
  private final LinkedHashMap<String, Preview> previews = new LinkedHashMap<>();

  public CodeGenService(CodeGenerator generator, Frontmatter frontmatter) {
    this.generator = generator;
    this.frontmatter = frontmatter;
  }

  @org.springframework.beans.factory.annotation.Autowired
  public CodeGenService(
    CodeGenerator generator,
    Frontmatter frontmatter,
    QueryCodeGenerator queryGenerator,
    QueryResultStore queryResults
  ) {
    this(generator, frontmatter);
    this.queryGenerator = queryGenerator;
    this.queryResults = queryResults;
  }

  public Inputs read(String markdown) {
    Object raw = frontmatter.read(markdown).get("workItem");
    if (!(raw instanceof Map<?, ?> item)) throw new IllegalArgumentException(
      "프론트매터에 workItem 항목이 필요합니다."
    );
    var values = new ArrayList<String>();
    var fields = new LinkedHashMap<String, String>();
    for (String key : List.of("table", "service", "serviceNm", "package")) {
      Object value = item.get(key);
      if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(
        "workItem." + key + "에 문자열 값을 입력하세요."
      );
      values.add(text.strip());
      fields.put(key, text.strip());
    }
    var options = new org.yaml.snakeyaml.DumperOptions();
    options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
    return new Inputs(
      new GenerationRequest(values.get(0), values.get(1), values.get(2), values.get(3), null),
      new org.yaml.snakeyaml.Yaml(options).dump(Map.of("workItem", fields))
    );
  }

  public synchronized Preview preview(GenerationRequest request) {
    request.validate();
    var files = List.copyOf(generator.generate(request));
    buildZip(files);
    var result = new Preview(
      UUID.randomUUID().toString(),
      request.service_name() + ".zip",
      files,
      generator.status(),
      List.of(),
      null
    );
    previews.put(result.id(), result);
    while (previews.size() > 100) previews.remove(previews.keySet().iterator().next());
    return result;
  }

  public synchronized Preview previewFromQuery(String queryId, GenerationRequest request) {
    var result = queryResults.get(queryId);
    var generated = queryGenerator.generate(request, result);
    buildZip(generated.files());
    var preview = new Preview(
      UUID.randomUUID().toString(),
      request.service_name() + ".zip",
      generated.files(),
      "DB 조회 결과 기반 코드 생성 완료",
      generated.warnings(),
      queryId
    );
    previews.put(preview.id(), preview);
    while (previews.size() > 100) previews.remove(previews.keySet().iterator().next());
    return preview;
  }

  public synchronized Preview get(String id) {
    var preview = previews.get(id);
    if (preview == null) throw new IllegalArgumentException(
      "미리보기가 만료되었습니다. 코드 미리보기를 다시 실행하세요."
    );
    return preview;
  }

  public static byte[] buildZip(List<CodeGenerator.GeneratedFile> files) {
    if (files.isEmpty()) throw new IllegalArgumentException("다운로드할 생성 파일이 없습니다.");
    var names = new HashSet<String>();
    try (
      var bytes = new ByteArrayOutputStream();
      var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)
    ) {
      for (var file : files) {
        String name = file.path();
        if (
          name == null ||
          name.isBlank() ||
          name.startsWith("/") ||
          name.contains("\\") ||
          name.contains(":") ||
          Arrays.asList(name.split("/", -1)).contains("..") ||
          name.equals(".")
        ) throw new IllegalArgumentException(
          "생성 파일 경로는 프로젝트 내부 상대 경로여야 합니다."
        );
        if (!names.add(name)) throw new IllegalArgumentException("중복된 생성 파일 경로입니다.");
        zip.putNextEntry(new ZipEntry(name));
        zip.write(file.content().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      zip.finish();
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("ZIP 파일을 만들지 못했습니다.", e);
    }
  }
}
