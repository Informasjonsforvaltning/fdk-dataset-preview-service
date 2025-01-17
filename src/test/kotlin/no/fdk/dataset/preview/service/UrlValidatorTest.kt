package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.util.UrlValidator
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UrlValidatorTest {

    @Test
    fun `valid url`() {
        assertDoesNotThrow { UrlValidator.validate("http://example.com") }
        assertDoesNotThrow { UrlValidator.validate("https://example.com") }
    }

    @Test
    fun `invalid scheme`() {
        assertThrows<UrlException> { UrlValidator.validate("file:///etc/passwd") }
    }

    @Test
    fun `private and internal throws exception`() {
        assertThrows<UrlException> { UrlValidator.validate("http://127.0.0.1") }
        assertThrows<UrlException> { UrlValidator.validate("http://localhost") }
        assertThrows<UrlException> { UrlValidator.validate("http://169.254.169.254") }
        assertThrows<UrlException> { UrlValidator.validate("http://10.0.0.1") }
        assertThrows<UrlException> { UrlValidator.validate("http://192.168.1.1") }
        assertThrows<UrlException> { UrlValidator.validate("http://172.16.0.1") }
        assertThrows<UrlException> { UrlValidator.validate("http:[fc00::1]") }
        assertThrows<UrlException> { UrlValidator.validate("http://[fd12:3456:789a:1::1]") }
        assertThrows<UrlException> { UrlValidator.validate("http://[::1]") }
        assertThrows<UrlException> { UrlValidator.validate("http://[fe80::1]") }
    }

    @Test
    fun `public does not throw exception`() {
        assertDoesNotThrow { UrlValidator.validate("http://93.184.216.34") }
        assertDoesNotThrow { UrlValidator.validate("http://[2001:4860:4860::8888]") }
    }

    @Test
    fun kubernetes() {
        assertThrows<UrlException> { UrlValidator.validate("http://kubernetes.default.svc") }
        assertThrows<UrlException> { UrlValidator.validate("http://internal-api.svc.cluster.local") }
    }
}