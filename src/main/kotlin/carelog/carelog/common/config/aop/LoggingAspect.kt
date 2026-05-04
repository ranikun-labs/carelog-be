package carelog.carelog.common.config.aop

import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.AfterThrowing
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Aspect
@Component
class LoggingAspect {

    private val log = LoggerFactory.getLogger(LoggingAspect::class.java)

    @Around("execution(* carelog.carelog..web.*Controller.*(..))")
    fun logController(joinPoint: ProceedingJoinPoint): Any? {
        val request = getCurrentRequest()
        val method = request.method
        val url = request.requestURI
        val className = getSimpleClassName(joinPoint)
        val methodName = joinPoint.signature.name

        log.info("=====> [{}] {} - {}.{}", method, url, className, methodName)

        val args = joinPoint.args
        if (args.isNotEmpty()) {
            val argsStr = args.joinToString(", ") { it?.javaClass?.simpleName ?: "null" }
            log.debug("Request Args Types: [{}]", argsStr)
        }

        val startTime = System.currentTimeMillis()
        return try {
            val result = joinPoint.proceed()
            val duration = System.currentTimeMillis() - startTime
            log.info("<===== [{}] {} - ✅ SUCCESS ({}ms)", method, url, duration)
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("<===== [{}] {} -  ❌ FAILED ({}ms) - {}: {}",
                method, url, duration, e.javaClass.simpleName, e.message)
            throw e
        }
    }

    @Around("execution(* carelog.carelog..app.*ServiceImpl.*(..))")
    fun logService(joinPoint: ProceedingJoinPoint): Any? {
        val className = getSimpleClassName(joinPoint)
        val methodName = joinPoint.signature.name

        log.debug("[Service] {}.{} - START", className, methodName)

        val startTime = System.currentTimeMillis()
        return try {
            val result = joinPoint.proceed()
            val duration = System.currentTimeMillis() - startTime

            if (duration > 1000) {
                log.warn("[Service] {}.{} - SLOW EXECUTION ({}ms)", className, methodName, duration)
            } else {
                log.debug("[Service] {}.{} - END ({}ms)", className, methodName, duration)
            }

            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("[Service] {}.{} - EXCEPTION ({}ms) - {}", className, methodName, duration, e.message)
            throw e
        }
    }

    @Around("execution(* carelog.carelog..domain.*Repository.*(..))")
    fun logRepository(joinPoint: ProceedingJoinPoint): Any? {
        log.trace("[Repository] {}.{}", getSimpleClassName(joinPoint), joinPoint.signature.name)
        return joinPoint.proceed()
    }

    @AfterThrowing(pointcut = "execution(* carelog.carelog..*(..)) && !within(*..*Filter)", throwing = "ex")
    fun logException(joinPoint: JoinPoint, ex: Exception) {
        log.error("❌ [Exception] {}.{} - {}: {}",
            getSimpleClassName(joinPoint), joinPoint.signature.name,
            ex.javaClass.simpleName, ex.message)
    }

    private fun getCurrentRequest(): HttpServletRequest =
        (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request

    private fun getSimpleClassName(joinPoint: JoinPoint): String {
        val fullClassName = joinPoint.signature.declaringTypeName
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1)
    }
}