package carelog.carelog.auth.app.adapter;

import carelog.carelog.auth.app.port.RefreshSession;
import carelog.carelog.auth.domain.RefreshToken;
import carelog.carelog.auth.domain.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyTokenSessionAdapterTest {

    private static final UUID ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private RefreshTokenRepository refreshTokenRepository;

    private LegacyTokenSessionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LegacyTokenSessionAdapter(refreshTokenRepository);
    }

    private RefreshToken newRefreshToken(UUID accountId, String token, OffsetDateTime expiresAt) {
        return RefreshToken.builder()
                .accountId(accountId)
                .refreshToken(token)
                .tokenExpiresAt(expiresAt)
                .build();
    }

    @DisplayName("replaceForAccount는 기존 Account의 토큰을 삭제한 뒤 신규 토큰을 저장하는 순서를 보존한다")
    @Test
    void replaceForAccount_deletesThenSavesInOrder() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-08-01T00:00:00Z");

        adapter.replaceForAccount(ACCOUNT_ID, "new-token", expiresAt);

        InOrder inOrder = inOrder(refreshTokenRepository);
        inOrder.verify(refreshTokenRepository).deleteByAccountId(ACCOUNT_ID);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        inOrder.verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(saved.getRefreshToken()).isEqualTo("new-token");
        assertThat(saved.getTokenExpiresAt()).isEqualTo(expiresAt);
    }

    @DisplayName("findByToken은 RefreshToken Entity를 RefreshSession으로 매핑한다")
    @Test
    void findByToken_mapsEntityToRefreshSession() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        RefreshToken entity = newRefreshToken(ACCOUNT_ID, "raw-token", expiresAt);
        when(refreshTokenRepository.findByRefreshToken("raw-token")).thenReturn(Optional.of(entity));

        Optional<RefreshSession> result = adapter.findByToken("raw-token");

        assertThat(result).isPresent();
        assertThat(result.get().tokenValue()).isEqualTo("raw-token");
        assertThat(result.get().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(result.get().expiresAt()).isEqualTo(expiresAt);
    }

    @DisplayName("findByToken은 저장된 토큰이 없으면 빈 Optional을 반환한다")
    @Test
    void findByToken_missing_returnsEmpty() {
        when(refreshTokenRepository.findByRefreshToken("missing-token")).thenReturn(Optional.empty());

        assertThat(adapter.findByToken("missing-token")).isEmpty();
    }

    @DisplayName("rotate는 Entity를 다시 조회해 토큰/만료시각을 갱신하고 별도 save를 호출하지 않는다")
    @Test
    void rotate_updatesReloadedEntity() {
        OffsetDateTime oldExpiry = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        OffsetDateTime newExpiry = OffsetDateTime.parse("2026-08-15T00:00:00Z");
        RefreshToken entity = newRefreshToken(ACCOUNT_ID, "old-token", oldExpiry);
        RefreshSession session = new RefreshSession("old-token", ACCOUNT_ID, oldExpiry);
        when(refreshTokenRepository.findByRefreshToken("old-token")).thenReturn(Optional.of(entity));

        adapter.rotate(session, "new-token", newExpiry);

        assertThat(entity.getRefreshToken()).isEqualTo("new-token");
        assertThat(entity.getTokenExpiresAt()).isEqualTo(newExpiry);
        verify(refreshTokenRepository, never()).save(entity);
    }

    @DisplayName("rotate는 세션의 만료 여부를 스스로 판단하지 않고, 이미 만료된 Entity도 그대로 갱신한다")
    @Test
    void rotate_doesNotCheckExpiryItself() {
        OffsetDateTime expiredAt = OffsetDateTime.parse("2020-01-01T00:00:00Z");
        OffsetDateTime newExpiry = OffsetDateTime.parse("2026-08-15T00:00:00Z");
        RefreshToken alreadyExpiredEntity = newRefreshToken(ACCOUNT_ID, "expired-token", expiredAt);
        RefreshSession session = new RefreshSession("expired-token", ACCOUNT_ID, expiredAt);
        when(refreshTokenRepository.findByRefreshToken("expired-token")).thenReturn(Optional.of(alreadyExpiredEntity));

        adapter.rotate(session, "new-token", newExpiry);

        assertThat(alreadyExpiredEntity.getRefreshToken()).isEqualTo("new-token");
        assertThat(alreadyExpiredEntity.getTokenExpiresAt()).isEqualTo(newExpiry);
    }

    @DisplayName("deleteForAccount는 RefreshTokenRepository.deleteByAccountId에 위임한다")
    @Test
    void deleteForAccount_delegatesToRepository() {
        adapter.deleteForAccount(ACCOUNT_ID);

        verify(refreshTokenRepository).deleteByAccountId(ACCOUNT_ID);
    }

    @DisplayName("delete는 세션을 다시 조회해 Entity 기준으로 삭제한다")
    @Test
    void delete_reloadsEntityThenDeletes() {
        OffsetDateTime expiresAt = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        RefreshToken entity = newRefreshToken(ACCOUNT_ID, "raw-token", expiresAt);
        RefreshSession session = new RefreshSession("raw-token", ACCOUNT_ID, expiresAt);
        when(refreshTokenRepository.findByRefreshToken("raw-token")).thenReturn(Optional.of(entity));

        adapter.delete(session);

        verify(refreshTokenRepository).delete(entity);
    }
}
