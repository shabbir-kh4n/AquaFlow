import { Fragment, useEffect, useMemo, useState } from 'react'
import { downloadMyInvoicePdf, getMyInvoices, payInvoice } from '../../api/billingApi'
import { Icon } from '../../components/resident/Icon'
import { downloadBlob } from '../../utils/downloadFile'
import RazorpaySimulatorModal from './RazorpaySimulatorModal'
import './ResidentBilling.css'

const PAGE_SIZE = 10

function ResidentBilling() {
  const [invoices, setInvoices] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [expandedId, setExpandedId] = useState(null)
  const [page, setPage] = useState(0)
  const [payingInvoice, setPayingInvoice] = useState(null)
  const [downloadingId, setDownloadingId] = useState(null)
  const [pdfError, setPdfError] = useState('')

  useEffect(() => {
    getMyInvoices()
      .then(setInvoices)
      .catch(() => setError('Could not load your billing history. Please try again.'))
      .finally(() => setLoading(false))
  }, [])

  function handlePaymentSuccess(invoiceId) {
    return payInvoice(invoiceId).then((updated) => {
      setInvoices((prev) => prev.map((inv) => (inv.id === updated.id ? updated : inv)))
      setPayingInvoice(null)
    })
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
      .catch(() => setPdfError('Could not download the PDF. Please try again.'))
      .finally(() => setDownloadingId(null))
  }

  if (loading) {
    return <p className="af-bills__empty">Loading your billing history…</p>
  }

  if (error) {
    return <div className="af-bills__error">{error}</div>
  }

  if (invoices.length === 0) {
    return (
      <div className="af-bills__panel">
        <p className="af-bills__empty">
          No invoices yet — once your community finalizes a billing cycle, your bills will show up here.
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
          <p className="af-bills__eyebrow">Outstanding Balance</p>
          <h2>₹{outstandingBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</h2>
          <p className="af-bills__summary-sub">
            {latestUnpaid ? `Billing Cycle: ${latestUnpaid.periodMonth}` : 'All invoices are paid'}
          </p>
          <span className={`af-bills__badge af-bills__badge--${latestUnpaid ? 'unpaid' : 'paid'}`}>
            {latestUnpaid ? 'Unpaid' : 'Paid'}
          </span>
        </div>
        <div className="af-bills__summary-actions">
          <button
            type="button"
            className="af-bills__cta"
            disabled
            title="Online payment isn't available yet — settle bills with your community admin directly"
          >
            Pay Total Outstanding
          </button>
          <button
            type="button"
            className="af-bills__cta af-bills__cta--outline"
            disabled
            title="Bill summary download isn't available yet"
          >
            Download Bill Summary
          </button>
        </div>
      </section>

      <section className="af-bills__panel">
        <div className="af-bills__panel-heading">
          <h3>Invoice History</h3>
          <button type="button" className="af-bills__export" disabled title="CSV export isn't available yet">
            Export All to CSV
          </button>
        </div>
        {pdfError && <div className="af-bills__error af-bills__error--inline">{pdfError}</div>}
        <div className="af-bills__table-wrap">
          <table className="af-bills__table">
            <thead>
              <tr>
                <th>Cycle</th>
                <th>Usage (kL)</th>
                <th>Amount Due</th>
                <th>Payment Status</th>
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
                        {invoice.paid ? 'Paid' : 'Unpaid'}
                      </span>
                    </td>
                    <td>
                      <div className="af-bills__row-actions">
                        <button type="button" onClick={() => toggleExpanded(invoice.id)}>
                          {expandedId === invoice.id ? 'Hide Breakdown ↑' : 'Itemized Breakdown ↓'}
                        </button>
                        <button
                          type="button"
                          className="af-bills__pdf-link"
                          disabled={downloadingId === invoice.id}
                          onClick={() => handleDownloadPdf(invoice)}
                        >
                          {downloadingId === invoice.id ? 'Downloading…' : 'PDF'}
                        </button>
                        {!invoice.paid && (
                          <button
                            type="button"
                            className="af-bills__pay-btn"
                            onClick={() => setPayingInvoice(invoice)}
                          >
                            Pay
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
                            <h4>Household Consumption Breakdown</h4>
                            {invoice.lineItems.map((line) => (
                              <div key={line.tierOrder} className="af-bills__breakdown-line">
                                <span>
                                  Tier {line.tierOrder}: {Number(line.unitsInTier).toFixed(1)} kL × ₹{line.ratePerUnit}
                                </span>
                                <strong>₹{Number(line.amount).toLocaleString('en-IN')}</strong>
                              </div>
                            ))}
                            <div className="af-bills__breakdown-subtotal">
                              <span>Subtotal Metered Usage</span>
                              <strong>₹{Number(invoice.tariffAmount).toLocaleString('en-IN')}</strong>
                            </div>
                          </div>
                          <div>
                            <h4>Common Area Apportionment</h4>
                            {invoice.commonAreaCharges.length > 0 ? (
                              <>
                                {invoice.commonAreaCharges.map((charge) => (
                                  <div key={charge.costItemId ?? charge.label} className="af-bills__breakdown-line">
                                    <span>{charge.label}</span>
                                    <strong>₹{Number(charge.amount).toLocaleString('en-IN')}</strong>
                                  </div>
                                ))}
                                <div className="af-bills__breakdown-subtotal">
                                  <span>Subtotal Shared Area</span>
                                  <strong>₹{Number(invoice.commonAreaAmount).toLocaleString('en-IN')}</strong>
                                </div>
                              </>
                            ) : (
                              <p className="af-bills__breakdown-empty">No shared common-area charges for this cycle.</p>
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
            Showing {page * PAGE_SIZE + 1}–{Math.min(invoices.length, page * PAGE_SIZE + PAGE_SIZE)} of{' '}
            {invoices.length}
          </span>
          <div className="af-bills__pagination-controls">
            <button type="button" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              ← Prev
            </button>
            <span className="af-bills__pagination-page">
              Page {page + 1} of {pageCount}
            </span>
            <button type="button" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)}>
              Next →
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
            <strong>Payment methods (preview)</strong>
            <p>Online payment isn't available yet — bills are settled with your community admin directly.</p>
          </div>
        </div>
        <button
          type="button"
          className="af-bills__cta af-bills__cta--outline"
          disabled
          title="Payment methods aren't available yet"
        >
          Manage Payment Methods
        </button>
      </section>

      {payingInvoice && (
        <RazorpaySimulatorModal
          invoice={payingInvoice}
          onClose={() => setPayingInvoice(null)}
          onSuccess={() => handlePaymentSuccess(payingInvoice.id)}
        />
      )}
    </>
  )
}

export default ResidentBilling
