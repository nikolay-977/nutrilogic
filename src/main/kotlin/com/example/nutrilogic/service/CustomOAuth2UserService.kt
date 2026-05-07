package com.example.nutrilogic.service

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service

@Service
class CustomOAuth2UserService(
    private val userService: UserService
) : DefaultOAuth2UserService() {

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(userRequest)
        val githubId = oauth2User.getAttribute<Any>("id").toString()
        val login = oauth2User.getAttribute<String>("login") ?: "unknown"
        val name = oauth2User.getAttribute<String>("name") ?: login

        val appUser = userService.createOrUpdateFromOAuth2(githubId, login, name)

        val attributes = HashMap(oauth2User.attributes)
        attributes["appUserId"] = appUser.id

        // Получаем имя атрибута из регистрации клиента
        val userNameAttributeName = userRequest.clientRegistration.providerDetails.userInfoEndpoint.userNameAttributeName
        return DefaultOAuth2User(oauth2User.authorities, attributes, userNameAttributeName)
    }
}