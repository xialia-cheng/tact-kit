package com.xialia.tactkit.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class LanDiagInfo(
  val transportLabel: String,
  val localIps: List<String>,
  val gatewayIp: String,
  val dnsServers: List<String>,
  val wifiSsid: String?,
  val wifiRssiDbm: Int?,
  val wifiLinkSpeedMbps: Int?,
  val wifiFrequencyMhz: Int?,
  val wifiBandLabel: String?,
)

data class PortProbeResult(
  val port: Int,
  val serviceHint: String,
  val isOpen: Boolean,
  val latencyMs: Long,
)

data class DnsSslReport(
  val domain: String,
  val resolvedIps: List<String>,
  val certSubject: String?,
  val certIssuer: String?,
  val validFrom: String?,
  val validUntil: String?,
  val daysRemaining: Long?,
  val sha256Fingerprint: String?,
  val sanList: List<String>,
  val sslError: String? = null,
)

data class WifiChannelScanEntry(
  val ssid: String,
  val bssid: String,
  val frequencyMhz: Int,
  val band: String,
  val channel: Int,
  val rssiDbm: Int,
)

object NetworkDiagEngine {

  val COMMON_PORTS =
    listOf(
      21 to "FTP",
      22 to "SSH",
      53 to "DNS",
      80 to "HTTP",
      443 to "HTTPS",
      3000 to "Web Dev",
      3306 to "MySQL",
      5432 to "PostgreSQL",
      6379 to "Redis",
      8080 to "HTTP Proxy",
      8443 to "HTTPS Alt",
      8765 to "TactKit Share",
    )

  fun readLanDiagInfo(context: Context): LanDiagInfo {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    val active = cm?.activeNetwork
    val caps = cm?.getNetworkCapabilities(active)
    val linkProps = cm?.getLinkProperties(active)

    val transport =
      when {
        caps == null -> "未连接网络"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi 无线局域网"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线以太网"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝移动网络"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN 隧道"
        else -> "其他网络"
      }

    val localIps =
      linkProps?.linkAddresses
        ?.mapNotNull { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.mapNotNull { it.hostAddress }
        ?.ifEmpty { LanShareServer.resolveLocalIpv4Addresses() }
        ?: LanShareServer.resolveLocalIpv4Addresses()

    val gatewayIp =
      linkProps?.routes
        ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
        ?.gateway
        ?.hostAddress
        ?: "—"

    val dnsServers =
      linkProps?.dnsServers
        ?.mapNotNull { it.hostAddress }
        .orEmpty()

    @Suppress("DEPRECATION")
    val wifiInfo = runCatching { wm?.connectionInfo }.getOrNull()
    val hasFineLoc =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val rawSsid =
      if (hasFineLoc) {
        wifiInfo?.ssid?.trim('"')?.takeUnless { it == "<unknown ssid>" || it.isBlank() }
      } else {
        null
      }

    val freq = wifiInfo?.frequency?.takeIf { it > 0 }
    val bandLabel = freq?.let { freqMhzToChannel(it).first }
    val rssi = wifiInfo?.rssi?.takeIf { it > -120 && it < 0 }
    val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 }

    return LanDiagInfo(
      transportLabel = transport,
      localIps = localIps,
      gatewayIp = gatewayIp,
      dnsServers = dnsServers,
      wifiSsid = rawSsid,
      wifiRssiDbm = rssi,
      wifiLinkSpeedMbps = linkSpeed,
      wifiFrequencyMhz = freq,
      wifiBandLabel = bandLabel,
    )
  }

  suspend fun probeLatencyMs(host: String, port: Int = 80, timeoutMs: Int = 1800): Long? =
    withContext(Dispatchers.IO) {
      val target = host.trim()
      if (target.isEmpty()) return@withContext null
      val start = System.nanoTime()
      val ok = runCatching {
        Socket().use { sock ->
          sock.connect(InetSocketAddress(target, port), timeoutMs)
          true
        }
      }.getOrElse {
        runCatching { InetAddress.getByName(target).isReachable(timeoutMs) }.getOrDefault(false)
      }
      if (!ok) null else ((System.nanoTime() - start) / 1_000_000L).coerceAtLeast(1L)
    }

