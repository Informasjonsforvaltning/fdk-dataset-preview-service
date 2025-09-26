package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.util.validate
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

@Tag("unit")
class UriValidatorTest {

    @Test
    fun `valid https url`() {
        assertDoesNotThrow { URI("https://example.com").validate() }
    }

    @Test
    fun `http scheme blocked`() {
        assertThrows<UrlException> { URI("http://example.com").validate() }
    }

    @Test
    fun `invalid scheme`() {
        assertThrows<UrlException> { URI("file:///etc/passwd").validate() }
    }

    @Test
    fun `private and internal throws exception`() {
        assertThrows<UrlException> { URI("https://127.0.0.1").validate() }
        assertThrows<UrlException> { URI("https://localhost").validate() }
        assertThrows<UrlException> { URI("https://169.254.169.254").validate() }
        assertThrows<UrlException> { URI("https://10.0.0.1").validate() }
        assertThrows<UrlException> { URI("https://192.168.1.1").validate() }
        assertThrows<UrlException> { URI("https://172.16.0.1").validate() }
        assertThrows<UrlException> { URI("https://[fc00::1]").validate() }
        assertThrows<UrlException> { URI("https://[fd12:3456:789a:1::1]").validate() }
        assertThrows<UrlException> { URI("https://[::1]").validate() }
        assertThrows<UrlException> { URI("https://[fe80::1]").validate() }
    }

    @Test
    fun `public https urls do not throw exception`() {
        assertDoesNotThrow { URI("https://93.184.216.34").validate() }
        assertDoesNotThrow { URI("https://[2001:4860:4860::8888]").validate() }
    }

    @Test
    fun kubernetes() {
        assertThrows<UrlException> { URI("http://kubernetes.default.svc").validate() }
        assertThrows<UrlException> { URI("http://internal-api.svc.cluster.local").validate() }
        assertThrows<UrlException> { URI("https://kubernetes.default.svc").validate() }
        assertThrows<UrlException> { URI("https://internal-api.svc.cluster.local").validate() }
    }
}