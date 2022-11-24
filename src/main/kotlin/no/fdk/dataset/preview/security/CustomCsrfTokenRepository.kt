//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
package no.fdk.dataset.preview.security

import no.fdk.dataset.preview.util.UrlUtil
import no.fdk.dataset.preview.util.UrlUtil.Companion.getDomainName
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRepository
import org.springframework.security.web.csrf.DefaultCsrfToken
import org.springframework.util.Assert
import org.springframework.util.StringUtils
import org.springframework.web.util.WebUtils
import java.util.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class CustomCsrfTokenRepository : CsrfTokenRepository {
    private var parameterName = "_csrf"
    private var headerName = "X-XSRF-TOKEN"
    private var cookieName = "DATASET-PREVIEW-CSRF-TOKEN"
    private var cookieHttpOnly = true
    private var cookiePath: String? = null
    private var allowedOrigins: List<String>? = null
    private var secure: Boolean? = null
    private var cookieMaxAge = -1L
    override fun generateToken(request: HttpServletRequest): CsrfToken {
        return DefaultCsrfToken(headerName, parameterName, createNewToken())
    }

    override fun saveToken(token: CsrfToken?, request: HttpServletRequest, response: HttpServletResponse) {
        val tokenValue = if (token != null) token.token else ""

        var responseCookieBuilder = ResponseCookie
            .from(cookieName, tokenValue)
            .secure((if (secure != null) secure else request.isSecure)!!)
            .httpOnly(cookieHttpOnly)
            .path(if (StringUtils.hasLength(cookiePath)) cookiePath else getRequestContext(request))
            .maxAge(if (token != null) cookieMaxAge else 0L)
            .sameSite("None")

        val domain = getDomainName(request.getHeader("referer"))
        if (allowedOrigins != null && allowedOrigins!!.any { getDomainName(it) == domain }) {
            responseCookieBuilder = responseCookieBuilder.domain(domain)
        }

        response.addHeader(HttpHeaders.SET_COOKIE, responseCookieBuilder.build().toString())
    }

    override fun loadToken(request: HttpServletRequest): CsrfToken? {
        val cookie = WebUtils.getCookie(request, cookieName)
        return if (cookie == null) {
            null
        } else {
            val token = cookie.value
            if (!StringUtils.hasLength(token)) null else DefaultCsrfToken(
                headerName, parameterName, token
            )
        }
    }

    fun setParameterName(parameterName: String) {
        Assert.notNull(parameterName, "parameterName cannot be null")
        this.parameterName = parameterName
    }

    fun setHeaderName(headerName: String) {
        Assert.notNull(headerName, "headerName cannot be null")
        this.headerName = headerName
    }

    fun setCookieName(cookieName: String) {
        Assert.notNull(cookieName, "cookieName cannot be null")
        this.cookieName = cookieName
    }

    fun setCookieHttpOnly(cookieHttpOnly: Boolean) {
        this.cookieHttpOnly = cookieHttpOnly
    }

    private fun getRequestContext(request: HttpServletRequest): String {
        val contextPath = request.contextPath
        return if (contextPath.length > 0) contextPath else "/"
    }

    private fun createNewToken(): String {
        return UUID.randomUUID().toString()
    }


    fun setSecure(secure: Boolean?) {
        this.secure = secure
    }

    fun setCookieMaxAge(cookieMaxAge: Long) {
        Assert.isTrue(cookieMaxAge != 0L, "cookieMaxAge cannot be zero")
        this.cookieMaxAge = cookieMaxAge
    }

    fun setAllowedOrigins(allowedOrigins: List<String>) {
        this.allowedOrigins = allowedOrigins
    }

    companion object {
        fun withHttpOnlyFalse(): CustomCsrfTokenRepository {
            val result = CustomCsrfTokenRepository()
            result.setCookieHttpOnly(false)
            return result
        }
    }
}