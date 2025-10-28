package carelog.carelog.relation.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RelationStatus {
    ACTIVE("ACTIVE", "활성"),
    TERMINATED("ENDED", "종료");

    private final String key;
    private final String title;
}
