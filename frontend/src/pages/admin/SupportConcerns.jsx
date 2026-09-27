import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  addAdminSupportTicketInboxMessage,
  addAdminSupportTicketMessage,
  createAdminSupportTicket,
  getAdminSupportTicketDetail,
  getAdminSupportTicketInboxDetail,
  listAdminSupportTickets,
  listAdminSupportTicketsInbox,
} from '../../api/supportTicketApi'
import SupportTicketBoard from '../../components/support/SupportTicketBoard'
import './SupportConcerns.css'

function SupportConcerns() {
  const { t } = useTranslation('admin')
  const [tab, setTab] = useState('mine')

  return (
    <div className="af-support-concerns">
      <div className="af-support-concerns__tabs">
        <button
          type="button"
          className={`af-support-concerns__tab${tab === 'mine' ? ' af-support-concerns__tab--active' : ''}`}
          onClick={() => setTab('mine')}
        >
          {t('support.tabs.mine')}
        </button>
        <button
          type="button"
          className={`af-support-concerns__tab${tab === 'inbox' ? ' af-support-concerns__tab--active' : ''}`}
          onClick={() => setTab('inbox')}
        >
          {t('support.tabs.inbox')}
        </button>
      </div>

      {tab === 'mine' ? (
        <SupportTicketBoard
          key="mine"
          title={t('support.mine.title')}
          subtitle={t('support.mine.subtitle')}
          listFn={listAdminSupportTickets}
          createFn={createAdminSupportTicket}
          getDetailFn={getAdminSupportTicketDetail}
          addMessageFn={addAdminSupportTicketMessage}
          emptyCreateHint={t('support.mine.emptyCreateHint')}
          selfRole="ADMIN"
        />
      ) : (
        <SupportTicketBoard
          key="inbox"
          title={t('support.inbox.title')}
          subtitle={t('support.inbox.subtitle')}
          listFn={listAdminSupportTicketsInbox}
          getDetailFn={getAdminSupportTicketInboxDetail}
          addMessageFn={addAdminSupportTicketInboxMessage}
          selfRole="ADMIN"
          showRecipients
        />
      )}
    </div>
  )
}

export default SupportConcerns
