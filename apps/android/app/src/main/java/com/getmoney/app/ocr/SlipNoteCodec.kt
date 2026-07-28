package com.getmoney.app.ocr

data class SlipNoteParts(
    val direction: TransferDirection? = null,
    val fromName: String? = null,
    val toName: String? = null,
    val memo: String? = null,
)

object SlipNoteCodec {
    private val fromOnly = Regex("""^จาก:\s*(.+)$""")
    private val toOnly = Regex("""^ถึง:\s*(.+)$""")
    private val both = Regex("""^จาก:\s*(.+?)\s*→\s*ถึง:\s*(.+)$""")

    fun compose(
        direction: TransferDirection?,
        fromName: String?,
        toName: String?,
        memo: String?,
    ): String? {
        val lines = mutableListOf<String>()
        direction?.let { lines += it.labelTh() }
        routeLine(fromName, toName)?.let { lines += it }
        memo?.trim()?.takeIf { it.isNotEmpty() }?.let { lines += it }
        return lines.joinToString("\n").takeIf { it.isNotBlank() }
    }

    fun parse(raw: String?): SlipNoteParts {
        if (raw.isNullOrBlank()) return SlipNoteParts()
        val lines = raw.lines()
        var index = 0
        var direction: TransferDirection? = null
        var fromName: String? = null
        var toName: String? = null

        TransferDirection.fromLabelTh(lines[0])?.let {
            direction = it
            index = 1
        }

        if (index < lines.size) {
            val route = parseRouteLine(lines[index])
            if (route != null) {
                fromName = route.first
                toName = route.second
                index += 1
            }
        }

        val memo = lines.drop(index).joinToString("\n").trim().takeIf { it.isNotEmpty() }

        // Legacy: no structured header → entire string is memo
        if (direction == null && fromName == null && toName == null) {
            return SlipNoteParts(memo = raw.trim())
        }

        return SlipNoteParts(
            direction = direction,
            fromName = fromName,
            toName = toName,
            memo = memo,
        )
    }

    fun displayTransferLine(parts: SlipNoteParts): String? {
        val direction = parts.direction?.labelTh()
        val route = routeLine(parts.fromName, parts.toName)
        return when {
            direction != null && route != null -> "$direction · $route"
            direction != null -> direction
            route != null -> route
            else -> null
        }
    }

    private fun routeLine(fromName: String?, toName: String?): String? {
        val from = fromName?.trim()?.takeIf { it.isNotEmpty() }
        val to = toName?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            from != null && to != null -> "จาก: $from → ถึง: $to"
            from != null -> "จาก: $from"
            to != null -> "ถึง: $to"
            else -> null
        }
    }

    private fun parseRouteLine(line: String): Pair<String?, String?>? {
        both.matchEntire(line.trim())?.let { match ->
            return match.groupValues[1].trim() to match.groupValues[2].trim()
        }
        fromOnly.matchEntire(line.trim())?.let { match ->
            return match.groupValues[1].trim() to null
        }
        toOnly.matchEntire(line.trim())?.let { match ->
            return null to match.groupValues[1].trim()
        }
        return null
    }
}
