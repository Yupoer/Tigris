package com.leslie.tvhome

internal object VpnBackgroundPolicy {
    const val INTERVAL_MS = 120_000L

    enum class WhiteList(val title: String, val field: String) {
        KILL("KillWhiteList", "mKillWhiteList"),
        ONE_KEY("OneKeyCleanWhiteList", "mOneKeyCleanWhiteList"),
    }

    private const val BEGIN = "=================== StrategyConfigInfo Begin ==================="
    private const val END = "=================== StrategyConfigInfo End   ==================="
    private val packageName = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

    fun validPackage(value: String): Boolean = packageName.matches(value)

    fun due(lastAttempt: Long?, now: Long): Boolean =
        lastAttempt == null || now < lastAttempt || now - lastAttempt >= INTERVAL_MS

    fun additions(dump: String, packages: Set<String>): Map<WhiteList, List<String>>? {
        if (packages.any { !validPackage(it) }) return null
        val lines = dump.lineSequence().map { it.trim() }.toList()
        val begin = lines.indexOf(BEGIN)
        val end = lines.indexOf(END)
        if (begin < 0 || end <= begin) return null
        val config = lines.subList(begin + 1, end)
        val result = linkedMapOf<WhiteList, List<String>>()
        for (list in WhiteList.entries) {
            val heading = config.withIndex().filter { it.value == list.title }.singleOrNull()?.index ?: return null
            val members = config.drop(heading + 1).takeWhile { it.startsWith("${list.title}:") }
                .map { it.removePrefix("${list.title}:").trim() }
            if (members.any { !validPackage(it) }) return null
            val missing = (packages - members.toSet()).sorted()
            if (missing.isNotEmpty()) result[list] = missing
        }
        return result
    }
}
