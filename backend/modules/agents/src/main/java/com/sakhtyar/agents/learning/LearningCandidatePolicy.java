package com.sakhtyar.agents.learning;

import static com.sakhtyar.agents.api.LearningDtos.FeedbackRequest;

import com.sakhtyar.knowledge.domain.KnowledgeCandidateType;

public final class LearningCandidatePolicy {

    private LearningCandidatePolicy(){}

    public static void validate(FeedbackRequest request) {
        if (request.feedbackType() == LearningFeedbackType.RESPONSE_RATING
                && request.rating() == null) {
            throw new IllegalArgumentException(
                    "rating is required for RESPONSE_RATING feedback."
            );
        }

        if ((request.feedbackType() == LearningFeedbackType.TERM_CORRECTION
                || request.feedbackType() == LearningFeedbackType.PARAMETER_CORRECTION
                || request.feedbackType() == LearningFeedbackType.INTENT_CORRECTION)
                && blank(request.correctedValue())) {
            throw new IllegalArgumentException(
                    "correctedValue is required for correction feedback."
            );
        }

        if (!request.proposeKnowledgeCandidate()) {
            return;
        }

        if (request.feedbackType() != LearningFeedbackType.UNKNOWN_TERM
                && request.feedbackType() != LearningFeedbackType.TERM_CORRECTION) {
            throw new IllegalArgumentException(
                    "Knowledge candidates can only be proposed from UNKNOWN_TERM or TERM_CORRECTION feedback."
            );
        }

        KnowledgeCandidateType type = candidateType(request);
        if (type != KnowledgeCandidateType.TERM
                && type != KnowledgeCandidateType.ALIAS
                && type != KnowledgeCandidateType.DEFINITION) {
            throw new IllegalArgumentException(
                    "Learning Loop supports TERM, ALIAS or DEFINITION knowledge candidates."
            );
        }

        if (blank(candidateNameFa(request))) {
            throw new IllegalArgumentException(
                    "A proposed Persian term/name is required for a knowledge candidate."
            );
        }
    }

    public static KnowledgeCandidateType candidateType(FeedbackRequest request) {
        return request.candidateType() == null
                ? KnowledgeCandidateType.TERM
                : request.candidateType();
    }

    public static String candidateNameFa(FeedbackRequest request) {
        if (!blank(request.proposedNameFa())) {
            return request.proposedNameFa().trim();
        }
        if (!blank(request.correctedValue())) {
            return request.correctedValue().trim();
        }
        if (!blank(request.originalValue())) {
            return request.originalValue().trim();
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}