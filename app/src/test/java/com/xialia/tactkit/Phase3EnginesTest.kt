package com.xialia.tactkit

import com.xialia.tactkit.data.repository.AiHubRepository
import com.xialia.tactkit.engine.ArchiveEngine
import com.xialia.tactkit.engine.CsvTableData
import com.xialia.tactkit.engine.DataFormat
import com.xialia.tactkit.engine.DataFormatEngine
import com.xialia.tactkit.engine.DetectedArchiveType
import com.xialia.tactkit.engine.DocxEngine
import com.xialia.tactkit.engine.PackArchiveFormat
import com.xialia.tactkit.util.SpecialtyFormatParser
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase3EnginesTest {

  @get:Rule
  val tempFolder = TemporaryFolder()

  @Test
  fun `ai hub normalizes endpoints and parses http errors cleanly`() {
    assertEquals(
      "https://api.deepseek.com/v1/chat/completions",
      AiHubRepository.normalizeChatCompletionsEndpoint("https://api.deepseek.com"),
    )
    assertEquals(
      "https://api.deepseek.com/v1/chat/completions",
      AiHubRepository.normalizeChatCompletionsEndpoint("https://api.deepseek.com/v1/"),
    )
    assertEquals(
      "https://open.bigmodel.cn/api/paas/v4/chat/completions",
      AiHubRepository.normalizeChatCompletionsEndpoint("https://open.bigmodel.cn/api/paas/v4"),
    )
    assertEquals(
      "https://custom.example.com/v1/chat/completions",
      AiHubRepository.normalizeChatCompletionsEndpoint("https://custom.example.com/v1/chat/completions"),
    )

    val parsedErr =
      AiHubRepository.formatHttpError(
        401,
        """{"error":{"message":"Incorrect API key provided","type":"invalid_request_error"}}""",
      )
    assertTrue(parsedErr.contains("HTTP 401"))
    assertTrue(parsedErr.contains("Incorrect API key provided"))
  }

  @Test
  fun `docx engine creates edits reads and extracts quick look preview`() {
    val bytes =
      DocxEngine.applyParagraphEditsToBytes(
        sourceBytes = null,
        editedParagraphs = listOf("简工坊标题", "第一段正文内容", "第二段：Apache POI 离线读写"),
      )
    assertTrue(bytes.isNotEmpty())

    val readBack = DocxEngine.readDocxBytes(bytes, "test.docx")
    assertEquals(3, readBack.paragraphs.size)
    assertEquals("简工坊标题", readBack.paragraphs[0].text)
    assertEquals("第二段：Apache POI 离线读写", readBack.paragraphs[2].text)

    val editedBytes =
      DocxEngine.applyParagraphEditsToBytes(
        sourceBytes = bytes,
        editedParagraphs = listOf("更新后标题", "仅保留两段"),
      )
    val editedReadBack = DocxEngine.readDocxBytes(editedBytes, "edited.docx")
    assertEquals(2, editedReadBack.paragraphs.size)
    assertEquals("更新后标题", editedReadBack.paragraphs[0].text)

    val quickText = DocxEngine.extractQuickLookText(ByteArrayInputStream(editedBytes))
    assertTrue(quickText.contains("更新后标题"))
    assertTrue(quickText.contains("仅保留两段"))
  }

  @Test
  fun `data format engine converts json yaml xml csv and validates syntax`() {
    val jsonInput = """[{"name":"简工坊","ver":"0.3.0"},{"name":"TactKit","ver":"0.3.0"}]"""
    assertTrue(DataFormatEngine.validate(jsonInput, DataFormat.JSON).isValid)
    assertFalse(DataFormatEngine.validate("{bad json", DataFormat.JSON).isValid)

    val yamlOut = DataFormatEngine.convert(jsonInput, DataFormat.JSON, DataFormat.YAML, pretty = true).getOrThrow()
    assertTrue(yamlOut.contains("简工坊"))

    val csvOut = DataFormatEngine.convert(yamlOut, DataFormat.YAML, DataFormat.CSV, pretty = true).getOrThrow()
    assertTrue(csvOut.contains("name,ver"))
    assertTrue(csvOut.contains("简工坊,0.3.0"))

    val xmlOut = DataFormatEngine.convert(csvOut, DataFormat.CSV, DataFormat.XML, pretty = true).getOrThrow()
    assertTrue(xmlOut.contains("<rows>"))
    assertTrue(xmlOut.contains("TactKit"))

    val table = DataFormatEngine.parseCsvTable("colA,colB\n\"hello, world\",123")
    assertEquals(listOf("colA", "colB"), table.headers)
    assertEquals("hello, world", table.rows[0][0])
    val serialized =
      DataFormatEngine.serializeCsvTable(
        CsvTableData(headers = listOf("A", "B"), rows = listOf(listOf("x,y", "z")))
      )
    assertTrue(serialized.contains("\"x,y\",z"))
  }

  @Test
  fun `archive engine packs inspects extracts zip7z targz tarxz and rejects rar and zipslip`() {
    val fileA = tempFolder.newFile("hello.txt").apply { writeText("Hello TactKit Archive!") }
    val fileB = tempFolder.newFile("note.md").apply { writeText("# Heading\nLocal first") }
    val inputs = listOf("docs/hello.txt" to fileA, "note.md" to fileB)

    // ZIP with AES-256 password
    val zipOut = File(tempFolder.root, "secure.zip")
    ArchiveEngine.packLocalFiles(inputs, zipOut, PackArchiveFormat.ZIP, zipPassword = "tactkit-secret")
    val zipInspect = ArchiveEngine.inspectArchiveFile(zipOut, "secure.zip")
    assertEquals(DetectedArchiveType.ZIP, zipInspect.archiveType)
    assertTrue(zipInspect.requiresPassword)
    assertEquals(2, zipInspect.entries.size)

    var extractedHello = ""
    ArchiveEngine.extractSingleFromFile(
      file = zipOut,
      type = DetectedArchiveType.ZIP,
      cleanTargetPath = "docs/hello.txt",
      password = "tactkit-secret",
    ) { stream ->
      extractedHello = stream.bufferedReader().readText()
    }
    assertEquals("Hello TactKit Archive!", extractedHello)

    // 7Z pack & extract all
    val sevenZOut = File(tempFolder.root, "bundle.7z")
    ArchiveEngine.packLocalFiles(inputs, sevenZOut, PackArchiveFormat.SEVEN_Z)
    val sevenZInspect = ArchiveEngine.inspectArchiveFile(sevenZOut, "bundle.7z")
    assertEquals(DetectedArchiveType.SEVEN_Z, sevenZInspect.archiveType)
    val extractedMap7z = mutableMapOf<String, String>()
    ArchiveEngine.extractAllFromFile(sevenZOut, "bundle.7z", DetectedArchiveType.SEVEN_Z) { path, stream ->
      extractedMap7z[path] = stream.bufferedReader().readText()
    }
    assertEquals("Hello TactKit Archive!", extractedMap7z["docs/hello.txt"])
    assertEquals("# Heading\nLocal first", extractedMap7z["note.md"])

    // TAR.GZ & TAR.XZ
    val tgzOut = File(tempFolder.root, "bundle.tar.gz")
    ArchiveEngine.packLocalFiles(inputs, tgzOut, PackArchiveFormat.TAR_GZ)
    val tgzInspect = ArchiveEngine.inspectArchiveFile(tgzOut, "bundle.tar.gz")
    assertEquals(DetectedArchiveType.TAR_GZ, tgzInspect.archiveType)
    assertEquals(2, tgzInspect.entries.size)

    val txzOut = File(tempFolder.root, "bundle.tar.xz")
    ArchiveEngine.packLocalFiles(inputs, txzOut, PackArchiveFormat.TAR_XZ)
    val txzInspect = ArchiveEngine.inspectArchiveFile(txzOut, "bundle.tar.xz")
    assertEquals(DetectedArchiveType.TAR_XZ, txzInspect.archiveType)
    assertEquals(2, txzInspect.entries.size)

    // RAR rejection + Zip Slip sanitization
    val rarCheck = runCatching { ArchiveEngine.detectArchiveType("legacy.rar") }
    assertTrue(rarCheck.isFailure)
    assertNull(ArchiveEngine.sanitizeRelativePath("../../etc/passwd"))
    assertNull(ArchiveEngine.sanitizeRelativePath("foo\\..\\..\\bar.txt"))
    assertEquals("dir/file.txt", ArchiveEngine.sanitizeRelativePath("/dir/./file.txt"))
  }

  @Test
  fun `specialty format parser handles ics vcf env toml log and empties keystore password array`() {
    val ics =
      """
      BEGIN:VCALENDAR
      BEGIN:VEVENT
      SUMMARY:TactKit 发版评审
      DTSTART:20261125T093000Z
      DTEND:20261125T103000Z
      LOCATION:线上会议室
      DESCRIPTION:检查 Release APK\n校验指纹
      END:VEVENT
      END:VCALENDAR
      """.trimIndent()
    val events = SpecialtyFormatParser.parseIcsEvents(ics)
    assertEquals(1, events.size)
    assertEquals("TactKit 发版评审", events[0].title)
    assertEquals("线上会议室", events[0].location)

    val vcf =
      """
      BEGIN:VCARD
      VERSION:2.1
      FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:=E5=BC=A0=E4=B8=89
      ORG:简工坊开源组
      TEL;CELL:13800138000
      EMAIL:demo@example.com
      END:VCARD
      """.trimIndent()
    val contacts = SpecialtyFormatParser.parseVCardContacts(vcf)
    assertEquals(1, contacts.size)
    assertEquals("张三", contacts[0].fullName)
    assertEquals("13800138000", contacts[0].phones.first())

    val envEntries =
      SpecialtyFormatParser.parseEnvOrTomlConfig(
        """
        APP_NAME=TactKit
        API_SECRET_TOKEN=super-secret
        """.trimIndent(),
        isToml = false,
      )
    assertEquals(2, envEntries.size)
    assertFalse(envEntries[0].isSensitive)
    assertTrue(envEntries[1].isSensitive)

    val logSummary =
      SpecialtyFormatParser.summarizeLogText(
        "INFO boot ok\nWARN slow disk\nERROR failed open\nFATAL crash"
      )
    assertEquals(4, logSummary.totalLines)
    assertEquals(2, logSummary.errorCount)
    assertEquals(1, logSummary.warnCount)

    // Verify in-memory empty PKCS12 keystore zeroes password CharArray
    val emptyPkcs12 = java.io.ByteArrayOutputStream()
    val ks = KeyStore.getInstance("PKCS12")
    val initPwd = "kspass".toCharArray()
    ks.load(null, initPwd)
    ks.store(emptyPkcs12, initPwd)

    val probeChars = "kspass".toCharArray()
    val certs =
      SpecialtyFormatParser.inspectKeyStoreInMemory(
        bytes = emptyPkcs12.toByteArray(),
        fileName = "empty.p12",
        passwordChars = probeChars,
      )
    assertTrue(certs.isEmpty())
    assertTrue(probeChars.all { it == '\u0000' })
  }
}
