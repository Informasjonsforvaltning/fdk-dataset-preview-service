package no.fdk.dataset.preview

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("application")
data class ApplicationSettings(val apiKey: String, val allowedOrigins: String)