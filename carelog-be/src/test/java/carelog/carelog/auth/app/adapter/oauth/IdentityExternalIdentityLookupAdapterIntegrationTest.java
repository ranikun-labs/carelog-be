package carelog.carelog.auth.app.adapter.oauth;

import carelog.carelog.CarelogApplication;
import carelog.carelog.PostgreSqlTestContainerConfiguration;
import carelog.carelog.auth.app.port.oauth.LinkedAccountStatus;
import carelog.carelog.identity.domain.ExternalIdentity;
import carelog.carelog.identity.domain.ExternalIdentityRepository;
import carelog.carelog.identity.domain.PlatformAccount;
import carelog.carelog.identity.domain.PlatformAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = CarelogApplication.class)
@ActiveProfiles("test")
@Import(PostgreSqlTestContainerConfiguration.class)
@Transactional
class IdentityExternalIdentityLookupAdapterIntegrationTest {

    @Autowired private IdentityExternalIdentityLookupAdapter adapter;
    @Autowired private ExternalIdentityRepository externalIdentityRepository;
    @Autowired private PlatformAccountRepository platformAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void provider와_subject가_일치하는_연결계정과_ACTIVE상태를_조회한다() {
        PlatformAccount account = platformAccountRepository.saveAndFlush(PlatformAccount.create("snapshot@example.com"));
        externalIdentityRepository.saveAndFlush(ExternalIdentity.create(account.getId(), "neutral", "subject-1", null));

        assertThat(adapter.findByProviderSubject("neutral", "subject-1"))
                .hasValueSatisfying(view -> {
                    assertThat(view.accountId()).isEqualTo(account.getId());
                    assertThat(view.status()).isEqualTo(LinkedAccountStatus.ACTIVE);
                });
        assertThat(adapter.findByProviderSubject("other", "subject-1")).isEmpty();
    }

    @Test
    void INACTIVE_계정은_neutral_상태로_변환한다() {
        PlatformAccount account = platformAccountRepository.saveAndFlush(PlatformAccount.create("snapshot@example.com"));
        externalIdentityRepository.saveAndFlush(ExternalIdentity.create(account.getId(), "neutral", "subject-2", null));
        jdbcTemplate.update("update platform_accounts set status = 'INACTIVE' where id = ?", account.getId());

        assertThat(adapter.findByProviderSubject("neutral", "subject-2"))
                .hasValueSatisfying(view -> assertThat(view.status()).isEqualTo(LinkedAccountStatus.INACTIVE));
    }

    @Test
    void 같은_subject라도_provider가_다르면_각각_자기_account만_격리해_조회한다() {
        PlatformAccount firstAccount = platformAccountRepository.saveAndFlush(PlatformAccount.create("first@example.com"));
        PlatformAccount secondAccount = platformAccountRepository.saveAndFlush(PlatformAccount.create("second@example.com"));
        externalIdentityRepository.saveAndFlush(ExternalIdentity.create(firstAccount.getId(), "kakao", "same-subject", null));
        externalIdentityRepository.saveAndFlush(ExternalIdentity.create(secondAccount.getId(), "google", "same-subject", null));

        assertThat(adapter.findByProviderSubject("kakao", "same-subject"))
                .hasValueSatisfying(view -> assertThat(view.accountId()).isEqualTo(firstAccount.getId()));
        assertThat(adapter.findByProviderSubject("google", "same-subject"))
                .hasValueSatisfying(view -> assertThat(view.accountId()).isEqualTo(secondAccount.getId()));
    }
}
