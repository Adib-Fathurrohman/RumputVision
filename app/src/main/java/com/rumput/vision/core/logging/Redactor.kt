package com.rumput.vision.core.logging

/** Removes secrets from text before it is logged. Applied by [AppLogger] to every message. */
object Redactor {
    private const val MASK = "***"

    private val patterns: List<Pair<Regex, String>> = listOf(
        // user:password@ in any URL (camera RTSP URLs from Frigate config contain these).
        Regex("""(\b[a-zA-Z][a-zA-Z0-9+.-]*://)[^/\s@"']+@""") to "$1$MASK@",
        // Authorization: Bearer xxx / Basic xxx
        Regex("""(?i)(authorization\s*[:=]\s*)(bearer|basic)\s+[^\s,;"']+""") to "$1$2 $MASK",
        Regex("""(?i)\bbearer\s+[A-Za-z0-9._~+/=-]+""") to "Bearer $MASK",
        // Cookie / Set-Cookie values, and Frigate's JWT cookie anywhere.
        Regex("""(?i)((?:set-)?cookie\s*[:=]\s*)[^\r\n]+""") to "$1$MASK",
        Regex("""(?i)(frigate_token=)[^;\s"']+""") to "$1$MASK",
        // JSON or form fields that carry secrets.
        Regex("""(?i)("(?:password|token|access_token|secret)"\s*:\s*")[^"]*(")""") to "$1$MASK$2",
        Regex("""(?i)\b((?:password|token|access_token|secret)=)[^&\s"']+""") to "$1$MASK",
        // Bare JWTs.
        Regex("""\beyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\b""") to MASK,
    )

    fun redact(text: String): String = patterns.fold(text) { acc, (regex, replacement) -> acc.replace(regex, replacement) }
}
