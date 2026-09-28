import { Fragment, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { downloadMyInvoicePdf, downloadMyStatementPdf, getMyInvoices, payAllOutstanding, payInvoice } from '../../api/billingApi'
import { Icon } from '../../components/resident/Icon'
import { downloadCsv } from '../../utils/csv'
import { downloadBlob } from '../../utils/downloadFile'
import RazorpaySimulatorModal from './RazorpaySimulatorModal'
import './ResidentBilling.css'

const PAGE_SIZE = 10

function ResidentBilling() {
  const { t } = useTranslation('resident')
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [expandedId, setExpandedId] = useState(null)
  const [page, setPage] = useState(0)
  const [payingInvoice, setPayingInvoice] = useState(null)
  const [downloadingId, setDownloadingId] = useState(null)
  const [pdfError, setPdfError] = useState('')
  const [payingAll, setPayingAll] = useState(false)
  const [downloadingStatement, setDownloadingStatement] = useState(false)

  useEffect(() => {
    getMyInvoices()
      .then(setInvoices)
      .catch(() => setError(t('billing.loadError')))
      .finally(() => setLoading(false))
  }, [t])

  function handlePaymentSuccess(invoiceId) {
    return payInvoice(invoiceId).then((updated) => {
      setInvoices((prev) => prev.map((inv) => (inv.id === updated.id ? updated : inv)))
      setPayingInvoice(null)
    })
  }

  function handlePayAllSuccess() {
    return payAllOutstanding().then((paid) => {
      const byId = new Map(paid.map((inv) => [inv.id, inv]))
      setInvoices((prev) => prev.map((inv) => byId.get(inv.id) ?? inv))
      setPayingAll(false)
    })
  }

  function handleDownloadStatement() {
    setPdfError('')
    setDownloadingStatement(true)
    downloadMyStatementPdf()
      .then((blob) => downloadBlob(blob, 'account-statement.pdf'))
      .catch(() => setPdfError(t('billing.pdfError')))
      .finally(() => setDownloadingStatement(false))
  }

  // Headers are translated; values stay raw (ISO dates, plain numbers, true/false).
  function handleExportCsv() {
    downloadCsv(
      'invoice-history.csv',
      [
        t('billing.csv.cycle'),
        t('billing.csv.consumption'),
        t('billing.csv.baseAmount'),
        t('billing.csv.tariffAmount'),
        t('billing.csv.commonAreaAmount'),
        t('billing.csv.totalAmount'),
        t('billing.csv.paid'),
        t('billing.csv.paidAt'),
        t('billing.csv.generatedAt'),
      ],
      invoices.map((inv) => [
        inv.periodMonth,
        inv.consumptionUnits,
        inv.baseAmount,
        inv.tariffAmount,
        inv.commonAreaAmount,
        inv.totalAmount,
        inv.paid,
        inv.paidAt ?? '',
        inv.generatedAt ?? '',
      ]),
    )
  }

  const pageCount = Math.max(1, Math.ceil(invoices.length / PAGE_SIZE))
  const pagedInvoices = useMemo(
    () => invoices.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE),
    [invoices, page],
  )

  function toggleExpanded(id) {
    setExpandedId((prev) => (prev === id ? null : id))
  }

  function handleDownloadPdf(invoice) {
    setPdfError('')
    setDownloadingId(invoice.id)
    downloadMyInvoicePdf(invoice.id)
      .then((blob) => downloadBlob(blob, `invoice-${invoice.periodMonth}.pdf`))
      .catch(() => setPdfError(t('billing.pdfError')))
      .finally(() => setDownloadingId(null))
  }

  if (loading) {
    return <p className="af-bills__empty">{t('billing.loading')}</p>
  }

  if (error) {
    return <div className="af-bills__error">{error}</div>
  }

  if (invoices.length === 0) {
    return (
      <div className="af-bills__panel">
        <p className="af-bills__empty">
          {t('billing.empty')}
        </p>
      </div>
    )
  }

  const unpaidInvoices = invoices.filter((inv) => !inv.paid)
  const outstandingBalance = unpaidInvoices.reduce((sum, inv) => sum + Number(inv.totalAmount), 0)
  const latestUnpaid = unpaidInvoices[0] ?? null

  return (
    <>
      <section className="af-bills__summary">
        <div className="af-bills__summary-main">
          <p className="af-bills__eyebrow">{t('billing.summary.outstandingBalance')}</p>
          <h2>₹{outstandingBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</h2>
          <p className="af-bills__summary-sub">
            {latestUnpaid ? t('billing.summary.billingCycle', { period: latestUnpaid.periodMonth }) : t('billing.summary.allPaid')}
          </p>
          <span className={`af-bills__badge af-bills__badge--${latestUnpaid ? 'unpaid' : 'paid'}`}>
            {latestUnpaid ? t('billing.status.unpaid') : t('billing.status.paid')}
          </span>
        </div>
        <div className="af-bills__summary-actions">
          {unpaidInvoices.length > 0 && (
            <button type="button" className="af-bills__cta" onClick={() => setPayingAll(true)}>
              {t('billing.summary.payTotal')}
            </button>
          )}
          <button
            type="button"
            className="af-bills__cta af-bills__cta--outline"
            disabled={downloadingStatement}
            onClick={handleDownloadStatement}
          >
            {downloadingStatement ? t('billing.table.downloading') : t('billing.summary.downloadSummary')}
          </button>
        </div>
      </section>

      <section className="af-bills__panel">
        <div className="af-bills__panel-heading">
          <h3>{t('billing.history.title')}</h3>
          <button type="button" className="af-bills__export" onClick={handleExportCsv}>
            {t('billing.history.exportCsv')}
          </button>
        </div>
        {pdfError && <div className="af-bills__error af-bills__error--inline">{pdfError}</div>}
        <div className="af-bills__table-wrap">
          <table className="af-bills__table">
            <thead>
              <tr>
                <th>{t('billing.table.cycle')}</th>
                <th>{t('billing.table.usage')}</th>
                <th>{t('billing.table.amountDue')}</th>
                <th>{t('billing.table.paymentStatus')}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {pagedInvoices.map((invoice) => (
                <Fragment key={invoice.id}>
                  <tr>
                    <td>{invoice.periodMonth}</td>
                    <td>{Number(invoice.consumptionUnits).toFixed(1)}</td>
                    <td>₹{Number(invoice.totalAmount).toLocaleString('en-IN')}</td>
                    <td>
                      <span className={`af-bills__badge af-bills__badge--${invoice.paid ? 'paid' : 'unpaid'}`}>
                        {invoice.paid ? t('billing.status.paid') : t('billing.status.unpaid')}
                      </span>
                    </td>
                    <td>
                      <div className="af-bills__row-actions">
                        <button type="button" onClick={() => toggleExpanded(invoice.id)}>
                          {expandedId === invoice.id ? t('billing.table.hideBreakdown') : t('billing.table.showBreakdown')}
                        </button>
                        <button
                          type="button"
                          className="af-bills__pdf-link"
                          disabled={downloadingId === invoice.id}
                          onClick={() => handleDownloadPdf(invoice)}
                        >
                          {downloadingId === invoice.id ? t('billing.table.downloading') : t('billing.table.pdf')}
                        </button>
                        {!invoice.paid && (
                          <button
                            type="button"
                            className="af-bills__pay-btn"
                            onClick={() => setPayingInvoice(invoice)}
                          >
                            {t('billing.table.pay')}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                  {expandedId === invoice.id && (
                    <tr className="af-bills__breakdown-row">
                      <td colSpan={5}>
                        <div className="af-bills__breakdown">
                          <div>
                            <h4>{t('billing.breakdown.householdTitle')}</h4>
                            {Number(invoice.baseAmount) > 0 && (
                              <div className="af-bills__breakdown-line">
                                <span>
                                  {t('billing.breakdown.basePrice', {
                                    units: Number(invoice.consumptionUnits).toFixed(1),
                                    rate: invoice.basePricePerUnit,
                                  })}
                                </span>
                                <strong>₹{Number(invoice.baseAmount).toLocaleString('en-IN')}</strong>
                              </div>
                            )}
                            {invoice.lineItems.map((line) => (
                              <div key={line.tierOrder} className="af-bills__breakdown-line">
                                <span>
                                  {t('billing.breakdown.tierLine', {
                                    tier: line.tierOrder,
                                    units: Number(line.unitsInTier).toFixed(1),
                                    rate: line.ratePerUnit,
                                  })}
                                </span>
                                <strong>₹{Number(line.amount).toLocaleString('en-IN')}</strong>
                              </div>
                            ))}
                            <div className="af-bills__breakdown-subtotal">
                              <span>{t('billing.breakdown.subtotalMetered')}</span>
                              <strong>
                                ₹{(Number(invoice.baseAmount) + Number(invoice.tariffAmount)).toLocaleString('en-IN')}
                              </strong>
                            </div>
                          </div>
                          <div>
                            <h4>{t('billing.breakdown.commonAreaTitle')}</h4>
                            {invoice.commonAreaCharges.length > 0 ? (
                              <>
                                {invoice.commonAreaCharges.map((charge) => (
                                  <div key={charge.costItemId ?? charge.label} className="af-bills__breakdown-line">
                                    <span>{charge.label}</span>
                                    <strong>₹{Number(charge.amount).toLocaleString('en-IN')}</strong>
                                  </div>
                                ))}
                                <div className="af-bills__breakdown-subtotal">
                                  <span>{t('billing.breakdown.subtotalShared')}</span>
                                  <strong>₹{Number(invoice.commonAreaAmount).toLocaleString('en-IN')}</strong>
                                </div>
                              </>
                            ) : (
                              <p className="af-bills__breakdown-empty">{t('billing.breakdown.noCommonArea')}</p>
                            )}
                          </div>
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>

        <div className="af-bills__pagination">
          <span className="af-bills__pagination-info">
            {t('billing.pagination.showing', {
              from: page * PAGE_SIZE + 1,
              to: Math.min(invoices.length, page * PAGE_SIZE + PAGE_SIZE),
              total: invoices.length,
            })}
          </span>
          <div className="af-bills__pagination-controls">
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              {t('billing.pagination.prev')}
            </button>
            <span className="af-bills__pagination-page">
              {t('billing.pagination.page', { page: page + 1, count: pageCount })}
            </span>
            <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
              {t('billing.pagination.next')}
            </button>
          </div>
        </div>
      </section>

      <section className="af-bills__panel af-bills__payment-panel">
        <div className="af-bills__payment-info">
          <span className="af-bills__payment-icon" aria-hidden="true">
            <Icon name="bill" />
          </span>
          <div>
            <strong>{t('billing.paymentMethods.title')}</strong>
            <p>{t('billing.paymentMethods.description')}</p>
          </div>
        </div>
        <button
          type="button"
          className="af-bills__cta af-bills__cta--outline"
          disabled
          title={t('billing.paymentMethods.manageUnavailable')}
        >
          {t('billing.paymentMethods.manage')}
        </button>
      </section>

      {payingInvoice && (
        <RazorpaySimulatorModal
          invoice={payingInvoice}
          onClose={() => setPayingInvoice(null)}
          onSuccess={() => handlePaymentSuccess(payingInvoice.id)}
        />
      )}

      {payingAll && (
        <RazorpaySimulatorModal
          amount={outstandingBalance}
          description={t('billing.summary.payAllDescription', { number: unpaidInvoices.length })}
          onClose={() => setPayingAll(false)}
          onSuccess={handlePayAllSuccess}
        />
      )}
    </>
  )
}

export default ResidentBilling
