package no.fdk.dataset.preview.util

import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

/**
 * Utility class for sanitizing content to prevent XSS attacks
 */
object ContentSanitizer {
    
    /**
     * Sanitizes HTML content by removing dangerous elements and attributes
     * while preserving safe formatting elements
     */
    fun sanitizeHtml(html: String): String {
        if (html.isBlank()) return html
        
        // Use Jsoup's Safelist to allow only safe HTML elements
        val safelist = Safelist.relaxed()
            .addTags("p", "br", "strong", "em", "u", "i", "b")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https")
            .removeTags("script", "object", "embed", "iframe", "form", "input", "button")
            .removeAttributes("onclick", "onload", "onerror", "onmouseover", "onfocus", "onblur")
        
        return Jsoup.clean(html, safelist)
    }
    
    /**
     * Escapes HTML special characters to prevent XSS
     */
    fun escapeHtml(text: String): String {
        if (text.isBlank()) return text
        
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
            .replace("/", "&#x2F;")
    }
    
    /**
     * Sanitizes cell content by removing potentially dangerous content
     */
    fun sanitizeCellContent(content: String): String {
        if (content.isBlank()) return content
        
        // First escape HTML to prevent XSS
        val escaped = escapeHtml(content)
        
        // Remove any remaining script-like patterns
        val sanitized = escaped
            .replace(Regex("(?i)javascript:"), "")
            .replace(Regex("(?i)vbscript:"), "")
            .replace(Regex("(?i)data:"), "")
            .replace(Regex("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']"), "")
        
        return sanitized
    }
    
    /**
     * Removes script tags and dangerous HTML elements completely
     */
    fun removeDangerousContent(content: String): String {
        if (content.isBlank()) return content
        
        return content
            .replace(Regex("(?i)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?i)<object[^>]*>.*?</object>"), "")
            .replace(Regex("(?i)<embed[^>]*>.*?</embed>"), "")
            .replace(Regex("(?i)<iframe[^>]*>.*?</iframe>"), "")
            .replace(Regex("(?i)<form[^>]*>.*?</form>"), "")
            .replace(Regex("(?i)<input[^>]*>"), "")
            .replace(Regex("(?i)<button[^>]*>.*?</button>"), "")
            .replace(Regex("(?i)on\\w+\\s*=\\s*[\"'][^\"']*[\"']"), "")
    }
}
