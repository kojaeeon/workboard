package com.kojaeeon.dashboard.codegen;

import java.util.*;

public record GenerationRequest(
  String table_name,
  String service_name,
  String service_korean_name,
  String package_path,
  List<String> targets
) {
  public static final List<String> TARGETS = List.of("Controller", "Service", "VO", "Mapper");

  public GenerationRequest {
    table_name = Objects.requireNonNullElse(table_name, "").strip();
    service_name = Objects.requireNonNullElse(service_name, "").strip();
    service_korean_name = Objects.requireNonNullElse(service_korean_name, "").strip();
    package_path = Objects.requireNonNullElse(package_path, "").strip();
    targets = targets == null ? TARGETS : List.copyOf(targets);
  }

  public void validate() {
    if (
      !table_name.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)?")
    ) throw new IllegalArgumentException(
      "테이블명은 테이블 또는 스키마.테이블 형식으로 입력하세요."
    );
    if (!service_name.matches("[A-Z][A-Za-z0-9_]*")) throw new IllegalArgumentException(
      "서비스명은 영문 대문자로 시작하는 클래스명으로 입력하세요."
    );
    if (service_korean_name.isBlank()) throw new IllegalArgumentException(
      "서비스 한글명을 입력하세요."
    );
    if (
      !package_path.matches("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*")
    ) throw new IllegalArgumentException("패키지 경로는 com.example.service 형식으로 입력하세요.");
    if (
      targets.isEmpty() ||
      new HashSet<>(targets).size() != targets.size() ||
      !TARGETS.containsAll(targets)
    ) throw new IllegalArgumentException("생성 대상을 하나 이상 선택하세요.");
  }
}
