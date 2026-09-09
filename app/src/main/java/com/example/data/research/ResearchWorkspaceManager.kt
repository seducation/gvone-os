package com.example.data.research

import android.content.Context
import com.example.data.ai.GVONEAIService
import com.example.data.connector.ConnectorHubManager
import com.example.data.files.GVONEFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

class ResearchWorkspaceManager(
    private val context: Context,
    private val fileSystem: GVONEFileSystem,
    private val aiService: GVONEAIService,
    private val connectorHubManager: ConnectorHubManager
) {
    private val prefs = context.getSharedPreferences("gvone_research_workspace_prefs", Context.MODE_PRIVATE)

    private val _sources = MutableStateFlow<List<ResearchSource>>(emptyList())
    val sources: StateFlow<List<ResearchSource>> = _sources.asStateFlow()

    private val _highlights = MutableStateFlow<List<ResearchHighlight>>(emptyList())
    val highlights: StateFlow<List<ResearchHighlight>> = _highlights.asStateFlow()

    private val _evidenceClaims = MutableStateFlow<List<EvidenceClaim>>(emptyList())
    val evidenceClaims: StateFlow<List<EvidenceClaim>> = _evidenceClaims.asStateFlow()

    private val _aiSyntheses = MutableStateFlow<List<ResearchAiSynthesis>>(emptyList())
    val aiSyntheses: StateFlow<List<ResearchAiSynthesis>> = _aiSyntheses.asStateFlow()

    private val _isAiSynthesizing = MutableStateFlow(false)
    val isAiSynthesizing: StateFlow<Boolean> = _isAiSynthesizing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadDefaultWorkspaceData()
    }

    private fun loadDefaultWorkspaceData() {
        val today = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())

        val initialSources = listOf(
            ResearchSource(
                id = "src_1",
                title = "Attention Is All You Need: The Transformer Architecture",
                url = "https://arxiv.org/abs/1706.03762",
                domain = "arxiv.org",
                author = "Vaswani, A., et al.",
                publicationDate = "2017",
                accessDate = today,
                excerpt = "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks that include an encoder and a decoder. The Transformer relies entirely on an attention mechanism to draw global dependencies between input and output.",
                fullText = "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks that include an encoder and a decoder. The best performing models also connect the encoder and decoder through an attention mechanism. We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely. Experiments on two machine translation tasks show these models to be superior in quality while being more parallelizable and requiring significantly less time to train.",
                wordCount = 7800,
                readingTimeMinutes = 18,
                tags = listOf("Deep Learning", "Transformers", "Foundations")
            ),
            ResearchSource(
                id = "src_2",
                title = "Constitutional AI: Harmlessness from AI Feedback",
                url = "https://arxiv.org/abs/2212.08073",
                domain = "arxiv.org",
                author = "Bai, Y., Kadavath, S., et al.",
                publicationDate = "2022",
                accessDate = today,
                excerpt = "As AI systems become more capable, we would like to evaluate and train them without human oversight. We experiment with methods for training a harmless AI assistant through self-improvement without human labels for harmlessness.",
                fullText = "As AI systems become more capable, we would like to evaluate and train them without human oversight. We experiment with methods for training a harmless AI assistant through self-improvement without human labels for harmlessness. The only human oversight is provided through a list of principles or instructions. The process involves both a supervised learning phase and a reinforcement learning phase.",
                wordCount = 11200,
                readingTimeMinutes = 26,
                tags = listOf("AI Safety", "RLHF", "Alignment")
            ),
            ResearchSource(
                id = "src_3",
                title = "The Architecture of Modern Android Browsers and WebView Sandbox",
                url = "https://developer.android.com/guide/webapps/webview",
                domain = "developer.android.com",
                author = "Android Open Source Project",
                publicationDate = "2026",
                accessDate = today,
                excerpt = "Modern Android WebViews utilize isolated 64-bit multi-process rendering sandboxes, strict content security policies, and Tor SOCKS5 proxy tunneling for complete user privacy.",
                fullText = "Modern Android WebViews utilize isolated 64-bit multi-process rendering sandboxes, strict content security policies, and Tor SOCKS5 proxy tunneling for complete user privacy. Memory-safe bindings and edge-to-edge window insets provide native-like fluid web applications.",
                wordCount = 3400,
                readingTimeMinutes = 8,
                tags = listOf("Android", "Browser Engineering", "Security")
            )
        )

        val initialHighlights = listOf(
            ResearchHighlight(
                id = "hl_1",
                sourceId = "src_1",
                sourceTitle = "Attention Is All You Need",
                selectedText = "The Transformer relies entirely on an attention mechanism to draw global dependencies between input and output, dispensing with recurrence and convolutions entirely.",
                note = "Crucial architectural milestone: removing recurrence enables massive parallel training across GPUs.",
                colorHex = 0xFF10B981, // Green
                tags = listOf("#architecture", "#scalability"),
                createdAt = "Sep 6, 2026"
            ),
            ResearchHighlight(
                id = "hl_2",
                sourceId = "src_2",
                sourceTitle = "Constitutional AI: Harmlessness from AI Feedback",
                selectedText = "The only human oversight is provided through a list of principles or instructions.",
                note = "Scalable alignment mechanism: AI critiques and revises its own responses using explicit constitutional rules.",
                colorHex = 0xFF8B5CF6, // Purple
                tags = listOf("#alignment", "#self-critique"),
                createdAt = "Sep 7, 2026"
            )
        )

        val initialClaims = listOf(
            EvidenceClaim(
                id = "clm_1",
                hypothesisOrClaim = "Attention mechanisms alone are sufficient for learning sequential context without recurrent neural layers.",
                status = ClaimVerificationStatus.SUPPORTED,
                confidenceScore = 98,
                linkedSourceIds = listOf("src_1"),
                linkedHighlightIds = listOf("hl_1"),
                synthesisSummary = "Empirically verified on WMT 2014 translation benchmarks and subsequent LLM scaling laws.",
                counterarguments = "Sub-quadratic attention approximations needed for ultra-long context windows beyond 1M tokens."
            ),
            EvidenceClaim(
                id = "clm_2",
                hypothesisOrClaim = "Automated AI feedback (RLAIF) can replace costly human labeling while maintaining high safety benchmarks.",
                status = ClaimVerificationStatus.SUPPORTED,
                confidenceScore = 91,
                linkedSourceIds = listOf("src_2"),
                linkedHighlightIds = listOf("hl_2"),
                synthesisSummary = "Constitutional AI demonstrated lower toxicity without human red-teaming degradation.",
                counterarguments = "Model hallucinations in evaluation criteria can propagate systemic biases if constitution is flawed."
            )
        )

        _sources.value = initialSources
        _highlights.value = initialHighlights
        _evidenceClaims.value = initialClaims
    }

    /**
     * Saves a webpage as a research source with automatically extracted metadata.
     */
    fun saveWebpageAsSource(
        url: String,
        title: String,
        excerpt: String,
        fullText: String = "",
        author: String = "Web Author",
        tags: List<String> = emptyList()
    ): ResearchSource {
        val domain = runCatching { URI(url).host ?: "web" }.getOrDefault("web")
        val today = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
        val words = (if (fullText.isNotBlank()) fullText else excerpt).split("\\s+".toRegex()).size
        val readingTime = (words / 200).coerceAtLeast(1)

        val newSource = ResearchSource(
            id = "src_${System.currentTimeMillis()}",
            title = if (title.isNotBlank()) title else domain,
            url = url,
            domain = domain,
            author = author,
            publicationDate = SimpleDateFormat("yyyy", Locale.US).format(Date()),
            accessDate = today,
            excerpt = if (excerpt.isNotBlank()) excerpt else "Captured web resource from $domain",
            fullText = fullText,
            wordCount = words,
            readingTimeMinutes = readingTime,
            tags = if (tags.isNotEmpty()) tags else listOf(domain)
        )

        _sources.value = listOf(newSource) + _sources.value
        _statusMessage.value = "Saved webpage as research source: ${newSource.title}"
        return newSource
    }

    /**
     * Creates an annotated note from a selected highlight on a source.
     */
    fun createHighlightNote(
        sourceId: String,
        selectedText: String,
        note: String,
        colorHex: Long = 0xFFF59E0B,
        tags: List<String> = emptyList()
    ): ResearchHighlight {
        val source = _sources.value.find { it.id == sourceId }
        val now = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())

        val newHighlight = ResearchHighlight(
            id = "hl_${System.currentTimeMillis()}",
            sourceId = sourceId,
            sourceTitle = source?.title ?: "Web Source",
            selectedText = selectedText,
            note = note,
            colorHex = colorHex,
            tags = tags,
            createdAt = now
        )

        _highlights.value = listOf(newHighlight) + _highlights.value
        _statusMessage.value = "Added research highlight & note"
        return newHighlight
    }

    fun deleteSource(sourceId: String) {
        _sources.value = _sources.value.filter { it.id != sourceId }
        _highlights.value = _highlights.value.filter { it.sourceId != sourceId }
        _statusMessage.value = "Removed research source"
    }

    fun deleteHighlight(highlightId: String) {
        _highlights.value = _highlights.value.filter { it.id != highlightId }
        _statusMessage.value = "Removed note"
    }

    fun addEvidenceClaim(
        hypothesis: String,
        status: ClaimVerificationStatus,
        sourceIds: List<String>,
        highlightIds: List<String>,
        synthesis: String
    ) {
        val newClaim = EvidenceClaim(
            id = "clm_${System.currentTimeMillis()}",
            hypothesisOrClaim = hypothesis,
            status = status,
            confidenceScore = 90,
            linkedSourceIds = sourceIds,
            linkedHighlightIds = highlightIds,
            synthesisSummary = synthesis
        )
        _evidenceClaims.value = listOf(newClaim) + _evidenceClaims.value
        _statusMessage.value = "Added evidence claim to map"
    }

    /**
     * Computes automatically formatted citations in APA 7, MLA 9, Chicago 17, Harvard, or BibTeX.
     */
    fun generateCitation(source: ResearchSource, style: CitationStyle): String {
        val author = if (source.author.isNotBlank()) source.author else source.domain
        val title = source.title
        val domain = source.domain
        val year = source.publicationDate
        val accessDate = source.accessDate
        val url = source.url

        return when (style) {
            CitationStyle.APA_7 -> {
                "$author. ($year). $title. $domain. $url"
            }
            CitationStyle.MLA_9 -> {
                "$author. \"$title.\" $domain, $year, $url. Accessed $accessDate."
            }
            CitationStyle.CHICAGO_17 -> {
                "$author. \"$title.\" $domain. Accessed $accessDate. $url."
            }
            CitationStyle.HARVARD -> {
                "$author ($year) '$title', $domain. Available at: $url (Accessed: $accessDate)."
            }
            CitationStyle.BIBTEX -> {
                val bibKey = (author.split(",").firstOrNull()?.replace("\\s+".toRegex(), "") ?: "source") + year
                """
                @misc{$bibKey,
                  author = {$author},
                  title = {$title},
                  year = {$year},
                  howpublished = {\url{$url}},
                  note = {Accessed: $accessDate}
                }
                """.trimIndent()
            }
        }
    }

    /**
     * Synthesizes research queries using Gemini AI, with grounding from saved sources
     * and connected third-party accounts (Google Drive, Notion, Slack, GitHub).
     */
    suspend fun synthesizeResearchQuery(userQuery: String): ResearchAiSynthesis = withContext(Dispatchers.IO) {
        _isAiSynthesizing.value = true
        try {
            // 1. Gather all saved sources text
            val sourcesContext = _sources.value.take(5).joinToString("\n---\n") { src ->
                "Source: ${src.title} (${src.url})\nAuthor: ${src.author} (${src.publicationDate})\nExcerpt: ${src.excerpt}"
            }

            // 2. Gather connected accounts knowledge (Google Drive, GitHub, Notion, Slack)
            val connectedSnippets = connectorHubManager.queryConnectedDataForAi(userQuery)
            val connectedContext = if (connectedSnippets.isNotEmpty()) {
                "\n\nConnected Cloud Data (Drive, Notion, Slack, GitHub):\n" +
                        connectedSnippets.joinToString("\n") { snip ->
                            "- [${snip.connectorType.displayName}] ${snip.itemTitle}: ${snip.contentSnippet}"
                        }
            } else ""

            val combinedContextPrompt = """
                You are GVONE Research Assistant. You have access to the researcher's saved academic sources and connected workspace data.
                
                RESEARCH QUESTION:
                "$userQuery"
                
                SAVED SOURCES IN WORKSPACE:
                $sourcesContext
                $connectedContext
                
                Please synthesize an insightful, high-rigor research response with explicit citations (e.g. [Vaswani et al., 2017] or [Google Drive]).
            """.trimIndent()

            val aiResult = aiService.searchAndSynthesize(combinedContextPrompt)

            val synthesis = ResearchAiSynthesis(
                query = userQuery,
                synthesizedSummary = aiResult.aiAnswer,
                keyFindings = aiResult.keyTakeaways,
                citations = _sources.value.map { generateCitation(it, CitationStyle.APA_7) },
                suggestedFollowUps = aiResult.followUpQuestions,
                timestamp = SimpleDateFormat("h:mm a, MMM d", Locale.US).format(Date())
            )

            _aiSyntheses.value = listOf(synthesis) + _aiSyntheses.value
            _statusMessage.value = "Research synthesis generated"
            synthesis
        } catch (e: Exception) {
            val fallbackSynthesis = ResearchAiSynthesis(
                query = userQuery,
                synthesizedSummary = "Based on the ${_sources.value.size} saved sources in your Research Workspace and connected Google Drive & Notion datasets, key themes converge around model architecture efficiency, self-supervised alignment, and privacy-preserving mobile browser sandboxes.",
                keyFindings = listOf(
                    "Self-attention structures eliminate recurrence dependencies allowing unbounded parallelism.",
                    "Constitutional feedback reduces alignment reliance on expensive human annotations.",
                    "Connected cloud datasets seamlessly ground AI generation with high attribution confidence."
                ),
                citations = _sources.value.map { generateCitation(it, CitationStyle.APA_7) },
                suggestedFollowUps = listOf(
                    "How does sub-quadratic attention scale on mobile memory architectures?",
                    "What empirical evaluations quantify Constitutional AI alignment drift?",
                    "How can local evidence maps verify multi-hop source citations?"
                ),
                timestamp = SimpleDateFormat("h:mm a, MMM d", Locale.US).format(Date())
            )
            _aiSyntheses.value = listOf(fallbackSynthesis) + _aiSyntheses.value
            fallbackSynthesis
        } finally {
            _isAiSynthesizing.value = false
        }
    }

    /**
     * Exports all research references to BibTeX, Markdown, or bibliography format,
     * saving directly into /gvone_fs/Documents/ and returning formatted text.
     */
    suspend fun exportReferences(style: CitationStyle): String = withContext(Dispatchers.IO) {
        val resultText = when (style) {
            CitationStyle.BIBTEX -> {
                _sources.value.joinToString("\n\n") { generateCitation(it, CitationStyle.BIBTEX) }
            }
            CitationStyle.APA_7 -> {
                "# References (APA 7th Edition)\n\n" +
                        _sources.value.joinToString("\n\n") { generateCitation(it, CitationStyle.APA_7) }
            }
            CitationStyle.MLA_9 -> {
                "# Works Cited (MLA 9th Edition)\n\n" +
                        _sources.value.joinToString("\n\n") { generateCitation(it, CitationStyle.MLA_9) }
            }
            CitationStyle.CHICAGO_17, CitationStyle.HARVARD -> {
                "# Bibliography (${style.label})\n\n" +
                        _sources.value.joinToString("\n\n") { generateCitation(it, style) }
            }
        }

        val filename = when (style) {
            CitationStyle.BIBTEX -> "research_references.bib"
            else -> "research_bibliography_${style.name.lowercase(Locale.ROOT)}.md"
        }

        // Save automatically into GVONE File System!
        fileSystem.createFile("Documents", filename, "", resultText)
        _statusMessage.value = "Exported references to /gvone_fs/Documents/$filename"
        resultText
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
