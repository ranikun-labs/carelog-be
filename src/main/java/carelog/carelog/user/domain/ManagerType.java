package carelog.carelog.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ManagerType {
    PHYSICAL_THERAPIST("PHYSICAL_THERAPIST", "도수치료사");
    // 추후 부동한 도메인 추가

    private final String key;
    private final String title;

}
