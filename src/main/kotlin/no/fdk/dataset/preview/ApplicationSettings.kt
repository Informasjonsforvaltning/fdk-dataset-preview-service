package no.fdk.dataset.preview

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties("application")
data class ApplicationSettings(val apiKey: String)