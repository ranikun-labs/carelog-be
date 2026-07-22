package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.UserPrincipal;
import carelog.carelog.auth.app.port.CredentialPort;
import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * {@link CredentialPort}의 Legacy 구현.
 *
 * <p>인증을 {@link AuthenticationManager}에 위임한다. Spring Security가 내부적으로
 * {@code CustomUserDetailsService}/{@code CustomUserDetails}를 호출하지만, 이 Adapter는
 * 그 concrete 타입을 import하지 않고 {@link Authentication#getPrincipal()}을
 * {@link UserPrincipal} 인터페이스로만 취급한다.
 */
@Component
@RequiredArgsConstructor
public class LegacyCredentialAdapter implements CredentialPort {

    private final AuthenticationManager authenticationManager;

    @Override
    public UserPrincipal authenticate(String userId, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(userId, password)
            );
            return (UserPrincipal) authentication.getPrincipal();
        } catch (AuthenticationException e) {
            throw new CustomException(ExceptionStatus.INVALID_CREDENTIALS);
        }
    }
}
