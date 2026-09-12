package com.example.agent.rules

import java.util.regex.Pattern

/**
 * Result of evaluating a condition against a context.
 */
data class ConditionEvaluationResult(
    val matched: Boolean,
    val specificity: Int,
    val reason: String
)

/**
 * High-performance, production-grade condition evaluator for the GVONE Rule Engine.
 * Supports all 18 comparison operators, composite boolean logic (AND, OR, NOT),
 * nested trees, dynamic path lookups, and deterministic specificity scoring.
 */
class ConditionEvaluator {

    /**
     * Evaluates a rule condition tree against the given RuleContext.
     */
    fun evaluate(condition: RuleCondition, context: RuleContext): ConditionEvaluationResult {
        return when (condition) {
            is SingleCondition -> evaluateSingle(condition, context)
            is CompositeCondition -> evaluateComposite(condition, context)
        }
    }

    private fun evaluateSingle(condition: SingleCondition, context: RuleContext): ConditionEvaluationResult {
        val actualValue = context.resolvePath(condition.field)
        val expectedValue = condition.value
        val op = condition.operator

        val matched = when (op) {
            ConditionOperator.EQUALS -> areEqual(actualValue, expectedValue)
            ConditionOperator.NOT_EQUALS -> !areEqual(actualValue, expectedValue)
            ConditionOperator.CONTAINS -> stringOrCollectionContains(actualValue, expectedValue)
            ConditionOperator.NOT_CONTAINS -> !stringOrCollectionContains(actualValue, expectedValue)
            ConditionOperator.STARTS_WITH -> {
                val str = actualValue?.toString() ?: ""
                val prefix = expectedValue?.toString() ?: ""
                str.startsWith(prefix, ignoreCase = true)
            }
            ConditionOperator.ENDS_WITH -> {
                val str = actualValue?.toString() ?: ""
                val suffix = expectedValue?.toString() ?: ""
                str.endsWith(suffix, ignoreCase = true)
            }
            ConditionOperator.MATCHES, ConditionOperator.REGEX -> {
                val str = actualValue?.toString() ?: ""
                val pattern = expectedValue?.toString() ?: ""
                try {
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(str).find()
                } catch (e: Exception) {
                    false
                }
            }
            ConditionOperator.GREATER_THAN -> compareNumbers(actualValue, expectedValue) { it > 0 }
            ConditionOperator.LESS_THAN -> compareNumbers(actualValue, expectedValue) { it < 0 }
            ConditionOperator.GREATER_OR_EQUAL -> compareNumbers(actualValue, expectedValue) { it >= 0 }
            ConditionOperator.LESS_OR_EQUAL -> compareNumbers(actualValue, expectedValue) { it <= 0 }
            ConditionOperator.EXISTS -> isExisting(actualValue)
            ConditionOperator.NOT_EXISTS -> !isExisting(actualValue)
            ConditionOperator.IN -> isContainedIn(actualValue, expectedValue)
            ConditionOperator.NOT_IN -> !isContainedIn(actualValue, expectedValue)
            ConditionOperator.IS_TRUE -> isTruthy(actualValue)
            ConditionOperator.IS_FALSE -> !isTruthy(actualValue)
        }

        val reason = if (matched) {
            "Condition matched: '${condition.field}' ($actualValue) ${op.symbol} '$expectedValue'"
        } else {
            "Condition unmet: '${condition.field}' ($actualValue) not ${op.symbol} '$expectedValue'"
        }

        return ConditionEvaluationResult(
            matched = matched,
            specificity = if (matched) 1 else 0,
            reason = reason
        )
    }

