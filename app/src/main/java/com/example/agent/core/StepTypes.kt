package com.example.agent.core

/**
 * Canonical action types for all GVONE agents, ported and evolved from KAI.KAMUI.GVONE.
 * Every agent action must be one of these types to ensure consistent auditing and observation.
 */
enum class StepType(val displayName: String) {
    CHECK("Checking"),
    DECIDE("Deciding"),
    FETCH("Fetching"),
    DOWNLOAD("Downloading"),
    EXTRACT("Extracting"),
    TRANSCRIBE("Transcribing"),
    ANALYZE("Analyzing"),
    MODIFY("Modifying"),
    VALIDATE("Validating"),
    STORE("Storing"),
    NAVIGATE("Navigating"),
    EXECUTE("Executing"),
    TOOL_CALL("Tool Call"),
    WAITING("Waiting"),
    COMPLETE("Completed"),
    ERROR("Error"),
    CANCELLED("Cancelled")
}
