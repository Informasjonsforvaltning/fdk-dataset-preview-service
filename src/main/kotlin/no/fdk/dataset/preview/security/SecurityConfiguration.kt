package no.fdk.dataset.preview.security

import no.fdk.dataset.preview.ApplicationSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
open class SecurityConfiguration(
    private val applicationSettings: ApplicationSettings
) {

    @Bean
    open fun filterChain(http: HttpSecurity): SecurityFilterChain {
        val filter = APIKeyAuthFilter("X-API-KEY")
        filter.setAuthenticationManager { authentication ->
            val principal = authentication.principal as String
            if (principal.isBlank() || applicationSettings.apiKey != principal
            ) {
                throw BadCredentialsException("The API key was not found or not the expected value.")
            }
            authentication.isAuthenticated = true
            authentication
        }

        http {
            cors {
                configurationSource = corsConfigurationSource()
            }
            csrf {
                requireCsrfProtectionMatcher = RequestMatcher {
                    it.servletPath.equals("/preview")
                }

                val csrfRepository = CustomCsrfTokenRepository.withHttpOnlyFalse()
                csrfRepository.setAllowedOrigins(applicationSettings.allowedOrigins.split(","))
                csrfTokenRepository = csrfRepository
            }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.STATELESS
            }
            addFilterBefore<BasicAuthenticationFilter>(filter)
            authorizeHttpRequests {
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize(HttpMethod.GET, "/ping", permitAll)
                authorize(HttpMethod.GET, "/ready", permitAll)
                authorize(HttpMethod.GET, "/swagger-ui/**", permitAll)
                authorize(HttpMethod.GET, "/v3/**", permitAll)
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }

    @Bean
    open fun corsConfigurationSource(): CorsConfigurationSource? {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = applicationSettings.allowedOrigins.split(",")
        configuration.allowedMethods = listOf("GET", "POST", "OPTIONS")
        configuration.allowCredentials = true
        configuration.allowedHeaders = listOf("X-API-KEY", "X-XSRF-TOKEN", "Content-Type")
        configuration.maxAge = 3600L
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
