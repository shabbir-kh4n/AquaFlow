import { useTranslation } from 'react-i18next'
import {
  addPlatformSupportTicketMessage,
  getPlatformSupportTicketDetail,
  listPlatformSupportTickets,
  updatePlatformSupportTicketStatus,
} from '../../api/supportTicketApi'
import SupportTicketBoard from '../../components/support/SupportTicketBoard'

function SupportTickets() {
  const { t } = useTranslation('platform')

  return (
    <SupportTicketBoard
      title={t('supportTickets.title')}
      subtitle={t('supportTickets.subtitle')}
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
