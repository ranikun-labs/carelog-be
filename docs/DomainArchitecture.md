### 1. 개요 (Introduction)

- **1.1. 목적**
    - 이 문서는 CareLog User 도메인의 아키텍처를 정의합니다.
- **1.2. 해결하려는 문제**
    - 기존의 전통적인 MVC(레이어드) 아키텍처는 비즈니스 로직(Service)이 특정 기술(e.g., Spring Data JPA)에 강하게 종속되어, 테스트가 어렵고 유지보수가 힘든 문제를 가집니다.
- **1.3. 핵심 목표**
    - 핵심 비즈니스 로직을 **외부 기술(DB, Web)**로부터 분리(보호)하여 **테스트 용이성**과 **유연성(유지보수성)**을 확보합니다.

---

### 2. 핵심 아키텍처: 헥사고날 (Ports & Adapters)

### 2.1. 아키텍처 구조 (흐름 및 파일 트리)

> 외부(Adapter)가 내부(Application)에 의존하고, 내부(Application)는 외부에 의존하지 않는 것이 핵심입니다.
> 

```json
src/main/java/carelog/carelog/user
├─ domain
│  ├─ User.java                 # 👈 1. 순수 POJO ( @build/classes/java/main/carelog/carelog/common/domain/BaseEntity.class 없음)
│  ├─ UserRole.java
│  └─ ManagerType.java
├─ application
│  ├─ port
│  │  ├─ in                   # 👈 2. Inbound Port (Service의 '계약서')
│  │  │  ├─ IUserService.java
│  │  │  ├─ ICreateManagerUseCase.java
│  │  │  └─ ICreateCustomerUseCase.java
│  │  └─ out                  # 👈 3. Outbound Port (Repository의 '계약서')
│  │     └─ UserRepositoryPort.java
│  └─ service
│     ├─ UserServiceImpl.java          # 👈 4. 'UserRepositoryPort' 인터페이스만 주입받음 (JPA 모름)
│     ├─ CreateManagerServiceImpl.java
│     └─ CreateCustomerServiceImpl.java
└─ adapter
   ├─ web
   │  ├─ UserController.java
   │  └─ dto
   │     ├─ UserCreateRequest.java
   │     (...etc)
   └─ persistence                # 👈 5. '기술'을 구현하는 곳
      ├─ UserRepository.java     # 5-1. JPA 기술 인터페이스 (extends JpaRepository<UserJpaEntity, Long>)
# 5-2부터는 멀티 어댑터에 따라 도메인별로 적용
      ├─ UserJpaEntity.java      # 5-2. @build/classes/java/main/carelog/carelog/common/domain/BaseEntity.class 전용 클래스
      ├─ UserMapper.java         # 5-3. 번역기 (Domain <-> Entity)
      └─ UserJpaAdapter.java     # 5-4. 🌟 'UserRepositoryPort'의 실제 구현체
```

### 2.2. 핵심 원칙: 의존성 역전 원칙 (DIP)

> Service가 JpaAdapter 같은 '구현체'에 의존하는 것이 아니라, UserRepositoryPort라는 '추상(인터페이스)'에 의존합니다.
> 
> 
> 이를 통해 Service는 JPA가 아닌 '순수 단위 테스트'가 가능해집니다.
> 

---

### 3. 디렉토리 구조 및 역할 (The "What")

- **`domain` (순수 비즈니스 모델)**
    - JPA(` @Entity`) 등 어떤 기술에도 의존하지 않는 순수한 Java 객체(POJO)입니다.
- **`application` (핵심 비즈니스 로직)**
    - **`port/in`**: 애플리케이션의 **"API 명세"**. 외부(Controller)에서 호출할 수 있는 기능(유스케이스)을 정의한 인터페이스.
    - **`port/out`**: 애플리케이션이 **"필요로 하는"** 외부 기능(e.g., DB 저장)을 정의한 인터페이스.
    - **`service`**: `port/in`의 실제 구현체. 모든 비즈니스 로직과 트랜잭션 경계가 이곳에 있습니다.
- **`adapter` (외부 기술 구현체)**
    - **`web`**: **Inbound Adapter.** `port/in (IUserService)`을 호출합니다.
    - **`persistence`**: **Outbound Adapter.** `port/out (UserRepositoryPort)`을 구현합니다.

---

### 4. `adapter/persistence` 상세 설계 (The "How")

Service가 `UserRepositoryPort.save(domain.User)`를 호출했을 때의 흐름입니다.

- **4.1. `UserJpaAdapter` (총책임자)**
    - `UserRepositoryPort`의 구현체. Service의 추상적인 요청을 JPA 기술로 변환하는 '조율'을 담당합니다.
- **4.2. `UserMapper` (번역기)**
    - `domain.User` ↔ `UserJpaEntity`를 변환합니다.
- **4.3. `UserRepository` (JPA 실무)**
    - `extends JpaRepository`. `UserJpaEntity`를 받아 실제 DB와 통신합니다. (Spring이 자동 구현)
- **4.4. `UserJpaEntity` (DB 모델)**
    - ` @Entity`가 붙은 DB 테이블 전용 객체입니다.

---

### 5. 주요 유스케이스 흐름 (예시: 유저 생성)

1. `UserController`가 `IUserService` (Port)를 호출합니다.
2. `UserServiceImpl` (Service)가 요청을 받아 공통 로직(e.g., 이메일 중복 검사)을 수행합니다.
3. `UserServiceImpl`은 `role`에 따라 `ICreateManagerUseCase` (Port)를 호출합니다.
4. `CreateManagerServiceImpl` (Service)이 매니저 생성 로직을 수행합니다.
5. `CreateManagerServiceImpl`이 `UserRepositoryPort` (Port)를 호출합니다.
6. `UserJpaAdapter` (Adapter)가 `User`를 `UserJpaEntity`로 변환(Mapper)하여 `UserRepository`를 통해 DB에 저장합니다.

---

### 6. 트레이드오프 (Trade-offs)

이 구조는 명확한 장점과 비용이 존재합니다.

### 📉 단점 (비용)

- **파일 개수 증가**: 단순 CRUD에도 많은 클래스(Port, Adapter, Mapper, Entity)가 필요합니다.
- **초기 학습 곡선**: 구조가 복잡하여 팀원의 이해가 필요합니다.

### 📈 장점 (이득)

- **🧪 압도적인 테스트 용이성**: Service 계층을 DB 없이 순수 단위 테스트(Mocking)할 수 있습니다.
- **⚙️ 유연성 및 유지보수성**: JPA를 MongoDB로 바꾸거나, 멀티 DB(psql+mongo)를 도입할 때 `adapter` 계층만 수정하면 됩니다. `application` 계층은 영향을 받지 않습니다.
- **🛡️ 도메인 순수성**: 핵심 비즈니스 로직이 특정 기술에 오염되지 않습니다.