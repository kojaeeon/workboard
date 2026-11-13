import { createApp } from 'vue'
import { Quasar, Dialog, Notify } from 'quasar'
import * as QuasarComponents from 'quasar'
import ko from 'quasar/lang/ko-KR'
import 'quasar/dist/quasar.css'
import '@quasar/extras/material-icons/material-icons.css'
import '@quasar/extras/roboto-font/roboto-font.css'
import './theme.css'
import App from './App.vue'
const components = Object.fromEntries(
  Object.entries(QuasarComponents).filter(([name]) => /^Q[A-Z]/.test(name)),
)
createApp(App)
  .use(Quasar, {
    components,
    plugins: { Dialog, Notify },
    lang: ko,
    config: { brand: { primary: '#b89a60', secondary: '#7d897b', accent: '#cbb788' } },
  })
  .mount('#app')
