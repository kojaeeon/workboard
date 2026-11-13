package com.kojaeeon.dashboard.mindmap;

import java.util.*;

/** Wire format is deliberately identical to the original KJA graph JSON. */
public record Graph(
  Integer version,
  String root,
  List<Node> nodes,
  List<Edge> edges,
  List<Summary> summaries,
  View view
) {
  public Graph {
    version = version == null ? 1 : version;
    edges = edges == null ? List.of() : List.copyOf(edges);
    summaries = summaries == null ? List.of() : List.copyOf(summaries);
    view = view == null ? new View(null, null, null, null, null, null) : view;
    if (
      version != 1 ||
      nodes == null ||
      nodes.isEmpty() ||
      nodes.size() > 2000 ||
      edges.size() > 5000 ||
      summaries.size() > 1000
    ) invalid("맵 크기 또는 버전을 확인하세요.");
    nodes = List.copyOf(nodes);
    var ids = new HashSet<String>();
    for (var node : nodes) if (!ids.add(node.id())) invalid("노드 ID 중복 또는 루트 누락입니다.");
    if (!ids.contains(root)) invalid("노드 ID 중복 또는 루트 누락입니다.");
    var edgeIds = new HashSet<String>();
    var parents = new HashSet<String>();
    var children = new HashMap<String, List<String>>();
    ids.forEach(id -> children.put(id, new ArrayList<>()));
    for (var edge : edges) {
      if (!edgeIds.add(edge.id())) invalid("관계 ID가 중복되었습니다.");
      if (!ids.contains(edge.source()) || !ids.contains(edge.target())) invalid(
        "존재하지 않는 노드를 연결한 관계입니다."
      );
      if (edge.tree()) {
        if (!parents.add(edge.target()) || edge.target().equals(root)) invalid(
          "계층 노드는 하나의 부모만 가질 수 있습니다."
        );
        children.get(edge.source()).add(edge.target());
      }
    }
    record Visit(String id, int depth) {}
    var stack = new ArrayDeque<Visit>();
    stack.push(new Visit(root, 0));
    var visited = new HashSet<String>();
    while (!stack.isEmpty()) {
      var visit = stack.pop();
      if (!visited.add(visit.id()) || visit.depth() > 100) invalid(
        "계층 순환 또는 100단계를 초과한 깊이입니다."
      );
      children.get(visit.id()).forEach(child -> stack.push(new Visit(child, visit.depth() + 1)));
    }
    if (!visited.equals(ids)) invalid(
      "모든 노드를 루트 계층에 연결하세요. 추가 관계는 별도로 지정할 수 있습니다."
    );
    var summaryIds = new HashSet<String>();
    for (var summary : summaries) {
      if (!summaryIds.add(summary.id())) invalid("요약 ID가 중복되었습니다.");
      if (
        !ids.contains(summary.parent()) ||
        summary.start() < 0 ||
        summary.end() < summary.start() ||
        summary.end() >= children.get(summary.parent()).size()
      ) invalid("요약의 대상 계층 범위가 올바르지 않습니다.");
    }
  }

  static void invalid(String message) {
    throw new IllegalArgumentException(message);
  }

  static String text(String value, int min, int max, String name) {
    if (
      value == null ||
      value.codePointCount(0, value.length()) < min ||
      value.codePointCount(0, value.length()) > max
    ) invalid(name + " 길이를 확인하세요.");
    return value;
  }

  static String identifier(String value) {
    text(value, 1, 100, "ID");
    if (!value.matches("[A-Za-z0-9_-]+")) invalid("올바른 ID를 입력하세요.");
    return value;
  }

  static String color(String value) {
    if (value != null && !value.matches("#[0-9a-fA-F]{6}")) invalid(
      "색상은 #RRGGBB 형식으로 입력하세요."
    );
    return value;
  }

  public record Style(
    String background,
    String color,
    String border_color,
    Integer font_size,
    String shape,
    String icon
  ) {
    public Style {
      Graph.color(background);
      Graph.color(color);
      Graph.color(border_color);
      if (font_size != null && (font_size < 10 || font_size > 40)) invalid(
        "글자 크기는 10~40입니다."
      );
      if (
        shape != null &&
        !Set.of(
          "rounded",
          "rectangle",
          "pill",
          "ellipse",
          "diamond",
          "cylinder",
          "hexagon",
          "document"
        ).contains(shape)
      ) invalid("지원하지 않는 모양입니다.");
      if (icon != null) {
        text(icon, 0, 12, "아이콘");
        if (icon.matches(".*[<>&\"'`].*")) invalid("아이콘에는 문자 또는 이모지만 입력하세요.");
      }
    }
  }

  public record Node(
    String id,
    String label,
    String type,
    String description,
    String file,
    Style overrides,
    Boolean expanded
  ) {
    public Node {
      identifier(id);
      text(label, 1, 1000, "노드 이름");
      type = type == null ? "서비스" : type;
      description = description == null ? "" : description;
      file = file == null ? "" : file;
      text(type, 0, 60, "유형");
      text(description, 0, 20000, "설명");
      text(file, 0, 4096, "파일 경로");
      overrides = overrides == null ? new Style(null, null, null, null, null, null) : overrides;
      expanded = expanded == null ? true : expanded;
    }
  }

  public record Edge(
    String id,
    String source,
    String target,
    String label,
    String kind,
    Boolean tree,
    String color,
    Boolean dashed,
    Boolean bidirectional
  ) {
    public Edge {
      text(id, 1, 100, "관계 ID");
      label = label == null ? "" : label;
      kind = kind == null ? "참조" : kind;
      text(label, 0, 1000, "관계 이름");
      text(kind, 0, 60, "관계 유형");
      tree = tree == null ? false : tree;
      color = color == null ? "#b89a60" : color;
      Graph.color(color);
      dashed = dashed == null ? true : dashed;
      bidirectional = bidirectional == null ? false : bidirectional;
    }
  }

  public record SummaryStyle(String stroke, String labelColor) {
    public SummaryStyle {
      stroke = stroke == null ? "#b89a60" : stroke;
      labelColor = labelColor == null ? "#eee9de" : labelColor;
      color(stroke);
      color(labelColor);
    }
  }

  public record Summary(
    String id,
    String parent,
    int start,
    int end,
    String label,
    SummaryStyle style
  ) {
    public Summary {
      identifier(id);
      label = label == null ? "요약" : label;
      text(label, 1, 1000, "요약 이름");
      style = style == null ? new SummaryStyle(null, null) : style;
    }
  }

  public record View(
    Integer direction,
    Double scale,
    Double pan_x,
    Double pan_y,
    Double scroll_left,
    Double scroll_top
  ) {
    public View {
      direction = direction == null ? 1 : direction;
      scale = scale == null ? 1 : scale;
      pan_x = pan_x == null ? 0 : pan_x;
      pan_y = pan_y == null ? 0 : pan_y;
      scroll_left = scroll_left == null ? 0 : scroll_left;
      scroll_top = scroll_top == null ? 0 : scroll_top;
      if (
        direction < 0 ||
        direction > 3 ||
        !Double.isFinite(scale) ||
        scale < .1 ||
        scale > 8 ||
        !Double.isFinite(pan_x) ||
        !Double.isFinite(pan_y) ||
        !Double.isFinite(scroll_left) ||
        !Double.isFinite(scroll_top) ||
        scroll_left < 0 ||
        scroll_top < 0
      ) invalid("맵 보기 설정을 확인하세요.");
    }
  }

  public record NodeType(String name, Style style) {
    public NodeType {
      text(name, 1, 60, "유형 이름");
      if (name.isBlank()) invalid("유형 이름을 입력하세요.");
      style = style == null ? new Style(null, null, null, null, null, null) : style;
    }
  }
}
