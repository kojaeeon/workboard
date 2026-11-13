# 포팅 기준과 검증

## 범위

원본 커밋 `804140b83b6a919803fdcd376f70eec4d713899b`의 문서·Work·일정·ADO WIQL·UI_TEST·code_gen·마인드맵이 기준입니다. 이후 DB조회와 실제 조회 결과 기반 코드 생성을 추가했습니다. 배포/백업은 제외했습니다. 원본 Python은 실행 의존성에 포함하지 않습니다.

## 원본 자산

- `frontend/src/MindMapEditor.js`: 원본 `app/common/mindmap.js`. 리소스 로더 경로와 Mind Elixir 모듈 로딩을 Vite에 맞게 변경했습니다. PNG 내보내기는 `features/mindmap/exportPng.ts`로 분리해 노드·연결선의 실제 좌표를 보존하며 화면 밖 노드가 잘리지 않도록 처리합니다.
- `frontend/src/vendor/MindElixir.js`, `frontend/public/vendor/mindmap`: 원본 Mind Elixir 자산 및 라이선스.
- `frontend/src/mindmap.css`: 원본 마인드맵 스타일.
- `frontend/src/Calendar.js`, `frontend/public/vendor/calendar`: 원본 캘린더 및 FullCalendar/Luxon 자산. 리소스 로더 import 경로를 변경했습니다.
- `frontend/src/features/documents/mermaidFullscreen.js`: 원본 전체화면 함수에 SVG를 인자로 전달하도록 수정했습니다.
- `frontend/src/features/documents/searchOverview.js`: 원본 검색 위치 표시를 공개 CodeMirror API로 연결했습니다.
- `frontend/tests/fixtures/original-formats.json`: 원본 Python으로 생성한 DOT·Mermaid·JSON 변환 기준 데이터. 따옴표·줄바꿈·스타일·요약을 포함합니다.

원본 자산의 저작권 및 각 라이브러리의 라이선스는 배포 자산에 유지했습니다. 원본 저장소에는 전체 프로젝트의 라이선스 파일이 없으므로 별도의 재배포 라이선스를 임의로 추가하지 않았습니다.

## 동작 보존

- 기존 SQLite 테이블과 저장 JSON을 유지합니다.
- 마인드맵 저장·삭제는 revision을 조건으로 사용해 다른 화면의 변경을 덮어쓰지 않습니다.
- 그래프는 Java에서 ID·색상·스타일·깊이·계층·요약 범위와 최대 크기를 검증합니다.
- 문서 원본 저장은 UTF-8 BOM·줄바꿈 보존, 변경 감지, 같은 폴더의 임시 파일과 교체 방식으로 구현했습니다.
- 일정은 서울 시간과 종일 종료일의 비포함 경계를 유지합니다.
- ADO 컬럼과 행 순서, 사용자 표시명, HTML 일반 텍스트, Flat WIQL, 200건 표시 제한, CSV 수식 이스케이프를 유지합니다.

## 검증 방법

서버 통합 테스트는 격리된 SQLite와 ADO HTTP 스텁을 사용합니다. 변환 테스트는 원본 파일의 가져오기와 다시 내보내기 결과를 비교합니다. 실제 브라우저에서 원본과 같은 1440×1000 화면을 비교하고, 편집·저장·재열기·검색·다운로드를 확인합니다. 작업 중 생성한 스크린샷과 PNG는 `output/playwright`에 보관하며 소스 관리 대상에서 제외합니다.

사내 ADO의 인증서·인증 방식·커스텀 필드는 로컬 스텁만으로 동일함을 보장할 수 없어 실제 환경 확인이 필요합니다. Markdown 렌더러는 Python markdown2에서 markdown-it으로, DOT 파서는 pydot에서 graphlib-dot으로 변경되었습니다. 지원 범위는 원본을 기준으로 구현했지만 라이브러리의 모든 비표준 문법까지 동일하다고 보장하지 않습니다. HTML은 DOMPurify로 정제합니다.

## 최종 검증 결과 · 2026-11-13

