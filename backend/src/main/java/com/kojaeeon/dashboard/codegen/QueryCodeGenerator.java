package com.kojaeeon.dashboard.codegen;

import com.kojaeeon.dashboard.database.QueryResult;
import freemarker.template.Configuration;
import java.io.StringWriter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class QueryCodeGenerator {

  public record Output(List<CodeGenerator.GeneratedFile> files, List<String> warnings) {}

  private final QueryMetadata metadata;
  private final Configuration templates;

  public QueryCodeGenerator(QueryMetadata metadata) {
    this.metadata = metadata;
    templates = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
    templates.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "codegen/database");
    templates.setDefaultEncoding("UTF-8");
    templates.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
    templates.setLogTemplateExceptions(false);
    templates.setAPIBuiltinEnabled(false);
  }

  public Output generate(GenerationRequest request, QueryResult result) {
    request.validate();
    var model = metadata.read(result, request);
    var context = new HashMap<String, Object>();
    context.put("package", request.package_path());
    context.put("service", request.service_name());
    context.put(
      "description",
      request.service_korean_name().replace("*/", "* /").replaceAll("\\R", " ")
    );
    context.put("table", model.quotedTable());
    context.put("columns", model.columns().stream().map(this::column).toList());
    context.put("keys", model.keys().stream().map(this::column).toList());
    context.put("insertable", model.insertable().stream().map(this::column).toList());
    context.put("updatable", model.updatable().stream().map(this::column).toList());
    context.put("imports", model.imports());
    context.put(
      "selectColumns",
      model
        .columns()
        .stream()
        .map(QueryMetadata.Column::quoted)
        .collect(java.util.stream.Collectors.joining(", "))
    );
    context.put(
      "keyArgs",
      model
        .keys()
        .stream()
        .map(c -> c.javaType() + " " + c.property())
        .collect(java.util.stream.Collectors.joining(", "))
    );
    context.put(
      "keyNames",
      model
        .keys()
        .stream()
        .map(QueryMetadata.Column::property)
        .collect(java.util.stream.Collectors.joining(", "))
    );
    context.put(
      "keyPath",
      model
        .keys()
        .stream()
        .map(c -> "/{" + c.property() + "}")
        .collect(java.util.stream.Collectors.joining())
    );
    context.put(
      "endpoint",
      request.service_name().replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT)
    );
    var files = new ArrayList<CodeGenerator.GeneratedFile>();
    String root = "src/main/java/" + request.package_path().replace('.', '/') + "/";
    for (String target : request.targets()) {
      if (target.equals("Mapper")) {
        files.add(
          render("Mapper.java.ftl", root + request.service_name() + "Mapper.java", "java", context)
        );
        files.add(
          render(
            "Mapper.xml.ftl",
            "src/main/resources/mappers/" + request.service_name() + "Mapper.xml",
            "xml",
            context
          )
        );
      } else files.add(
        render(
          target + ".java.ftl",
          root + request.service_name() + target + ".java",
          "java",
          context
        )
      );
    }
    var warnings = new ArrayList<>(model.warnings());
    if (
      request.targets().contains("Controller") && !request.targets().contains("Service")
    ) warnings.add(
      "Controller가 참조하는 Service는 선택하지 않았습니다. 기존 클래스가 필요합니다."
    );
    if (
      (request.targets().contains("Service") || request.targets().contains("Controller")) &&
      !request.targets().contains("Mapper")
    ) warnings.add("참조하는 Mapper는 선택하지 않았습니다. 기존 인터페이스가 필요합니다.");
    if (!request.targets().contains("VO")) warnings.add(
      "참조하는 VO는 선택하지 않았습니다. 기존 클래스가 필요합니다."
    );
    return new Output(List.copyOf(files), List.copyOf(warnings));
  }

  private Map<String, Object> column(QueryMetadata.Column column) {
    var value = new HashMap<String, Object>();
    value.put("name", column.name());
    value.put("quoted", column.quoted());
    value.put("property", column.property());
    value.put(
      "suffix",
      Character.toUpperCase(column.property().charAt(0)) + column.property().substring(1)
    );
    value.put("javaType", column.javaType());
    value.put("jdbcType", column.jdbcType());
    value.put("comment", column.comment());
    value.put("primary", column.pkPosition() > 0);
    value.put("nullable", column.nullable());
    return value;
  }

  private CodeGenerator.GeneratedFile render(
    String template,
    String path,
    String language,
    Map<String, Object> context
  ) {
    try {
      var output = new StringWriter();
      templates.getTemplate(template).process(context, output);
      return new CodeGenerator.GeneratedFile(path, output.toString(), language);
    } catch (Exception e) {
      throw new IllegalArgumentException(
        "DB 조회 결과로 템플릿을 처리하지 못했습니다: " + template,
        e
      );
    }
  }
}
