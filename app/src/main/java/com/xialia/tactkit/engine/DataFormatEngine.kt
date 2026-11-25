package com.xialia.tactkit.engine

import java.io.StringReader
import java.io.StringWriter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

enum class DataFormat(val label: String, val ext: String, val mime: String) {
  CSV("CSV", "csv", "text/csv"),
  JSON("JSON", "json", "application/json"),
  YAML("YAML", "yaml", "application/x-yaml"),
  XML("XML", "xml", "application/xml"),
}

data class CsvTableData(
  val headers: List<String>,
  val rows: List<List<String>>,
)

data class SyntaxValidationResult(
  val isValid: Boolean,
  val message: String,
)

object DataFormatEngine {

  private val jsonPretty = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
  }
  private val jsonCompact = Json {
    prettyPrint = false
    ignoreUnknownKeys = true
  }

  fun validate(raw: String, format: DataFormat): SyntaxValidationResult {
    if (raw.isBlank()) return SyntaxValidationResult(false, "内容为空")
    return runCatching {
      parseToGeneric(raw, format)
      SyntaxValidationResult(true, "${format.label} 语法合法")
    }.getOrElse { err ->
      SyntaxValidationResult(false, "${format.label} 语法错误：${err.message ?: "格式无法解析"}")
    }
  }

  fun convert(
    raw: String,
    from: DataFormat,
    to: DataFormat,
    pretty: Boolean = true,
  ): Result<String> = runCatching {
    require(raw.isNotBlank()) { "输入内容不能为空" }
    val parsed = parseToGeneric(raw, from)
    serializeFromGeneric(parsed, to, pretty)
  }

  fun formatOrMinify(
    raw: String,
    format: DataFormat,
    pretty: Boolean,
  ): Result<String> = convert(raw, format, format, pretty)

  fun parseCsvTable(raw: String): CsvTableData {
    val allRows = parseCsvRecords(raw)
    if (allRows.isEmpty()) {
      return CsvTableData(headers = listOf("列1", "列2"), rows = listOf(listOf("", "")))
    }
    val headers = allRows.first().mapIndexed { idx, h -> h.ifEmpty { "column_${idx + 1}" } }
    val width = headers.size.coerceAtLeast(1)
    val bodyRows =
      allRows.drop(1).map { row ->
        List(width) { col -> row.getOrElse(col) { "" } }
      }
    return CsvTableData(headers = headers, rows = bodyRows)
  }

  fun serializeCsvTable(table: CsvTableData): String {
    val width = table.headers.size.coerceAtLeast(1)
    val records = buildList {
      add(table.headers)
      table.rows.forEach { row ->
        add(List(width) { col -> row.getOrElse(col) { "" } })
      }
    }
    return writeCsvRecords(records)
  }

  internal fun parseToGeneric(raw: String, format: DataFormat): Any? {
    return when (format) {
      DataFormat.JSON -> jsonElementToAny(jsonCompact.parseToJsonElement(raw.trim()))
      DataFormat.YAML -> {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))
        normalizeYamlValue(yaml.load<Any>(raw))
      }
      DataFormat.CSV -> csvToGenericListOfMaps(raw)
      DataFormat.XML -> parseXmlToGeneric(raw)
    }
  }

  internal fun serializeFromGeneric(value: Any?, format: DataFormat, pretty: Boolean): String {
    return when (format) {
      DataFormat.JSON -> {
        val elem = anyToJsonElement(value)
        val serializer = if (pretty) jsonPretty else jsonCompact
        serializer.encodeToString(JsonElement.serializer(), elem)
      }
      DataFormat.YAML -> {
        val opts =
          DumperOptions().apply {
            defaultFlowStyle =
              if (pretty) DumperOptions.FlowStyle.BLOCK else DumperOptions.FlowStyle.FLOW
            isPrettyFlow = pretty
            indent = 2
            isAllowUnicode = true
          }
        Yaml(opts).dump(value ?: emptyMap<String, Any?>()).trimEnd()
      }
      DataFormat.CSV -> genericToCsv(value)
      DataFormat.XML -> genericToXml(value, pretty)
    }
  }

  private fun jsonElementToAny(el: JsonElement): Any? {
    return when (el) {
      is JsonNull -> null
      is JsonPrimitive -> {
        if (el.isString) {
          el.content
        } else {
          el.booleanOrNull ?: el.longOrNull ?: el.doubleOrNull ?: el.content
        }
      }
      is JsonArray -> el.map { jsonElementToAny(it) }
      is JsonObject -> el.entries.associate { (k, v) -> k to jsonElementToAny(v) }
    }
  }

  private fun anyToJsonElement(value: Any?): JsonElement {
    return when (value) {
      null -> JsonNull
      is Boolean -> JsonPrimitive(value)
      is Number -> JsonPrimitive(value)
      is String -> JsonPrimitive(value)
      is Map<*, *> ->
        JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) })
      is Iterable<*> -> JsonArray(value.map { anyToJsonElement(it) })
      else -> JsonPrimitive(value.toString())
    }
  }

  private fun normalizeYamlValue(value: Any?): Any? {
    return when (value) {
      null -> null
      is Map<*, *> ->
        value.entries.associate { (k, v) -> k.toString() to normalizeYamlValue(v) }
      is Iterable<*> -> value.map { normalizeYamlValue(it) }
      else -> value
    }
  }

  private fun csvToGenericListOfMaps(raw: String): List<Map<String, String>> {
    val records = parseCsvRecords(raw)
    if (records.isEmpty()) return emptyList()
    val headers = records.first().mapIndexed { idx, h -> h.ifBlank { "col_${idx + 1}" } }
    return records.drop(1).map { row ->
      linkedMapOf<String, String>().apply {
        headers.forEachIndexed { idx, header ->
          put(header, row.getOrElse(idx) { "" })
        }
      }
    }
  }

  private fun genericToCsv(value: Any?): String {
    val unpacked = unwrapSingleRootForTabular(value)
    val rowsList: List<Map<String, Any?>> =
      when (unpacked) {
        is List<*> -> {
          val maps = unpacked.filterIsInstance<Map<*, *>>()
          if (maps.size == unpacked.size && maps.isNotEmpty()) {
            maps.map { m -> m.entries.associate { (k, v) -> k.toString() to v } }
          } else {
            unpacked.map { item -> mapOf("value" to item) }
          }
        }
        is Map<*, *> -> listOf(unpacked.entries.associate { (k, v) -> k.toString() to v })
        null -> emptyList()
        else -> listOf(mapOf("value" to unpacked))
      }

    if (rowsList.isEmpty()) return ""
    val headers = linkedSetOf<String>()
    rowsList.forEach { row -> headers.addAll(row.keys) }
    val headerList = headers.toList()

    val records = buildList {
      add(headerList)
      rowsList.forEach { row ->
        add(headerList.map { h -> formatScalarForCell(row[h]) })
      }
    }
    return writeCsvRecords(records)
  }

  private fun unwrapSingleRootForTabular(value: Any?): Any? {
    var cur = value
    repeat(2) {
      if (cur is Map<*, *> && cur.size == 1) {
        val child = cur.values.first()
        if (child is List<*> || child is Map<*, *>) {
          cur = child
        }
      }
    }
    return cur
  }

  private fun formatScalarForCell(v: Any?): String {
    return when (v) {
      null -> ""
      is Map<*, *>, is Iterable<*> ->
        jsonCompact.encodeToString(JsonElement.serializer(), anyToJsonElement(v))
      else -> v.toString()
    }
  }

  internal fun parseCsvRecords(raw: String): List<List<String>> {
    val clean = raw.removePrefix("\uFEFF")
    if (clean.isBlank()) return emptyList()

    val rows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    val cell = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < clean.length) {
      val ch = clean[i]
      if (inQuotes) {
        if (ch == '"') {
          if (i + 1 < clean.length && clean[i + 1] == '"') {
            cell.append('"')
            i += 2
            continue
          } else {
            inQuotes = false
          }
        } else {
          cell.append(ch)
        }
      } else {
        when (ch) {
          '"' -> inQuotes = true
          ',' -> {
            currentRow.add(cell.toString())
            cell.clear()
          }
          '\r' -> {
            if (i + 1 < clean.length && clean[i + 1] == '\n') i++
            currentRow.add(cell.toString())
            cell.clear()
            rows.add(currentRow)
            currentRow = mutableListOf()
          }
          '\n' -> {
            currentRow.add(cell.toString())
            cell.clear()
            rows.add(currentRow)
            currentRow = mutableListOf()
          }
          else -> cell.append(ch)
        }
      }
      i++
    }
    if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
      currentRow.add(cell.toString())
      rows.add(currentRow)
    }
    return rows.filterNot { row -> row.size == 1 && row[0].isEmpty() }
  }

  private fun writeCsvRecords(records: List<List<String>>): String {
    return records.joinToString("\n") { row ->
      row.joinToString(",") { cell ->
        val needsQuotes =
          cell.contains(',') || cell.contains('"') || cell.contains('\n') || cell.contains('\r')
        if (needsQuotes) {
          "\"${cell.replace("\"", "\"\"")}\""
        } else {
          cell
        }
      }
    }
  }

  private fun parseXmlToGeneric(raw: String): Map<String, Any?> {
    val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
    val parser = factory.newPullParser()
    parser.setInput(StringReader(raw.trim()))

    var event = parser.eventType
    while (event != XmlPullParser.START_TAG && event != XmlPullParser.END_DOCUMENT) {
      event = parser.next()
    }
    require(event == XmlPullParser.START_TAG) { "未找到有效 XML 根节点" }
    val rootName = parser.name
    val rootValue = readXmlElementValue(parser)
    return mapOf(rootName to rootValue)
  }

  private fun readXmlElementValue(parser: XmlPullParser): Any? {
    val map = linkedMapOf<String, Any?>()
    for (i in 0 until parser.attributeCount) {
      map["@${parser.getAttributeName(i)}"] = parser.getAttributeValue(i)
    }
    val textBuilder = StringBuilder()

    while (true) {
      val event = parser.next()
      when (event) {
        XmlPullParser.TEXT -> textBuilder.append(parser.text.orEmpty())
        XmlPullParser.START_TAG -> {
          val childName = parser.name
          val childVal = readXmlElementValue(parser)
          val existing = map[childName]
          when {
            existing == null -> map[childName] = childVal
            existing is MutableList<*> -> {
              @Suppress("UNCHECKED_CAST")
              (existing as MutableList<Any?>).add(childVal)
            }
            else -> map[childName] = mutableListOf(existing, childVal)
          }
        }
        XmlPullParser.END_TAG -> {
          val text = textBuilder.toString().trim()
          return if (map.isEmpty()) {
            text
          } else {
            if (text.isNotEmpty()) map["#text"] = text
            map
          }
        }
        XmlPullParser.END_DOCUMENT -> error("XML 标签未闭合")
      }
    }
  }

  private fun genericToXml(value: Any?, pretty: Boolean): String {
    val writer = StringWriter()
    val factory = XmlPullParserFactory.newInstance()
    val serializer: XmlSerializer = factory.newSerializer()
    serializer.setOutput(writer)
    if (pretty) {
      runCatching {
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)
      }
    }
    serializer.startDocument("UTF-8", true)

    when (value) {
      is Map<*, *> -> {
        if (value.size == 1 && !value.keys.first().toString().startsWith("@")) {
          val rootTag = sanitizeXmlTag(value.keys.first().toString())
          writeXmlNode(serializer, rootTag, value.values.first())
        } else {
          writeXmlNode(serializer, "root", value)
        }
      }
      is List<*> -> {
        serializer.startTag(null, "rows")
        value.forEach { item -> writeXmlNode(serializer, "item", item) }
        serializer.endTag(null, "rows")
      }
      else -> writeXmlNode(serializer, "root", value)
    }

    serializer.endDocument()
    serializer.flush()
    val rawXml = writer.toString()
    return if (pretty) rawXml.trim() else rawXml.replace(Regex(">\\s+<"), "><").trim()
  }

  private fun writeXmlNode(serializer: XmlSerializer, tagName: String, value: Any?) {
    val cleanTag = sanitizeXmlTag(tagName)
    when (value) {
      is Map<*, *> -> {
        serializer.startTag(null, cleanTag)
        value.entries.forEach { (k, v) ->
          val key = k.toString()
          if (key.startsWith("@") && key.length > 1 && v != null) {
            serializer.attribute(null, sanitizeXmlTag(key.substring(1)), v.toString())
          }
        }
        val textContent = value["#text"]?.toString()
        if (!textContent.isNullOrEmpty()) {
          serializer.text(textContent)
        }
        value.entries.forEach { (k, v) ->
          val key = k.toString()
          if (!key.startsWith("@") && key != "#text") {
            if (v is List<*>) {
              v.forEach { item -> writeXmlNode(serializer, key, item) }
            } else {
              writeXmlNode(serializer, key, v)
            }
          }
        }
        serializer.endTag(null, cleanTag)
      }
      is List<*> -> {
        serializer.startTag(null, cleanTag)
        value.forEach { item -> writeXmlNode(serializer, "item", item) }
        serializer.endTag(null, cleanTag)
      }
      else -> {
        serializer.startTag(null, cleanTag)
        if (value != null) serializer.text(value.toString())
        serializer.endTag(null, cleanTag)
      }
    }
  }

  private fun sanitizeXmlTag(raw: String): String {
    val trimmed = raw.trim().replace(Regex("[^a-zA-Z0-9_.-]"), "_")
    return if (trimmed.isEmpty() || trimmed[0].isDigit() || trimmed.startsWith("xml", ignoreCase = true)) {
      "item_$trimmed"
    } else {
      trimmed
    }
  }
}
