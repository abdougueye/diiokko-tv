package com.diokko.player.data.parser

import android.util.Xml
import com.diokko.player.data.models.EpgProgram
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Individual program from XMLTV
 */
data class XmltvProgram(
    val channelId: String,  // Maps to Channel.epgChannelId
    val title: String,
    val description: String?,
    val startTime: Long,
    val endTime: Long,
    val iconUrl: String?
)

/**
 * XMLTV parser for EPG data.
 * 
 * Parses EPG data from XMLTV format commonly used by IPTV providers.
 * The EPG URL format is typically: http://server/xmltv.php?username=X&password=Y
 * 
 * This parser is streaming and memory-efficient - it reads directly from an InputStream
 * without loading the entire XML into memory.
 */
@Singleton
class XmltvParser @Inject constructor() {
    
    companion object {
        private const val TAG = "XmltvParser"
        // XMLTV Standard Date Format: 20250106035500 +0100
        private const val DATE_PATTERN = "yyyyMMddHHmmss Z"
    }
    
    /**
     * Build EPG URL for Xtream Codes provider
     */
    fun buildEpgUrl(serverUrl: String, username: String, password: String): String {
        val baseUrl = serverUrl.removeSuffix("/")
        return "$baseUrl/xmltv.php?username=$username&password=$password"
    }
    
    /**
     * Parses an XMLTV InputStream and returns a list of programs.
     * This method is streaming and memory efficient.
     * Filters to only include programs from past 24 hours to 1 week ahead.
     */
    fun parse(inputStream: InputStream): List<XmltvProgram> {
        val programs = mutableListOf<XmltvProgram>()
        val parser = Xml.newPullParser()
        
        // Create formatters ONCE here to avoid GC churn (20k programs = 40k formatter allocations otherwise)
        val dateFormatter = SimpleDateFormat(DATE_PATTERN, Locale.US)
        val fallbackFormatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        
        // Time window filter: 24 hours ago to 1 week ahead
        val now = System.currentTimeMillis()
        val minTime = now - (24 * 60 * 60 * 1000L)  // 24 hours ago
        val maxTime = now + (7 * 24 * 60 * 60 * 1000L)  // 1 week ahead
        
        var programmeCount = 0
        var parseErrors = 0
        var filteredOut = 0
        var lastLogTime = System.currentTimeMillis()
        val LOG_INTERVAL = 5000L // Log every 5 seconds

        android.util.Log.i(TAG, "Starting XMLTV parsing with time filter...")

        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (parser.name == "programme") {
                        programmeCount++
                        
                        // Log progress every 5 seconds
                        val nowLog = System.currentTimeMillis()
                        if (nowLog - lastLogTime > LOG_INTERVAL) {
                            android.util.Log.i(TAG, "Parsing progress: $programmeCount total, ${programs.size} kept, $filteredOut filtered...")
                            lastLogTime = nowLog
                        }
                        
                        try {
                            val program = parseProgramme(parser, dateFormatter, fallbackFormatter)
                            if (program != null) {
                                // Filter by time window
                                if (program.endTime >= minTime && program.startTime <= maxTime) {
                                    programs.add(program)
                                } else {
                                    filteredOut++
                                }
                            }
                        } catch (e: Exception) {
                            parseErrors++
                            if (parseErrors <= 5) {
                                android.util.Log.w(TAG, "Error parsing programme #$programmeCount: ${e.message}")
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
            
            android.util.Log.i(TAG, "XMLTV parsing complete: $programmeCount total, ${programs.size} kept, $filteredOut filtered (time), $parseErrors errors")
            
        } catch (e: XmlPullParserException) {
            android.util.Log.e(TAG, "XML Parsing Error after $programmeCount programmes", e)
        } catch (e: IOException) {
            android.util.Log.e(TAG, "IO Error during parsing after $programmeCount programmes", e)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "OUT OF MEMORY after $programmeCount programmes! Returning ${programs.size} parsed so far.")
            return programs
        }

        return programs
    }

    private fun parseProgramme(
        parser: XmlPullParser,
        formatter: SimpleDateFormat,
        fallbackFormatter: SimpleDateFormat
    ): XmltvProgram? {
        // 1. Extract Attributes
        val channelId = parser.getAttributeValue(null, "channel") ?: return null
        val startStr = parser.getAttributeValue(null, "start")
        val stopStr = parser.getAttributeValue(null, "stop")

        var title: String? = null
        var desc: String? = null
        var icon: String? = null

        // 2. Parse Child Tags
        while (parser.next() != XmlPullParser.END_TAG || parser.name != "programme") {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when (parser.name) {
                "title" -> title = readText(parser)
                "desc" -> desc = readText(parser)
                "icon" -> {
                    icon = parser.getAttributeValue(null, "src")
                    skip(parser)
                }
                else -> skip(parser)
            }
        }

        // 3. Convert Dates using passed formatters
        val startTime = parseDateTime(startStr, formatter, fallbackFormatter)
        val endTime = parseDateTime(stopStr, formatter, fallbackFormatter)

        // 4. Return Valid Program
        return if (title != null && startTime > 0 && endTime > 0) {
            XmltvProgram(
                channelId = channelId,
                title = title,
                description = desc,
                startTime = startTime,
                endTime = endTime,
                iconUrl = icon
            )
        } else {
            null
        }
    }
    
    private fun parseDateTime(
        dateStr: String?,
        formatter: SimpleDateFormat,
        fallbackFormatter: SimpleDateFormat
    ): Long {
        if (dateStr.isNullOrBlank()) return 0L
        
        return try {
            formatter.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            // Try without timezone
            try {
                val cleanStr = dateStr.trim().take(14)
                fallbackFormatter.parse(cleanStr)?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.START_TAG -> depth++
            }
        }
    }
    
    /**
     * Convert XMLTV programs to EpgProgram entities.
     * Requires a map of epgChannelId -> channelDatabaseId to link programs to channels.
     */
    fun convertToEpgPrograms(
        programs: List<XmltvProgram>,
        channelIdMap: Map<String, Long>  // epgChannelId -> database channel ID
    ): List<EpgProgram> {
        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000)  // Keep programs from last 24 hours
        val oneWeekAhead = now + (7 * 24 * 60 * 60 * 1000)  // Keep programs up to 1 week ahead
        
        return programs
            .filter { program ->
                // Only keep programs within time window
                program.endTime > oneDayAgo && program.startTime < oneWeekAhead
            }
            .mapNotNull { program ->
                val channelDbId = channelIdMap[program.channelId]
                if (channelDbId != null) {
                    EpgProgram(
                        channelId = channelDbId,
                        title = program.title,
                        description = program.description,
                        startTime = program.startTime,
                        endTime = program.endTime,
                        iconUrl = program.iconUrl
                    )
                } else {
                    // Channel not found in database - skip this program
                    null
                }
            }
    }
}
