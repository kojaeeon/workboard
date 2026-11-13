package com.kojaeeon.dashboard.codegen;

import freemarker.template.Configuration;
import java.io.StringWriter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PreviewCodeGenerator implements CodeGenerator {

  private final Configuration templates;

  public PreviewCodeGenerator() {
    templates = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
    templates.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "codegen");
    templates.setDefaultEncoding("UTF-8");
    templates.setLogTemplateExceptions(false);
    templates.setWrapUncheckedExceptions(true);
  }

  public List<GeneratedFile> generate(GenerationRequest request) {
    request.validate();
    var files = new ArrayList<GeneratedFile>();
    for (String target : request.targets()) {
      boolean xml = target.equals("Mapper");
      var context = Map.of(
        "table_name",
        request.table_name(),
        "service_name",
        request.service_name(),
        "service_korean_name",
        request.service_korean_name().replaceAll("\\R", " "),
        "package_path",
        request.package_path(),
        "target",
        target
      );
      var text = new StringWriter();
      try {
        templates.getTemplate(xml ? "mapper.xml.ftl" : "class.java.ftl").process(context, text);
      } catch (Exception e) {
        throw new IllegalStateException("코드 템플릿을 처리하지 못했습니다.", e);
      }
      String directory = xml
        ? "src/main/resources/mappers"
        : "src/main/java/" + request.package_path().replace('.', '/');
      files.add(
        new GeneratedFile(
          directory + "/" + request.service_name() + target + (xml ? ".xml" : ".java"),
          text.toString(),
          xml ? "xml" : "java"
        )
      );
    }
    return List.copyOf(files);
  }

  @Override
  public String status() {
    return "샘플 미리보기 · 실제 생성 서비스 연결 전";
  }
}
