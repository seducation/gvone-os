package com.example.data.research

enum class CitationStyle(val label: String, val description: String) {
    APA_7("APA 7th", "American Psychological Association 7th edition"),
    MLA_9("MLA 9th", "Modern Language Association 9th edition"),
    CHICAGO_17("Chicago 17th", "Chicago Manual of Style (Notes & Bibliography)"),
    HARVARD("Harvard", "Harvard Author-Date referencing system"),
    BIBTEX("BibTeX", "LaTeX / BibTeX format for academic publications")
}

data class ResearchSource(
    val id: String,
    val title: String,
    val url: String,
    val domain: String,
    val author: String = "Unknown Author",
    val publicationDate: String = "2026",
    val accessDate: String,
    val excerpt: String,
    val fullText: String = "",
    val wordCount: Int = 0,
    val readingTimeMinutes: Int = 1,
    val tags: List<String> = emptyList(),
    val savedAtTimestamp: Long = System.currentTimeMillis()
)

data class ResearchHighlight(
    val id: String,
    val sourceId: String,
    val sourceTitle: String,
    val selectedText: String,
    val note: String = "",
    val colorHex: Long = 0xFFF59E0B, // Amber/Yellow default
    val tags: List<String> = emptyList(),
    val createdAt: String
)

enum class ClaimVerificationStatus(val label: String, val colorHex: Long) {
    SUPPORTED("Supported", 0xFF10B981), // Emerald
    NEUTRAL("Under Review", 0xFFF59E0B), // Amber
    CONTRADICTED("Contradicted", 0xFFEF4444) // Red
}

data class EvidenceClaim(
    val id: String,
    val hypothesisOrClaim: String,
    val status: ClaimVerificationStatus = ClaimVerificationStatus.SUPPORTED,
    val confidenceScore: Int = 85,
    val linkedSourceIds: List<String> = emptyList(),
    val linkedHighlightIds: List<String> = emptyList(),
    val synthesisSummary: String = "",
    val counterarguments: String = ""
)

data class ResearchAiSynthesis(
    val query: String,
    val synthesizedSummary: String,
    val keyFindings: List<String> = emptyList(),
    val citations: List<String> = emptyList(),
    val suggestedFollowUps: List<String> = emptyList(),
    val timestamp: String
)
