# 🌾 UMC 7th FarmON BackEnd
![Image](https://github.com/user-attachments/assets/95c2519f-7e02-4cec-83e2-6064737ba3e9)
**농업의 연결 고리** **FarmON**은 UMC 7기에서 진행된 프로젝트 및 농업의 혁신을 이끄는 디지털 솔루션으로, <br>
**디지털 커뮤니티**를 통해 소규모 영세농업의 **공동농업을 활성화**하고, 플랫폼을 활용하여 **전국의 농업 전문가를 연결**하며, **농업 데이터**를 기반으로 **체계적인 농업 서비스**를 제공합니다.
&nbsp;

---
# 📊 부하 테스트 및 성능 최적화
## 홈 화면 API 단계별 최적화 및 동시 사용자 1,000 VUs 가용성 검증

본 문서는 홈 화면 커뮤니티 게시글 조회 API를 대상으로 k6를 활용해 **동시 사용자 1,000명 규모의 부하 테스트**를 수행하고,
Prometheus와 Grafana를 통해 주요 성능 지표를 모니터링하며 **시스템의 성능 한계**와 **병목 지점**을 분석하여 개선한 과정을 정리했습니다.

## 1. 실험 개요 및 환경

### 1.1 실험 환경 및 시나리오
- **테스트 도구**: k6 (ramping-vus)
- **테스트 시나리오**: 32분간 가상 사용자(VU)를 1 -> 1,000까지 13단계에 걸쳐 점진적 증가
- **대상 API**: 홈 화면 카테고리별 게시물 정보 반환 API (좋아요/댓글 수 포함)
- **모니터링**: Prometheus, Grafana
- **백엔드**: Spring Boot(3.0.0), Java(17)
- **인프라**: AWS (EC2, RDS), Docker

### 1.2 실험 지표 및 목표
- **에러율 (http_req_failed)**: 목표 < 1.0%
- **응답 시간 (http_req_duration)**: 핵심 목표 p(95) < 2.0s / 가이드라인 p(99) < 5.0s
- **처리량 (http_reqs)**: VU 증가에 따른 RPS(Throughput) 선형 증가 여부 확인

---

## 2. [v1] Baseline: 기존 코드 분석 (N+1 발생 구조)

### 🚩 기존 로직 및 문제점
- **데이터 조회 구조**: 게시글 목록 조회(1회) + 각 게시물별 좋아요 COUNT(N회) + 댓글 COUNT(N회)
- **병목 원인**: 총 **1 + 2N 쿼리**가 발생하여, 트래픽 증가 시 DB I/O 부하 및 커넥션 점유 시간 급증

### 2.1 구간별 성능 변화 분석
- **① [안정 구간] VUs 0~500명**: RPS가 선형적으로 상승하며 p(95) 1초 미만 유지.
- **② [지연 발생 구간] VUs 500~800명**: 500명 지점에서 **성능 변곡점(Elbow Point)** 발생. 요청이 Queue에 쌓이며 지연 시간 급증.
- **③ [붕괴 및 임계 구간] VUs 800~1,000명**: p(95) 응답 시간이 **6.7s**로 치솟으며 RPS는 **155 req/s**에서 정체(Saturation).

<img width="1280" height="675" alt="v1_result_1" src="https://github.com/user-attachments/assets/d045e6f8-4323-4782-acf0-0a24ae35b3da" />
<img width="1280" height="989" alt="v1_result_2" src="https://github.com/user-attachments/assets/4ea13c6b-080b-4892-b88d-4475ffbbf03f" />
<img width="1280" height="851" alt="image" src="https://github.com/user-attachments/assets/1f1e09a6-9b11-4819-97f3-fdd3c2a5b437" />


### 2.2 실험 결과 (1,000 VUs)
| 지표 항목 | 측정 결과 | 판정 및 의미 |
| :--- | :--- | :--- |
| **p(95) Latency** | **6.7s** | **Fail**: 목표치(2s) 대비 3배 이상 지연 |
| **p(99) Latency** | **7.94s** | **Fail**: 최악의 상황 응답성 붕괴 |
| **Peak RPS** | **155 req/s** | 인프라 환경의 물리적 처리 한계 노출 |
| **Error Rate** | **0.00% (15건)** | `dial: i/o timeout` 등 소수 하드 에러 발생 |

---

## 3. [v2] 1차 개선: QueryDSL 기반 단일 조회 (N+1 제거)

### ✅ 변경 사항
- **쿼리 통합**: QueryDSL을 이용해 `JOIN` 및 `GROUP BY`를 활용한 **단일 집계 쿼리**로 리팩토링
- **최적화 기법**: `COUNT(DISTINCT ...)`를 적용하여 조인 시 중복 집계 방지
- **DTO Projection**: Entity 대신 조회 전용 DTO(**HomePostRow**)를 사용하여 영속성 컨텍스트 부하 절감

### 3.1 구간별 성능 변화 분석
- **① [성능 개선 확인]**: 이전 테스트 대비 RPS가 **최대 268.29 req/s**까지 상승하며 연산 효율 **88%** 향상 입증.
- **② [병목 잔존]**: 600 VUs 이후 처리량은 늘었으나 DB 커넥션 자원 부족으로 인한 타임아웃 경고 재발생.
- **③ [지연 감소]**: p(95) 응답 시간은 **3.38s**로 v1 대비 약 **50% 개선**되었으나 목표치(2s)에는 미달.

<img width="1280" height="716" alt="v2_result_1" src="https://github.com/user-attachments/assets/23708d85-d470-4123-b3c8-b7e1b36b4a21" />
<img width="1280" height="933" alt="v2_result_2" src="https://github.com/user-attachments/assets/914a33b3-17ee-44ec-b4be-3f14a6f38450" />
<img width="1280" height="336" alt="image" src="https://github.com/user-attachments/assets/c6ed97a4-3618-4b71-a6a4-e46e9ce719a3" />


### 3.2 성능 지표 비교 (v1 vs v2)
| 지표 항목 | v1 (Baseline) | v2 (로직 최적화) | 성과 |
| :--- | :--- | :--- | :--- |
| **p(95) Latency** | 6.7s | **3.38s** | **50% 단축** |
| **Avg Throughput** | 142.4 req/s | **268.29 req/s** | **88% 향상** |
| **Peak RPS** | 155 req/s | **312 req/s** | **101% 향상** |

---

## 4. [v3] 2차 개선: 인프라 설정 최적화 (WAS/DB 튜닝)

### ✅ 변경 사항
- **HikariCP**: `maximum-pool-size: 30`, `connection-timeout: 30000` (커넥션 부족 해소)
- **Tomcat**: `threads.max: 400`, `max-connections: 8192` (동시 요청 수용량 증대)
- **RDS**: DB 파라미터 그룹 수정을 통해 `max_connections: 300` 확보

### 4.1 구간별 성능 변화 분석
- **① [인프라 병목 해소]**: v2 대비 Peak RPS가 **358 req/s**로 약 **130%** 상승하며 설정 튜닝 효과 입증.
- **② [처리 용량 극대화]**: 총 처리 요청 수 **584,006건**으로 확장.
- **③ [물리 임계점 식별]**: 설정을 확장했음에도 p(95)가 **3.30s**에서 정체됨. 현재 구조상 **물리적 Disk I/O 포화**로 판단됨.

<img width="1280" height="717" alt="v3_result_1" src="https://github.com/user-attachments/assets/cc0ab432-26e8-43e1-b355-80ed0748dd11" />
<img width="1280" height="915" alt="image" src="https://github.com/user-attachments/assets/a4ac54aa-83a8-4295-8f7f-ce8b78b7de56" />
<img width="1280" height="322" alt="v3_result_3" src="https://github.com/user-attachments/assets/08322a78-6dff-40aa-8f83-371ab8de1951" />

---

## 5. [v4] 개선: Redis 캐시 도입 (In-memory 아키텍처)

### ✅ 변경 사항
- **캐싱 전략**: 홈 커뮤니티 데이터를 `category:{PostType}` 키 구조로 **Redis**에 저장 (In-memory)
- **유효 정책**: `TTL 60초` 적용 및 좋아요/댓글 변경 시 `afterCommit` 시점에 **선택적 캐시 무효화(Evict)**
- **직렬화**: `GenericJackson2JsonRedisSerializer`를 통한 DTO 직렬화

### 5.1 구간별 성능 변화 분석
- **① [응답 혁신]**: 물리 Disk를 타지 않는 조회로 p(95) 응답 시간을 **2.56s**로 단축 (**v3 대비 22% 추가 개선**).
- **② [RPS 극대화]**: Peak RPS **498** 달성. 시스템 처리 용량이 초기 대비 약 **3.2배** 확장됨.
- **③ [안정성 유지]**: 총 요청 수 **77.3만 건**으로 폭증했으나, 에러율 **0.009%**로 신뢰성 있는 응답 유지.

<img width="1280" height="706" alt="v4_result_1" src="https://github.com/user-attachments/assets/560b66db-4e21-47fa-bfdc-7e07f844d14f" />
<img width="1280" height="896" alt="image" src="https://github.com/user-attachments/assets/05a7555e-e7b8-478b-879b-143b3e2ba44b" />
<img width="1280" height="584" alt="image" src="https://github.com/user-attachments/assets/b68da464-c7ca-4602-92a0-2acaed73759d" />


### 5.2 성능 지표 비교 (v1 ~ v4)
| 지표 항목 | v1 (Baseline) | v2 (로직) | v3 (설정) | v4 (Redis) | 성과 (v1 vs v4) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **p(95) Latency** | 6.71s | 3.38s | 3.30s | **2.56s** | **62% 단축** |
| **Avg Throughput**| 142.4 | 268.29 | 300.49 | **401.7** | **182% 향상** |
| **Peak RPS** | 155 | 312 | 358 | **498** | **221% 향상** |
| **Total Requests**| 27.7만 | 52.0만 | 58.4만 | **77.3만** | **2.8배 증가** |

---

## 6. 최종 성능 개선 목표 및 달성 현황 요약

| 구분 | 목표치 (Thresholds) | v1 (기존) | v4 (최종) | 결과 |
| :--- | :--- |:--------| :--- | :--- |
| **p(95) Latency** | **2.0s 미만** | 6.71s   | **2.56s** | **미달 (CPU 자원 임계)** |
| **p(99) Latency** | **5.0s 미만** | 7.94s   | **3.98s** | **통과** |
| **Error Rate** | **1.0% 미만** | 0.005%  | **0.009%** | **통과** |

**향후 목표**:  
로직, 인프라, 캐싱 최적화를 통해 비약적인 성능 향상을 거두었으나, 1,000 VU 환경에서 단일 인스턴스의 CPU 부하로 인해 p(95) 2.0s 목표에는 미달했습니다. 향후 **인스턴스 확장(Scale-out)** 및 **로드밸런서(ALB)** 적용을 통해 자원 부하를 분산하고 최종 목표 지표를 달성할 예정입니다.

---

&nbsp;
## 🔧 Tech Stack
<p>
  <img src="https://img.shields.io/badge/SpringBoot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white">
  <img src="https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=openjdk&logoColor=white">
    <img src="https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white">
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white">
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">
  <img src="https://img.shields.io/badge/STOMP-6E4C13?style=for-the-badge&logo=apachekafka&logoColor=white">
  <img src="https://img.shields.io/badge/RabbitMQ-FF6600?style=for-the-badge&logo=rabbitmq&logoColor=white">

</p>

<p>
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white">
  <img src="https://img.shields.io/badge/Nginx-009639?style=for-the-badge&logo=nginx&logoColor=white">
  <img src="https://img.shields.io/badge/AWS-232F3E?style=for-the-badge&logo=amazonaws&logoColor=white">
  <img src="https://img.shields.io/badge/github-181717?style=for-the-badge&logo=github&logoColor=white">
  <img src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white">
  <img src="https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=white">
</p>

<p>
  <img src="https://img.shields.io/badge/k6-7D64FF?style=for-the-badge&logo=k6&logoColor=white">
  <img src="https://img.shields.io/badge/Prometheus-E6522C?style=for-the-badge&logo=prometheus&logoColor=white">
  <img src="https://img.shields.io/badge/Grafana-F46800?style=for-the-badge&logo=grafana&logoColor=white">
</p>

&nbsp;
## 🛠 Backend Architecture
<img width="3002" height="2376" alt="image" src="https://github.com/user-attachments/assets/0faefea2-ad94-4a9c-b423-4f8fa49fbf27" />

&nbsp;
## 🗂 ERD
<img width="972" alt="Image" src="https://github.com/user-attachments/assets/f6805244-44b5-45b1-9e47-c47521d8d53a" />

&nbsp;
## 🚀 git flow
- `main`
  - 프로젝트 최종 merge
  - 기본 프로젝트 세팅, 배포 가능한 브랜치, 항상 배포 가능한 상태를 유지
- `develop`
  - 데모데이 전까지 완성한 기능들을 계속해서 merge
  - 배포 가능한 브랜치, 항상 배포 가능한 상태를 유지
- `{type}/{description}`: 개발 브랜치
  - 예: `feat/login`, `fix/login-token`

&nbsp;
## 👩‍💻👨‍💻 Organization Repository
https://github.com/Farm-On/BE