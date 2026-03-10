# Soft Delete & 삭제 감사 로그 설계 리뷰

## 현재 상태

`User`, `Relation` 엔티티에 `deletedAt` 컬럼으로 soft delete 적용 중.

```java
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class User extends TenantBaseEntity {
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
```

---

## 문제 제기

**`deletedAt`만으로는 누가 삭제했는지 알 수 없음.**

의료 데이터 특성상 삭제 행위자 추적이 필요할 수 있음.
현재 `BaseEntity`에는 `createdBy`, `updatedBy`가 있지만 `deletedBy`는 없음.

---

## 옵션 분석

### Option A. 각 엔티티에 `deletedBy` 개별 선언

```java
@Column(name = "deleted_at")
private OffsetDateTime deletedAt;

@Column(name = "deleted_by")
private String deletedBy;

public void delete(String actorId) {
    this.deletedAt = OffsetDateTime.now();
    this.deletedBy = actorId;
}
```

- `@SQLDelete` 제거, 서비스에서 `user.delete(currentUserId)` + `save()` 호출
- **장점**: 명확하고 단순
- **단점**: User, Relation마다 반복 선언

---

### Option B. `SoftDeletableEntity` 중간 추상 클래스 도입

```
BaseEntity
└── TenantBaseEntity
    ├── SoftDeletableEntity (deletedAt, deletedBy)
    │   ├── User
    │   └── Relation
    └── Journal (SUPERSEDED 패턴 → soft delete 불필요)
```

```java
@MappedSuperclass
public abstract class SoftDeletableEntity extends TenantBaseEntity {
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    public void delete(String actorId) {
        this.deletedAt = OffsetDateTime.now();
        this.deletedBy = actorId;
    }
}
```

- **장점**: 중복 제거, 계층 명확
- **단점**: 클래스 하나 추가

---

### Option C. `TenantBaseEntity`에 직접 추가

- **단점**: Journal, TenantAuditLog 등 soft delete 불필요한 엔티티에도 컬럼 생성 → **부적합**

---

### Option D. `deletedBy` 없이 `TenantAuditLog`로 추적

삭제 이벤트를 별도 감사 로그 테이블에 기록하는 방식.

```
TenantAuditLog { action="DELETE", targetEntity="User", targetId=1, actorId="..." }
```

- **장점**: 엔티티 오염 없음, 감사 로그 일원화
- **단점**: 삭제자 조회 시 조인 필요, 구현 복잡도 증가

---

## 권장안

**Option B (SoftDeletableEntity 도입)** 를 권장.

이유:
- Journal은 SUPERSEDED 패턴으로 soft delete 없음 → TenantBaseEntity 직접 오염 방지
- User/Relation에 반복 코드 없음
- 계층 구조가 도메인 의도를 명확히 표현

```
BaseEntity
└── TenantBaseEntity
    ├── SoftDeletableEntity ← User, Relation
    └── (Journal, AuditLog 등은 직접 상속)
```

---

## 구현 시점

**Phase 2 (JWT 도입) 이후에 구현 권장.**

`deletedBy`에 넣을 현재 유저 ID는 `SecurityContext`에서 꺼내야 하는데,
JWT 필터가 없으면 SecurityContext가 비어있어서 지금은 구현해도 값을 못 넣음.

**현재는 `deletedAt`만 유지하고, JWT 완성 후 아래 순서로 작업:**

1. `SoftDeletableEntity` 추가
2. `User`, `Relation` 상속 변경
3. `@SQLDelete` 제거 → `delete(actorId)` 도메인 메서드로 교체
4. 서비스에서 `SecurityContext`로 `actorId` 주입
