<template>
  <div class="container">
    <!-- Search box -->
    <section class="section">
      <div class="columns is-centered">
        <div class="column is-three-quarters">
          <h2 class="subtitle is-size-6">{{ title }}</h2>
          <div class="box">
            <div class="field is-grouped">
              <div class="control is-expanded">
                <input
                  class="input is-white"
                  type="text"
                  v-bind:disabled="isSearching"
                  v-model="bitcoinAddress"
                  placeholder="Bitcoin address"
                >
              </div>
              <div class="control">
                <button
                  class="button is-primary"
                  v-bind:class="{'is-loading': isSearching}"
                  v-bind:disabled="isSearching || !isValidAddress"
                  v-on:click="showTransactions(1)"
                >Watch</button>
              </div>
            </div>
          </div>
          <div class="field" v-show="isNonEmptyResult">
            <div class="control is-expanded">
              <table class="table is-fullwidth">
                <thead>
                  <tr>
                    <th>Amount</th>
                    <th class="has-text-right">Date</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(t, index) in transactions" :key="index">
                    <td>
                      <span
                        class="has-text-weight-bold"
                        :class="{
                        'has-text-primary': t.amount > 0,
                        'has-text-danger': t.amount < 0
                        }"
                      >{{ t.amount.toFixed(18) }}</span>
                      <span class="currency is-size-7">&nbsp;BTC</span>
                    </td>
                    <td class="has-text-right">{{ beautifulDate(t.bitcoinBlockTimestamp) }}</td>
                  </tr>
                </tbody>
              </table>
              <nav class="pagination">
                <button
                  class="pagination-previous"
                  :disabled="currentPage === 1"
                  v-on:click="showPreviousTransactions()"
                >Previous</button>
                <button class="pagination-next" v-on:click="showMoreTransactions()">More</button>
                <button class="pagination-next" v-on:click="showEvenMoreTransactions()">Much more</button>
                <ul class="pagination-list">
                  <li v-show="currentPage > historyBatchSize">
                    <button
                      class="pagination-link"
                      :class="{ 'is-current': currentPage === 1 }"
                      v-on:click="showTransactions(1)"
                    >1</button>
                  </li>
                  <li v-show="currentPage > (historyBatchSize + 1)">
                    <button class="pagination-ellipsis">&hellip;</button>
                  </li>
                  <li v-for="n in pages" :key="n">
                    <button
                      class="pagination-link"
                      :class="{ 'is-current': n === currentPage }"
                      v-on:click="showTransactions(n)"
                    >{{ n }}</button>
                  </li>
                </ul>
              </nav>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
const WAValidator = require("wallet-address-validator");
import utils from "@/mixins/utils";
export default {
  mixins: [utils],
  props: {
    state: Boolean,
    title: String,
    searchFunction: Function
  },
  methods: {
    showPreviousTransactions() {
      this.showTransactions(this.currentPage - 1);
    },
    showMoreTransactions() {
      this.showTransactions(this.currentPage + 1);
    },
    showEvenMoreTransactions() {
      this.showTransactions(this.currentPage + 10);
    },
    showTransactions(page) {
      const vm = this;
      vm.searchFunction(vm.bitcoinAddress, page).then(function(result) {
        if (result.data.length == 0) {
          vm.showNotification(
            "Couldn't find any more transactions right now. Try again in a while."
          );
        } else {
          vm.transactions = result.data;
          vm.currentPage = result.page;
        }
      });
    },
    showNotification(message) {
      this.$notify({
        group: "notification",
        type: "warn",
        text: message
      });
    }
  },
  data: function() {
    return {
      bitcoinAddress: "",
      currentPage: 1,
      historyBatchSize: 3,
      transactions: []
    };
  },
  computed: {
    isValidAddress: function() {
      return WAValidator.validate(this.bitcoinAddress, "bitcoin", "prod");
    },
    isSearching: function() {
      return this.state;
    },
    isNonEmptyResult: function() {
      return this.transactions && this.transactions.length > 0;
    },
    pages: function() {
      const batchPages = new Array(0);
      const maxIndex = Math.max(0, this.currentPage - this.historyBatchSize);
      for (var i = this.currentPage; i > maxIndex; i--) {
        batchPages.unshift(i);
      }
      return batchPages;
    }
  }
};
</script>
