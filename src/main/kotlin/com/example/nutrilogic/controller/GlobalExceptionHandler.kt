package com.example.nutrilogic.controller

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.resource.NoResourceFoundException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNotFound(ex: NoResourceFoundException, request: HttpServletRequest): ModelAndView {
        logger.warn("404: ${request.requestURI}")
        val mav = ModelAndView("error/404")
        mav.addObject("errorMessage", "Страница не найдена")
        mav.addObject("detail", "Запрашиваемый адрес: ${request.requestURI}")
        mav.status = HttpStatus.NOT_FOUND
        return mav
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ModelAndView {
        logger.error("Ошибка на ${request.requestURI}: ", ex)
        val mav = ModelAndView("error/500")
        mav.addObject("errorMessage", "Внутренняя ошибка сервера")
        mav.addObject("detail", ex.message ?: "Неизвестная ошибка")
        mav.status = HttpStatus.INTERNAL_SERVER_ERROR
        return mav
    }
}