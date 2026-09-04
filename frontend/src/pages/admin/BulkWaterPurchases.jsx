import { useEffect, useState } from 'react'
import {
  createBulkPurchase,
  deleteBulkPurchase,
  listBulkPurchases,
  updateBulkPurchase,
} from '../../api/bulkPurchaseApi'
import '../../components/AuthForm.css'
import './BulkWaterPurchases.css'

const EMPTY_FORM = {
  purchaseDate: '',
  source: '',
  volumeLiters: '',
  unitCost: '',
  notes: '',
}

function BulkWaterPurchases() {
  const [purchases, setPurchases] = useState([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState(EMPTY_FORM)
  const [formError, setFormError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // Same silent-refresh pattern as BillingCycles.jsx: avoids blanking the whole table to a
  // "Loading…" placeholder on every add/edit/delete.
  function loadPurchases(silent = false) {
    if (!silent) setLoading(true)
    setLoadError('')
    return listBulkPurchases()
      .then(setPurchases)
      .catch(() => setLoadError('Could not load bulk water purchases. Please try again.'))
      .finally(() => {
        if (!silent) setLoading(false)
      })
  }

  useEffect(() => {
    listBulkPurchases()
      .then(setPurchases)
      .catch(() => setLoadError('Could not load bulk water purchases. Please try again.'))
      .finally(() => setLoading(false))
  }, [])

  function openAddForm() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setFormError('')
    setShowForm(true)
  }

  function openEditForm(purchase) {
    setEditingId(purchase.id)
    setForm({
      purchaseDate: purchase.purchaseDate,
      source: purchase.source,
      volumeLiters: String(purchase.volumeLiters),
      unitCost: String(purchase.unitCost),
      notes: purchase.notes || '',
    })
    setFormError('')
    setShowForm(true)
  }

  function closeForm() {
    setShowForm(false)
    setEditingId(null)
  }

  function handleChange(event) {
    const { name, value } = event.target
    setForm((prev) => ({ ...prev, [name]: value }))
  }

  const previewTotal =
    form.volumeLiters && form.unitCost ? Number(form.volumeLiters) * Number(form.unitCost) : null

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')

    if (!form.purchaseDate || !form.source.trim() || !form.volumeLiters || !form.unitCost) {
      setFormError('Please fill in the date, source, volume, and unit cost.')
      return
    }

    setSubmitting(true)

    const payload = {
      purchaseDate: form.purchaseDate,
      source: form.source.trim(),
      volumeLiters: Number(form.volumeLiters),
      unitCost: Number(form.unitCost),
      notes: form.notes.trim() || null,
    }

    try {
      if (editingId) {
        await updateBulkPurchase(editingId, payload)
      } else {
        await createBulkPurchase(payload)
      }
      closeForm()
      await loadPurchases(true)
    } catch (err) {
      setFormError(err.response?.data?.message || 'Could not save this purchase. Please check the details.')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDelete(purchase) {
    if (!window.confirm(`Remove the ${purchase.source} delivery on ${purchase.purchaseDate}?`)) {
      return
    }

    try {
      await deleteBulkPurchase(purchase.id)
      await loadPurchases(true)
    } catch {
      setLoadError('Could not remove this purchase. Please try again.')
    }
  }

  return (
    <>
      <div className="af-bulk__toolbar">
        <span className="af-bulk__count">
          {purchases.length} purchase{purchases.length === 1 ? '' : 's'} recorded
        </span>
        <button type="button" className="af-btn af-btn--cyan" onClick={openAddForm}>
          + Record Purchase
        </button>
      </div>

      {loadError && <div className="af-form__error">{loadError}</div>}

      <div className="af-bulk__table-wrap">
        {loading ? (
          <p className="af-bulk__empty">Loading bulk water purchases…</p>
        ) : purchases.length === 0 ? (
          <p className="af-bulk__empty">
            No bulk water purchases recorded yet. Click "Record Purchase" to log a tanker delivery or
            municipal supply purchase.
          </p>
        ) : (
          <table className="af-bulk__table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Source</th>
                <th>Volume (L)</th>
                <th>Unit Cost</th>
                <th>Total Cost</th>
                <th>Notes</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {purchases.map((purchase) => (
                <tr key={purchase.id}>
                  <td>{purchase.purchaseDate}</td>
                  <td>{purchase.source}</td>
                  <td>{Number(purchase.volumeLiters).toLocaleString('en-IN')}</td>
                  <td>₹{Number(purchase.unitCost).toLocaleString('en-IN')}</td>
                  <td>₹{Number(purchase.totalCost).toLocaleString('en-IN')}</td>
                  <td>{purchase.notes || '—'}</td>
                  <td className="af-bulk__row-actions">
                    <button type="button" onClick={() => openEditForm(purchase)}>
                      Edit
                    </button>
                    <button type="button" className="af-bulk__delete" onClick={() => handleDelete(purchase)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {showForm && (
        <div className="af-bulk__modal-backdrop" onClick={closeForm}>
          <div className="af-bulk__modal" onClick={(e) => e.stopPropagation()}>
            <h2>{editingId ? 'Edit Bulk Water Purchase' : 'Record Bulk Water Purchase'}</h2>
            <p className="af-bulk__modal-sub">
              Track a tanker delivery or municipal supply purchase for your community — this is
              recorded for cost visibility only and does not affect household invoices.
            </p>

            <form onSubmit={handleSubmit}>
              {formError && <div className="af-form__error">{formError}</div>}

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="purchaseDate">Purchase date</label>
                  <input
                    id="purchaseDate"
                    name="purchaseDate"
                    type="date"
                    value={form.purchaseDate}
                    onChange={handleChange}
                  />
                </div>
                <div className="af-form__field">
                  <label htmlFor="source">Source / supplier</label>
                  <input id="source" name="source" value={form.source} onChange={handleChange} />
                </div>
              </div>

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="volumeLiters">Volume (liters)</label>
                  <input
                    id="volumeLiters"
                    name="volumeLiters"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={form.volumeLiters}
                    onChange={handleChange}
                  />
                </div>
                <div className="af-form__field">
                  <label htmlFor="unitCost">Unit cost (₹/liter)</label>
                  <input
                    id="unitCost"
                    name="unitCost"
                    type="number"
                    min="0.0001"
                    step="0.0001"
                    value={form.unitCost}
                    onChange={handleChange}
                  />
                </div>
              </div>

              {previewTotal != null && (
                <p className="af-bulk__total-preview">
                  Total cost: ₹{previewTotal.toLocaleString('en-IN')}
                </p>
              )}

              <div className="af-form__row">
                <div className="af-form__field">
                  <label htmlFor="notes">Notes (optional)</label>
                  <input id="notes" name="notes" value={form.notes} onChange={handleChange} />
                </div>
              </div>

              <div className="af-bulk__modal-actions">
                <button type="button" className="af-btn af-btn--outline" onClick={closeForm}>
                  Cancel
                </button>
                <button type="submit" className="af-form__submit" disabled={submitting}>
                  {submitting ? 'Saving…' : editingId ? 'Save Changes' : 'Record Purchase'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}

export default BulkWaterPurchases
