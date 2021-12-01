package no.fdk.dataset.preview.security

import no.fdk.dataset.preview.ApplicationSettings
import org.apache.logging.log4j.util.Strings
import org.springframework.context.annotation.Bean
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@EnableWebSecurity
@EnableGlobalMethodSecurity(securedEnabled = true)
open class SecurityConfiguration(
    private val applicationSettings: ApplicationSettings) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        val filter = APIKeyAuthFilter("X-API-KEY")
        filter.setAuthenticationManager { authentication ->
            val principal = authentication.principal as String
            if (Strings.isBlank(principal) || applicationSettings.apiKey != principal
            ) {
                throw BadCredentialsException("The API key was not found or not the expected value.")
            }
            authentication.isAuthenticated = true
            authentication
        }

        http.cors().and()
            .csrf().disable()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and().addFilter(filter).authorizeRequests().anyRequest().authenticated()
    }

    @Bean
    open fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", CorsConfiguration().applyPermitDefaultValues())
        return source
    }
}