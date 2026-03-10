package carelog.carelog.common.config.aop

import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes


@Aspect
@Component
class LoggingAspect {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Controller 메서드 실행 로깅
     */
    @Around("execution(* carelog.carelog..web.*Controller.*(..))")
    fun logController(joinPoint: ProceedingJoinPoint): Any? {
        val request = getCurrentRequest()
        val method = request.method
        val url = request.requestURL
        val className = getSimpleClassName(joinPoint)
        val methodName = joinPoint.signature.name

        log.info("=====> [{}] {} - {}.{}", method, url, className, methodName);

        // Request 파라미터 로깅(민감정보 제외)
        val args = joinPoint.args
        if (args.isNotEmpty()) {
            val argsStr = args.joinToString(", ") {
                it?.javaClass?.simpleName?: "null"
            }
//                Arrays.stream(args)
//                    .map(arg -> arg == null ? "null" : arg.getClass().getSimpleName())
//                    .collect(Collectors.joining(", "));
            log.debug("Request Args Types: [{}]", argsStr)
        }

        val startTime = System.currentTimeMillis()
        return try {
            val result = joinPoint.proceed()
            val duration = System.currentTimeMillis() - startTime
            log.info("<===== [{}] {} - ✅ SUCCESS ({}ms)", method, url, duration);
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("<===== [{}] {} -  ❌ FAILED ({}ms) - {}: {}",
                    method, url, duration, e.javaClass.simpleName, e.message)
            throw e
        }
    }

    /**
     * 서비스 메서드 실행 로깅
     */
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
                log.warn("[Service] {}.{} - SLOW EXECUTION ({}ms)",
                        className, methodName, duration)
            } else {
                log.debug("[Service] {}.{} - END ({}ms)", className, methodName, duration);
            }
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("[Service] {}.{} - EXCEPTION ({}ms) - {}",
                    className, methodName, duration, e.message)
            throw e
        }
    }
//
//    /**
//     * Repository 메서드 실행 로깅 (선택적)
//     */
//    @Around("execution(* carelog.carelog..domain.*Repository.*(..))")
//    public Object logRepository(ProceedingJoinPoint joinPoint) throws Throwable {
//        String className = getSimpleClassName(joinPoint);
//        String methodName = joinPoint.getSignature().getName();
//
//        log.trace("[Repository] {}.{}", className, methodName);
//
//        return joinPoint.proceed();
//    }
//
//    /**
//     * 예외 발생 로깅
//     */
//    @AfterThrowing(pointcut = "execution(* carelog.carelog..*(..))", throwing = "ex")
//    public void logException(JoinPoint joinPoint, Exception ex) {
//        String className = getSimpleClassName(joinPoint);
//        String methodName = joinPoint.getSignature().getName();
//
//        log.error("❌ [Exception] {}.{} - {}: {}",
//                className, methodName,
//                ex.getClass().getSimpleName(),
//                ex.getMessage());
//    }

    // === Helper Methods ===
    private fun getCurrentRequest(): HttpServletRequest =
        (RequestContextHolder.currentRequestAttributes() as
                ServletRequestAttributes).request


    private fun getSimpleClassName(joinPoint: JoinPoint): String =
        joinPoint.signature.declaringTypeName.substringAfterLast('.')

}
