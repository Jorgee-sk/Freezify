package com.freezify.households.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HouseholdRepository extends JpaRepository<HouseholdEntity, UUID> {}

interface HouseholdMemberRepository extends JpaRepository<HouseholdMemberEntity, UUID> {

    Optional<HouseholdMemberEntity> findByHouseholdIdAndUserId(UUID householdId, UUID userId);

    List<HouseholdMemberEntity> findByUserIdOrderByJoinedAtAsc(UUID userId);

    List<HouseholdMemberEntity> findByHouseholdIdOrderByJoinedAtAsc(UUID householdId);

    long countByHouseholdId(UUID householdId);

    @Query("select m.householdId as householdId, count(m) as members from HouseholdMemberEntity m"
            + " where m.householdId in :householdIds group by m.householdId")
    List<MemberCount> countByHouseholdIds(@Param("householdIds") Collection<UUID> householdIds);

    interface MemberCount {
        UUID getHouseholdId();

        long getMembers();
    }
}

interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitationEntity, UUID> {

    Optional<HouseholdInvitationEntity> findByCode(String code);

    boolean existsByCode(String code);

    List<HouseholdInvitationEntity> findByHouseholdIdAndExpiresAtAfterOrderByExpiresAtAscIdAsc(UUID householdId, Instant now);

    Optional<HouseholdInvitationEntity> findByHouseholdIdAndCode(UUID householdId, String code);
}
