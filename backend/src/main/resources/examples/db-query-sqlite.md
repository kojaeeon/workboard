---
title: SQLite · Work 테이블 컬럼 조회
connection:
  type: sqlite
  path: data/dashboard.db
query:
  sql: |
    SELECT name AS COLUMN_NAME,
           type AS DATA_TYPE,
           CASE WHEN "notnull" = 1 OR pk > 0 THEN 0 ELSE 1 END AS NULLABLE,
           pk AS PK_POSITION,
           CASE WHEN hidden IN (2, 3) THEN 1 ELSE 0 END AS IS_GENERATED,
           '' AS COMMENT
    FROM pragma_table_xinfo(:table)
    ORDER BY cid
  parameters:
    table: work_items
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
  table: work_items
  service: WorkItem
  serviceNm: 작업
  package: com.example.work
---

# SQLite 조회 → 코드 생성

상대 DB 경로는 대시보드 실행 폴더 기준입니다. 기존 DB를 읽기 전용으로 엽니다.
`query.sql`과 `parameters`를 바꿔 조회하고, 결과를 code_gen으로 보내세요.
쿼리는 명명 매개변수(`:table`)를 사용하며 문자열 이어붙이기를 하지 않습니다.

메타데이터 쿼리는 한 행을 한 컬럼으로 해석합니다. 별칭이 다르면 `codegen.mapping`을 수정하세요.
PK가 없는 테이블은 조회·입력 코드만 생성하며 수정·삭제 SQL을 만들지 않습니다.
자동 증가 키를 INSERT에서 제외하려면 쿼리에서 해당 컬럼의 IS_GENERATED를 1로 반환하세요.
SQLite INTEGER PRIMARY KEY도 직접 값을 지정해 INSERT할 수 있으므로 이 예제는 자동 생성으로 추측하지 않습니다.

일반 데이터 조회 결과의 컬럼 구조로 생성하려면 `codegen.mode: result_columns`를 지정하고
`primary_keys: [id]`, `generated_columns: [id]`처럼 필요한 키 정보를 직접 지정할 수 있습니다.
