# carelog 레포지토리 구조 설계 결정

> 작성일: 2026-03-18

---

## 배경

carelog는 세 개의 독립적인 서비스로 구성된다.

| 서비스 | 역할 | 배포 환경 | 언어/런타임 |
|--------|------|-----------|------------|
| `carelog-be` | 비즈니스 로직 (Spring Boot) | 온프레미스 | Kotlin/JVM |
| `carelog-gateway` | API Gateway, JWT 검증, 라우팅 (Spring Cloud Gateway + Redis) | EC2 | Kotlin/JVM |
| `carelog-rag` | RAG 파이프라인 (FastAPI + Vector DB) | 온프레미스 | Python |

---

## 결정: JVM 서비스는 모노레포, RAG는 별도 레포

```
GitHub: care-log/carelog          ← Gradle Multi-Module 모노레포
├── carelog-be/                   → 온프레미스 (Spring Boot)
├── carelog-gateway/              → EC2 (Spring Cloud Gateway)
└── build.gradle (root)

GitHub: care-log/carelog-rag      ← 별도 레포 (Python, 다른 런타임)
```

분리 기준은 **언어/런타임 경계**다. JVM 기반 서비스는 모노레포, Python 기반 RAG는 별도 레포.

---

## 왜 모노레포인가

### 1. 같은 Spring 에코시스템 — 공유 코드가 생긴다

`carelog-be`와 `carelog-gateway`는 둘 다 Kotlin/Spring 기반이다. JWT 관련 DTO, 에러 코드 enum, 공통 설정 클래스 같은 것들이 공유 대상이 된다.

멀티레포로 가면 이를 공유하기 위해 별도 라이브러리 모듈 생성 → Maven/Gradle publish → 버전 관리가 필요하다. 서비스 2개짜리 규모에서 이 오버헤드는 실익이 없다.

모노레포에서는 `carelog-common` 모듈을 추가하면 끝이다.

### 2. 배포 독립성은 path filter로 해결된다

"모노레포면 SCG 설정 하나 바꾸는데 be CI/CD도 같이 돌아간다"는 path filter를 쓰지 않을 때의 문제다.

GitHub Actions의 `paths` 필터로 변경된 모듈만 배포 트리거하면 완전한 배포 독립성이 확보된다. 이는 우회책이 아니라 모노레포의 정석적인 배포 전략이다.

```yaml
# carelog-be 배포 워크플로우
on:
  push:
    paths:
      - 'carelog-be/**'

# carelog-gateway 배포 워크플로우
on:
  push:
    paths:
      - 'carelog-gateway/**'
```

### 3. 1인 개발에서 멀티레포의 장점이 없다

멀티레포의 실익은 팀 규모가 있을 때 나온다.

| 멀티레포 장점 | 1인 개발에서의 현실 |
|--------------|-------------------|
| 서비스별 오너십 분리 | 오너가 한 명 |
| 접근 권한 관리 | 관리할 팀이 없음 |
| 독립 이슈 트래킹 | 레포 3개 왔다갔다하는 인지 비용만 증가 |

### 4. 이 시스템은 MSA가 아니다

"백엔드 MSA에서 레포는 서비스별로 분리한다"는 대규모 조직에서 팀별로 서비스 오너십이 나뉠 때 맞는 말이다. 서비스 2~3개짜리 시스템에 MSA 레포 전략을 그대로 적용하는 것은 과설계다.

---

## Spring Cloud Config 판단

Spring Cloud Config는 여러 서비스의 설정을 중앙 서버에서 관리하는 컴포넌트다.

**현재 도입하지 않는 이유**
- 서비스 3개 수준에서는 `application.yml` 각자 관리로 충분
- Config Server 자체가 하나의 서비스 → 관리 포인트 추가
- MVP 단계에서 복잡도 대비 이득 없음

**도입을 고려할 시점**
- JVM 기반 서비스가 4개 이상으로 늘어날 때
- 환경별(dev/staging/prod) 설정 분기가 복잡해질 때
- 설정 변경 시 재배포 없이 반영이 필요할 때

---

## 인프라 전체 구조

```
[클라이언트]
     ↓ HTTPS
[Cloudflare Tunnel]
     ↓
[EC2] carelog-gateway (SCG + Redis)
  ├── JWT 서명 검증
  ├── Redis Blacklist 체크
  └── 라우팅
       ├── /api/**   → [온프레미스] carelog-be
       └── /rag/**   → [온프레미스] carelog-rag (FastAPI)

[온프레미스]
  ├── carelog-be  (Spring Boot + PostgreSQL)
  └── carelog-rag (FastAPI + Vector DB)
```

> 의료법 대응: 환자 데이터는 온프레미스, 라우팅/토큰 검증만 EC2에서 처리