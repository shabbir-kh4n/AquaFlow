import { useState } from 'react'
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
  const [tab, setTab] = useState('mine')

  return (
    <div className="af-support-concerns">
      <div className="af-support-concerns__tabs">
        <button
          type="button"
          className={`af-support-concerns__tab${tab === 'mine' ? ' af-support-concerns__tab--active' : ''}`}
          onClick={() => setTab('mine')}
        >
          My Tickets
        </button>
        <button
          type="button"
          className={`af-support-concerns__tab${tab === 'inbox' ? ' af-support-concerns__tab--active' : ''}`}
          onClick={() => setTab('inbox')}
        >
          Resident Tickets
        </button>
      </div>

      {tab === 'mine' ? (
        <SupportTicketBoard
          key="mine"
          title="My Support Tickets"
          subtitle="Raise a question or concern about your community to the System Admin."
          listFn={listAdminSupportTickets}
          createFn={createAdminSupportTicket}
          getDetailFn={getAdminSupportTicketDetail}
          addMessageFn={addAdminSupportTicketMessage}
          emptyCreateHint="Raise a question or concern about your community. A System Admin will review and respond here."
          selfRole="ADMIN"
        />
      ) : (
        <SupportTicketBoard
          key="inbox"
          title="Resident Tickets"
          subtitle="Support tickets and concerns residents in your community have addressed to you."
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
