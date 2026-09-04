// The single droplet mark used everywhere in the app (navbar, footer, auth
// modal/pages, admin + resident shells) — white rounded-square backdrop, dark
// green droplet, small white accent stroke. Keeping one component means every
// surface stays in sync if the mark ever changes again.
function Logo({ size = 32 }) {
  return (
    <span
      style={{
        width: size,
        height: size,
        display: 'grid',
        placeItems: 'center',
        flexShrink: 0,
        borderRadius: size * 0.28,
        background: '#ffffff',
      }}
      aria-hidden="true"
    >
      <svg width={size * 0.62} height={size * 0.62} viewBox="0 0 24 24">
        <path
          d="M12 2.5S5.5 10 5.5 14.7a6.5 6.5 0 0 0 13 0C18.5 10 12 2.5 12 2.5Z"
          fill="#0b2818"
        />
        <path
          d="M9 15.2c.5 1.7 1.5 2.5 3.2 2.7"
          fill="none"
          stroke="#fff"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
      </svg>
    </span>
  )
}

export default Logo
