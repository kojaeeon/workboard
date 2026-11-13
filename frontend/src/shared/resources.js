const loaded = new Map();
export function loadResource(url) {
  if (!loaded.has(url)) loaded.set(url, new Promise((resolve, reject) => {
    const css = url.endsWith('.css');
    const element = document.createElement(css ? 'link' : 'script');
    if (css) { element.rel = 'stylesheet'; element.href = url; } else { element.src = url; element.async = false; }
    element.onload = resolve; element.onerror = () => { loaded.delete(url); reject(new Error(`리소스를 불러오지 못했습니다: ${url}`)); };
    document.head.appendChild(element);
  }));
  return loaded.get(url);
}
