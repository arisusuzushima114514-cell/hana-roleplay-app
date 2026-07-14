package com.hana.app.data.remote

import com.hana.app.data.db.entity.SavedModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ProviderAccountInfo(
    val providerName: String,
    val summary: String,
    val balanceText: String? = null,
    val quotaText: String? = null,
    val usedText: String? = null,
    val detailLines: List<String> = emptyList(),
    val dashboardUrl: String? = null,
    val detectedFrom: String = ""
)

class ProviderAccountService(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun queryAccount(provider: SavedModelEntity): Result<ProviderAccountInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(provider.apiKey.isNotBlank()) { "API Key 为空，无法查询余额" }
            val baseUrl = provider.baseUrl.trim().trimEnd('/')
            require(baseUrl.isNotBlank()) { "服务商地址为空，无法查询余额" }

            val probes = buildProbeCandidates(baseUrl)
            val errors = mutableListOf<String>()
            for (probe in probes) {
                val responseText = runProbe(baseUrl, provider.apiKey, probe)
                    .onFailure { errors += "${probe.label}: ${it.message.orEmpty()}" }
                    .getOrNull()
                    ?: continue
                parseProbeResponse(provider, baseUrl, probe, responseText)?.let { return@runCatching it }
                errors += "${probe.label}: 返回成功，但未识别到余额字段"
            }

            error(
                buildString {
                    append("这个服务商暂时没有识别到可用的余额/额度接口")
                    if (errors.isNotEmpty()) {
                        append("。已尝试: ")
                        append(errors.take(3).joinToString("；"))
                    }
                }
            )
        }
    }

    private fun buildProbeCandidates(baseUrl: String): List<ProbeCandidate> {
        val lower = baseUrl.lowercase(Locale.ROOT)
        val baseCandidates = mutableListOf<ProbeCandidate>()

        if (lower.contains("openrouter")) {
            baseCandidates += ProbeCandidate("OpenRouter Key", "/auth/key")
        }
        if (lower.contains("openai")) {
            baseCandidates += ProbeCandidate("OpenAI Credits", "/dashboard/billing/credit_grants")
            baseCandidates += ProbeCandidate("OpenAI Subscription", "/dashboard/billing/subscription")
        }

        baseCandidates += listOf(
            ProbeCandidate("Account Balance", "/account/balance"),
            ProbeCandidate("User Balance", "/user/balance"),
            ProbeCandidate("API Balance", "/api/user/balance"),
            ProbeCandidate("Account Info", "/account/info"),
            ProbeCandidate("User Info", "/user/info"),
            ProbeCandidate("Billing Info", "/billing/info"),
            ProbeCandidate("Credit Grants", "/dashboard/billing/credit_grants"),
            ProbeCandidate("Subscription", "/dashboard/billing/subscription"),
            ProbeCandidate("Auth Key", "/auth/key")
        )

        return baseCandidates.distinctBy { it.path }
    }

    private fun runProbe(baseUrl: String, apiKey: String, probe: ProbeCandidate): Result<String> {
        return runCatching {
            val url = buildProbeUrl(baseUrl, probe.path)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Content-Type", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${body.ifBlank { response.message }}")
                }
                body
            }
        }
    }

    private fun buildProbeUrl(baseUrl: String, path: String): String {
        val root = if (baseUrl.endsWith("/v1")) baseUrl.removeSuffix("/v1") else baseUrl
        return root + path
    }

    private fun parseProbeResponse(
        provider: SavedModelEntity,
        baseUrl: String,
        probe: ProbeCandidate,
        body: String
    ): ProviderAccountInfo? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        return parseOpenRouterStyle(provider, baseUrl, probe, json)
            ?: parseCreditGrantsStyle(provider, baseUrl, probe, json)
            ?: parseGenericBalanceStyle(provider, baseUrl, probe, json)
    }

    private fun parseOpenRouterStyle(
        provider: SavedModelEntity,
        baseUrl: String,
        probe: ProbeCandidate,
        json: JSONObject
    ): ProviderAccountInfo? {
        val data = json.optJSONObject("data") ?: return null
        val label = data.optString("label").ifBlank { provider.name }
        val usage = data.optDouble("usage", Double.NaN)
        val limit = data.optDouble("limit", Double.NaN)
        val isFreeTier = data.optBoolean("is_free_tier", false)
        val rateLimit = data.optJSONObject("rate_limit")
        val detailLines = buildList {
            if (isFreeTier) add("当前账号处于免费层")
            rateLimit?.optInt("requests")?.takeIf { it > 0 }?.let { add("请求速率上限: $it 次") }
            rateLimit?.optString("interval")?.takeIf { it.isNotBlank() }?.let { add("速率窗口: $it") }
        }
        return ProviderAccountInfo(
            providerName = label,
            summary = if (!usage.isNaN() && !limit.isNaN()) "已使用 ${formatMoney(usage)} / ${formatMoney(limit)}" else "已识别到账户信息",
            balanceText = if (!usage.isNaN() && !limit.isNaN()) formatMoney((limit - usage).coerceAtLeast(0.0)) else null,
            quotaText = if (!limit.isNaN()) formatMoney(limit) else null,
            usedText = if (!usage.isNaN()) formatMoney(usage) else null,
            detailLines = detailLines,
            dashboardUrl = guessDashboardUrl(baseUrl),
            detectedFrom = probe.label
        )
    }

    private fun parseCreditGrantsStyle(
        provider: SavedModelEntity,
        baseUrl: String,
        probe: ProbeCandidate,
        json: JSONObject
    ): ProviderAccountInfo? {
        val totalGranted = json.optDouble("total_granted", Double.NaN)
        val totalUsed = json.optDouble("total_used", Double.NaN)
        val totalAvailable = json.optDouble("total_available", Double.NaN)
        if (totalGranted.isNaN() && totalUsed.isNaN() && totalAvailable.isNaN()) return null
        return ProviderAccountInfo(
            providerName = provider.name,
            summary = "已识别到额度信息",
            balanceText = if (!totalAvailable.isNaN()) formatMoney(totalAvailable) else null,
            quotaText = if (!totalGranted.isNaN()) formatMoney(totalGranted) else null,
            usedText = if (!totalUsed.isNaN()) formatMoney(totalUsed) else null,
            dashboardUrl = guessDashboardUrl(baseUrl),
            detectedFrom = probe.label
        )
    }

    private fun parseGenericBalanceStyle(
        provider: SavedModelEntity,
        baseUrl: String,
        probe: ProbeCandidate,
        json: JSONObject
    ): ProviderAccountInfo? {
        val balance = findNumber(json, listOf("balance", "credit", "credits", "remaining_balance", "available_balance", "available_credit"))
        val quota = findNumber(json, listOf("quota", "limit", "total", "total_quota", "credit_limit"))
        val used = findNumber(json, listOf("used", "usage", "consumed", "spent", "used_credit"))
        if (balance == null && quota == null && used == null) return null

        val currency = findString(json, listOf("currency", "unit")).ifBlank { "USD" }
        val detailLines = buildList {
            findString(json, listOf("plan", "tier", "group")).takeIf { it.isNotBlank() }?.let { add("套餐: $it") }
            findString(json, listOf("expires_at", "expire_at", "expiry", "expiration_time")).takeIf { it.isNotBlank() }?.let { add("到期时间: $it") }
        }

        return ProviderAccountInfo(
            providerName = provider.name,
            summary = "已识别到余额/额度信息",
            balanceText = balance?.let { formatMoney(it, currency) },
            quotaText = quota?.let { formatMoney(it, currency) },
            usedText = used?.let { formatMoney(it, currency) },
            detailLines = detailLines,
            dashboardUrl = guessDashboardUrl(baseUrl),
            detectedFrom = probe.label
        )
    }

    private fun findNumber(json: JSONObject, keys: List<String>): Double? {
        keys.forEach { key ->
            if (json.has(key)) {
                val value = json.opt(key)
                when (value) {
                    is Number -> return value.toDouble()
                    is String -> value.toDoubleOrNull()?.let { return it }
                }
            }
        }
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            when (val value = json.opt(key)) {
                is JSONObject -> findNumber(value, keys)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val item = value.optJSONObject(i) ?: continue
                        findNumber(item, keys)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun findString(json: JSONObject, keys: List<String>): String {
        keys.forEach { key ->
            json.optString(key).takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun guessDashboardUrl(baseUrl: String): String? {
        val lower = baseUrl.lowercase(Locale.ROOT)
        return when {
            lower.contains("openrouter") -> "https://openrouter.ai/settings/credits"
            lower.contains("openai") -> "https://platform.openai.com/settings/organization/billing/overview"
            lower.contains("x.ai") || lower.contains("xai") -> "https://console.x.ai/"
            else -> null
        }
    }

    private fun formatMoney(value: Double, currency: String = "USD"): String {
        return when (currency.uppercase(Locale.ROOT)) {
            "USD" -> String.format(Locale.US, "$%.2f", value)
            "CNY", "RMB" -> String.format(Locale.CHINA, "%.2f 元", value)
            else -> String.format(Locale.US, "%.2f %s", value, currency.uppercase(Locale.ROOT))
        }
    }

    private data class ProbeCandidate(
        val label: String,
        val path: String
    )
}
