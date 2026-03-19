package carelog.carelog.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
    MANAGER("MANAGER", "관리자"),
    CUSTOMER("CUSTOMER", "고객");

    private final String key;
    private final String title;
}