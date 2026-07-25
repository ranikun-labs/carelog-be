package carelog.carelog.identity.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.identity.app.port.IdentityAccount;
import carelog.carelog.identity.app.port.IdentityAccountRegistrationPort;
import carelog.carelog.identity.domain.PasswordCredential;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import carelog.carelog.identity.domain.PlatformAccount;
import carelog.carelog.identity.domain.PlatformAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * {@link IdentityAccountRegistrationPort}의 구현. 호출자가 이미 연 Transaction에 참여한다
 * (별도 {@code @Transactional}을 선언하지 않음 — Carelog Enrollment Coordinator가 단일 로컬
 * Transaction을 유지한다).
 */
@Service
@RequiredArgsConstructor
public class IdentityAccountRegistrationService implements IdentityAccountRegistrationPort {

    private final PlatformAccountRepository platformAccountRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IdentityAccount registerPasswordAccount(String loginId, String email, String rawPassword) {
        if (passwordCredentialRepository.existsByLoginId(loginId)) {
            throw new CustomException(ExceptionStatus.DUPLICATE_USER_ID);
        }

        String encodedPasswordHash = passwordEncoder.encode(rawPassword);

        PlatformAccount account = PlatformAccount.create(email);
        platformAccountRepository.save(account);

        PasswordCredential credential = PasswordCredential.create(account.getId(), loginId, encodedPasswordHash);
        passwordCredentialRepository.save(credential);

        return new IdentityAccount(account.getId(), loginId, encodedPasswordHash);
    }
}
