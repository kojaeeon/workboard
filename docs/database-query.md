# DB조회 · MD → SQL → 코드 생성

## 사용 흐름

1. `DB조회`에서 내장 SQLite 또는 DB2 LUW 예시, 저장 문서, 등록 폴더 하위 MD를 선택합니다. MD 업로드도 가능합니다.
2. MD의 `connection`, `query.sql`, `query.parameters`, `codegen`, `workItem`을 수정합니다.
3. `설정 확인`은 형식과 조회 SQL을 검사하고, `접속 확인`은 실제 DB에 접속합니다.
4. `SQL 실행`으로 조회한 결과를 그리드에서 확인합니다. 정렬·필터·CSV·행 상세를 지원합니다.
5. `code_gen으로 보내기`를 누르면 해당 조회 결과가 code_gen에 전달됩니다. 생성 정보 확인 후 미리보기·ZIP 다운로드를 실행합니다.

코드 생성 시 DB를 재조회하지 않습니다. 최대 20개의 조회 결과를 30분간 메모리에 보관하며 재시작하면 사라집니다. MD나 SQL을 변경하면 이전 결과와 그 결과의 생성 미리보기를 무효화합니다.

## MD 저장

`MD 저장`은 등록 폴더에서 연 파일의 원본을 저장합니다. UTF-8 BOM·CRLF/LF를 보존하고 외부 변경 시 저장을 거부합니다. 기존 SQLite 문서는 수정하며, 내장 예시와 업로드 파일은 새 SQLite 문서로 저장합니다. `MD 다운로드`로 편집한 내용을 파일로 받을 수도 있습니다. 저장 문서는 기존 MD 보기에서도 확인할 수 있습니다.

## 접속정보

[SQLite 예시](../examples/db-query-sqlite.md)는 `connection.path`의 기존 파일을 읽기 전용으로 엽니다. 상대 경로는 대시보드 실행 폴더 기준이며 DB 파일이 없으면 생성하지 않습니다.

[DB2 LUW 예시](../examples/db-query-db2.md)는 IBM JCC 드라이버의 Type 4 URL을 사용합니다.

```yaml
connection:
  type: db2
  jdbc_url: jdbc:db2://host:50000/DATABASE
  username_env: DB2_USER
  password_env: DB2_PASSWORD
  properties:
    sslConnection: "true"
    # sslTrustStoreLocation: C:/certs/company.jks
  # properties_env:
  #   sslTrustStorePassword: DB2_TRUSTSTORE_PASSWORD
```

계정 이름은 `username`으로 직접 지정하거나 `username_env`로 참조합니다. 비밀번호는 `password_env`로만 지정합니다. 일반 속성의 비밀번호나 JDBC URL의 비밀번호는 거부합니다. 비밀 속성은 `properties_env`로 환경변수를 참조합니다. 값은 OS 환경변수 또는 프로젝트 루트 `.env`에서 읽습니다. 비밀번호와 연결 설정은 조회 결과·생성 ZIP에 포함하지 않습니다.

DB2 예제 SQL은 LUW `SYSCAT.COLUMNS`, `SYSCAT.KEYCOLUSE`, `SYSCAT.TABCONST`를 사용합니다. 실제 스키마·테이블·호스트·SSL 설정과 조회 권한이 있는 계정으로 수정해야 합니다. 사내 DB2 실접속은 해당 환경에서 확인해야 합니다.

## SQL과 매개변수

하나의 `SELECT` 또는 `WITH ... SELECT`를 지원합니다. 데이터 변경·DDL·프로시저 호출·다중 문장은 거부합니다. 문자열·주석과 식별자를 구분해 검사하며, SQLite는 파일 읽기 전용과 `query_only`도 적용합니다. DB2의 readOnly는 JDBC 힌트이므로 DB 계정 자체에도 조회 권한을 사용하세요.

```yaml
query:
  sql: |
    SELECT ID, NAME FROM APP.CUSTOMER WHERE ID = :id
  parameters:
    id: 100
  max_rows: 1000
  timeout_seconds: 30
```

매개변수는 PreparedStatement에 바인딩합니다. 문자열·숫자·불리언·null을 지원하며 테이블명 등 SQL 식별자 자리에 바인딩하지 않습니다. 행 수는 최대 5000, 시간은 최대 120초입니다. 연결 로그인 제한은 별도로 적용합니다. 대용량 셀 또는 전체 결과가 잘리면 표시하고 코드 생성을 차단합니다. SQL 오류는 SQLState·오류 코드로 표시하며 접속 비밀값을 응답하지 않습니다.

