package carelog.carelog.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
    THERAPIST("THERAPIST", "치료사"),
    CLIENT("CLIENT", "고객");

    private final String key;
    private final String title;
}