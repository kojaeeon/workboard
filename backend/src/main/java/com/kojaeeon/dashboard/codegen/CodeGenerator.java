package com.kojaeeon.dashboard.codegen;

import java.util.List;

public interface CodeGenerator {
  record GeneratedFile(String path, String content, String language) {}

  List<GeneratedFile> generate(GenerationRequest request);

  default String status() {
    return "코드 생성 완료";
  }
}
