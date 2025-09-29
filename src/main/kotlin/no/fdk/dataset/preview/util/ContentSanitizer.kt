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
     * Sanitizes cell content by removing potentially dangerous content
     * Note: HTML escaping is handled by React on the frontend
     */
    fun sanitizeCellContent(content: String): String {
        if (content.isBlank()) return content
        
        // Remove script-like patterns and dangerous protocols
        val sanitized = content
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
