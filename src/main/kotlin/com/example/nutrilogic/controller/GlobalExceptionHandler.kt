package com.example.nutrilogic.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.NoHandlerFoundException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGenericException(ex: Exception, model: Model): String {
        logger.error("Произошла ошибка: ", ex)
        model.addAttribute("errorMessage", "Внутренняя ошибка сервера. Пожалуйста, попробуйте позже.")
        model.addAttribute("detail", ex.message ?: "Неизвестная ошибка")
        return "error/500"
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: NoHandlerFoundException, model: Model): String {
        logger.warn("Страница не найдена: ${ex.requestURL}")
        model.addAttribute("errorMessage", "Страница не найдена")
        model.addAttribute("detail", "Запрашиваемый адрес: ${ex.requestURL}")
        return "error/404"
    }
}