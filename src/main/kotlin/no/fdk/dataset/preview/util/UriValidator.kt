package no.fdk.dataset.preview.util

import no.fdk.dataset.preview.service.UrlException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// DNS cache to prevent repeated lookups and potential DNS rebinding
private val dnsCache = ConcurrentHashMap<String, List<String>>()
private val dnsCacheExpiry = ConcurrentHashMap<String, Long>()
private const val DNS_CACHE_TTL_SECONDS = 300L // 5 minutes
private val ALLOWED_SCHEMES = setOf("https")
private const val DEFAULT_HTTPS_PORT = 443
private val SUSPICIOUS_HOST_PATTERNS =
    listOf(
        "localhost",
        "127.",
        "0.0.0.0",
        "169.254.",
        "metadata",
        "instance-data",
        "169.254.169.254", // AWS, Google Cloud, and Azure metadata
        "100.100.100.200", // Alibaba Cloud metadata
        "192.0.0.192", // Oracle Cloud metadata
    )
private val KUBERNETES_HOST_PATTERNS =
    listOf(
        "kubernetes.default.svc",
        ".svc.cluster.local",
    )
private val HOSTNAME_REGEX =
    Regex(
        "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))*$",
    )

fun URI.validate() {
    val host = this.host ?: throw UrlException("Invalid URL format")
    val scheme = this.scheme?.lowercase() ?: throw UrlException("Invalid URL format")
    requireAllowedScheme(scheme)
    requireAllowedPort(this.port)
    requireValidHostname(host)
    requireNoSuspiciousHostPatterns(host)
    requireNoKubernetesHostPatterns(host)

    val resolvedIps = resolveHostIPsWithCache(host)
    requireNoPrivateOrInternalIps(resolvedIps)
    requireStableDnsResolution(host, resolvedIps)
}

private fun requireAllowedScheme(scheme: String) {
    if (scheme !in ALLOWED_SCHEMES) {
        throw UrlException("Unsafe URL scheme not allowed")
    }
}

private fun requireAllowedPort(port: Int) {
    if (port != -1 && port != DEFAULT_HTTPS_PORT) {
        throw UrlException("Non-standard port not allowed")
    }
}

private fun requireValidHostname(host: String) {
    if (!isValidHostname(host)) {
        throw UrlException("Invalid hostname format")
    }
}

private fun requireNoSuspiciousHostPatterns(host: String) {
    if (containsSuspiciousPatterns(host)) {
        throw UrlException("Suspicious hostname pattern not allowed")
    }
}

private fun requireNoKubernetesHostPatterns(host: String) {
    if (isKubernetesHost(host)) {
        throw UrlException("Internal service access not allowed")
    }
}

private fun requireNoPrivateOrInternalIps(resolvedIps: List<String>) {
    if (resolvedIps.any { isPrivateOrInternal(it) }) {
        throw UrlException("Internal network access not allowed")
    }
}

private fun requireStableDnsResolution(host: String, resolvedIps: List<String>) {
    val recheckIps = resolveHostIPsWithCache(host)
    if (resolvedIps.toSet() != recheckIps.toSet()) {
        throw UrlException("DNS rebinding attack detected")
    }
}

private fun resolveHostIPsWithCache(host: String): List<String> {
    val now = System.currentTimeMillis()
    val cached = dnsCache[host]
    val expiry = dnsCacheExpiry[host] ?: 0L

    if (cached != null && now < expiry) {
        return cached
    }

    val resolved = resolveHostIPs(host)
    dnsCache[host] = resolved
    dnsCacheExpiry[host] = now + TimeUnit.SECONDS.toMillis(DNS_CACHE_TTL_SECONDS)
    return resolved
}

private fun resolveHostIPs(host: String): List<String> = try {
    InetAddress.getAllByName(host).map { it.hostAddress }
} catch (e: UnknownHostException) {
    throw UrlException("Hostname cannot be resolved")
}

private fun isValidHostname(hostname: String): Boolean {
    if (hostname.isEmpty() || hostname.length > 253) return false

    // Check for IPv6 address in bracket notation
    if (hostname.startsWith("[") && hostname.endsWith("]")) {
        val ipv6Content = hostname.substring(1, hostname.length - 1)
        return isValidIPv6(ipv6Content)
    }

    return HOSTNAME_REGEX.matches(hostname)
}

private fun isValidIPv6(ipv6: String): Boolean = try {
    InetAddress.getByName(ipv6) is Inet6Address
} catch (e: Exception) {
    false
}

private fun containsSuspiciousPatterns(hostname: String): Boolean =
    SUSPICIOUS_HOST_PATTERNS.any { hostname.contains(it, ignoreCase = true) }

private fun isKubernetesHost(host: String): Boolean = KUBERNETES_HOST_PATTERNS.any { host.contains(it, ignoreCase = true) }

private fun isPrivateOrInternal(ip: String): Boolean {
    val address = InetAddress.getByName(ip)

    return if (address is Inet4Address) {
        isPrivateIPv4(address)
    } else {
        isPrivateIPv6(address as Inet6Address)
    }
}

private fun isPrivateIPv4(address: Inet4Address): Boolean {
    return try {
        val hostAddress = address.hostAddress.lowercase()

        return hostAddress.startsWith("10.") ||
            hostAddress.startsWith("192.168.") ||
            (hostAddress.startsWith("172.") && hostAddress.split(".")[1].toInt() in 16..31) ||
            hostAddress.startsWith("127.") ||
            hostAddress.startsWith("169.254.")
    } catch (e: UnknownHostException) {
        false
    }
}

private fun isPrivateIPv6(address: Inet6Address): Boolean {
    return try {
        val hostAddress = address.hostAddress.lowercase()

        if (address.isLoopbackAddress) return true

        if (hostAddress.startsWith("fd") || hostAddress.startsWith("fc") || hostAddress.startsWith("fe80")) return true

        if (address.isSiteLocalAddress) return true

        false
    } catch (e: UnknownHostException) {
        false
    }
}
