import { useState } from 'react'
import './RazorpaySimulatorModal.css'

const METHODS = [
  { id: 'upi', label: 'UPI', icon: 'upi' },
  { id: 'card', label: 'Cards', icon: 'card' },
  { id: 'netbanking', label: 'Netbanking', icon: 'bank' },
  { id: 'wallet', label: 'Wallet', icon: 'wallet' },
]

const BANKS = ['State Bank of India', 'HDFC Bank', 'ICICI Bank', 'Axis Bank', 'Kotak Mahindra Bank']
const WALLETS = ['Paytm', 'Amazon Pay', 'Mobikwik', 'Freecharge']

// A fixed, hand-authored module grid that only needs to *read* as a QR code (three finder
// squares + noise) — it isn't a real scannable code, this is purely a checkout UI simulation.
const QR_FINDER_CELLS = new Set()
for (let r = 0; r < 7; r += 1) {
  for (let c = 0; c < 7; c += 1) {
    const onRing = r === 0 || r === 6 || c === 0 || c === 6
    const onCore = r >= 2 && r <= 4 && c >= 2 && c <= 4
    if (onRing || onCore) {
      QR_FINDER_CELLS.add(`${r}-${c}`)
    }
  }
}
const QR_GRID_SIZE = 21
function isQrDark(row, col) {
  const inFinder = (r0, c0) => {
    const r = row - r0;
    const c = col - c0
    return r >= 0 && r < 7 && c >= 0 && c < 7 && QR_FINDER_CELLS.has(`${r}-${c}`)
  }
  if (inFinder(0, 0) || inFinder(0, QR_GRID_SIZE - 7) || inFinder(QR_GRID_SIZE - 7, 0)) {
    return true
  }
  // Deterministic pseudo-random fill for the rest so it looks like real QR noise but never changes.
  const seed = (row * 928371 + col * 12923 + row * col * 7) % 5
  return seed < 2
}

function RazorpayMark({ size = 22 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <rect width="24" height="24" rx="6" fill="url(#af-rzp-grad)" />
      <path d="M9 6h5.2c2.1 0 3.6 1.2 3.6 3.1 0 1.5-.9 2.5-2.1 2.9L18 18h-2.9l-2-5.7H11V18H9z" fill="#fff" />
      <path d="M11 8v2.6h2.9c.9 0 1.5-.5 1.5-1.3S13.8 8 12.9 8z" fill="url(#af-rzp-grad)" />
      <defs>
        <linearGradient id="af-rzp-grad" x1="0" y1="0" x2="24" y2="24">
          <stop offset="0" stopColor="#3395ff" />
          <stop offset="1" stopColor="#0a3d91" />
        </linearGradient>
      </defs>
    </svg>
  )
}

function MethodIcon({ name }) {
  if (name === 'card') {
    return (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <rect x="2.5" y="5.5" width="19" height="13" rx="2" />
        <path d="M2.5 9.5h19" />
        <path d="M5.5 14.5h4" />
      </svg>
    )
  }
  if (name === 'bank') {
    return (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 9.5 12 4l9 5.5" />
        <path d="M4.5 9.5v9M9 9.5v9M15 9.5v9M19.5 9.5v9" />
        <path d="M3 18.5h18" />
      </svg>
    )
  }
  if (name === 'wallet') {
    return (
      <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
        <path d="M3 7.5A2 2 0 0 1 5 5.5h13a1 1 0 0 1 1 1V8" />
        <rect x="3" y="7.5" width="18" height="11.5" rx="2" />
        <path d="M16.5 13.25h2.25" />
      </svg>
    )
  }
  // upi — stylised twin-arrow exchange mark in UPI's own green/orange
  return (
    <svg viewBox="0 0 24 24" width="22" height="22">
      <path d="M4 14.5 10 8v3.4h10v2.2H10V17z" fill="#f68b1f" />
      <path d="M20 9.5 14 16v-3.4H4v-2.2h10V7z" fill="#097939" />
    </svg>
  )
}

function generateFakePaymentId() {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789'
  let id = 'pay_'
  for (let i = 0; i < 14; i += 1) {
    id += chars[Math.floor(Math.random() * chars.length)]
  }
  return id
}

