# k6 부하 테스트 스크립트

범용 API 부하 테스트를 수행하는 k6 스크립트 모음입니다.

## 환경 변수 설정

테스트 실행 전 다음 환경 변수를 설정해야 합니다:

### 필수 환경 변수

- `TARGET_BASE_URL`: 테스트 대상 서버 URL (예: `http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080`)
- `API_TOKEN`: JWT 인증 토큰 (Bearer 토큰)
- `TEST_API_ENDPOINTS`: 테스트할 API 엔드포인트 목록
  - 형식: `"GET:/api/path1,POST:/api/path2,GET:/api/path3?param=value"`
  - 예시: `"GET:/api/chat/room/list?userId={userId}&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1,POST:/api/chat/room?userId={userId}&estimateId=1"`

### 선택 환경 변수

- `TEST_USER_IDS`: 테스트에 사용할 사용자 ID 목록 (쉼표로 구분, 예: `1,2,3,4,5`) - 기본값: 1~10
- `READ_WRITE_RATIO`: 읽기/쓰기 비율 (0.0~1.0, 기본값: 0.9 = 90% 읽기, 10% 쓰기)

## TEST_API_ENDPOINTS 형식

```
METHOD:/api/path?param1=value1&param2={dynamic}
```

- `METHOD`: HTTP 메서드 (GET, POST, PUT, DELETE, PATCH)
- `/api/path`: API 경로
- `param=value`: 쿼리 파라미터
- `{userId}`, `{page}`: 동적 파라미터 (자동으로 랜덤 값으로 치환됨)
  - `{userId}`: TEST_USER_IDS에서 랜덤 선택
  - `{page}`: 1~5 사이 랜덤 값

### 예시

```bash
# 채팅 API 테스트
TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1,GET:/api/chat/room/message?userId={userId}&chatRoomId=1,POST:/api/chat/room?userId={userId}&estimateId=1"

# 검색 API 테스트
TEST_API_ENDPOINTS="GET:/api/home/search?userId={userId}&name=곡,GET:/api/home/community?category=POPULAR"

# 커뮤니티 API 테스트
TEST_API_ENDPOINTS="GET:/api/posts/popular/list?page={page},GET:/api/posts/all/list?page={page},POST:/api/posts"
```

## 테스트 시나리오

### A. 스모크 테스트 (`smoke.js`)
- **목적**: 배포 직후 정상 동작 확인
- **부하**: 1~5 RPS (평균 3 RPS)
- **시간**: 3~5분 (4분)
- **성공 기준**: 에러율 < 1%, p95 < 2초

### B. 베이스라인 테스트 (`baseline.js`)
- **목적**: 리팩토링 전/후 성능 비교의 대표 지표 확보
- **워밍업**: 50 RPS 10분
- **측정**: 100 RPS 15분
- **총 시간**: 25분
- **성공 기준**: 에러율 < 1%, p95 < 3초, p99 < 5초

### C. 로드 테스트 (`load.js`)
- **목적**: 안정 구간에서 지연/자원 사용 확인
- **워밍업**: 100 RPS 10분
- **측정**: 100 RPS 20분
- **총 시간**: 30분
- **성공 기준**: 에러율 < 1%, p95 < 3초, p99 < 5초

### D. 스트레스 테스트 (`stress.js`)
- **목적**: 에러율 증가 또는 p95 급등이 시작되는 임계점 확인
- **부하**: 100 → 150 → 200 → 250 → 300 RPS (각 5분)
- **총 시간**: 25분
- **성공 기준**: 임계 RPS와 원인을 식별하여 기록

### E. 내구 테스트 (`endurance.js`)
- **목적**: 장시간 테스트로 메모리 누수, 커넥션 누수 확인
- **부하**: 50~100 RPS 고정 (75 RPS)
- **시간**: 2시간 (필요시 조정)
- **성공 기준**: 시간이 지날수록 에러율이 증가하지 않고 p95가 서서히 악화되지 않음

### F. 용량 테스트 (`capacity-test.js`, `capacity-test-simple.js`, `capacity-test-api.js`)
- **목적**: 시스템이 몇 명의 동시 사용자까지 버틸 수 있는지 확인
- **방법**: 점진적으로 사용자 수를 늘려가며 에러율/응답시간 임계값을 넘는 지점 탐색
- **시간**: 약 30~40분 (단계 수에 따라 다름)
- **성공 기준**: 에러율 < 1%, p95 < 2초를 유지하는 최대 사용자 수 확인
- **스크립트 종류**:
  - `capacity-test.js`: API 공통 모듈 사용, TEST_API_ENDPOINTS 환경 변수 필요
  - `capacity-test-api.js`: capacity-test.js와 동일 (별칭)
  - `capacity-test-simple.js`: 기존 community-post-test.js 스타일, 단일 엔드포인트 테스트

## 실행 방법

### 0. 통합 테스트 (모든 시나리오 한 번에 실행)

#### 전체 테스트 (약 1시간 30분)
```bash
# 모든 테스트를 순차적으로 실행 (스모크 → 베이스라인 → 로드 → 스트레스)
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/all-tests.js
```

#### 빠른 통합 테스트 (약 10분, 실험용)
```bash
# 모든 테스트를 짧은 시간으로 축소하여 빠르게 검증
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/quick-tests.js
```

