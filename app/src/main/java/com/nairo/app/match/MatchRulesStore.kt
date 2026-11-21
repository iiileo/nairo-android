package com.nairo.app.match

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class MatchRulesSnapshot(
    val rules: AppMatchRules,
    val fromServer: Boolean,
    val rawJson: String = "",
)

@Singleton
class MatchRulesStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _snapshot = MutableStateFlow(loadSnapshot())
    val snapshot: StateFlow<MatchRulesSnapshot> = _snapshot.asStateFlow()

    fun current(): AppMatchRules = _snapshot.value.rules

    fun save(rules: AppMatchRules, rawJson: String) {
        prefs.edit()
            .putString(KEY_JSON, rawJson)
            .putInt(KEY_VERSION, rules.version)
            .putString(KEY_UPDATED_AT, rules.updatedAt)
            .apply()
        _snapshot.value = MatchRulesSnapshot(
            rules = rules,
            fromServer = true,
            rawJson = rawJson,
        )
    }

    fun cachedVersion(): Int = current().version

    private fun loadSnapshot(): MatchRulesSnapshot {
        val raw = prefs.getString(KEY_JSON, null)?.trim().orEmpty()
        if (raw.isEmpty()) {
            return MatchRulesSnapshot(rules = AppMatchRules.DEFAULT, fromServer = false)
        }
        val parsed = runCatching { AppMatchRules.fromJson(raw) }.getOrElse { AppMatchRules.DEFAULT }
        return MatchRulesSnapshot(rules = parsed, fromServer = true, rawJson = raw)
    }

    companion object {
        private const val PREFS = "nairo_match_rules"
        private const val KEY_JSON = "json"
        private const val KEY_VERSION = "version"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}
