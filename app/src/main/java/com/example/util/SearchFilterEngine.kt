package com.example.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/**
 * Universal Search & Filter Engine for LIC Advisor Pro
 * Provides production-quality multi-keyword search, text highlighting,
 * and standard empty states across all app screens.
 */
object SearchFilterEngine {

    /**
     * Production Multi-Keyword Search Matcher
     * Matches if EVERY keyword token in [query] is contained in the concatenated [fields] haystack.
     * Handles:
     * - Case insensitivity
     * - Whitespace normalization (extra spaces ignored)
     * - Partial substring matching across multiple fields
     * - Multi-word tokens (e.g., "Pintu 8942" or "Bhubaneswar 9876")
     */
    fun matchesQuery(query: String, fields: List<String?>): Boolean {
        if (query.isBlank()) return true

        val cleanQuery = query.trim().lowercase().replace("\\s+".toRegex(), " ")
        val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return true

        val haystack = fields.filterNotNull().joinToString(" ").lowercase().replace("\\s+".toRegex(), " ")
        return tokens.all { token -> haystack.contains(token) }
    }

    /**
     * Variadic convenience overload for [matchesQuery]
     */
    fun matchesQuery(query: String, vararg fields: String?): Boolean {
        return matchesQuery(query, fields.toList())
    }

    /**
     * Visual Text Highlighter for Search Results
     * Highlights matching keyword tokens in [text] with a contrasting color and bold style.
     */
    fun highlightMatches(
        text: String,
        query: String,
        highlightColor: Color = Color(0xFFFBBF24) // Amber Yellow Glow
    ): AnnotatedString {
        if (query.isBlank() || text.isBlank()) {
            return AnnotatedString(text)
        }

        val cleanQuery = query.trim().lowercase().replace("\\s+".toRegex(), " ")
        val tokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return AnnotatedString(text)
        }

        val textLower = text.lowercase()
        val spanRanges = mutableListOf<IntRange>()

        for (token in tokens) {
            var startIndex = 0
            while (startIndex < text.length) {
                val index = textLower.indexOf(token, startIndex)
                if (index == -1) break
                spanRanges.add(index until (index + token.length))
                startIndex = index + token.length
            }
        }

        if (spanRanges.isEmpty()) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            append(text)
            for (range in spanRanges) {
                addStyle(
                    style = SpanStyle(
                        color = highlightColor,
                        fontWeight = FontWeight.Bold,
                        background = highlightColor.copy(alpha = 0.25f)
                    ),
                    start = range.first,
                    end = range.last + 1
                )
            }
        }
    }

    /**
     * Safely parses LocalDate from String or returns null
     */
    fun parseLocalDateSafe(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateStr.trim())
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Search History Manager - Persists top 10 recent search queries
     */
    object HistoryManager {
        private const val PREFS_NAME = "lic_advisor_search_history"
        private const val KEY_HISTORY = "recent_queries"
        private const val MAX_HISTORY = 10

        fun getHistory(context: android.content.Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_HISTORY, "") ?: ""
            if (jsonString.isBlank()) return emptyList()
            return jsonString.split("|||").filter { it.isNotBlank() }
        }

        fun saveSearchQuery(context: android.content.Context, query: String) {
            val clean = query.trim()
            if (clean.length < 2) return

            val current = getHistory(context).toMutableList()
            current.remove(clean)
            current.add(0, clean)

            val trimmed = current.take(MAX_HISTORY)
            val jsonString = trimmed.joinToString("|||")

            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_HISTORY, jsonString).apply()
        }

        fun clearHistory(context: android.content.Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_HISTORY).apply()
        }
    }
}

/**
 * Standard Professional Empty State for Search and Filter Results
 * Implements Requirement 6: "No matching records found."
 */
@Composable
fun NoMatchingRecordsEmptyState(
    modifier: Modifier = Modifier,
    title: String = "No matching records found.",
    subtitle: String = "Try adjusting your search keywords or clearing active filters.",
    query: String = "",
    onResetFilters: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("empty_state_container"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (query.isNotBlank()) Icons.Default.SearchOff else Icons.Default.FilterListOff,
                    contentDescription = "No Results",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (query.isNotBlank()) "No records matching \"$query\"" else subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center
                )
            )

            if (onResetFilters != null) {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onResetFilters,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF60A5FA)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f)),
                    modifier = Modifier.testTag("reset_filters_button")
                ) {
                    Text(text = "Clear Search & Filters", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
