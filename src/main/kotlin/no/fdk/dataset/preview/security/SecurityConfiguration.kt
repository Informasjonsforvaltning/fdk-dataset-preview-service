package no.fdk.dataset.preview.security

import no.fdk.dataset.preview.ApplicationSettings
import no.fdk.dataset.preview.util.UrlUtil
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
open class SecurityConfiguration(
    private val applicationSettings: ApplicationSettings) {

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

        val csrfRepository = CustomCsrfTokenRepository.withHttpOnlyFalse()
        csrfRepository.setAllowedOrigins(applicationSettings.allowedOrigins.split(","))

        http.cors().and()
            .csrf()
            .csrfTokenRepository(csrfRepository)
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and().addFilter(filter)
            .authorizeRequests{ authorize ->
                authorize.antMatchers(HttpMethod.GET, "/ping").permitAll()
                    .antMatchers(HttpMethod.GET, "/ready").permitAll()
                    .anyRequest().authenticated()
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
