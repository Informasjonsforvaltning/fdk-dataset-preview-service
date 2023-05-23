package no.fdk.dataset.preview

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableWebSecurity
open class Application

fun main(args: Array<String>) {
    SpringApplication.run(Application::class.java, *args)
}
