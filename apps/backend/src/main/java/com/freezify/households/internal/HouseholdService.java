package com.freezify.households.internal;

import com.freezify.common.ApiException;
import com.freezify.households.HouseholdAccess;
import com.freezify.households.HouseholdDirectory;
import com.freezify.households.HouseholdEvents.HouseholdDeleted;
import com.freezify.households.HouseholdEvents.MemberRemoved;
import com.freezify.households.HouseholdRole;
import com.freezify.households.internal.HouseholdMemberRepository.MemberCount;
import com.freezify.users.UserAccount;
import com.freezify.users.Users;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdService implements HouseholdAccess, HouseholdDirectory {

    private final HouseholdRepository households;
    private final HouseholdMemberRepository members;
    private final HouseholdInvitationRepository invitations;
    private final Users users;
    private final HouseholdProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    HouseholdService(
            HouseholdRepository households,
            HouseholdMemberRepository members,
            HouseholdInvitationRepository invitations,
            Users users,
            HouseholdProperties properties,
            Clock clock,
            ApplicationEventPublisher events) {
        this.households = households;
        this.members = members;
        this.invitations = invitations;
        this.users = users;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
    }

    public record HouseholdView(UUID id, String name, HouseholdRole role, long memberCount, Instant createdAt) {}

    public record MemberView(UUID userId, String displayName, String email, HouseholdRole role, Instant joinedAt) {}

    /**
     * @param createdBy who generated the code; they and the owner can revoke it
     */
    public record InvitationView(String code, Instant expiresAt, UUID createdBy) {}

    @Override
    @Transactional(readOnly = true)
    public HouseholdRole requireMember(UUID householdId, UUID userId) {
        return members.findByHouseholdIdAndUserId(householdId, userId)
                .map(HouseholdMemberEntity::role)
                .orElseThrow(HouseholdService::householdNotFound);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireOwner(UUID householdId, UUID userId) {
        if (requireMember(householdId, userId) != HouseholdRole.OWNER) {
            throw ApiException.forbidden("NOT_HOUSEHOLD_OWNER", "Only the household owner can do this.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> memberIds(UUID householdId) {
        return members.findByHouseholdIdOrderByJoinedAtAsc(householdId).stream()
                .map(HouseholdMemberEntity::userId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> names(Collection<UUID> householdIds) {
        return households.findAllById(householdIds).stream()
                .collect(Collectors.toMap(HouseholdEntity::id, HouseholdEntity::name));
    }

    @Transactional
    public HouseholdView create(UUID userId, String name) {
        HouseholdEntity household = households.saveAndFlush(new HouseholdEntity(name.strip()));
        members.save(new HouseholdMemberEntity(household.id(), userId, HouseholdRole.OWNER));
        return new HouseholdView(household.id(), household.name(), HouseholdRole.OWNER, 1, household.createdAt());
    }

    @Transactional(readOnly = true)
    public List<HouseholdView> listFor(UUID userId) {
        List<HouseholdMemberEntity> memberships = members.findByUserIdOrderByJoinedAtAsc(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> ids =
                memberships.stream().map(HouseholdMemberEntity::householdId).toList();
        Map<UUID, HouseholdEntity> byId =
                households.findAllById(ids).stream().collect(Collectors.toMap(HouseholdEntity::id, Function.identity()));
        Map<UUID, Long> counts = members.countByHouseholdIds(ids).stream()
                .collect(Collectors.toMap(MemberCount::getHouseholdId, MemberCount::getMembers));
        return memberships.stream()
                .map(membership -> {
                    HouseholdEntity household = byId.get(membership.householdId());
                    return new HouseholdView(
                            household.id(),
                            household.name(),
                            membership.role(),
                            counts.getOrDefault(household.id(), 0L),
                            household.createdAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public HouseholdView get(UUID householdId, UUID userId) {
        HouseholdRole role = requireMember(householdId, userId);
        return view(load(householdId), role);
    }

    @Transactional
    public HouseholdView rename(UUID householdId, UUID userId, String name) {
        requireOwner(householdId, userId);
        HouseholdEntity household = load(householdId);
        household.rename(name.strip());
        return view(households.saveAndFlush(household), HouseholdRole.OWNER);
    }

    /** Deletes the household and, through the foreign keys, everything that belongs to it. */
    @Transactional
    public void delete(UUID householdId, UUID userId) {
        requireOwner(householdId, userId);
        households.deleteById(householdId);
        events.publishEvent(new HouseholdDeleted(householdId));
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(UUID householdId, UUID userId) {
        requireMember(householdId, userId);
        List<HouseholdMemberEntity> memberships = members.findByHouseholdIdOrderByJoinedAtAsc(householdId);
        Map<UUID, UserAccount> accounts = users
                .findAllById(memberships.stream().map(HouseholdMemberEntity::userId).toList())
                .stream()
                .collect(Collectors.toMap(UserAccount::id, Function.identity()));
        return memberships.stream()
                .filter(membership -> accounts.containsKey(membership.userId()))
                .map(membership -> {
                    UserAccount account = accounts.get(membership.userId());
                    return new MemberView(
                            account.id(),
                            account.displayName(),
                            account.email(),
                            membership.role(),
                            membership.joinedAt());
                })
                .toList();
    }

    /** A member may leave; the owner may remove anyone but themselves. */
    @Transactional
    public void removeMember(UUID householdId, UUID actorId, UUID targetUserId) {
        HouseholdRole actorRole = requireMember(householdId, actorId);
        boolean leaving = actorId.equals(targetUserId);
        if (leaving && actorRole == HouseholdRole.OWNER) {
            throw ApiException.conflict(
                    "OWNER_CANNOT_LEAVE",
                    "The owner cannot leave the household; hand it over to another member or delete it.");
        }
        if (!leaving && actorRole != HouseholdRole.OWNER) {
            throw ApiException.forbidden("NOT_HOUSEHOLD_OWNER", "Only the household owner can remove members.");
        }
        HouseholdMemberEntity target = members.findByHouseholdIdAndUserId(householdId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Member not found."));
        members.delete(target);
        events.publishEvent(new MemberRemoved(householdId, targetUserId));
    }

    @Transactional
    public InvitationView invite(UUID householdId, UUID userId) {
        requireMember(householdId, userId);
        Instant now = clock.instant();
        String code;
        do {
            code = InvitationCodes.generate();
        } while (invitations.existsByCode(code));
        HouseholdInvitationEntity invitation = invitations.save(
                new HouseholdInvitationEntity(householdId, code, userId, now, now.plus(properties.invitationTtl())));
        return view(invitation);
    }

    /** The codes that still let someone join. Any member sees them: any member can share them. */
    @Transactional(readOnly = true)
    public List<InvitationView> invitations(UUID householdId, UUID userId) {
        requireMember(householdId, userId);
        return invitations.findByHouseholdIdAndExpiresAtAfterOrderByExpiresAtAscIdAsc(householdId, clock.instant()).stream()
                .map(HouseholdService::view)
                .toList();
    }

    /**
     * Stops a code from letting anyone else join. Whoever generated it can revoke it, and so can the owner. Those
     * who already joined with it stay.
     */
    @Transactional
    public void revokeInvitation(UUID householdId, UUID userId, String rawCode) {
        HouseholdRole role = requireMember(householdId, userId);
        HouseholdInvitationEntity invitation = invitations
                .findByHouseholdIdAndCode(householdId, InvitationCodes.normalize(rawCode))
                .orElseThrow(() -> ApiException.notFound(
                        "INVITATION_NOT_FOUND", "The invitation code is invalid or has expired."));
        if (role != HouseholdRole.OWNER && !invitation.createdBy().equals(userId)) {
            throw ApiException.forbidden(
                    "NOT_HOUSEHOLD_OWNER", "Only the owner or whoever generated the code can revoke it.");
        }
        invitations.delete(invitation);
    }

    /**
     * Hands the household over to another member, who becomes its owner; the previous owner stays as a member and
     * can then leave. There is always exactly one owner.
     */
    @Transactional
    public void transferOwnership(UUID householdId, UUID ownerId, UUID newOwnerId) {
        requireOwner(householdId, ownerId);
        if (ownerId.equals(newOwnerId)) {
            return;
        }
        HouseholdMemberEntity newOwner = members.findByHouseholdIdAndUserId(householdId, newOwnerId)
                .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Member not found."));
        HouseholdMemberEntity owner = members.findByHouseholdIdAndUserId(householdId, ownerId)
                .orElseThrow(HouseholdService::householdNotFound);
        owner.becomes(HouseholdRole.MEMBER);
        newOwner.becomes(HouseholdRole.OWNER);
    }

    private static InvitationView view(HouseholdInvitationEntity invitation) {
        return new InvitationView(invitation.code(), invitation.expiresAt(), invitation.createdBy());
    }

    @Transactional
    public HouseholdView join(UUID userId, String rawCode) {
        HouseholdInvitationEntity invitation = invitations.findByCode(InvitationCodes.normalize(rawCode))
                .filter(candidate -> !candidate.isExpired(clock.instant()))
                .orElseThrow(() -> ApiException.notFound(
                        "INVITATION_NOT_FOUND", "The invitation code is invalid or has expired."));
        UUID householdId = invitation.householdId();
        if (members.findByHouseholdIdAndUserId(householdId, userId).isPresent()) {
            throw ApiException.conflict("ALREADY_MEMBER", "You already belong to this household.");
        }
        members.saveAndFlush(new HouseholdMemberEntity(householdId, userId, HouseholdRole.MEMBER));
        return view(load(householdId), HouseholdRole.MEMBER);
    }

    private HouseholdView view(HouseholdEntity household, HouseholdRole role) {
        return new HouseholdView(
                household.id(),
                household.name(),
                role,
                members.countByHouseholdId(household.id()),
                household.createdAt());
    }

    private HouseholdEntity load(UUID householdId) {
        return households.findById(householdId).orElseThrow(HouseholdService::householdNotFound);
    }

    private static ApiException householdNotFound() {
        return ApiException.notFound("HOUSEHOLD_NOT_FOUND", "Household not found.");
    }
}