**통합 테스트 사용 시 주의사항:**
- ✅ **실험적 검증에 적합**: 전체 시스템이 정상 동작하는지 빠르게 확인
- ✅ **개발/스테이징 환경에서 유용**: 배포 전 전체 검증
- ⚠️ **정확한 성능 측정에는 부적합**: 각 테스트가 서로 영향을 줄 수 있음
- ⚠️ **운영 환경에서는 권장하지 않음**: 지속적인 부하로 인한 영향
- 💡 **권장 사용**: 빠른 검증 → `quick-tests.js`, 정확한 측정 → 개별 스크립트 실행

### 1. EC2 직결 테스트 (반복 테스트, 전/후 비교)

```bash
# 스모크 테스트
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/smoke.js

# 베이스라인 테스트
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/baseline.js

# 로드 테스트
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/load.js

# 스트레스 테스트
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/stress.js

# 내구 테스트
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/endurance.js
```

### 2. ALB 경유 테스트 (최종 검증, 운영 유사)

```bash
# ALB 엔드포인트를 TARGET_BASE_URL로 설정
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=https://your-alb-endpoint.ap-northeast-2.elb.amazonaws.com \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},GET:/api/chat/room?userId={userId}&chatRoomId=1" \
  /scripts/baseline.js
```

### 3. 사용자 ID 및 읽기/쓰기 비율 커스터마이징

```bash
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page},POST:/api/chat/room?userId={userId}&estimateId=1" \
  -e TEST_USER_IDS=1,2,3,4,5,6,7,8,9,10 \
  -e READ_WRITE_RATIO=0.8 \
  /scripts/baseline.js
```

### 4. 용량 테스트 실행 (최대 사용자 수 확인)

#### API 공통 모듈 사용 (여러 엔드포인트 테스트)
```bash
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_API_ENDPOINTS="GET:/api/home/community?category=POPULAR,GET:/api/home/search?userId={userId}&name=곡" \
  /scripts/capacity-test.js
```

#### 단일 엔드포인트 테스트 (기존 스타일)
```bash
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx-xxx-xxx.ap-northeast-2.compute.amazonaws.com:8080 \
  -e API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9... \
  -e TEST_ENDPOINT=/api/home/community?category=POPULAR \
  /scripts/capacity-test-simple.js
```

**용량 테스트 결과 해석:**
- 각 단계(50명, 100명, 200명 등)에서 에러율과 응답시간 확인
- 에러율이 1%를 넘거나 p95가 2초를 넘는 단계가 최대 용량
- 예: 500명까지는 정상, 1000명에서 에러율 증가 → 최대 용량 약 500명

### 5. 실행 순서 예시

#### 1단계: 반복 테스트 (EC2 직결, 전/후 비교)

리팩토링 전:
```bash
# A → B → C → D 순서로 실행
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx:8080 \
  -e API_TOKEN=<token> \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page}" \
  /scripts/smoke.js

docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx:8080 \
  -e API_TOKEN=<token> \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page}" \
  /scripts/baseline.js

docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx:8080 \
  -e API_TOKEN=<token> \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page}" \
  /scripts/load.js

docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=http://ec2-xxx:8080 \
  -e API_TOKEN=<token> \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page}" \
  /scripts/stress.js
```

리팩토링 후:
- 동일한 명령어를 다시 실행하여 결과 비교

#### 2단계: 최종 검증 (ALB 경유, 운영 유사)

```bash
# A → B → C 순서로 실행 (1~2회)
docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=https://your-alb-endpoint \
  -e API_TOKEN=<token> \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page}" \
  /scripts/smoke.js

docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=https://your-alb-endpoint \
  -e API_TOKEN=<token> \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page}" \
  /scripts/baseline.js

docker-compose -f docker-compose.yml -f docker-compose.local.yml run --rm k6 \
  -e TARGET_BASE_URL=https://your-alb-endpoint \
  -e API_TOKEN=<token> \
  -e TEST_API_ENDPOINTS="GET:/api/chat/room/list?userId={userId}&read=0&page={page}" \
  /scripts/load.js
```

## 읽기/쓰기 비율

기본적으로 **읽기 90%, 쓰기 10%** 비율로 구성되어 있습니다. `READ_WRITE_RATIO` 환경 변수로 조정 가능합니다.

- `READ_WRITE_RATIO=0.9`: 읽기 90%, 쓰기 10% (기본값)
- `READ_WRITE_RATIO=0.8`: 읽기 80%, 쓰기 20%
- `READ_WRITE_RATIO=1.0`: 읽기만 수행

## 지표 확인

테스트 실행 후 다음 지표를 확인하세요:

- **에러율**: `http_req_failed` < 1% (목표)
- **지연**: `http_req_duration` p95, p99
- **처리량**: RPS (Requests Per Second)
- **자원 사용량**: 
  - EC2 CPU/메모리
  - JVM GC 시간/빈도
  - 스레드 수
  - Hikari active/idle 커넥션
  - RDS DBConnections
  - Slow query

## 반복 테스트

리팩토링 전/후 비교를 위해 각 시나리오를 **1~3회 반복 실행**하여 평균 및 편차를 확인하세요.

## 주의사항

1. **인증 토큰**: 모든 API는 JWT 토큰이 필요합니다. 유효한 토큰을 `API_TOKEN` 환경 변수로 전달하세요.
2. **테스트 데이터**: 실제 존재하는 `userId`와 리소스 ID를 사용해야 합니다.
3. **부하 제한**: 스트레스 테스트는 서버에 과도한 부하를 줄 수 있으므로 운영 환경에서는 주의하세요.
4. **네트워크**: k6는 로컬에서 실행되므로 네트워크 지연이 결과에 영향을 줄 수 있습니다.
5. **동적 파라미터**: `{userId}`, `{page}` 같은 동적 파라미터는 자동으로 랜덤 값으로 치환됩니다.
