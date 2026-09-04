import { useEffect, useMemo, useState } from 'react'
import {
  archiveBillingCycle,
  finalizeBillingCycle,
  getBillingCycleDetail,
  listBillingCycles,
  openBillingCycle,
  setInvoicePaymentStatus,
} from '../../api/billingApi'
import './BillingCycles.css'

const CYCLES_PAGE_SIZE = 4
const DETAIL_PAGE_SIZE = 10

function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

const STATUS_LABEL = {
  OPEN: 'Open',
  FINALIZED: 'Finalized',
  ARCHIVED: 'Archived',
}

function BillingCycles() {
  const [cycles, setCycles] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [newMonth, setNewMonth] = useState(currentMonth())
  const [opening, setOpening] = useState(false)
  const [openError, setOpenError] = useState('')

  const [selectedId, setSelectedId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  const [busyId, setBusyId] = useState(null)
  const [actionError, setActionError] = useState('')

  const [paymentFilter, setPaymentFilter] = useState('all')
  const [paymentBusyId, setPaymentBusyId] = useState(null)
  const [detailPage, setDetailPage] = useState(0)
  const [detailPageResetKey, setDetailPageResetKey] = useState('')
  const [cyclesPage, setCyclesPage] = useState(0)
  const [cyclesPageResetKey, setCyclesPageResetKey] = useState(-1)

  // `silent` refreshes (after finalize/archive/open) update the table's data in place without
  // blanking it out to a "Loading…" placeholder first — flashing the whole panel away and back
  // on every click reads as the page re-rendering, even though only this component re-renders.
  function loadCycles(silent = false) {
    if (!silent) setLoading(true)
    setLoadError('')
    return listBillingCycles()
      .then(setCycles)
      .catch(() => setLoadError('Could not load billing cycles. Please try again.'))
      .finally(() => {
        if (!silent) setLoading(false)
      })
  }

  useEffect(() => {
    listBillingCycles()
      .then(setCycles)
      .catch(() => setLoadError('Could not load billing cycles. Please try again.'))
      .finally(() => setLoading(false))
  }, [])

  function selectCycle(id) {
    setSelectedId(id)
    setDetail(null)
    setPaymentFilter('all')
    setDetailPage(0)
    setDetailLoading(true)
    getBillingCycleDetail(id)
      .then(setDetail)
      .catch(() => setDetail(null))
      .finally(() => setDetailLoading(false))
  }

  function refreshDetailSilently(id) {
    return getBillingCycleDetail(id)
      .then(setDetail)
      .catch(() => {
        // Keep showing the last-known detail rather than clearing it on a background refresh failure.
      })
  }

  async function handleTogglePaid(invoice) {
    setPaymentBusyId(invoice.id)
    try {
      const updated = await setInvoicePaymentStatus(invoice.id, !invoice.paid)
      setDetail((prev) => ({
        ...prev,
        invoices: prev.invoices.map((inv) => (inv.id === updated.id ? updated : inv)),
      }))
    } catch {
      setActionError('Could not update this invoice\'s payment status.')
    } finally {
      setPaymentBusyId(null)
    }
  }

  const filteredInvoices = useMemo(() => {
    const invoices = detail?.invoices ?? []
    if (paymentFilter === 'paid') return invoices.filter((inv) => inv.paid)
    if (paymentFilter === 'unpaid') return invoices.filter((inv) => !inv.paid)
    return invoices
  }, [detail, paymentFilter])

  const detailPageCount = Math.max(1, Math.ceil(filteredInvoices.length / DETAIL_PAGE_SIZE))

  const detailResetKey = `${paymentFilter}|${selectedId}`
  if (detailResetKey !== detailPageResetKey) {
    setDetailPageResetKey(detailResetKey)
    setDetailPage(0)
  }

  const pagedInvoices = useMemo(
    () => filteredInvoices.slice(detailPage * DETAIL_PAGE_SIZE, detailPage * DETAIL_PAGE_SIZE + DETAIL_PAGE_SIZE),
    [filteredInvoices, detailPage],
  )

  const cyclesPageCount = Math.max(1, Math.ceil(cycles.length / CYCLES_PAGE_SIZE))

  if (cycles.length !== cyclesPageResetKey) {
    setCyclesPageResetKey(cycles.length)
    setCyclesPage(0)
  }

  const pagedCycles = useMemo(
    () => cycles.slice(cyclesPage * CYCLES_PAGE_SIZE, cyclesPage * CYCLES_PAGE_SIZE + CYCLES_PAGE_SIZE),
    [cycles, cyclesPage],
  )

  async function handleOpen(event) {
    event.preventDefault()
    setOpenError('')

    if (!newMonth) {
      setOpenError('Please choose a month to open.')
      return
    }

    setOpening(true)
    try {
      await openBillingCycle(newMonth)
      await loadCycles(true)
    } catch (err) {
      setOpenError(err.response?.data?.message || 'Could not open a billing cycle for that month.')
    } finally {
      setOpening(false)
    }
  }

  async function handleFinalize(id) {
    setActionError('')
    setBusyId(id)
    try {
      await finalizeBillingCycle(id)
      await loadCycles(true)
      if (selectedId === id) {
        await refreshDetailSilently(id)
      }
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not finalize this billing cycle.')
    } finally {
      setBusyId(null)
    }
  }

  async function handleArchive(id) {
    setActionError('')
    setBusyId(id)
    try {
      await archiveBillingCycle(id)
      await loadCycles(true)
      if (selectedId === id) {
        await refreshDetailSilently(id)
      }
    } catch (err) {
      setActionError(err.response?.data?.message || 'Could not archive this billing cycle.')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <>
      <section className="af-billing__panel">
        <h2>Open a New Billing Cycle</h2>
        <form className="af-billing__open-form" onSubmit={handleOpen}>
          {openError && <div className="af-form__error">{openError}</div>}
          <div className="af-billing__open-row">
            <input
              type="month"
              value={newMonth}
              onChange={(e) => setNewMonth(e.target.value)}
              aria-label="Billing period month"
            />
            <button type="submit" className="af-billing__open-btn" disabled={opening}>
              {opening ? 'Opening…' : 'Open Cycle'}
            </button>
          </div>
        </form>
      </section>

      <section className="af-billing__panel">
        <h2>Billing Cycles</h2>
        {actionError && <div className="af-form__error">{actionError}</div>}
        {loadError && <div className="af-form__error">{loadError}</div>}

        {loading ? (
          <p className="af-billing__empty">Loading billing cycles…</p>
        ) : cycles.length === 0 ? (
          <p className="af-billing__empty">No billing cycles yet. Open one above to get started.</p>
        ) : (
          <table className="af-billing__table">
            <thead>
              <tr>
                <th>Period</th>
                <th>Status</th>
                <th>Households Billed</th>
                <th>Total Billed</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pagedCycles.map((cycle) => (
                <tr
                  key={cycle.id}
                  className={selectedId === cycle.id ? 'af-billing__row--selected' : ''}
                  onClick={() => {
                    if (selectedId !== cycle.id) selectCycle(cycle.id)
                  }}
                >
                  <td>{cycle.periodMonth}</td>
                  <td>
                    <span className={`af-billing__badge af-billing__badge--${cycle.status.toLowerCase()}`}>
                      {STATUS_LABEL[cycle.status] || cycle.status}
                    </span>
                  </td>
                  <td>{cycle.invoiceCount}</td>
                  <td>₹{Number(cycle.totalBilled).toLocaleString('en-IN')}</td>
                  <td onClick={(e) => e.stopPropagation()}>
                    {cycle.status === 'OPEN' && (
                      <button
                        type="button"
                        className="af-billing__action-link"
                        disabled={busyId === cycle.id}
                        onClick={() => handleFinalize(cycle.id)}
                      >
                        {busyId === cycle.id ? 'Finalizing…' : 'Finalize'}
                      </button>
                    )}
                    {cycle.status === 'FINALIZED' && (
                      <button
                        type="button"
                        className="af-billing__action-link"
                        disabled={busyId === cycle.id}
                        onClick={() => handleArchive(cycle.id)}
                      >
                        {busyId === cycle.id ? 'Archiving…' : 'Archive'}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {cycles.length > 0 && (
          <div className="af-billing__pagination">
            <span className="af-billing__pagination-info">
              Showing {cyclesPage * CYCLES_PAGE_SIZE + 1}–
              {Math.min(cycles.length, cyclesPage * CYCLES_PAGE_SIZE + CYCLES_PAGE_SIZE)} of {cycles.length}
            </span>
            <div className="af-billing__pagination-controls">
              <button type="button" disabled={cyclesPage === 0} onClick={() => setCyclesPage((p) => p - 1)}>
                ← Prev
              </button>
              <span className="af-billing__pagination-page">
                Page {cyclesPage + 1} of {cyclesPageCount}
              </span>
              <button
                type="button"
                disabled={cyclesPage >= cyclesPageCount - 1}
                onClick={() => setCyclesPage((p) => p + 1)}
              >
                Next →
              </button>
            </div>
          </div>
        )}
      </section>

      {selectedId && (
        <section className="af-billing__panel">
          <div className="af-billing__detail-heading">
            <h2>Cycle Detail — {detail?.cycle?.periodMonth || '…'}</h2>
            {detail && detail.invoices.length > 0 && (
              <div className="af-billing__payment-filter" role="group" aria-label="Filter by payment status">
                {['all', 'paid', 'unpaid'].map((option) => (
                  <button
                    key={option}
                    type="button"
                    className={`af-billing__filter-btn ${paymentFilter === option ? 'af-billing__filter-btn--active' : ''}`}
                    onClick={() => setPaymentFilter(option)}
                  >
                    {option === 'all' ? 'All' : option === 'paid' ? 'Paid' : 'Unpaid'}
                  </button>
                ))}
              </div>
            )}
          </div>
          {detailLoading ? (
            <p className="af-billing__empty">Loading invoices…</p>
          ) : !detail || detail.invoices.length === 0 ? (
            <p className="af-billing__empty">
              {detail?.cycle?.status === 'OPEN'
                ? 'This cycle has not been finalized yet — no invoices generated.'
                : 'No invoices for this cycle.'}
            </p>
          ) : filteredInvoices.length === 0 ? (
            <p className="af-billing__empty">No invoices match this filter.</p>
          ) : (
            <table className="af-billing__table">
              <thead>
                <tr>
                  <th>Household</th>
                  <th>Resident</th>
                  <th>Meter No.</th>
                  <th>Consumption (kL)</th>
                  <th>Tier Breakdown</th>
                  <th>Common Area</th>
                  <th>Total</th>
                  <th>Payment Status</th>
                </tr>
              </thead>
              <tbody>
                {pagedInvoices.map((invoice) => (
                  <tr key={invoice.id}>
                    <td>{invoice.houseNumber}</td>
                    <td>{invoice.residentName || '—'}</td>
                    <td>{invoice.meterNumber || '—'}</td>
                    <td>{Number(invoice.consumptionUnits).toFixed(1)}</td>
                    <td>
                      {invoice.lineItems.map((line) => (
                        <div key={line.tierOrder} className="af-billing__tier-line">
                          Tier {line.tierOrder}: {Number(line.unitsInTier).toFixed(1)} kL × ₹{line.ratePerUnit} = ₹{line.amount}
                        </div>
                      ))}
                    </td>
                    <td>
                      {invoice.commonAreaCharges && invoice.commonAreaCharges.length > 0 ? (
                        invoice.commonAreaCharges.map((charge) => (
                          <div key={charge.costItemId ?? charge.label} className="af-billing__tier-line">
                            {charge.label}: ₹{charge.amount}
                          </div>
                        ))
                      ) : (
                        <span>—</span>
                      )}
                    </td>
                    <td>₹{Number(invoice.totalAmount).toLocaleString('en-IN')}</td>
                    <td>
                      <button
                        type="button"
                        className={`af-billing__badge af-billing__badge--${invoice.paid ? 'paid' : 'unpaid'} af-billing__badge--btn`}
                        disabled={paymentBusyId === invoice.id}
                        onClick={() => handleTogglePaid(invoice)}
                        title={invoice.paid ? 'Mark as unpaid' : 'Mark as paid'}
                      >
                        {paymentBusyId === invoice.id ? 'Updating…' : invoice.paid ? 'Paid' : 'Unpaid'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          {filteredInvoices.length > 0 && (
            <div className="af-billing__pagination">
              <span className="af-billing__pagination-info">
                Showing {detailPage * DETAIL_PAGE_SIZE + 1}–
                {Math.min(filteredInvoices.length, detailPage * DETAIL_PAGE_SIZE + DETAIL_PAGE_SIZE)} of {filteredInvoices.length}
              </span>
              <div className="af-billing__pagination-controls">
                <button type="button" disabled={detailPage === 0} onClick={() => setDetailPage((p) => p - 1)}>
                  ← Prev
                </button>
                <span className="af-billing__pagination-page">
                  Page {detailPage + 1} of {detailPageCount}
                </span>
                <button
                  type="button"
                  disabled={detailPage >= detailPageCount - 1}
                  onClick={() => setDetailPage((p) => p + 1)}
                >
                  Next →
                </button>
              </div>
            </div>
          )}
        </section>
      )}
    </>
  )
}

export default BillingCycles
