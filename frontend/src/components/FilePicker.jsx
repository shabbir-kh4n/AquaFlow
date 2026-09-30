import { useTranslation } from 'react-i18next'
import './FilePicker.css'

// Themed replacement for the browser's native file input. The real input stays in the DOM
// (visually hidden, still focusable) and the label acts as the button.
function FilePicker({ id, accept, file, onChange }) {
  const { t } = useTranslation('common')

  return (
    <div className="af-filepicker">
      <input id={id} type="file" accept={accept} className="af-filepicker__input" onChange={onChange} />
      <label htmlFor={id} className="af-filepicker__btn">
        {file ? t('filePicker.change') : t('filePicker.choose')}
      </label>
      <span className="af-filepicker__name">{file ? file.name : t('filePicker.none')}</span>
    </div>
  )
}

export default FilePicker
