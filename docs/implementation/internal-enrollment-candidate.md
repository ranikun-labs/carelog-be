# 내부 등록 쓰기 경계 후보

이 코드는 기본 비활성 로컬 후보이며 배포 승인을 의미하지 않는다. `carelog.identity.enrollment.enabled=true`인 경우에만 `PUT /internal/v1/identity/carelog/enrollments/{accountId}`가 servlet 상대 경로로 등록된다. 현재 Carelog의 `/api/v1` servlet context를 포함한 실제 HTTP 경로는 `PUT /api/v1/internal/v1/identity/carelog/enrollments/{accountId}`다. 전용 `carelog.identity.enrollment.service-secret`은 최소 32자이며 claims 읽기 비밀과 Gateway 내부 비밀과 달라야 한다. `Authorization: Bearer <secret>`만 허용하고, 사용자·Gateway·claims 서비스 인증 헤더와 혼합하면 거부한다. 다른 HTTP 메서드와 경로는 거부한다.

본문은 `userId`, `email`, `name`이며 최초 생성과 동일 재시도는 빈 201, 충돌은 빈 409, 검증 실패는 빈 400, 기타 실패는 빈 500이다. 호출자는 검증된 외부 계정 UUID를 제공해야 한다. 이 경계는 공개 라우트를 추가하지 않으며 네트워크 격리와 암호화된 내부 전송을 대체하지 않는다. 신뢰된 호출자, 비밀 관리, 배포 설정 및 실제 두 서비스 간 통합 검증은 별도 선행 작업이다.
