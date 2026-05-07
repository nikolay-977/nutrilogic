package com.example.nutrilogic.config

import com.example.nutrilogic.service.UserService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler(
    private val userService: UserService
) : SavedRequestAwareAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauth2User = authentication.principal as OAuth2User
        val githubId = oauth2User.getAttribute<Any>("id").toString()
        val login = oauth2User.getAttribute<String>("login") ?: "unknown"
        val name = oauth2User.getAttribute<String>("name") ?: login

        userService.createOrUpdateFromOAuth2(githubId, login, name)

        super.onAuthenticationSuccess(request, response, authentication)
    }
}