  suspend fun probePorts(
    host: String,
    ports: List<Pair<Int, String>> = COMMON_PORTS,
    timeoutMs: Int = 900,
  ): List<PortProbeResult> =
    withContext(Dispatchers.IO) {
      val cleanHost = host.trim()
      if (cleanHost.isEmpty()) return@withContext emptyList()
      coroutineScope {
        ports.map { (port, hint) ->
          async {
            val t0 = System.nanoTime()
            val open = runCatching {
              Socket().use { sock ->
                sock.connect(InetSocketAddress(cleanHost, port), timeoutMs)
                true
              }
            }.getOrDefault(false)
            val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            PortProbeResult(port = port, serviceHint = hint, isOpen = open, latencyMs = ms)
          }
        }.awaitAll()
      }
    }

  suspend fun inspectDnsAndSsl(domainInput: String, port: Int = 443): DnsSslReport =
    withContext(Dispatchers.IO) {
      val domain =
        domainInput.trim()
          .removePrefix("https://")
          .removePrefix("http://")
          .substringBefore("/")
          .substringBefore(":")
      require(domain.isNotBlank()) { "请输入有效域名" }

      val ips =
        runCatching {
          InetAddress.getAllByName(domain).mapNotNull { it.hostAddress }.distinct()
        }.getOrElse { emptyList() }

      val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
      try {
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val cert =
          (factory.createSocket() as SSLSocket).use { sock ->
            sock.soTimeout = 5000
            sock.connect(InetSocketAddress(domain, port), 5000)
            sock.startHandshake()
            sock.session.peerCertificates.firstOrNull() as? X509Certificate
          }
        if (cert == null) {
          return@withContext DnsSslReport(
            domain = domain,
            resolvedIps = ips,
            certSubject = null,
            certIssuer = null,
            validFrom = null,
            validUntil = null,
            daysRemaining = null,
            sha256Fingerprint = null,
            sanList = emptyList(),
            sslError = "未收到服务器 SSL 证书",
          )
        }
        val nowMs = System.currentTimeMillis()
        val daysLeft = (cert.notAfter.time - nowMs) / (24L * 60L * 60L * 1000L)
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val fingerprint = digest.joinToString(":") { "%02X".format(it) }
        val sans =
          runCatching {
            cert.subjectAlternativeNames
              ?.mapNotNull { it.getOrNull(1)?.toString() }
              .orEmpty()
              .take(12)
          }.getOrDefault(emptyList())

        DnsSslReport(
          domain = domain,
          resolvedIps = ips,
          certSubject = cert.subjectX500Principal.name,
          certIssuer = cert.issuerX500Principal.name,
          validFrom = fmt.format(Date(cert.notBefore.time)),
          validUntil = fmt.format(Date(cert.notAfter.time)),
          daysRemaining = daysLeft,
          sha256Fingerprint = fingerprint,
          sanList = sans,
        )
      } catch (e: Exception) {
        DnsSslReport(
          domain = domain,
          resolvedIps = ips,
          certSubject = null,
          certIssuer = null,
          validFrom = null,
          validUntil = null,
          daysRemaining = null,
          sha256Fingerprint = null,
          sanList = emptyList(),
          sslError = e.message ?: "TLS 握手失败",
        )
      }
    }

  fun freqMhzToChannel(freqMhz: Int): Pair<String, Int> {
    return when {
      freqMhz in 2412..2472 -> "2.4 GHz" to ((freqMhz - 2412) / 5 + 1)
      freqMhz == 2484 -> "2.4 GHz" to 14
      freqMhz in 5170..5885 -> "5 GHz" to ((freqMhz - 5000) / 5)
      freqMhz in 5955..7115 -> "6 GHz" to ((freqMhz - 5950) / 5)
      else -> "未知频段" to 0
    }
  }

  @Suppress("DEPRECATION")
  fun readWifiChannelScan(context: Context): List<WifiChannelScanEntry> {
    val hasLoc =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasLoc) return emptyList()
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      ?: return emptyList()
    runCatching { wm.startScan() }
    return runCatching {
      wm.scanResults.orEmpty().mapNotNull { sr ->
        val (band, ch) = freqMhzToChannel(sr.frequency)
        if (ch <= 0) return@mapNotNull null
        val name =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sr.wifiSsid?.toString()?.trim('"').orEmpty().ifBlank { "隐藏网络" }
          } else {
            sr.SSID.orEmpty().ifBlank { "隐藏网络" }
          }
        WifiChannelScanEntry(
          ssid = name,
          bssid = sr.BSSID.orEmpty(),
          frequencyMhz = sr.frequency,
          band = band,
          channel = ch,
          rssiDbm = sr.level,
        )
      }.sortedByDescending { it.rssiDbm }
    }.getOrDefault(emptyList())
  }
}
