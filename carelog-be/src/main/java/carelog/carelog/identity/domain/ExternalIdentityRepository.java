package carelog.carelog.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, Long> {

    Optional<ExternalIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    @Query("""
            select e.accountId as accountId, a.status as status
            from ExternalIdentity e
            join PlatformAccount a on a.id = e.accountId
            where e.provider = :provider and e.providerSubject = :providerSubject
            """)
    Optional<LinkedAccountProjection> findLinkedAccountByProviderAndProviderSubject(
            @Param("provider") String provider,
            @Param("providerSubject") String providerSubject
    );

    interface LinkedAccountProjection {
        UUID getAccountId();
        AccountStatus getStatus();
    }
}
