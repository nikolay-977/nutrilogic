package com.example.nutrilogic.controller

import com.example.nutrilogic.service.ProductService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile

@Controller
@RequestMapping("/content-management")
class ContentManagementController(
    private val productService: ProductService
) {

    @GetMapping
    fun contentManagementPage(model: Model): String {
        return "content-management"
    }

    @PostMapping("/upload")
    fun uploadProducts(
        @RequestParam("files") files: List<MultipartFile>,
        model: Model
    ): String {
        val results = mutableListOf<String>()
        var successCount = 0
        var errorCount = 0

        files.forEach { file ->
            if (file.originalFilename?.endsWith(".json") == true && !file.isEmpty) {
                try {
                    val result = productService.uploadProductFromJson(file)
                    results.add("✅ ${file.originalFilename}: $result")
                    successCount++
                } catch (e: Exception) {
                    results.add("❌ ${file.originalFilename}: ${e.message}")
                    errorCount++
                }
            } else {
                results.add("❌ ${file.originalFilename}: недопустимый формат (только .json)")
                errorCount++
            }
        }

        model.addAttribute("uploadResults", results)
        model.addAttribute("successCount", successCount)
        model.addAttribute("errorCount", errorCount)
        return "content-management"
    }
}