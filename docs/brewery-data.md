# 양조장 공공데이터 적재

양조장 매칭이 웹 검색보다 먼저 DB를 조회하도록 공개 양조장 목록을 `brewery` 테이블에 적재한다.
변환 스크립트는 저장소에 넣지 않는다(일회성). 어떤 출처든 아래 CSV 형식으로 바꿔서 적재한다.

## CSV 형식

헤더는 고정이고 열 순서는 무관하다. 알 수 없는 헤더(오타 포함)가 있거나 `name` 헤더가 없으면 아무것도 적재하지 않고 실패한다.

| 열 | 필수 | 설명 |
|---|---|---|
| `name` | O | 양조장 이름. 비어 있으면 그 행은 건너뛰고 사유를 기록 |
| `address` | | 주소 |
| `phone` | | 전화번호 |
| `homepage` | | 홈페이지 URL |
| `instagram` | | 인스타그램 핸들. `@` 없이 (있어도 떼고 저장) |
| `aliases` | | 별칭 목록, `\|`로 구분. 정규화해 저장하고 이미 있는 별칭은 건너뜀 |
| `source_url` | | 출처 URL |

- 정규화한 이름이 같은 양조장이 있으면 갱신(`updated`), 없으면 추가(`inserted`). 같은 정규화 이름이 여럿이면 id가 가장 작은 하나만 갱신한다.
- 적재된 양조장은 `source=PUBLIC_DATA`, `verified=true`가 된다 (기존 `WEB_SEARCH`도 덮어씀). CSV에서 빈 값인 열은 기존 값을 유지한다.
- 전체가 한 트랜잭션이라 예외가 나면 모두 롤백된다.
- 인코딩 기본 UTF-8. 공공데이터 CSV가 CP949면 `--app.brewery-import.charset=CP949`.

## 빈 칸 보강

분석 중 DB에서 찾은 양조장에 주소·전화번호·홈페이지·인스타그램 중 빈 칸이 있으면 Claude 웹 검색으로 한 번 보강한다.

- 양조장마다 한 번만: 조사가 끝나면(찾았든 못 찾았든) `researched_at`을 남기고, 값이 있으면 다시 조사하지 않는다. 웹 검색으로 새로 만든 양조장은 만들 때 남긴다. CSV 재적재는 이 값을 건드리지 않는다.
- 빈 칸만 채우고 값이 있는 칸은 덮어쓰지 않는다. `source`·`verified`·`source_url`도 그대로 둔다.
- 조사한 이름이 그 양조장(정규화 이름 또는 이름·별칭 조회 결과)과 같을 때만 받고, 새 양조장 저장과 같은 검증(홈페이지 http(s), 인스타그램 핸들 형식, 컬럼 길이)을 거친다.
- 보강 조사에서 Claude 오류가 나면 게시물을 실패시키지 않는다. 경고 로그만 남기고 DB 값 그대로 진행하며, `researched_at`은 비워 두어 다음 게시물 때 다시 시도한다.

## 출처 후보 — 출처 미정 — 사용자 결정 대기

전국 단위 "전통주 양조장" 파일데이터는 공공데이터포털에서 확인되지 않았다. 후보:

1. 지자체 식품제조가공업 파일데이터에서 주류 유형만 걸러 변환
2. 더술닷컴(한국농수산식품유통공사) 양조장 목록
3. 농식품부 "찾아가는 양조장" 목록

## 실행

로컬 (H2, `apps/api`에서):

```bash
./gradlew bootRun --args='--app.brewery-import.file=/경로/breweries.csv --app.brewery-import.charset=UTF-8 --spring.main.web-application-type=none'
```

`app.brewery-import.file`이 있을 때만 러너가 돌고, 로그에 `ImportReport`를 남기고 종료한다. 환경변수는 추가하지 않았다(명령행 인자 전용).

운영 (이미지 ENTRYPOINT가 `java -jar app.jar`라 인자가 그대로 붙는다. CSV는 컨테이너에서 보이는 경로에 둔다):

```bash
docker compose -f deploy/compose.yml --env-file deploy/.env run --rm \
  -v /volume1/docker/k-drinker/import:/import:ro api \
  --app.brewery-import.file=/import/breweries.csv \
  --app.brewery-import.charset=UTF-8 \
  --spring.main.web-application-type=none
```

운영 적재는 사용자가 요청할 때.

## 적재 결과

아직 적재하지 않음.