    private fun evaluateComposite(condition: CompositeCondition, context: RuleContext): ConditionEvaluationResult {
        var totalSpecificity = 0
        val reasons = mutableListOf<String>()

        // 1. Evaluate 'all' (AND) with short-circuiting
        if (condition.all.isNotEmpty()) {
            for (sub in condition.all) {
                val res = evaluate(sub, context)
                if (!res.matched) {
                    return ConditionEvaluationResult(
                        matched = false,
                        specificity = 0,
                        reason = "AND condition failed on: ${res.reason}"
                    )
                }
                totalSpecificity += res.specificity
                reasons.add(res.reason)
            }
        }

        // 2. Evaluate 'any' (OR) with short-circuiting
        if (condition.any.isNotEmpty()) {
            var anyMatched = false
            var bestSubSpecificity = 0
            val subReasons = mutableListOf<String>()

            for (sub in condition.any) {
                val res = evaluate(sub, context)
                if (res.matched) {
                    anyMatched = true
                    bestSubSpecificity = maxOf(bestSubSpecificity, res.specificity)
                    subReasons.add(res.reason)
                }
            }

            if (!anyMatched) {
                return ConditionEvaluationResult(
                    matched = false,
                    specificity = 0,
                    reason = "OR condition failed: none of the ${condition.any.size} branches matched"
                )
            }
            totalSpecificity += bestSubSpecificity
            reasons.add("ANY matched: [${subReasons.joinToString("; ")}]")
        }

        // 3. Evaluate 'not' (NOT)
        if (condition.not != null) {
            val res = evaluate(condition.not, context)
            if (res.matched) {
                return ConditionEvaluationResult(
                    matched = false,
                    specificity = 0,
                    reason = "NOT condition failed: negated condition was true (${res.reason})"
                )
            }
            totalSpecificity += 1
            reasons.add("NOT condition passed")
        }

        return ConditionEvaluationResult(
            matched = true,
            specificity = totalSpecificity.coerceAtLeast(1),
            reason = reasons.joinToString(", ")
        )
    }

    private fun areEqual(actual: Any?, expected: Any?): Boolean {
        if (actual == null && expected == null) return true
        if (actual == null || expected == null) return false

        // Compare as numbers if possible
        val numActual = toDoubleOrNull(actual)
        val numExpected = toDoubleOrNull(expected)
        if (numActual != null && numExpected != null) {
            return numActual == numExpected
        }

        // Compare as booleans if possible
        val boolActual = toBooleanOrNull(actual)
        val boolExpected = toBooleanOrNull(expected)
        if (boolActual != null && boolExpected != null) {
            return boolActual == boolExpected
        }

        // Compare as case-insensitive strings
        return actual.toString().trim().equals(expected.toString().trim(), ignoreCase = true)
    }

    private fun stringOrCollectionContains(actual: Any?, needle: Any?): Boolean {
        if (actual == null || needle == null) return false
        val needleStr = needle.toString().trim()

        return when (actual) {
            is Collection<*> -> {
                actual.any { it?.toString()?.trim()?.equals(needleStr, ignoreCase = true) == true }
            }
            is Array<*> -> {
                actual.any { it?.toString()?.trim()?.equals(needleStr, ignoreCase = true) == true }
            }
            is Map<*, *> -> {
                actual.containsKey(needleStr) || actual.containsValue(needleStr)
            }
            else -> {
                actual.toString().contains(needleStr, ignoreCase = true)
            }
        }
    }

    private fun compareNumbers(actual: Any?, expected: Any?, predicate: (Int) -> Boolean): Boolean {
        val n1 = toDoubleOrNull(actual) ?: return false
        val n2 = toDoubleOrNull(expected) ?: return false
        return predicate(n1.compareTo(n2))
    }

    private fun isExisting(value: Any?): Boolean {
        if (value == null) return false
        if (value is String && value.isBlank()) return false
        if (value is Collection<*> && value.isEmpty()) return false
        if (value is Map<*, *> && value.isEmpty()) return false
        return true
    }

    private fun isContainedIn(item: Any?, container: Any?): Boolean {
        if (item == null || container == null) return false
        val itemStr = item.toString().trim()

        return when (container) {
            is Collection<*> -> container.any { it?.toString()?.trim()?.equals(itemStr, ignoreCase = true) == true }
            is Array<*> -> container.any { it?.toString()?.trim()?.equals(itemStr, ignoreCase = true) == true }
            is String -> {
                // Check comma-separated values or substring
                container.split(",").map { it.trim() }.any { it.equals(itemStr, ignoreCase = true) }
            }
            else -> false
        }
    }

    private fun isTruthy(value: Any?): Boolean {
        if (value == null) return false
        if (value is Boolean) return value
        val str = value.toString().trim().lowercase()
        return str == "true" || str == "1" || str == "yes" || str == "on"
    }

    private fun toDoubleOrNull(value: Any?): Double? {
        if (value == null) return null
        if (value is Number) return value.toDouble()
        return value.toString().trim().toDoubleOrNull()
    }

    private fun toBooleanOrNull(value: Any?): Boolean? {
        if (value == null) return null
        if (value is Boolean) return value
        val str = value.toString().trim().lowercase()
        return when (str) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    companion object {
        val global: ConditionEvaluator by lazy { ConditionEvaluator() }

        fun evaluate(condition: RuleCondition, context: RuleContext): Boolean {
            return global.evaluate(condition, context).matched
        }

        fun evaluateCondition(condition: RuleCondition, context: RuleContext): Boolean {
            return global.evaluate(condition, context).matched
        }
    }
}
