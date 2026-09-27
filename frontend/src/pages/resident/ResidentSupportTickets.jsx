import { useTranslation } from 'react-i18next'
import {
  addMySupportTicketMessage,
  createMySupportTicket,
  getMySupportTicketDetail,
  listMySupportTickets,
} from '../../api/supportTicketApi'
import SupportTicketBoard from '../../components/support/SupportTicketBoard'

function ResidentSupportTickets() {
  const { t } = useTranslation('resident')

  return (
    <SupportTicketBoard
      title={t('support.title')}
      subtitle={t('support.subtitle')}
      listFn={listMySupportTickets}
      createFn={createMySupportTicket}
      getDetailFn={getMySupportTicketDetail}
      addMessageFn={addMySupportTicketMessage}
      emptyCreateHint={t('support.emptyCreateHint')}
      selfRole="RESIDENT"
      recipientPicker
    />
  )
}

export default ResidentSupportTickets
