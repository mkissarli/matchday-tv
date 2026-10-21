package dev.matchday.domain.catalog

import java.text.Normalizer
import java.util.Locale

/**
 * Provider names are noisy: "FR: Canal+ UHD HEVC", "|AR| beIN SPORTS 1 HD",
 * "Sky Sports F1 [4K]", "UK| BBC ONE +1". The normaliser strips presentation tags,
 * leaves the channel identity, and produces a stable merge key.
 */
object NameNormalizer {

    private val COUNTRY_PREFIX = Regex("""^\s*(?:\|[A-Z]{2,4}\||[A-Z]{2,4}\s*[:|\-])\s*""")
    private val BRACKET_TAG = Regex("""[\[\(\{][^\]\)\}]{0,24}[\]\)\}]""")
    private val QUALITY = Regex("""(?i)\b(?:8K|4K|UHD|2160P|3840P|FHD|1080P|HD|720P|SD|RAW)\b""")
    private val CODEC = Regex("""(?i)\b(?:HEVC|H\.?265|H\.?264|AVC|X265|X264|MPEG-?2|MPEG-?4)\b""")
    private val AUDIO = Regex("""(?i)\b(?:AAC|AC3|EAC3|DD|DDP|ATMOS|5\.1|7\.1|2\.0|MULTI|MONO)\b""")
    private val PROVIDER_NOISE = Regex("""(?i)\b(?:VIP|BACKUP|BK|SERVER|SERVER\s*\d+|FEED|EVENT|PPV)\b""")
    private val TIME_SHIFT = Regex("""(?i)\s*\+\s*\d+\s*$""")
    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")
    private val SPACES = Regex("""\s+""")

    fun normalize(raw: String): String {
        var value = raw.trim()
        value = COUNTRY_PREFIX.replace(value, " ")
        value = BRACKET_TAG.replace(value, " ")
        value = QUALITY.replace(value, " ")
        value = CODEC.replace(value, " ")
        value = AUDIO.replace(value, " ")
        value = PROVIDER_NOISE.replace(value, " ")
        value = TIME_SHIFT.replace(value, " ")
        value = Normalizer.normalize(value, Normalizer.Form.NFD)
        value = value.replace(Regex("""\p{Mn}+"""), "")
        value = value.lowercase(Locale.ROOT)
        value = value.replace(NON_ALNUM, " ")
        value = SPACES.replace(value, " ").trim()
        return value
    }

    /** Human-facing name with provider presentation tags removed, but casing kept. */
    fun display(raw: String): String {
        var value = raw.trim()
        value = COUNTRY_PREFIX.replace(value, " ")
        value = BRACKET_TAG.replace(value, " ")
        value = QUALITY.replace(value, " ")
        value = CODEC.replace(value, " ")
        value = AUDIO.replace(value, " ")
        value = PROVIDER_NOISE.replace(value, " ")
        value = TIME_SHIFT.replace(value, " ")
        value = SPACES.replace(value, " ").trim()
        return value.ifBlank { raw.trim() }
    }

    fun quality(raw: String): ParsedQuality {
        val tags = buildSet {
            if (Regex("""(?i)\b8K\b""").containsMatchIn(raw)) add("8K")
            if (Regex("""(?i)\b(?:4K|UHD|2160P|3840P)\b""").containsMatchIn(raw)) add("4K")
            if (Regex("""(?i)\b(?:FHD|1080P)\b""").containsMatchIn(raw)) add("FHD")
            if (Regex("""(?i)\b(?:HD|720P)\b""").containsMatchIn(raw)) add("HD")
            if (Regex("""(?i)\bSD\b""").containsMatchIn(raw)) add("SD")
        }
        val tier = when {
            "8K" in tags -> QualityTier.EIGHT_K
            "4K" in tags -> QualityTier.UHD
            "FHD" in tags -> QualityTier.FHD
            "HD" in tags -> QualityTier.HD
            "SD" in tags -> QualityTier.SD
            else -> QualityTier.UNKNOWN
        }
        val codec = when {
            Regex("""(?i)\b(?:HEVC|H\.?265|X265)\b""").containsMatchIn(raw) -> "HEVC"
            Regex("""(?i)\b(?:H\.?264|AVC|X264)\b""").containsMatchIn(raw) -> "H.264"
            else -> null
        }
        return ParsedQuality(tier, codec, tags)
    }
}
