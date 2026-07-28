package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.util.validate
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

@Tag("unit")
class UriValidatorTest {
    private fun assertInvalidUrl(uri: String) {
        assertThrows<UrlException> { URI(uri).validate() }
    }

    @Test
    fun `valid https url`() {
        assertDoesNotThrow { URI("https://example.com").validate() }
    }

    @Test
    fun `http scheme blocked`() {
        assertInvalidUrl("http://example.com")
    }

    @Test
    fun `invalid scheme`() {
        assertInvalidUrl("file:///etc/passwd")
    }

    @Test
    fun `private and internal addresses are blocked`() {
        listOf(
            "https://127.0.0.1",
            "https://localhost",
            "https://169.254.169.254",
            "https://10.0.0.1",
            "https://192.168.1.1",
            "https://172.16.0.1",
            "https://[fc00::1]",
            "https://[fd12:3456:789a:1::1]",
            "https://[::1]",
            "https://[fe80::1]",
        ).forEach(::assertInvalidUrl)
    }

    @Test
    fun `public https urls do not throw exception`() {
        assertDoesNotThrow { URI("https://93.184.216.34").validate() }
        assertDoesNotThrow { URI("https://[2001:4860:4860::8888]").validate() }
    }

    @Test
    fun `kubernetes service hosts are blocked`() {
        listOf(
            "http://kubernetes.default.svc",
            "http://internal-api.svc.cluster.local",
            "https://kubernetes.default.svc",
            "https://internal-api.svc.cluster.local",
        ).forEach(::assertInvalidUrl)
    }
}
