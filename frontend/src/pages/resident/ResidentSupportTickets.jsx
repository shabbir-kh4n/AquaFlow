import {
  addMySupportTicketMessage,
  createMySupportTicket,
  getMySupportTicketDetail,
  listMySupportTickets,
} from '../../api/supportTicketApi'
import SupportTicketBoard from '../../components/support/SupportTicketBoard'

function ResidentSupportTickets() {
  return (
    <SupportTicketBoard
      title="Resident Support & Concerns Desk"
      subtitle="Submit water leakage reports, meter calibration requests, or billing inquiries directly to your community admin and/or System Admin."
      listFn={listMySupportTickets}
      createFn={createMySupportTicket}
      getDetailFn={getMySupportTicketDetail}
      addMessageFn={addMySupportTicketMessage}
      emptyCreateHint="Describe your issue or concern below, and choose who should handle it."
      selfRole="RESIDENT"
      recipientPicker
    />
  )
}

export default ResidentSupportTickets
