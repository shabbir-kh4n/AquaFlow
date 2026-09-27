import {
  addPlatformSupportTicketMessage,
  getPlatformSupportTicketDetail,
  listPlatformSupportTickets,
  updatePlatformSupportTicketStatus,
} from '../../api/supportTicketApi'
import SupportTicketBoard from '../../components/support/SupportTicketBoard'

function SupportTickets() {
  return (
    <SupportTicketBoard
      title="Support Tickets"
      subtitle="Support tickets and concerns raised by residents and community admins, platform-wide."
      listFn={listPlatformSupportTickets}
      getDetailFn={getPlatformSupportTicketDetail}
      addMessageFn={addPlatformSupportTicketMessage}
      updateStatusFn={updatePlatformSupportTicketStatus}
      showRaiser
      showRecipients
      selfRole="SYSTEM_ADMIN"
    />
  )
}

export default SupportTickets
