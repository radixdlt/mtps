require('./custom.scss')

import Vue from 'vue'
import VueResource from 'vue-resource'
import Notification from 'vue-notification'

import App from './App.vue'

Vue.use(VueResource)
Vue.use(Notification)

Vue.config.productionTip = false
Vue.http.options.root = process.env.VUE_APP_BASE_URL

new Vue({
  render: function (h) {
    return h(App)
  },
}).$mount('#app')