// Simulates a Razorpay Checkout session end-to-end without any real gateway integration —
// no live keys are configured, so this replicates the same look-and-feel and processing →
// success flow, then calls back into the real backend endpoint to mark the invoice paid.
function RazorpaySimulatorModal({ invoice, onClose, onSuccess }) {
  const [method, setMethod] = useState('upi')
  const [stage, setStage] = useState('checkout') // checkout | processing | success | error
  const [paymentId] = useState(generateFakePaymentId)
  const [upiId, setUpiId] = useState('')

  const amountLabel = Number(invoice.totalAmount).toLocaleString('en-IN', { minimumFractionDigits: 2 })

  function handlePay() {
    setStage('processing')
    setTimeout(() => {
      setStage('success')
      setTimeout(() => {
        onSuccess()?.catch(() => setStage('error'))
      }, 1300)
    }, 1900)
  }

  return (
    <div className="af-rzp__overlay" role="dialog" aria-modal="true">
      <div className="af-rzp__modal">
        {(stage === 'checkout') && (
          <>
            <div className="af-rzp__brandstrip">
              <RazorpayMark size={14} />
              <span>Secured by Razorpay</span>
            </div>

            <div className="af-rzp__topbar">
              <div className="af-rzp__merchant-id">
                <span className="af-rzp__merchant-logo">HL</span>
                <div>
                  <strong>HydroLedger</strong>
                  <small>Billing Cycle {invoice.periodMonth}</small>
                </div>
              </div>
              <button type="button" className="af-rzp__close" onClick={onClose} aria-label="Close">
                ×
              </button>
            </div>

            <div className="af-rzp__amount-row">
              <span>Amount Payable</span>
              <strong>₹{amountLabel}</strong>
            </div>

            <div className="af-rzp__body">
              <nav className="af-rzp__rail">
                {METHODS.map((m) => (
                  <button
                    key={m.id}
                    type="button"
                    className={`af-rzp__rail-item ${method === m.id ? 'af-rzp__rail-item--active' : ''}`}
                    onClick={() => setMethod(m.id)}
                  >
                    <span className="af-rzp__rail-icon" aria-hidden="true">
                      <MethodIcon name={m.icon} />
                    </span>
                    {m.label}
                  </button>
                ))}
              </nav>

              <div className="af-rzp__pane">
                {method === 'upi' && (
                  <div className="af-rzp__pane-content">
                    <h4>Pay via UPI</h4>
                    <label className="af-rzp__field">
                      <span>UPI ID</span>
                      <input
                        type="text"
                        placeholder="yourname@upi"
                        value={upiId}
                        onChange={(event) => setUpiId(event.target.value)}
                      />
                    </label>
                    <div className="af-rzp__divider"><span>OR</span></div>
                    <div className="af-rzp__qr-box">
                      <svg
                        className="af-rzp__qr-svg"
                        viewBox={`0 0 ${QR_GRID_SIZE} ${QR_GRID_SIZE}`}
                        aria-hidden="true"
                      >
                        <rect width={QR_GRID_SIZE} height={QR_GRID_SIZE} fill="#fff" />
                        {Array.from({ length: QR_GRID_SIZE }).map((_, row) =>
                          Array.from({ length: QR_GRID_SIZE }).map((__, col) =>
                            isQrDark(row, col) ? (
                              <rect key={`${row}-${col}`} x={col} y={row} width="1" height="1" fill="#16213e" />
                            ) : null,
                          ),
                        )}
                      </svg>
                      <span>Scan with any UPI app</span>
                    </div>
                  </div>
                )}

                {method === 'card' && (
                  <div className="af-rzp__pane-content">
                    <h4>Pay via Card</h4>
                    <label className="af-rzp__field">
                      <span>Card Number</span>
                      <input type="text" placeholder="1234 5678 9012 3456" maxLength={19} />
                    </label>
                    <div className="af-rzp__field-row">
                      <label className="af-rzp__field">
                        <span>Expiry</span>
                        <input type="text" placeholder="MM / YY" maxLength={7} />
                      </label>
                      <label className="af-rzp__field">
                        <span>CVV</span>
                        <input type="password" placeholder="•••" maxLength={3} />
                      </label>
                    </div>
                    <label className="af-rzp__field">
                      <span>Name on Card</span>
                      <input type="text" placeholder="As printed on card" />
                    </label>
                  </div>
                )}

                {method === 'netbanking' && (
                  <div className="af-rzp__pane-content">
                    <h4>Pay via Netbanking</h4>
                    <div className="af-rzp__list">
                      {BANKS.map((bank) => (
                        <label key={bank} className="af-rzp__list-item">
                          <input type="radio" name="rzp-bank" defaultChecked={bank === BANKS[0]} />
                          {bank}
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                {method === 'wallet' && (
                  <div className="af-rzp__pane-content">
                    <h4>Pay via Wallet</h4>
                    <div className="af-rzp__list">
                      {WALLETS.map((wallet) => (
                        <label key={wallet} className="af-rzp__list-item">
                          <input type="radio" name="rzp-wallet" defaultChecked={wallet === WALLETS[0]} />
                          {wallet}
                        </label>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className="af-rzp__footer">
              <button type="button" className="af-rzp__pay-btn" onClick={handlePay}>
                Pay ₹{amountLabel}
              </button>
            </div>
          </>
        )}

        {stage === 'processing' && (
          <div className="af-rzp__status">
            <div className="af-rzp__spinner-wrap">
              <RazorpayMark size={30} />
              <div className="af-rzp__spinner" aria-hidden="true" />
            </div>
            <p>Processing your payment…</p>
            <span className="af-rzp__status-sub">Please do not press back or refresh this page</span>
          </div>
        )}

        {stage === 'success' && (
          <div className="af-rzp__status">
            <svg className="af-rzp__success-icon" viewBox="0 0 52 52" aria-hidden="true">
              <circle className="af-rzp__success-circle" cx="26" cy="26" r="24" fill="none" />
              <path className="af-rzp__success-check" fill="none" d="M14 27l7 7 16-16" />
            </svg>
            <p>Payment Successful</p>
            <span className="af-rzp__status-sub">{paymentId}</span>
          </div>
        )}

        {stage === 'error' && (
          <div className="af-rzp__status">
            <p className="af-rzp__error">Something went wrong confirming your payment. Please try again.</p>
            <button type="button" className="af-rzp__pay-btn" onClick={() => setStage('checkout')}>
              Retry
            </button>
            <button type="button" className="af-rzp__cancel-btn" onClick={onClose}>
              Close
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default RazorpaySimulatorModal