- 전체 빌드 및 실행 JAR 생성 성공. 개발 서버 없이 JAR의 네 화면과 로컬 자산 로딩 확인.
- Java 통합 테스트 10개, 프런트엔드 테스트 10개 통과.
- 원본 Python이 생성한 SQLite DB를 Java에서 열어 문서·작업·일정·마인드맵의 기존 데이터 확인.
- 브라우저에서 문서 편집·저장·목차·Mermaid 전체화면, 작업 저장, 일정 등록, 마인드맵 편집·저장·재열기·검색·PNG 다운로드 확인.
- 모의 WIQL 응답으로 동적 그리드·상세 보기·CSV 다운로드 및 수식 이스케이프 확인.
- 1440×1000 최종 브라우저 검증에서 콘솔 오류·경고 0건.

### UI_TEST·code_gen 추가

- Java Playwright를 별도 JVM에서 실행하며 서버의 이벤트 처리와 브라우저 생명주기를 분리했습니다. 실행 중지 시 협력 취소 후 응답하지 않는 자식 프로세스를 정리합니다.
- 원본 시나리오의 action·locator 우선순위·다중 대상 오류·iframe·팝업·검증·캡처를 이식했습니다. YAML은 SafeConstructor로 읽고 임의 코드 실행 action은 제공하지 않습니다.
- 브라우저 MD는 명시한 필드만 시나리오 설정에 병합합니다. 실행 중 런타임 브라우저 다운로드를 하지 않도록 구성했습니다.
- 결과 JSON은 완성된 임시 파일로 교체하며, Windows의 짧은 파일 읽기 잠금에 재시도합니다.
- 원본 Jinja2 샘플 출력을 FreeMarker 템플릿으로 유지했습니다. 샘플 모드임을 화면에 표시하며, 실제 생성은 DB조회에서 받은 결과를 사용합니다.
- 별도 JVM의 실제 Chromium으로 입력·클릭·iframe·팝업·스크린샷·trace, 첫 실패 후 건너뛰기, 모호한 대상 거부, 중지를 검증했습니다. 화면의 기본 9단계 시나리오도 통과했습니다.
- code_gen 화면의 4개 파일 미리보기·ZIP 다운로드·입력 변경 후 무효화를 검증했습니다.
- 자동 테스트는 Java 21개·프런트엔드 10개, 총 31개가 통과했습니다. UI에서 MD 자동 입력·생성 대상 유지와 실행 중지도 확인했습니다.
- 배포 JAR에서도 별도 JVM으로 Chromium을 실행해 입력·iframe·팝업·결과 파일, 실패 후 건너뛰기, 실행 중지를 다시 확인했습니다. 최종 두 메뉴의 브라우저 콘솔 오류·경고는 0건입니다.

### DB조회·실제 코드 생성 추가 · 2026-11-14

- SQLite·DB2 LUW 접속을 Connector로, SQL 식별자·타입 차이를 Dialect로 분리했습니다. 접속정보와 SQL은 프론트매터 MD로 관리합니다.
- 읽기 전용 SELECT, 매개변수 바인딩, 조회 시간·결과 크기 제한과 코드 생성용 결과 스냅샷을 적용했습니다.
- 컬럼 메타데이터 또는 SELECT 결과 구조로 Controller·Service·VO·Mapper Java/XML을 생성합니다. 기존 샘플 모드도 유지합니다.
- 임시 SQLite의 실제 조회·조회 취소·MD 저장 충돌·BOM 보존, 생성 Java 컴파일과 생성 Mapper CRUD를 검증합니다. DB2는 LUW 메타데이터 형태와 복합 PK·타입 매핑을 검증하며 실접속은 사용자 환경에서 확인해야 합니다.
- 사용 방법과 확장 지점은 [DB조회 안내](database-query.md)를 참조하세요.
- 전체 빌드 성공, Java 33개·프런트엔드 10개로 총 43개 자동 테스트 통과. 배포 JAR에서 MD 업로드·저장, SQL 조회, code_gen 전달·5개 파일 생성, 입력 변경 시 무효화와 변경 SQL 거부를 확인했습니다. 정상 조회·생성 흐름의 브라우저 콘솔 오류·경고는 0건입니다.
