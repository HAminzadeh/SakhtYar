package com.sakhtyar.agents.learning;

import static com.sakhtyar.agents.api.LearningDtos.*;
import static org.junit.jupiter.api.Assertions.*;

import com.sakhtyar.knowledge.domain.KnowledgeCandidateType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningCandidatePolicyTest {

    @Test
    void ratingFeedbackRequiresRating() {
        var request=new FeedbackRequest(
                null,null,null,
                LearningFeedbackType.RESPONSE_RATING,
                null,null,null,null,null,
                false,null,null,null,null,null,Map.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> LearningCandidatePolicy.validate(request)
        );
    }

    @Test
    void parameterCorrectionCannotAutoCreateKnowledgeCandidate() {
        var request=new FeedbackRequest(
                null,null,null,
                LearningFeedbackType.PARAMETER_CORRECTION,
                "landAreaM2","1200","1250",null,null,
                true,KnowledgeCandidateType.TERM,null,null,null,null,Map.of()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> LearningCandidatePolicy.validate(request)
        );
    }

    @Test
    void unknownTermDefaultsToTermCandidateAndUsesCorrectedValue() {
        var request=new FeedbackRequest(
                null,null,null,
                LearningFeedbackType.UNKNOWN_TERM,
                null,
                "\u06A9\u0644\u0645\u0647",
                "\u0627\u0635\u0637\u0644\u0627\u062D",
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                Map.of()
        );

        LearningCandidatePolicy.validate(request);

        assertEquals(
                KnowledgeCandidateType.TERM,
                LearningCandidatePolicy.candidateType(request)
        );
        assertEquals(
                "\u0627\u0635\u0637\u0644\u0627\u062D",
                LearningCandidatePolicy.candidateNameFa(request)
        );
    }

    @Test
    void termCorrectionCanCreateAliasCandidate() {
        var request=new FeedbackRequest(
                null,null,null,
                LearningFeedbackType.TERM_CORRECTION,
                null,
                "\u0648\u0627\u0698\u0647",
                "\u0648\u0627\u0698\u0647 \u0635\u062D\u06CC\u062D",
                null,
                null,
                true,
                KnowledgeCandidateType.ALIAS,
                "TARGET_TERM",
                null,
                null,
                null,
                Map.of()
        );

        assertDoesNotThrow(
                () -> LearningCandidatePolicy.validate(request)
        );
    }
}