## 코드 생성 결과 매핑

`codegen.mode: metadata`는 한 행을 한 컬럼 정의로 해석합니다. 쿼리 별칭이 다르면 매핑을 지정합니다.

| 생성 정보 | 기본 조회 별칭 | 의미 |
| --- | --- | --- |
| name | COLUMN_NAME 또는 name | 실제 컬럼명, 필수 |
| type | DATA_TYPE 또는 type | SQL 타입명, 필수 |
| nullable | NULLABLE | 1/true/Y/YES이면 nullable |
| primary_key | PRIMARY_KEY 또는 is_pk | PK 여부 |
| pk_position | PK_POSITION 또는 pk | 0은 비 PK, 양수는 복합 PK 순서 |
| generated | IS_GENERATED 또는 generated | DB 생성·계산 컬럼. INSERT·UPDATE에서 제외 |
| comment | COMMENT 또는 REMARKS | Java 필드 설명 |

```yaml
codegen:
  mode: metadata
  mapping:
    name: COLNAME
    type: TYPENAME
    pk_position: KEY_ORDER
  type_overrides:
    AMOUNT: java.math.BigDecimal
  property_overrides:
    CLASS: className
```

`result_columns` 모드는 일반 SELECT의 JDBC 컬럼 구조로 VO 필드를 만듭니다. 이때 PK는 추측하지 않습니다. `primary_keys: [ID]`, `generated_columns: [ID]`로 명시할 수 있습니다. Mapper도 생성하려면 SELECT의 별칭과 `workItem.table`의 실제 컬럼명이 일치해야 합니다. 계산식·조인 결과 DTO는 VO만 선택하거나 템플릿을 조정하세요.

`workItem.table`, `service`, `serviceNm`, `package`는 기존 형식을 유지합니다. 표준 영문·숫자·밑줄 식별자를 지원하며 DB2 테이블 식별자는 일반적인 비인용 이름처럼 대문자로 정규화합니다. 공백·특수문자가 있는 인용 식별자는 현재 코드 생성 범위에 포함하지 않습니다.

## 생성 파일과 범위

전체 선택 시 Controller, Service, VO, Mapper Java 인터페이스, Mapper XML 5개를 생성합니다. ResultMap과 SQL은 실제 조회 컬럼으로 구성하고 MyBatis `#{...}` 바인딩을 사용합니다. 복합 PK를 지원하며 PK가 없으면 단건 조회·수정·삭제를 만들지 않습니다. DB 생성 컬럼을 INSERT·UPDATE에서 제외합니다.

수집한 PK·생성 컬럼 정보만 사용하고 자동 증가나 기본값을 추측하지 않습니다. 필수값·기본값 처리, 검증, 권한, 트랜잭션, 페이징과 대상 프로젝트의 MyBatis 스캔·mapper 위치·DataSource 설정은 프로젝트 요구에 맞게 적용하세요. 미선택 의존 파일이 필요하거나 모르는 SQL 타입을 String으로 매핑한 경우 화면에 표시합니다. 생성 파일 자체를 대시보드 DB에 적용하거나 실행하지 않습니다.

## 확장 구조

- `database/DatabaseConnector`: 접속 규칙. 새 DB의 구현을 Spring bean으로 추가합니다.
- `database/DatabaseDialect`: 식별자 인용·테이블명 정규화·정수 타입 차이. 새 DB의 구현을 추가합니다.
- `database/QueryProfileParser`: MD 계약과 환경변수 참조 형식.
- `database/DatabaseQueryService`: 바인딩·제한·조회 결과 수집. 접속 비밀값을 결과에서 분리합니다.
- `codegen/QueryMetadata`: 조회 결과를 DB 공통 컬럼 모델로 변환합니다.
- `codegen/QueryCodeGenerator`: 모델과 FreeMarker 템플릿을 연결합니다.
- `resources/codegen/database`: 실제 출력 템플릿. 추가 산출물은 이 계층에서 확장합니다.

SQLite 실제 조회 → 생성 Java 컴파일 → 생성 Mapper의 실제 CRUD를 임시 DB에서 검증합니다. DB2는 LUW 메타데이터 형태·복합 PK·타입 매핑·드라이버 포함을 검증하며, 사용자 DB2 접속 정보는 포함하지 않습니다.
