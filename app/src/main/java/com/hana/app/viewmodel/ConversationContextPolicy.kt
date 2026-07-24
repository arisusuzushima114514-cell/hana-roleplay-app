package com.hana.app.viewmodel

internal fun splitIndexForRecentUserRounds(roles: List<String>, roundLimit: Int): Int {
    var userRounds = 0
    for (index in roles.indices.reversed()) {
        if (roles[index] == "user") {
            userRounds += 1
            if (userRounds > roundLimit.coerceAtLeast(1)) return index + 1
        }
    }
    return 0
}

internal fun selectSummaryBatchIndexes(contentLengths: List<Int>, charBudget: Int): IntRange? {
    var selectedCount = 0
    var usedChars = 0
    for (length in contentLengths) {
        val safeLength = length.coerceAtLeast(0)
        if (usedChars + safeLength > charBudget.coerceAtLeast(1)) break
        usedChars += safeLength
        selectedCount += 1
    }
    return if (selectedCount == 0) null else 0 until selectedCount
}
