package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.util.validate
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URI

class UriValidatorTest {

    @Test
    fun `valid url`() {
        assertDoesNotThrow { URI("http://example.com").validate() }
        assertDoesNotThrow { URI("https://example.com").validate() }
    }

    @Test
    fun `invalid scheme`() {
        assertThrows<UrlException> { URI("file:///etc/passwd").validate() }
    }

    @Test
    fun `private and internal throws exception`() {
        assertThrows<UrlException> { URI("http://127.0.0.1").validate() }
        assertThrows<UrlException> { URI("http://localhost").validate() }
        assertThrows<UrlException> { URI("http://169.254.169.254").validate() }
        assertThrows<UrlException> { URI("http://10.0.0.1").validate() }
        assertThrows<UrlException> { URI("http://192.168.1.1").validate() }
        assertThrows<UrlException> { URI("http://172.16.0.1").validate() }
        assertThrows<UrlException> { URI("http:[fc00::1]").validate() }
        assertThrows<UrlException> { URI("http://[fd12:3456:789a:1::1]").validate() }
        assertThrows<UrlException> { URI("http://[::1]").validate() }
        assertThrows<UrlException> { URI("http://[fe80::1]").validate() }
    }

    @Test
    fun `public does not throw exception`() {
        assertDoesNotThrow { URI("http://93.184.216.34").validate() }
        assertDoesNotThrow { URI("http://[2001:4860:4860::8888]").validate() }
    }

    @Test
    fun kubernetes() {
        assertThrows<UrlException> { URI("http://kubernetes.default.svc").validate() }
        assertThrows<UrlException> { URI("http://internal-api.svc.cluster.local").validate() }
    }
}