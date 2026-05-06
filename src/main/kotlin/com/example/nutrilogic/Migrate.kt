import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

fun main() {
    val mapper = ObjectMapper().registerKotlinModule()
    val dataDir = File("data/full_products")
    dataDir.listFiles { f -> f.extension == "json" }?.forEach { file ->
        val json = mapper.readTree(file)
        // Если нет поля category, добавляем из имени файла
        if (!json.has("category")) {
            val category = file.nameWithoutExtension.substringBefore('_')
            (json as? com.fasterxml.jackson.databind.node.ObjectNode)?.put("category", category)
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, json)
            println("Обновлён: ${file.name} -> категория: $category")
        }
    }
}