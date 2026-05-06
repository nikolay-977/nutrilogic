package com.example.nutrilogic.config

import com.example.nutrilogic.model.UserProfile
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2LoginSuccessHandler : SavedRequestAwareAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oauth2User = authentication.principal as OAuth2User
        val login = oauth2User.getAttribute<String>("login")
        val name = oauth2User.getAttribute<String>("name") ?: login

        val session = request.session
        var profile = session.getAttribute("userProfile") as? UserProfile
        if (profile == null) {
            profile = UserProfile().apply {
                this.name = name ?: "GitHub User"
                gender = "male"
                birthDate = null
                height = 180.0
                weight = 80.0
                activityLevel = "moderate"
                targetCalories = 2000.0
                targetProtein = 120.0
                targetFat = 70.0
                targetCarbs = 250.0
                customTargets = mutableMapOf()
                favoriteProducts = mutableSetOf()
                bannedProducts = mutableSetOf()
            }
            session.setAttribute("userProfile", profile)
        } else {
            profile.name = name ?: profile.name
            session.setAttribute("userProfile", profile)
        }

        super.onAuthenticationSuccess(request, response, authentication)
    }
}