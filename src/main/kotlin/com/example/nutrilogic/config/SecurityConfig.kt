package com.example.nutrilogic.config

import com.example.nutrilogic.service.CustomOAuth2UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig(private val oauth2UserService: CustomOAuth2UserService) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers("/", "/error", "/webjars/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { it.userService(oauth2UserService) }
                    .defaultSuccessUrl("/dashboard", true)
                    .failureUrl("/login?error")
            }
            .logout { logout -> logout.logoutSuccessUrl("/") }
        return http.build()
    }
}