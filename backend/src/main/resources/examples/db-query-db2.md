---
title: DB2 LUW · 테이블 컬럼 조회
connection:
  type: db2
  jdbc_url: jdbc:db2://db2-host:50000/DATABASE
  username_env: DB2_USER
  password_env: DB2_PASSWORD
  properties:
    sslConnection: "false"
query:
  sql: |
    SELECT c.COLNAME AS COLUMN_NAME,
           c.TYPENAME AS DATA_TYPE,
           CASE WHEN c.NULLS = 'Y' THEN 1 ELSE 0 END AS NULLABLE,
           COALESCE(k.COLSEQ, 0) AS PK_POSITION,
           CASE WHEN c.GENERATED IN ('A', 'D') THEN 1 ELSE 0 END AS IS_GENERATED,
           COALESCE(c.REMARKS, '') AS COMMENT
    FROM SYSCAT.COLUMNS c
    LEFT JOIN (
      SELECT k.TABSCHEMA, k.TABNAME, k.COLNAME, k.COLSEQ
      FROM SYSCAT.KEYCOLUSE k
      JOIN SYSCAT.TABCONST t
        ON t.TABSCHEMA = k.TABSCHEMA
       AND t.TABNAME = k.TABNAME
       AND t.CONSTNAME = k.CONSTNAME
      WHERE t.TYPE = 'P'
    ) k ON k.TABSCHEMA = c.TABSCHEMA
       AND k.TABNAME = c.TABNAME
       AND k.COLNAME = c.COLNAME
    WHERE c.TABSCHEMA = :schema AND c.TABNAME = :table
    ORDER BY c.COLNO
  parameters:
    schema: APP
    table: CUSTOMER
  max_rows: 1000
  timeout_seconds: 30
codegen:
  mode: metadata
  mapping:
    name: COLUMN_NAME
    type: DATA_TYPE
    nullable: NULLABLE
    pk_position: PK_POSITION
    generated: IS_GENERATED
    comment: COMMENT
workItem:
  table: APP.CUSTOMER
  service: Customer
  serviceNm: 고객
  package: com.example.customer
---

# DB2 LUW 조회 → 코드 생성

호스트·포트·DB명·스키마·테이블을 실제 환경에 맞게 수정하세요.
DB2_USER와 DB2_PASSWORD를 OS 환경변수 또는 프로젝트 루트 `.env`에 설정합니다.
비밀번호 자체는 MD나 JDBC URL에 작성하지 않습니다. 조회 권한이 있는 계정을 사용하세요.
SSL 사용 환경에서는 sslConnection을 "true"로 바꾸고 사내 인증서 설정을 추가하세요.
비밀번호가 필요한 JDBC 속성은 `properties_env`에서 환경변수 이름으로 지정합니다.

IBM JCC 드라이버를 포함합니다. 쿼리는 LUW의 SYSCAT 카탈로그 기준이며 수정할 수 있습니다.
codegen.mapping은 조회 결과의 별칭과 코드 생성용 컬럼 정보를 연결합니다.
자료형을 바꾸려면 `type_overrides: {AMOUNT: java.math.BigDecimal}`를 사용할 수 있습니다.
