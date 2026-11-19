# 워크보드 변경 이력

## 2026-11-19 · MD 열기와 PDF 출력

| 변경 | 이유와 처리 방식 | 주요 파일 |
| --- | --- | --- |
| MD 보기의 열기·삭제 | 로컬·SQLite 문서 열기와 가져오기는 최신 선택만 반영한다. 삭제는 확인창을 열 때 받은 ID로 처리하며, 뒤이어 연 문서를 초기화하지 않는다. | `DocumentsPage.ts`, `pages/documents.html` |
| DB조회 프로필 | 지연된 응답이 최신 선택이나 편집을 덮어쓰지 않도록 수정했다. 프로필을 불러오거나 저장했을 때 해당 작업의 상태를 표시하고, 직접 편집하면 이전 조회 결과를 해제한다. | `DatabaseQueryPage.ts`, `pages/database.html` |
| SQLite 문서 목록 | `GET /api/documents`는 ID·제목·수정일시만 반환한다. 본문은 선택 시 `GET /api/documents/{id}`로 읽는다. DB조회와 코드 생성에서 쓰는 서버 내부 문서 검색은 기존 조회를 사용한다. | `DocumentController.java`, `DocumentRepository.java`, `DocumentsPage.ts` |
| 저장·출력 메뉴 | 저장, PDF, Mermaid SVG·HTML에 툴팁과 접근성 이름이 있는 아이콘 버튼을 적용했다. PDF는 현재 편집 본문을 A4 파일로 내려받으며 YAML 프론트매터를 제외한다. | `pages/documents.html`, `MermaidDiagram.vue`, `markdownPdf.ts`, `DocumentPdf.java`, `document-pdf.css` |

PDF 생성은 UI 테스트용 Chromium을 재사용한다. 폐쇄망에서는 이미 설치된 Chrome 또는 Edge 실행 파일을 `DASHBOARD_PDF_CHROMIUM_PATH`로 지정할 수 있다. 기존 설치와 한글 글꼴 설정은 [README](../README.md#md-보기에서-pdf-출력)에 기록했다.

### 검증

- 프런트엔드 테스트 11개와 프로덕션 빌드 통과. 빈 프론트매터와 BOM·CRLF 형식을 검증했다.
- 백엔드 테스트 35개와 JAR 패키징 통과. 문서 목록과 상세 응답 분리, 빈 PDF 요청 검증을 포함했다.
- 패키징한 JAR을 임시 SQLite DB로 실행해 Chromium에서 문서 열기·삭제 지연과 DB조회 프로필 전환을 확인했다.
- 실제로 다운로드한 PDF에서 A4 크기, 한글 파일명과 본문, 표, 코드, Mermaid를 확인했다. 프론트매터는 제외됐고 SQLite 원본은 유지됐다.
