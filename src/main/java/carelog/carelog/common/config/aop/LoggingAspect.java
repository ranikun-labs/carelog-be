package carelog.carelog.common.config.aop;

import jakarta.servlet.http.*;
import lombok.extern.slf4j.*;
import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.web.context.request.*;

import java.util.*;
import java.util.stream.*;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * Controller 메서드 실행 로깅
     */
    @Around("execution(* carelog.carelog..web.*Controller.*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getCurrentRequest();

        String method = request.getMethod();
        String url = request.getRequestURI();
        String className = getSimpleClassName(joinPoint);
        String methodName = joinPoint.getSignature().getName();

        log.info("=====> [{}] {} - {}.{}", method, url, className, methodName);

        // Request 파라미터 로깅(민감정보 제외)
        Object[] args = joinPoint.getArgs();
        if (args.length > 0) {
            String argsStr = Arrays.stream(args)
                    .map(arg -> arg == null ? "null" : arg.getClass().getSimpleName())
                    .collect(Collectors.joining(", "));
            log.debug("Request Args Types: [{}]", argsStr);
        }

        long startTime = System.currentTimeMillis();
        Object result = null;
        try {
            result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.info("<===== [{}] {} - ✅ SUCCESS ({}ms)", method, url, duration);
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("<===== [{}] {} -  ❌ FAILED ({}ms) - {}: {}",
                    method, url, duration, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    /**
     * 서비스 메서드 실행 로깅
     */
    @Around("execution(* carelog.carelog..app.*ServiceImpl.*(..))")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = getSimpleClassName(joinPoint);
        String methodName = joinPoint.getSignature().getName();

        log.debug("[Service] {}.{} - START", className, methodName);

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            if (duration > 1000) {
                log.warn("[Service] {}.{} - SLOW EXECUTION ({}ms)",
                        className, methodName, duration);
            } else {
                log.debug("[Service] {}.{} - END ({}ms)", className, methodName, duration);
            }

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[Service] {}.{} - EXCEPTION ({}ms) - {}",
                    className, methodName, duration, e.getMessage());
            throw e;
        }
    }

    /**
     * Repository 메서드 실행 로깅 (선택적)
     */
    @Around("execution(* carelog.carelog..domain.*Repository.*(..))")
    public Object logRepository(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = getSimpleClassName(joinPoint);
        String methodName = joinPoint.getSignature().getName();

        log.trace("[Repository] {}.{}", className, methodName);

        return joinPoint.proceed();
    }

    /**
     * 예외 발생 로깅
     */
    @AfterThrowing(pointcut = "execution(* carelog.carelog..*(..))", throwing = "ex")
    public void logException(JoinPoint joinPoint, Exception ex) {
        String className = getSimpleClassName(joinPoint);
        String methodName = joinPoint.getSignature().getName();

        log.error("❌ [Exception] {}.{} - {}: {}",
                className, methodName,
                ex.getClass().getSimpleName(),
                ex.getMessage());
    }

    // === Helper Methods ===
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }

    private String getSimpleClassName(JoinPoint joinPoint) {
        String fullClassName = joinPoint.getSignature().getDeclaringTypeName();
        return fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
    }
}
