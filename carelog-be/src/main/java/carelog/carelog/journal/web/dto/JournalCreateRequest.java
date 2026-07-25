package carelog.carelog.journal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record JournalCreateRequest(
        @Schema(description = "템플릿 ID (자유 양식이면 null)")
        UUID templatePublicId,

        @NotBlank(message = "제목은 필수입니다")
        @Schema(description = "일지 제목", example = "1회차 방문")
        String title,

        @NotNull(message = "방문일은 필수입니다")
        @Schema(description = "방문일 (예: 2026-03-16)", example = "2026-03-16")
        LocalDate visitDate,

        @NotNull(message = "업무 데이터는 필수입니다")
        @Schema(
                description = "업무 데이터 — 업종별 동적 구조, AI 파이프라인 전달 대상",
                example = """
                    {
                      "bodyPart":  "허리",
                      "painScore": 7,
                      "painType":  "둔통",
                      "symptoms":  "만성 요통, 앉을 때 악화",
                      "treatment": "도수치료 30분",
                      "nextPlan":  "다음 주 재방문"
                    }
                    """
        )
        Map<String, Object> caseData,

        @Schema(
                description = "개인 식별 정보 (PII) — 내부 전용, AI 파이프라인 진입 불가 (null 허용)",
                example = """
                    {
                      "emergencyContact": "010-1234-5678",
                      "memo":             "보호자 동반 필요"
                    }
                    """
        )
        Map<String, Object> privateData
) {}

