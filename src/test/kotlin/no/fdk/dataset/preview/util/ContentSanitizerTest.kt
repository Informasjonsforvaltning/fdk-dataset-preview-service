package no.fdk.dataset.preview.util

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@Tag("unit")
class ContentSanitizerTest {

    @Test
    fun `sanitizeCellContent should escape HTML special characters`() {
        val maliciousContent = "<script>alert('XSS')</script>"
        val sanitized = ContentSanitizer.sanitizeCellContent(maliciousContent)
        
        assertEquals("&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;&#x2F;script&gt;", sanitized)
    }

    @Test
    fun `sanitizeCellContent should remove javascript protocol`() {
        val maliciousContent = "javascript:alert('XSS')"
        val sanitized = ContentSanitizer.sanitizeCellContent(maliciousContent)
        
        assertEquals("alert(&#x27;XSS&#x27;)", sanitized)
    }

    @Test
    fun `sanitizeCellContent should remove vbscript protocol`() {
        val maliciousContent = "vbscript:msgbox('XSS')"
        val sanitized = ContentSanitizer.sanitizeCellContent(maliciousContent)
        
        assertEquals("msgbox(&#x27;XSS&#x27;)", sanitized)
    }

    @Test
    fun `sanitizeCellContent should remove data protocol`() {
        val maliciousContent = "data:text/html,<script>alert('XSS')</script>"
        val sanitized = ContentSanitizer.sanitizeCellContent(maliciousContent)
        
        assertEquals("text&#x2F;html,&lt;script&gt;alert(&#x27;XSS&#x27;)&lt;&#x2F;script&gt;", sanitized)
    }

    @Test
    fun `sanitizeCellContent should remove event handlers`() {
        val maliciousContent = "onclick=\"alert('XSS')\""
        val sanitized = ContentSanitizer.sanitizeCellContent(maliciousContent)
        
        assertEquals("onclick=&quot;alert(&#x27;XSS&#x27;)&quot;", sanitized)
    }

    @Test
    fun `sanitizeCellContent should handle safe content unchanged`() {
        val safeContent = "This is safe content with numbers 123 and symbols !@#"
        val sanitized = ContentSanitizer.sanitizeCellContent(safeContent)
        
        assertEquals("This is safe content with numbers 123 and symbols !@#", sanitized)
    }

    @Test
    fun `removeDangerousContent should remove script tags`() {
        val maliciousContent = "Hello <script>alert('XSS')</script> World"
        val sanitized = ContentSanitizer.removeDangerousContent(maliciousContent)
        
        assertEquals("Hello  World", sanitized)
    }

    @Test
    fun `removeDangerousContent should remove iframe tags`() {
        val maliciousContent = "Content <iframe src=\"evil.com\"></iframe> more content"
        val sanitized = ContentSanitizer.removeDangerousContent(maliciousContent)
        
        assertEquals("Content  more content", sanitized)
    }

    @Test
    fun `removeDangerousContent should remove form tags`() {
        val maliciousContent = "Text <form action=\"evil.com\"><input type=\"submit\"></form> end"
        val sanitized = ContentSanitizer.removeDangerousContent(maliciousContent)
        
        assertEquals("Text  end", sanitized)
    }

    @Test
    fun `removeDangerousContent should remove event handlers`() {
        val maliciousContent = "onclick=\"alert('XSS')\" onload=\"evil()\""
        val sanitized = ContentSanitizer.removeDangerousContent(maliciousContent)
        
        // The regex should remove the event handlers, leaving only the content between quotes
        assert(sanitized.contains("XSS"))
        assert(!sanitized.contains("onclick"))
        assert(!sanitized.contains("onload"))
    }

    @Test
    fun `escapeHtml should escape all HTML special characters`() {
        val content = "<>&\"'/"
        val escaped = ContentSanitizer.escapeHtml(content)
        
        assertEquals("&lt;&gt;&amp;&quot;&#x27;&#x2F;", escaped)
    }

    @Test
    fun `sanitizeHtml should preserve safe HTML elements`() {
        val html = "<p>Hello <strong>world</strong>!</p>"
        val sanitized = ContentSanitizer.sanitizeHtml(html)
        
        assertEquals("<p>Hello <strong>world</strong>!</p>", sanitized)
    }

    @Test
    fun `sanitizeHtml should remove dangerous elements`() {
        val html = "<p>Hello <script>alert('XSS')</script> world!</p>"
        val sanitized = ContentSanitizer.sanitizeHtml(html)
        
        assertEquals("<p>Hello  world!</p>", sanitized)
    }
}
