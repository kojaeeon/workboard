/** Capture laid-out nodes and vector connectors without re-running flex layout. */
export async function exportMindMapPng(map: HTMLElement, scale: number, filename: string) {
  await document.fonts.ready
  await new Promise<void>((resolve) =>
    requestAnimationFrame(() => requestAnimationFrame(() => resolve())),
  )
  const visible = (element: Element) =>
    !element.closest('defs,.linkcontroller,.mm-relation-overlay') &&
    getComputedStyle(element).visibility !== 'hidden'
  const bounds = Array.from(map.querySelectorAll('.me-tpc,.svg-label,svg path'))
    .filter(visible)
    .map((element) => element.getBoundingClientRect())
    .filter((rect) => rect.width || rect.height)
  if (!bounds.length) throw new Error('내보낼 노드가 없습니다.')
  const padding = 32
  const left = Math.min(...bounds.map((rect) => rect.left))
  const top = Math.min(...bounds.map((rect) => rect.top))
  const width = Math.ceil(
    (Math.max(...bounds.map((rect) => rect.right)) - left) / scale + padding * 2,
  )
  const height = Math.ceil(
    (Math.max(...bounds.map((rect) => rect.bottom)) - top) / scale + padding * 2,
  )
  const wrapper = document.createElement('div')
  wrapper.setAttribute('xmlns', 'http://www.w3.org/1999/xhtml')
  Object.assign(wrapper.style, {
    position: 'relative',
    width: `${width}px`,
    height: `${height}px`,
    overflow: 'hidden',
    background: '#18221e',
  })
  const connectors = Array.from(map.querySelectorAll('svg')).filter(
    (element) => !element.parentElement?.closest('svg,.me-tpc') && visible(element),
  )
  const labels = Array.from(map.querySelectorAll('.me-tpc,.svg-label')).filter(
    (element) => !element.parentElement?.closest('.me-tpc,.svg-label') && visible(element),
  )
  for (const element of [...connectors, ...labels]) {
    const rect = element.getBoundingClientRect()
    const copy = element.cloneNode(true) as HTMLElement | SVGElement
    const originals = [element, ...Array.from(element.querySelectorAll('*'))]
    const copies = [copy, ...Array.from(copy.querySelectorAll('*'))]
    originals.forEach((original, index) => {
      const style = getComputedStyle(original)
      const target = copies[index] as HTMLElement | SVGElement
      target.removeAttribute('style')
      for (const property of style)
        target.style.setProperty(property, style.getPropertyValue(property))
      target.style.animation = 'none'
      target.style.transition = 'none'
    })
    copy
      .querySelectorAll('.linkcontroller,.circle,.mm-relation-overlay,me-epd')
      .forEach((child) => child.remove())
    copy
      .querySelectorAll('.mm-muted')
      .forEach((child) => ((child as HTMLElement).style.opacity = '1'))
    for (const property of [
      'inset',
      'inset-inline',
      'inset-block',
      'inset-inline-start',
      'inset-inline-end',
      'inset-block-start',
      'inset-block-end',
      'left',
      'right',
      'top',
      'bottom',
      'margin-inline',
      'margin-block',
      'margin-inline-start',
      'margin-inline-end',
      'margin-block-start',
      'margin-block-end',
    ])
      copy.style.removeProperty(property)
    Object.assign(copy.style, {
      position: 'absolute',
      left: `${(rect.left - left) / scale + padding}px`,
      top: `${(rect.top - top) / scale + padding}px`,
      width: `${rect.width / scale}px`,
      height: `${rect.height / scale}px`,
      minWidth: '0',
      maxWidth: 'none',
      minHeight: '0',
      maxHeight: 'none',
      boxSizing: 'border-box',
      margin: '0',
      transform: 'none',
      translate: 'none',
      rotate: 'none',
      scale: 'none',
      opacity: '1',
      outline: 'none',
      boxShadow: 'none',
      overflow: 'visible',
    })
    wrapper.appendChild(copy)
  }
  const markup = new XMLSerializer().serializeToString(wrapper)
  const image = new Image()
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve()
    image.onerror = () => reject(new Error('이미지 변환에 실패했습니다.'))
    image.src =
      'data:image/svg+xml;charset=utf-8,' +
      encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><foreignObject width="100%" height="100%">${markup}</foreignObject></svg>`,
      )
  })
  const ratio = Math.min(2, 8192 / width, 8192 / height, Math.sqrt(32000000 / (width * height)))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.floor(width * ratio))
  canvas.height = Math.max(1, Math.floor(height * ratio))
  const context = canvas.getContext('2d')
  if (!context) throw new Error('이미지 캔버스를 만들 수 없습니다.')
  context.drawImage(image, 0, 0, canvas.width, canvas.height)
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'))
  if (!blob) throw new Error('PNG 파일을 만들 수 없습니다.')
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 60000)
  return { ok: true }
}
