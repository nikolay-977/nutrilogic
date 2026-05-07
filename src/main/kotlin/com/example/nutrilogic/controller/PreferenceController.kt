package com.example.nutrilogic.controller

import com.example.nutrilogic.service.ProductService
import jakarta.servlet.http.HttpSession
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/preferences")
class PreferenceController(private val productService: ProductService) {

    @PostMapping("/add-favorite")
    fun addFavorite(@RequestParam productName: String, session: HttpSession): String {
        val product = productService.searchProducts(productName, null).firstOrNull()
        if (product != null) {
            val favorites = session.getAttribute("favorites") as? MutableList<Pair<String, Int>> ?: mutableListOf()
            if (favorites.none { it.first == product.name }) {
                // добавляем с приоритетом (чем меньше число, тем выше)
                val maxPriority = favorites.maxOfOrNull { it.second } ?: 0
                favorites.add(product.name to (maxPriority + 1))
                session.setAttribute("favorites", favorites)
            }
        }
        return redirectToReferer(session)
    }

    @PostMapping("/remove-favorite")
    fun removeFavorite(@RequestParam productName: String, session: HttpSession): String {
        val favorites = session.getAttribute("favorites") as? MutableList<Pair<String, Int>> ?: mutableListOf()
        favorites.removeIf { it.first == productName }
        session.setAttribute("favorites", favorites)
        return redirectToReferer(session)
    }

    @PostMapping("/add-stoplist")
    fun addStoplist(@RequestParam productName: String, session: HttpSession): String {
        val product = productService.searchProducts(productName, null).firstOrNull()
        if (product != null) {
            val stoplist = session.getAttribute("stoplist") as? MutableList<String> ?: mutableListOf()
            if (!stoplist.contains(product.name)) {
                stoplist.add(product.name)
                session.setAttribute("stoplist", stoplist)
            }
        }
        return redirectToReferer(session)
    }

    @PostMapping("/remove-stoplist")
    fun removeStoplist(@RequestParam productName: String, session: HttpSession): String {
        val stoplist = session.getAttribute("stoplist") as? MutableList<String> ?: mutableListOf()
        stoplist.remove(productName)
        session.setAttribute("stoplist", stoplist)
        return redirectToReferer(session)
    }

    @PostMapping("/update-favorite-priority")
    fun updateFavoritePriority(
        @RequestParam productName: String,
        @RequestParam newPriority: Int,
        session: HttpSession
    ): String {
        val favorites = session.getAttribute("favorites") as? MutableList<Pair<String, Int>> ?: mutableListOf()
        val index = favorites.indexOfFirst { it.first == productName }
        if (index >= 0) {
            favorites[index] = productName to newPriority
            // сортируем по приоритету
            favorites.sortBy { it.second }
            session.setAttribute("favorites", favorites)
        }
        return "redirect:/profile/preferences"
    }

    private fun redirectToReferer(session: HttpSession): String {
        val referer = session.getAttribute("referer") as? String ?: "/recommend"
        return "redirect:$referer"
    }
}