package no.fdk.dataset.preview.util

import no.fdk.dataset.preview.service.UrlException
import java.net.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// DNS cache to prevent repeated lookups and potential DNS rebinding
private val dnsCache = ConcurrentHashMap<String, List<String>>()
private val dnsCacheExpiry = ConcurrentHashMap<String, Long>()
private const val DNS_CACHE_TTL_SECONDS = 300L // 5 minutes

fun URI.validate() {
    val host = this.host ?: throw UrlException("Invalid URI host")
    val scheme = this.scheme?.lowercase() ?: throw UrlException("Invalid URI scheme")
    val port = this.port

    // Enhanced scheme validation
    if (scheme !in listOf("https")) {
        throw UrlException("Blocked unsafe URL scheme: $scheme")
    }

    // Port validation - only allow standard HTTPS port
    if (port != -1 && port != 443) {
        throw UrlException("Blocked non-standard port: $port")
    }

    // Hostname validation
    if (!isValidHostname(host)) {
        throw UrlException("Invalid hostname format: $host")
    }

    // Check for suspicious patterns
    if (containsSuspiciousPatterns(host)) {
        throw UrlException("Blocked suspicious hostname pattern: $host")
    }

    if (isKubernetes(host)) {
        throw UrlException("Blocked access to Kubernetes internal services: $host")
    }

    val resolvedIps = resolveHostIPsWithCache(host)
    if (resolvedIps.any { isPrivateOrInternal(it) }) {
        throw UrlException("Blocked internal network access: $host (${resolvedIps.joinToString()})")
    }

    // Double-check DNS resolution to prevent rebinding attacks
    val recheckIps = resolveHostIPsWithCache(host)
    if (resolvedIps.toSet() != recheckIps.toSet()) {
        throw UrlException("Possible DNS Rebinding attack detected: ${resolvedIps.joinToString()} -> ${recheckIps.joinToString()}")
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

private fun resolveHostIPs(host: String): List<String> {
    return try {
        InetAddress.getAllByName(host).map { it.hostAddress }
    } catch (e: UnknownHostException) {
        throw UrlException("Unresolvable host: $host")
    }
}

private fun isValidHostname(hostname: String): Boolean {
    if (hostname.isEmpty() || hostname.length > 253) return false
    
    // Check for IPv6 address in bracket notation
    if (hostname.startsWith("[") && hostname.endsWith("]")) {
        val ipv6Content = hostname.substring(1, hostname.length - 1)
        return isValidIPv6(ipv6Content)
    }
    
    // Check for valid hostname pattern (domain names)
    val hostnameRegex = Regex("^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?))*$")
    return hostnameRegex.matches(hostname)
}

private fun isValidIPv6(ipv6: String): Boolean {
    return try {
        InetAddress.getByName(ipv6) is Inet6Address
    } catch (e: Exception) {
        false
    }
}

private fun containsSuspiciousPatterns(hostname: String): Boolean {
    val suspiciousPatterns = listOf(
        "localhost",
        "127.",
        "0.0.0.0",
        "169.254.",
        "metadata",
        "instance-data",
        "169.254.169.254", // AWS metadata service
        "100.100.100.200", // Alibaba Cloud metadata
        "192.0.0.192", // Oracle Cloud metadata
        "169.254.169.254", // Google Cloud metadata
        "169.254.169.254" // Azure metadata
    )
    
    return suspiciousPatterns.any { hostname.contains(it, ignoreCase = true) }
}

private fun isKubernetes(host: String): Boolean {
    return listOf(
        "kubernetes.default.svc",
        ".svc.cluster.local"
    ).any { host.contains(it, ignoreCase = true) }
}

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
