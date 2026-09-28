import { useState } from 'react'
import { avatarUrl, initialsFor } from './avatarKeys'
import './Avatar.css'

// Renders a preset avatar for `avatarKey`, or the user's initials when the key is null,
// unknown, or the image fails to load. Sizing/colours for the initials come from the
// caller's className (each shell already styles its own avatar circle).
function Avatar({ avatarKey, name, fallbackName = '', className = '', size }) {
  const url = avatarUrl(avatarKey)
  const [failedUrl, setFailedUrl] = useState(null)
  const showImage = url && failedUrl !== url
  const style = size ? { width: size, height: size } : undefined

  return (
    <span
      className={`af-avatar ${showImage ? 'af-avatar--image' : ''} ${className}`.trim()}
      style={style}
      aria-hidden="true"
    >
      {showImage ? (
        <img src={url} alt="" draggable="false" onError={() => setFailedUrl(url)} />
      ) : (
        initialsFor(name, fallbackName)
      )}
    </span>
  )
}

export default Avatar
