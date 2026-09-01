package com.remockable.api.model.enums;

/** 非同步 AI 工作的種類。決定 job result 的形狀（docs/01-api-interface.md §1.4）。 */
public enum AiJobType {
    JOB_POSTING_PARSE,
    QUESTION_SET_GENERATION,
    QUESTION_ADDITION,
    ATTEMPT_TRANSCRIPTION,
    REFERENCE_ANSWER_GENERATION,
    ANSWER_ANALYSIS
}
