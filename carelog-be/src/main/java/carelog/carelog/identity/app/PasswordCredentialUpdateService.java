package carelog.carelog.identity.app;

import carelog.carelog.common.web.exception.CustomException;
import carelog.carelog.common.web.exception.ExceptionStatus;
import carelog.carelog.identity.app.port.PasswordCredentialUpdatePort;
import carelog.carelog.identity.app.port.UpdatedPasswordCredential;
import carelog.carelog.identity.domain.PasswordCredential;
import carelog.carelog.identity.domain.PasswordCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * {@link PasswordCredentialUpdatePort}의 구현. 호출자가 이미 연 Transaction에 참여한다
 * (별도 {@code @Transactional}을 선언하지 않음 — Carelog Enrollment Coordinator가 단일 로컬
 * Transaction을 유지한다). accountId는 {@link PasswordCredential}의 PK이므로 별도 조회 메서드
 * 추가 없이 {@code findById}로 식별한다.
 */
@Service
@RequiredArgsConstructor
public class PasswordCredentialUpdateService implements PasswordCredentialUpdatePort {

    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UpdatedPasswordCredential updatePassword(UUID accountId, String rawPassword) {
        PasswordCredential credential = passwordCredentialRepository.findById(accountId)
                .orElseThrow(() -> new CustomException(ExceptionStatus.USER_NOT_FOUND));

        String encodedPassword = passwordEncoder.encode(rawPassword);
        credential.updatePasswordHash(encodedPassword);
        passwordCredentialRepository.save(credential);

        return new UpdatedPasswordCredential(encodedPassword);
    }
}
