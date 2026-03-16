package carelog.carelog.journal.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record JournalTemplateCreateRequest(
        @NotBlank(message = "템플릿 이름은 필수입니다")
        @Schema(description = "템플릿 이름", example = "기본 진료 양식")
        String name,

        @NotNull(message = "필드 목록은 필수입니다")
        @Schema(
                description = "템플릿 필드 목록 — key(저장키), label(화면표시명), type(text/number/textarea), category(case/private)",
//                example = "[{\"key\":\"bodyPart\",\"label\":\"부위\",\"type\":\"text\",\"category\":\"case\"},{\"key\":\"painScore\",\"label\":\"통증점수\",\"type\":\"number\",\"category\":\"case\"},{\"key\":\"painType\",\"label\":\"통증양상\",\"type\":\"text\",\"category\":\"case\"},{\"key\":\"symptoms\",\"label\":\"증상\",\"type\":\"textarea\",\"category\":\"case\"},{\"key\":\"treatment\",\"label\":\"치료내용\",\"type\":\"textarea\",\"category\":\"case\"},{\"key\":\"nextPlan\",\"label\":\"다음 계획\",\"type\":\"text\",\"category\":\"case\"},{\"key\":\"emergencyContact\",\"label\":\"비상연락처\",\"type\":\"text\",\"category\":\"private\"},{\"key\":\"memo\",\"label\":\"메모\",\"type\":\"textarea\",\"category\":\"private\"}]"
                example = """
                [
                  {"key": "bodyPart",         "label": "부위",       "type": "text",     "category": "case"},
                  {"key": "painScore",        "label": "통증점수",   "type": "number",   "category": "case"},
                  {"key": "painType",         "label": "통증양상",   "type": "text",     "category": "case"},
                  {"key": "symptoms",         "label": "증상",       "type": "textarea", "category": "case"},
                  {"key": "treatment",        "label": "치료내용",   "type": "textarea", "category": "case"},
                  {"key": "nextPlan",         "label": "다음 계획",  "type": "text",     "category": "case"},
                  {"key": "emergencyContact", "label": "비상연락처", "type": "text",     "category": "private"},
                  {"key": "memo",             "label": "메모",       "type": "textarea", "category": "private"}
                ]
                """
        )
        @NotNull List<Map<String, Object>> fields
){}
