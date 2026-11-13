package com.kojaeeon.dashboard;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kojaeeon.dashboard.automation.ScenarioParser;
import com.kojaeeon.dashboard.codegen.*;
import com.kojaeeon.dashboard.shared.Frontmatter;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.*;

class AutomationAndCodeGenTest {

  final ObjectMapper json = new ObjectMapper();
  final Frontmatter frontmatter = new Frontmatter();
  final ScenarioParser parser = new ScenarioParser(frontmatter, json);

  @Test
  void originalScenarioKeepsStepOrderAndLeadingZeros() {
    var scenario = parser.parse(
      "---\nworkItem: {serviceNm: 고객 조회}\nstep:\n - {action: menu, label: CDD, syscode: oras}\n - {action: text, label: 고객번호, value: '001234'}\n - {action: click, attr: {data-testid: search}}\n---\n설명은 실행하지 않습니다."
    );
    assertThat(scenario.title()).isEqualTo("고객 조회");
    assertThat(scenario.steps())
      .extracting(ScenarioParser.Step::action)
      .containsExactly("menu", "text", "click");
    assertThat(scenario.steps().get(1).stringValue()).isEqualTo("001234");
  }

  @Test
  void invalidScenariosAreRejectedBeforeLaunching() {
    for (String source : List.of(
      "# no frontmatter",
      "---\nstep: []\n---",
      "---\nstep: [{action: eval, value: danger}]\n---",
      "---\nstep: [{action: fill, label: Username}]\n---",
      "---\nstep: [{action: click, unexpected: true}]\n---",
      "---\nbrowser: {base_url: 'file:///etc'}\nstep: [{action: screenshot}]\n---",
      "---\nstep: !!python/object:os.system {}\n---"
    )) {
      assertThatThrownBy(() -> parser.parse(source))
        .as(source)
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void browserOverrideMergesOnlyExplicitFieldsAndViewport() {
    var scenario = parser.parse(
      "---\nbrowser: {base_url: 'http://example.test', timeout: 5000, viewport: {width: 800, height: 600}}\nstep: [{action: screenshot}]\n---"
    );
    var merged = parser.effective(
      scenario.browser(),
      "---\nbrowser: chromium\nviewport: {width: 1920}\nheadless: true\n---",
      null,
      null
    );
    assertThat(merged.base_url()).isEqualTo("http://example.test");
    assertThat(merged.timeout()).isEqualTo(5000);
    assertThat(merged.viewport().width()).isEqualTo(1920);
    assertThat(merged.viewport().height()).isEqualTo(600);
    assertThat(merged.headless()).isTrue();
    var manual = parser.effective(
      scenario.browser(),
      "---\nbrowser: {headless: true}\n---",
      "http://localhost:8080",
      true
    );
    assertThat(manual.base_url()).isEqualTo("http://localhost:8080");
    assertThat(manual.headless()).isFalse();
  }

  @Test
  void invalidBrowserConfigDoesNotFallBack() {
    for (String source : List.of(
      "---\nbrowser: unknown\n---",
      "---\nbrowser: chromium\nviewport: {width: 0}\n---",
      "---\nbrowser: chromium\ntimeout: -1\n---",
      "---\nheadless: false\n---"
    ))
      assertThatThrownBy(() -> parser.browserOverride(source)).isInstanceOf(
        IllegalArgumentException.class
      );
  }

  @Test
  void previewContentsMatchOriginalTemplates() throws Exception {
    var request = new GenerationRequest(
      "KAML.TB_CRA",
      "CraInspection",
      "CRA 정기점검",
      "com.kaml.cra",
      null
    );
    var files = new PreviewCodeGenerator().generate(request);
    assertThat(files).hasSize(4);
    assertThat(files.get(0).content()).isEqualTo(
      "package com.kaml.cra;\n\n// 샘플: CRA 정기점검\n// 대상 테이블: KAML.TB_CRA\npublic class CraInspectionController {\n    // 실제 생성 서비스 연결 영역\n}\n"
    );
    assertThat(files.get(3).content()).isEqualTo(
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!-- 샘플 Mapper: 실제 SQL 생성 서비스 연결 전 -->\n<mapper namespace=\"com.kaml.cra.CraInspectionMapper\">\n    <!-- 대상 테이블: KAML.TB_CRA -->\n</mapper>\n"
    );
    try (
      var zip = new ZipInputStream(
        new ByteArrayInputStream(CodeGenService.buildZip(files)),
        StandardCharsets.UTF_8
      )
    ) {
      for (var file : files) {
        assertThat(zip.getNextEntry().getName()).isEqualTo(file.path());
        assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(
          file.content()
        );
      }
      assertThat(zip.getNextEntry()).isNull();
    }
  }

  @Test
  void generatedZipRejectsTraversalAndDuplicatePaths() {
    for (String path : List.of("../bad.java", "/bad.java", "C:/bad.java", "a\\bad.java"))
      assertThatThrownBy(() ->
        CodeGenService.buildZip(List.of(new CodeGenerator.GeneratedFile(path, "", "java")))
      ).isInstanceOf(IllegalArgumentException.class);
    var file = new CodeGenerator.GeneratedFile("same.java", "", "java");
    assertThatThrownBy(() -> CodeGenService.buildZip(List.of(file, file))).isInstanceOf(
      IllegalArgumentException.class
    );
  }

  @Test
  void generationValidatesIdentifiersAndSelectedTargets() {
    var generator = new PreviewCodeGenerator();
    assertThatThrownBy(() ->
      generator.generate(new GenerationRequest("TB_CRA", "../bad", "점검", "com.example", null))
    ).isInstanceOf(IllegalArgumentException.class);
    var files = generator.generate(
      new GenerationRequest("TB_CRA", "Cra", "점검", "com.example", List.of("VO"))
    );
    assertThat(files)
      .extracting(CodeGenerator.GeneratedFile::path)
      .containsExactly("src/main/java/com/example/CraVO.java");
    assertThatThrownBy(() ->
      generator.generate(new GenerationRequest("TB_CRA", "Cra", "점검", "com.example", List.of()))
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void readsAllFourWorkItemInputsAtomically() {
    var service = new CodeGenService(new PreviewCodeGenerator(), frontmatter);
    var input = service.read(
      "---\nworkItem: {table: ' KAML.TB_CRA ', service: CraInspection, serviceNm: CRA 정기점검, package: com.kaml.cra}\n---"
    );
    assertThat(input.values().table_name()).isEqualTo("KAML.TB_CRA");
    assertThat(input.yaml()).contains("workItem:", "serviceNm: CRA 정기점검");
    assertThatThrownBy(() ->
      service.read("---\nworkItem: {table: TB_CRA, service: Cra}\n---")
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void previewDownloadKeepsItsOwnSnapshot() {
    var service = new CodeGenService(new PreviewCodeGenerator(), frontmatter);
    var first = service.preview(
      new GenerationRequest("TB_FIRST", "First", "첫 번째", "com.example", null)
    );
    service.preview(new GenerationRequest("TB_NEXT", "Next", "다음", "com.example", null));
    assertThat(service.get(first.id()).files().get(0).content())
      .contains("class FirstController", "TB_FIRST")
      .doesNotContain("TB_NEXT");
  }
}
