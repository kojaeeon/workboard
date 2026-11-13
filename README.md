# KJA_BOARD · Spring Boot 포팅

Python/NiceGUI 대시보드의 **문서, Work, 일정, ADO WIQL, UI_TEST, code_gen, 마인드맵**을 Java/Spring Boot + Vue/TypeScript + SQLite/JdbcClient로 포팅했습니다. ADO는 Work 내부에 있어 상단 탭은 6개입니다. 배포/백업은 제외했습니다.

원본 기준: [kojaeeon/dashboard, 804140b83b6a919803fdcd376f70eec4d713899b](https://github.com/kojaeeon/dashboard/tree/804140b83b6a919803fdcd376f70eec4d713899b).

## 실행

JDK 21 이상, Node.js 22.12 이상이 필요합니다. Maven은 동봉한 Wrapper가 준비합니다.

```powershell
# 프로젝트 루트에서 실행
.\build.ps1
.\start.ps1
```

브라우저에서 [http://127.0.0.1:8080](http://127.0.0.1:8080)을 엽니다. 프런트엔드는 JAR 안에 포함되므로 실행 시 Node.js나 Python은 필요하지 않습니다. 소스를 수정한 뒤에는 build.ps1을 다시 실행하세요.

환경 설정이 필요하면 `.env.example`을 `.env`로 복사합니다. 환경변수가 `.env`보다 우선합니다. `.env`는 프로젝트 루트에서 읽으며, 경로는 `C:/documents/dashboard.db`처럼 슬래시로 작성하세요. 값 주위에 따옴표를 붙이지 않습니다.

### 개발

서로 다른 터미널에서 실행합니다.

```powershell
.\backend\mvnw.cmd -f backend/pom.xml spring-boot:run
npm.cmd --prefix frontend run dev
```

개발 화면은 [http://127.0.0.1:5173](http://127.0.0.1:5173)이며 `/api`를 서버의 8080 포트로 전달합니다.
Windows에서는 실행 중인 대시보드와 개발 서버를 종료한 뒤 전체 빌드를 실행하세요. 사용 중인 JAR 또는 Vite의 esbuild 파일은 교체할 수 없습니다. 전체 빌드는 이전 서버 빌드 산출물을 정리하므로 오래된 프런트엔드 자산이 JAR에 누적되지 않습니다.

## 기능

| 기능 | 구현 내용 |
| --- | --- |
| 문서 | SQLite 저장·삭제, 등록 폴더의 MD 열기·원본 저장, UTF-8 업로드(2MB), CodeMirror 검색·검색 위치 표시, 목차·처음/끝 이동, Markdown·코드·Mermaid 미리보기 |
| Work | 할 일·진행 중·완료 보드, 작업 추가·수정·삭제·메모 |
| 일정 | 월·주·일 보기, 등록·수정·삭제, 드래그·크기 변경, 분류별 색상, 기간 목록, 서울 시간대, 종일 종료일의 비포함 경계 |
| ADO | Flat WIQL, 응답 메타데이터 기반 동적 컬럼, 200건 제한, ID 순서 유지, 정렬·필터·검색·선택·CSV·상세 보기 |
| UI_TEST | MD 프론트매터 검증, 브라우저 MD 설정·부분 병합, 별도 JVM 실행·중지, 단계별 결과, 실패 시 후속 단계 건너뛰기, PNG·trace·JSON·로그 다운로드 |
| code_gen | 저장 문서·등록 폴더 하위 MD의 workItem 자동 입력, 샘플 Controller·Service·VO·Mapper XML 미리보기, 파일 선택·복사, 미리보기와 동일한 UTF-8 ZIP 다운로드 |
| 마인드맵 | 원본 Mind Elixir 편집기, 노드·관계·요약·유형·스타일, 검색·강조, 복사/붙여넣기, 실행 취소/다시 실행, 방향·확대·축소·전체화면, SQLite 저장·충돌 검사, DOT·Mermaid·JSON·PNG 입출력 |

화면 외형을 맞추기 위해 NiceGUI와 동일한 Quasar를 사용합니다. 원본 마인드맵 편집기와 CSS, 캘린더 배포 자산, Mermaid 전체화면 동작을 재사용했습니다. 코드 변경 지점과 검증 범위는 [포팅 기록](docs/porting.md)에 있습니다.

## 기존 데이터

원본과 같은 테이블·컬럼·마인드맵 JSON 형식을 사용합니다. 원본 앱을 종료하고 DB 파일을 복사한 뒤 `DASHBOARD_DB_PATH`로 복사본 경로를 지정하세요. 폴더 등록 경로는 원본 PC의 경로이므로 다른 PC에서는 다시 등록해야 합니다.

로컬 문서는 **서버가 실행되는 PC**의 파일입니다. 원본 파일 저장은 UTF-8 BOM과 CRLF/LF를 보존하며, 열었을 때의 SHA-256과 달라졌으면 저장을 거부합니다. 업로드한 문서는 SQLite로 저장합니다. 앱 종료 후 DB 파일을 복사해 백업하세요.

## ADO 설정

`ADO_BASE_URL`, `ADO_PROJECT`, `ADO_PAT`가 필요합니다. WIQL/상세 URL은 원본처럼 `ADO_BASE_URL + /_apis/wit/...`이며 프로젝트 경로를 자동으로 붙이지 않습니다. API 버전, 30초 연결·읽기 제한, PAT Basic 인증, 리다이렉트 금지를 유지합니다. PAT는 브라우저나 SQLite에 저장하지 않습니다.

원본 사내 서버 호환을 위해 `ADO_VERIFY_SSL=false`가 기본입니다. 사내 인증기관을 JVM 신뢰 저장소에 등록한 환경에서는 `true`를 사용하세요. 실제 사내 ADO 연결은 해당 환경에서 별도 확인해야 합니다.

## UI 테스트

처음 사용할 PC에서는 사용할 브라우저를 한 번 준비합니다. 현재 작업 PC에는 Chromium을 설치했습니다.

```powershell
.\install-browsers.ps1
# 선택: 다른 엔진을 사용할 때
.\install-browsers.ps1 -Browser firefox
```

`UI_TEST`에서 기본 예시 또는 등록 폴더 바로 아래의 MD를 선택하거나 파일을 불러옵니다. `단계 확인` → 시작 URL·브라우저 표시 여부 확인 → `테스트 실행` 순서입니다. `중지`는 해당 실행의 브라우저와 자식 프로세스를 정리합니다. 입력 수정은 실행 복사본에만 반영하며 원본 MD를 덮어쓰지 않습니다.

시나리오 형식과 지원 동작은 [기본 예시](examples/ui-test-smoke.md), 별도 브라우저 설정은 [브라우저 MD 예시](examples/browser.md)를 참고하세요. 브라우저 MD에 명시한 필드만 덮어쓰며 viewport도 필드별로 병합합니다. 화면의 URL·표시 스위치가 최종 실행값입니다. 잘못된 브라우저 MD는 실행을 차단합니다. 기존 Edge 등은 `executable_path`로 지정할 수 있습니다.

결과는 `data/ui_tests/<실행 ID>/`에 보관합니다. 성공은 `final.png`, 실패는 `failure.png`, 명시한 screenshot 단계는 번호별 PNG를 남기며, `result.json`, `trace.zip`, `browser.json`, `worker.log`를 화면에서 다운로드할 수 있습니다. 실행 시 브라우저를 자동 다운로드하지 않습니다. 폐쇄망에서는 Java Playwright 버전에 맞는 브라우저 캐시도 함께 복사하세요. 강제 중지가 필요한 경우에는 trace 파일이 완성되지 않을 수 있습니다.

## 코드 생성

원본과 같은 **샘플 미리보기 기능**이며 DB 메타데이터 조회·실제 SQL 생성은 포함하지 않습니다. `workItem.table`, `service`, `serviceNm`, `package`를 MD에서 읽거나 직접 입력합니다. 원본 Jinja2 템플릿의 출력 내용을 Java FreeMarker로 유지했습니다. 입력이 바뀌면 이전 다운로드를 비활성화하고, ZIP은 서버가 보관한 미리보기의 파일 내용 그대로 생성합니다.

템플릿은 `backend/src/main/resources/codegen`에서 수정합니다. 실제 생성기를 연결할 때는 `CodeGenerator` 구현을 교체하세요. 미리보기는 메모리에 최대 100개를 보관하며 앱을 재시작하면 다시 생성해야 합니다.

## 검증

```powershell
npm.cmd --prefix frontend test
.\backend\mvnw.cmd -f backend/pom.xml test
```

프런트엔드는 원본이 생성한 변환 파일을 기준으로 DOT·flowchart·mindmap·JSON 왕복, 특수문자·줄바꿈·요약·관계 보존을 검증합니다. 서버는 별도 임시 DB와 HTTP ADO 스텁을 사용해 실제 서버·사용자 파일에 영향을 주지 않습니다.

## 코드 구조

`backend/src/main/java/com/kojaeeon/dashboard`와 `frontend/src/features`를 같은 기능 단위로 나눴습니다. Controller는 HTTP, Service는 규칙과 외부 실행, Repository는 SQL을 담당합니다. 단순 Work CRUD는 Repository와 요청 검증으로 구성했습니다. SQL 스키마는 `backend/src/main/resources/db/migration`에 있습니다. 현재 V001은 기존 DB에 없는 테이블·인덱스를 생성하는 멱등 초기화이며, 자동 버전 관리 프레임워크는 도입하지 않았습니다.

JS/CSS/폰트는 로컬 자산으로 빌드되며 CDN을 사용하지 않습니다. 폐쇄망 빌드는 Maven·npm 의존성 캐시를 미리 준비해야 하고, 빌드된 JAR의 실행에는 패키지 다운로드가 필요 없습니다.
