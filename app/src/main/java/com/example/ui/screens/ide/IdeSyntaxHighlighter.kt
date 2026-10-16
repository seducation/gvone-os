package com.example.ui.screens.ide

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

/**
 * Token-based syntax highlighter for VS Code style code editing.
 * Formats keywords, strings, comments, numbers, and types adaptively
 * based on MaterialTheme.colorScheme.
 */
object IdeSyntaxHighlighter {

    private val KOTLIN_KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "val", "var", "fun",
        "return", "if", "else", "when", "for", "while", "do", "break", "continue",
        "throw", "try", "catch", "finally", "true", "false", "null", "this", "super",
        "is", "in", "as", "by", "suspend", "data", "sealed", "enum", "override",
        "open", "abstract", "private", "protected", "public", "internal", "companion",
        "init", "constructor", "const", "lateinit", "inline", "tailrec", "operator"
    )

    private val JS_KEYWORDS = setOf(
        "function", "const", "let", "var", "return", "if", "else", "switch", "case",
        "default", "for", "while", "do", "break", "continue", "try", "catch", "finally",
        "throw", "class", "extends", "import", "export", "from", "async", "await",
        "yield", "true", "false", "null", "undefined", "new", "this", "typeof", "instanceof"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "break",
        "continue", "try", "except", "finally", "raise", "import", "from", "as",
        "with", "pass", "lambda", "yield", "True", "False", "None", "async", "await",
        "in", "is", "not", "and", "or", "global", "nonlocal", "assert"
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until",
        "do", "done", "in", "function", "select", "time", "return", "exit", "echo",
        "export", "source", "alias", "cd", "ls", "grep", "cat", "chmod", "mkdir"
    )

    // Precompiled Regex Patterns
    private val STRING_DOUBLE_REGEX = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"")
    private val STRING_SINGLE_REGEX = Pattern.compile("'(\\\\.|[^'\\\\])*'")
    private val LINE_COMMENT_SLASH_REGEX = Pattern.compile("//.*")
    private val LINE_COMMENT_HASH_REGEX = Pattern.compile("#.*")
    private val BLOCK_COMMENT_REGEX = Pattern.compile("/\\*[\\s\\S]*?\\*/")
    private val NUMBER_REGEX = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFL]?\\b|\\b0x[0-9a-fA-F]+\\b")
    private val ANNOTATION_REGEX = Pattern.compile("@[A-Za-z0-9_]+")
    private val WORD_REGEX = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
    private val HTML_TAG_REGEX = Pattern.compile("</?[a-zA-Z0-9\\-]+(\\s+[^>]*?)?/?>")

    fun highlight(
        code: String,
        extension: String,
        colorScheme: ColorScheme
    ): AnnotatedString {
        if (code.isEmpty()) return buildAnnotatedString { }

        val ext = extension.lowercase().removePrefix(".")
        val isKotlin = ext in listOf("kt", "kts", "java")
        val isJs = ext in listOf("js", "jsx", "ts", "tsx")
        val isPython = ext == "py"
        val isShell = ext in listOf("sh", "bash", "zsh")
        val isJson = ext == "json"
        val isHtml = ext in listOf("html", "htm", "xml", "svg")

        val keywords = when {
            isKotlin -> KOTLIN_KEYWORDS
            isJs -> JS_KEYWORDS
            isPython -> PYTHON_KEYWORDS
            isShell -> SHELL_KEYWORDS
            else -> emptySet()
        }

        // Theme colors mapped cleanly to syntax tokens
        val keywordColor = colorScheme.primary
        val stringColor = Color(0xFF10B981) // Vibrant emerald green
        val numberColor = Color(0xFFF59E0B) // Amber
        val commentColor = Color(0xFF94A3B8) // Muted slate gray
        val annotationColor = Color(0xFFA855F7) // Purple
        val typeColor = Color(0xFF06B6D4) // Cyan
        val tagColor = colorScheme.primary

        return buildAnnotatedString {
            append(code)

            // Numbers
            val numMatcher = NUMBER_REGEX.matcher(code)
            while (numMatcher.find()) {
                addStyle(SpanStyle(color = numberColor), numMatcher.start(), numMatcher.end())
            }

            // HTML Tags
            if (isHtml) {
                val tagMatcher = HTML_TAG_REGEX.matcher(code)
                while (tagMatcher.find()) {
                    addStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold), tagMatcher.start(), tagMatcher.end())
                }
            }

            // Words (Keywords, Types)
            val wordMatcher = WORD_REGEX.matcher(code)
            while (wordMatcher.find()) {
                val word = wordMatcher.group()
                if (keywords.contains(word)) {
                    addStyle(
                        SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                        wordMatcher.start(),
                        wordMatcher.end()
                    )
                } else if (word.firstOrNull()?.isUpperCase() == true && word.length > 1) {
                    addStyle(
                        SpanStyle(color = typeColor),
                        wordMatcher.start(),
                        wordMatcher.end()
                    )
                }
            }

            // Annotations / Decorators (@Composable, @Override, @staticmethod)
            val annotMatcher = ANNOTATION_REGEX.matcher(code)
            while (annotMatcher.find()) {
                addStyle(
                    SpanStyle(color = annotationColor, fontWeight = FontWeight.Medium),
                    annotMatcher.start(),
                    annotMatcher.end()
                )
            }

            // Strings (Double quotes)
            val strDoubleMatcher = STRING_DOUBLE_REGEX.matcher(code)
            while (strDoubleMatcher.find()) {
                addStyle(SpanStyle(color = stringColor), strDoubleMatcher.start(), strDoubleMatcher.end())
            }

            // Strings (Single quotes)
            val strSingleMatcher = STRING_SINGLE_REGEX.matcher(code)
            while (strSingleMatcher.find()) {
                addStyle(SpanStyle(color = stringColor), strSingleMatcher.start(), strSingleMatcher.end())
            }

            // Comments (Lines // or #)
            val lineCommentMatcher = if (isPython || isShell) {
                LINE_COMMENT_HASH_REGEX.matcher(code)
            } else {
                LINE_COMMENT_SLASH_REGEX.matcher(code)
            }
            while (lineCommentMatcher.find()) {
                addStyle(SpanStyle(color = commentColor), lineCommentMatcher.start(), lineCommentMatcher.end())
            }

            // Block comments /* ... */
            if (!isPython && !isShell) {
                val blockCommentMatcher = BLOCK_COMMENT_REGEX.matcher(code)
                while (blockCommentMatcher.find()) {
                    addStyle(SpanStyle(color = commentColor), blockCommentMatcher.start(), blockCommentMatcher.end())
                }
            }
        }
    }
}
