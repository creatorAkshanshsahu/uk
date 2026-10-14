package com.akshansh.aktv

data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val category: String,
    val url: String,
    val userAgent: String? = null,
    val referrer: String? = null,
)

data class ChannelRow(val title: String, val items: List<Channel>)

/** Parses standard M3U / M3U8 IPTV playlists. */
object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name = ""
        var logo: String? = null
        var group = ""
        var ua: String? = null
        var ref: String? = null
        var have = false

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    have = true
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    logo = attrs["tvg-logo"]?.takeIf { it.startsWith("http") }
                    group = attrs["group-title"].orEmpty()
                    val comma = line.indexOf(',', maxOf(line.lastIndexOf('"'), 0))
                    name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    if (name.isEmpty()) name = attrs["tvg-name"].orEmpty()
                    ua = null
                    ref = null
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val kv = line.substringAfter(':')
                    val v = kv.substringAfter('=', "")
                    when (kv.substringBefore('=').lowercase()) {
                        "http-user-agent" -> ua = v
                        "http-referrer", "http-referer" -> ref = v
                    }
                }
                line.startsWith("#") -> Unit
                have -> {
                    val parts = line.split('|', limit = 2)
                    val url = parts[0].trim()
                    if (parts.size > 1) {
                        parts[1].split('&').forEach {
                            val v = it.substringAfter('=', "")
                            when (it.substringBefore('=').lowercase()) {
                                "user-agent" -> ua = v
                                "referer", "referrer" -> ref = v
                            }
                        }
                    }
                    if (url.startsWith("http") && name.isNotBlank()) {
                        out.add(
                            Channel(
                                id = (name + url).hashCode().toString(),
                                name = name,
                                logo = logo,
                                category = Categorizer.of(name, group),
                                url = url,
                                userAgent = ua,
                                referrer = ref,
                            )
                        )
                    }
                    have = false
                }
            }
        }
        return out.distinctBy { it.url }
    }
}

/** Sorts channels into Netflix-style rows, even when the playlist has one big "General" group. */
object Categorizer {
    val order = listOf(
        "News", "Movies", "Entertainment", "Music", "Kids", "Sports",
        "Devotional", "Regional", "Facts & Lifestyle", "More Channels",
    )
    private val generic = setOf("", "general", "undefined", "other", "others", "india", "uncategorized", "all")

    private val rules: List<Pair<String, List<String>>> = listOf(
        "Kids" to listOf("kids", "cartoon", "nick", "pogo", "disney", "hungama", "toon", "chutti", "baby", "sonic"),
        "Sports" to listOf("sport", "cricket", "wwe", "fancode", "eurosport", " ten 1", " ten 2", " ten 3"),
        "Music" to listOf("music", "mtv", "9xm", "zing", "mastiii", "hits", "radio", "9x ", "channel v"),
        "Devotional" to listOf("bhakti", "devotional", "aastha", "sanskar", "satsang", "darshan", "shubh", "divya", "gurbani", "sadhna", "ishwar", "peace of mind"),
        "News" to listOf("news", " tak", "abp", "ndtv", "republic", "tv9", "times now", "mirror now", "cnbc", "sansad", "lok sabha", "rajya sabha", "wion", "bloomberg", "cnn", "bbc", "al jazeera", "et now"),
        "Movies" to listOf("movie", "cinema", "film", "flix", " max", "pictures", "gold", "utv", "b4u", "cineplex", "picture"),
        "Facts & Lifestyle" to listOf("discovery", "geographic", "nat geo", "history", "tlc", "animal", "travel", "food", "fashion", "epic", "lifestyle", "science"),
        "Regional" to listOf("tamil", "telugu", "kannada", "malayalam", "bangla", "bengali", "marathi", "gujarati", "punjabi", "odia", "oriya", "assam", "bhojpuri", "sun tv", "kalaignar", "asianet", "gemini", "vijay", "suvarna", "maa ", "etv", "kairali", "manorama", "mazhavil"),
        "Entertainment" to listOf("zee", "star plus", "star bharat", "colors", "sony", "sab", "&tv", "dangal", "shemaroo", "big magic", "doordarshan", "dd national", "dd india", "life ok", "bindass", "pop"),
    )

    fun of(name: String, group: String): String {
        val g = group.trim()
        if (g.lowercase() !in generic) {
            // Use the playlist's own group when it is meaningful; normalise common ones.
            val n = g.lowercase()
            return order.firstOrNull { it.lowercase() == n } ?: g.replaceFirstChar { it.uppercase() }
        }
        val n = " ${name.lowercase()} "
        for ((cat, keys) in rules) if (keys.any { n.contains(it) }) return cat
        return "More Channels"
    }

    fun buildRows(all: List<Channel>, favIds: Set<String>, recentIds: List<String>): List<ChannelRow> {
        val rows = ArrayList<ChannelRow>()
        val byId = all.associateBy { it.id }
        val favs = all.filter { it.id in favIds }
        if (favs.isNotEmpty()) rows.add(ChannelRow("My Favourites", favs))
        val recents = recentIds.mapNotNull { byId[it] }
        if (recents.isNotEmpty()) rows.add(ChannelRow("Continue Watching", recents))
        val grouped = all.groupBy { it.category }
        order.forEach { c -> grouped[c]?.let { rows.add(ChannelRow(c, it)) } }
        grouped.keys.filter { it !in order }.sorted().forEach { rows.add(ChannelRow(it, grouped.getValue(it))) }
        return rows
    }
}
