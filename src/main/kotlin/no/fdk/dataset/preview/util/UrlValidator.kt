package no.fdk.dataset.preview.util

import no.fdk.dataset.preview.service.UrlException
import java.net.*

object UrlValidator {

    fun validate(url: String) {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw UrlException("Invalid URL format: ${e.message}")
        }

        val host = uri.host ?: throw UrlException("Invalid URL host")
        val scheme = uri.scheme?.lowercase() ?: throw UrlException("Invalid URL scheme")

        if (scheme !in listOf("http", "https")) {
            throw UrlException("Blocked unsafe URL scheme: $scheme")
        }

        if (isKubernetes(host)) {
            throw UrlException("Blocked access to Kubernetes internal services: $host")
        }

        val resolvedIps = resolveHostIPs(host)
        if (resolvedIps.any { isPrivateOrInternal(it) }) {
            throw UrlException("Blocked internal network access: $host (${resolvedIps.joinToString()})")
        }

        val recheckIps = resolveHostIPs(host)
        if (resolvedIps.toSet() != recheckIps.toSet()) {
            throw UrlException("Possible DNS Rebinding attack detected: ${resolvedIps.joinToString()} -> ${recheckIps.joinToString()}")
        }
    }

    private fun resolveHostIPs(host: String): List<String> {
        return try {
            InetAddress.getAllByName(host).map { it.hostAddress }
        } catch (e: UnknownHostException) {
            throw UrlException("Unresolvable host: $host")
        }
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
}
