import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { householdsApi } from '../api/endpoints'
import type { Member } from '../api/endpoints'
import { queryKeys } from '../api/queryKeys'
import { useAuth } from '../auth/useAuth'
import { ErrorMessage } from '../components/ErrorMessage'
import { formatDate } from '../i18n'

export function HouseholdDetailPage() {
  const { householdId = '' } = useParams()
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [draftName, setDraftName] = useState<string | null>(null)

  const household = useQuery({
    queryKey: queryKeys.household(householdId),
    queryFn: () => householdsApi.get(householdId),
  })
  const members = useQuery({
    queryKey: queryKeys.members(householdId),
    queryFn: () => householdsApi.members(householdId),
    enabled: household.isSuccess,
  })

  const invitations = useQuery({
    queryKey: queryKeys.invitations(householdId),
    queryFn: () => householdsApi.invitations(householdId),
    enabled: household.isSuccess,
  })

  const refreshAll = () => queryClient.invalidateQueries({ queryKey: queryKeys.households })
  const leaveToList = async () => {
    await navigate('/')
    queryClient.removeQueries({ queryKey: queryKeys.household(householdId) })
    await refreshAll()
  }

  const rename = useMutation({
    mutationFn: (name: string) => householdsApi.rename(householdId, name),
    onSuccess: async () => {
      setDraftName(null)
      await refreshAll()
    },
  })
  const invite = useMutation({
    mutationFn: () => householdsApi.invite(householdId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.invitations(householdId) }),
  })
  const revoke = useMutation({
    mutationFn: (code: string) => householdsApi.revokeInvitation(householdId, code),
    onSuccess: () => {
      invite.reset()
      return queryClient.invalidateQueries({ queryKey: queryKeys.invitations(householdId) })
    },
  })
  const transfer = useMutation({
    mutationFn: (userId: string) => householdsApi.transferOwnership(householdId, userId),
    onSuccess: refreshAll,
  })
  const removeMember = useMutation({
    mutationFn: (userId: string) => householdsApi.removeMember(householdId, userId),
    onSuccess: (_, userId) => (userId === user?.id ? leaveToList() : refreshAll()),
  })
  const remove = useMutation({
    mutationFn: () => householdsApi.remove(householdId),
    onSuccess: leaveToList,
  })

  if (household.isPending) return <p className="muted">{t('app.loading')}</p>
  if (household.isError) {
    return (
      <>
        <ErrorMessage error={household.error} />
        <Link to="/">{t('households.back')}</Link>
      </>
    )
  }

  const { name, role } = household.data
  const isOwner = role === 'OWNER'

  function submitRename(event: FormEvent) {
    event.preventDefault()
    if (draftName !== null) rename.mutate(draftName)
  }

  // Destructive actions always ask first.
  function confirmRemove(member: Member) {
    if (window.confirm(t('households.confirmRemove', { name: member.displayName }))) {
      removeMember.mutate(member.userId)
    }
  }
  function confirmTransfer(member: Member) {
    if (window.confirm(t('households.confirmTransfer', { name: member.displayName }))) {
      transfer.mutate(member.userId)
    }
  }
  function confirmRevoke(code: string) {
    if (window.confirm(t('households.confirmRevoke', { code }))) revoke.mutate(code)
  }
  function confirmLeave() {
    if (user && window.confirm(t('households.confirmLeave', { name }))) removeMember.mutate(user.id)
  }
  function confirmDelete() {
    if (window.confirm(t('households.confirmDelete', { name }))) remove.mutate()
  }

  return (
    <>
      <Link to={`/households/${householdId}`} className="back-link">
        ← {t('households.backToInventory')}
      </Link>

      {draftName === null ? (
        <div className="title-row">
          <h1>{name}</h1>
          <span className={`badge ${isOwner ? 'badge-owner' : ''}`}>{t(`households.role.${role}`)}</span>
          {isOwner && (
            <button type="button" className="button ghost" onClick={() => setDraftName(name)}>
              {t('households.rename')}
            </button>
          )}
        </div>
      ) : (
        <form className="title-row" onSubmit={submitRename}>
          <input
            type="text"
            aria-label={t('households.nameLabel')}
            required
            maxLength={80}
            autoFocus
            value={draftName}
            onChange={(event) => setDraftName(event.target.value)}
          />
          <button type="submit" className="button primary" disabled={rename.isPending}>
            {t('households.save')}
          </button>
          <button type="button" className="button ghost" onClick={() => setDraftName(null)}>
            {t('households.cancel')}
          </button>
        </form>
      )}
      <ErrorMessage error={rename.error} />

      <div className="grid-two">
        <section className="card" aria-labelledby="members-title">
          <h2 id="members-title">{t('households.membersTitle')}</h2>
          {members.isPending && <p className="muted">{t('app.loading')}</p>}
          <ErrorMessage error={members.error} />
          <ul className="member-list">
            {members.data?.map((member) => (
              <li key={member.userId}>
                <div>
                  <strong>{member.displayName}</strong>
                  {member.userId === user?.id && <span className="muted"> ({t('households.you')})</span>}
                  <div className="muted small">
                    {member.email} · {t(`households.role.${member.role}`)} ·{' '}
                    {t('households.joined', { date: formatDate(member.joinedAt) })}
                  </div>
                </div>
                {isOwner && member.userId !== user?.id && (
                  <span className="button-row">
                    <button
                      type="button"
                      className="button ghost"
                      aria-label={t('households.transferTo', { name: member.displayName })}
                      disabled={transfer.isPending}
                      onClick={() => confirmTransfer(member)}
                    >
                      {t('households.transfer')}
                    </button>
                    <button
                      type="button"
                      className="button ghost danger"
                      disabled={removeMember.isPending}
                      onClick={() => confirmRemove(member)}
                    >
                      {t('households.remove')}
                    </button>
                  </span>
                )}
              </li>
            ))}
          </ul>
          <ErrorMessage error={removeMember.error ?? transfer.error} />
        </section>

        <section className="card" aria-labelledby="invite-title">
          <h2 id="invite-title">{t('households.inviteTitle')}</h2>
          <p className="muted">{t('households.inviteHelp')}</p>
          {invite.data && (
            <div className="invite-code" aria-live="polite">
              <span className="muted small">{t('households.inviteCode')}</span>
              <output>{invite.data.code}</output>
              <span className="muted small">
                {t('households.inviteExpires', { date: formatDate(invite.data.expiresAt) })}
              </span>
            </div>
          )}
          <ErrorMessage error={invite.error} />
          <button type="button" className="button" disabled={invite.isPending} onClick={() => invite.mutate()}>
            {t('households.inviteGenerate')}
          </button>
          {(invitations.data?.length ?? 0) > 0 && (
            <>
              <h3 id="active-codes-title">{t('households.activeCodes')}</h3>
              <p className="muted small">{t('households.activeCodesHelp')}</p>
              <ul className="member-list" aria-labelledby="active-codes-title">
                {invitations.data?.map((invitation) => (
                  <li key={invitation.code}>
                    <div>
                      <strong>{invitation.code}</strong>
                      <div className="muted small">
                        {t('households.inviteExpires', { date: formatDate(invitation.expiresAt) })}
                      </div>
                    </div>
                    {(isOwner || invitation.createdBy === user?.id) && (
                      <button
                        type="button"
                        className="button ghost danger"
                        aria-label={t('households.revokeCode', { code: invitation.code })}
                        disabled={revoke.isPending}
                        onClick={() => confirmRevoke(invitation.code)}
                      >
                        {t('households.revoke')}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </>
          )}
          <ErrorMessage error={invitations.error ?? revoke.error} />
        </section>
      </div>

      <div className="danger-zone">
        <ErrorMessage error={remove.error} />
        {isOwner ? (
          <>
            <button type="button" className="button danger" disabled={remove.isPending} onClick={confirmDelete}>
              {t('households.delete')}
            </button>
            {household.data.memberCount > 1 && <p className="muted small">{t('households.ownerLeaves')}</p>}
          </>
        ) : (
          <button type="button" className="button danger" disabled={removeMember.isPending} onClick={confirmLeave}>
            {t('households.leave')}
          </button>
        )}
      </div>
    </>
  )
}
