package com.kojaeeon.dashboard.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.HandlerMapping;

@Controller
class WorkspaceController {

  record Page(String id, String label, String icon) {
    public String path() {
      return "/" + id;
    }
  }

  private static final List<Page> PAGES = List.of(
    new Page("documents", "MD 보기", "description"),
    new Page("work", "Work", "task_alt"),
    new Page("schedule", "일정", "calendar_month"),
    new Page("ui-test", "UI 테스트", "science"),
    new Page("codegen", "코드 생성", "code"),
    new Page("database", "DB 조회", "storage"),
    new Page("mindmap", "마인드맵", "account_tree")
  );

  @GetMapping({
    "/",
    "/documents",
    "/work",
    "/schedule",
    "/ui-test",
    "/codegen",
    "/database",
    "/mindmap",
  })
  String workspace(HttpServletRequest request, Model model) {
    Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    Page page = PAGES.stream()
      .filter(item -> item.path().equals(route))
      .findFirst()
      .orElseGet(PAGES::getFirst);
    model.addAttribute("pages", PAGES);
    model.addAttribute("page", page);
    return "workspace";
  }
}
