<template>
  <div>
    <notifications position="top center" group="notification" style="margin:20px" width="450px"/>
    <Header/>
    <Before
      v-if="isTerminated"
      v-bind:prevTestTimestamp="testEndTimestamp"
      v-bind:nextTestTimestamp="testNextTimestamp"
      v-bind:peakTransactions="peakTps"
      v-bind:averageTransactions="averageTps"
    />
    <During
      v-else-if="isRunning"
      v-bind:throughput="currentTps"
      v-bind:progress="progress"
      v-bind:maxProgress="maxProgress"
    />
    <After
      v-else-if="isFinished"
      v-bind:testStartTimestamp="testStartTimestamp"
      v-bind:testStopTimestamp="testEndTimestamp"
      v-bind:nextTestTimestamp="testNextTimestamp"
      v-bind:peakTransactions="peakTps"
      v-bind:averageTransactions="averageTps"
    />
    <Search
      v-if="isRunning || isFinished"
      v-bind:state="searching"
      v-bind:title="searchTitle"
      v-bind:searchFunction="getTransactions"
    />
    <Social/>
    <Footer/>
  </div>
</template>

<script>
import Header from "@/components/Header.vue";
import Before from "@/components/Before.vue";
import During from "@/components/During.vue";
import After from "@/components/After.vue";
import Search from "@/components/Search.vue";
import Social from "@/components/Social.vue";
import Footer from "@/components/Footer.vue";

const STATE_RUNNING = "STARTED";
const STATE_FINISHED = "FINISHED";
const STATE_TERMINATED = "TERMINATED";
const STATE_UNKNOWN = "UNKNOWN";

export default {
  components: {
    Header,
    Before,
    During,
    After,
    Search,
    Social,
    Footer
  },
  computed: {
    isRunning: function() {
      return this.state === STATE_RUNNING;
    },
    isFinished: function() {
      return this.state === STATE_FINISHED;
    },
    isTerminated: function() {
      return this.state === STATE_TERMINATED || this.state === STATE_UNKNOWN;
    }
  },
  methods: {
    getMetrics: function() {
      this.$http.get("api/metrics").then(
        function(response) {
          if (response.status == 200) {
            const metrics = response.body;
            const meta = metrics.meta;
            const data = metrics.data;
            // Meta
            this.state = meta.testState;
            this.testStartTimestamp = meta.testStart;
            this.testEndTimestamp = meta.testStop;
            this.testNextTimestamp = this.testEndTimestamp + 604800000; // 7 days
            this.maxProgress = meta.progressMax;
            // Data
            this.currentTps = data.spotTps;
            this.peakTps = data.peakTps;
            this.averageTps = data.averageTps;
            this.progress = data.progress;
            if (this.isRunning) {
              this.searchTitle =
                "Watch us reconstruct your transaction history at hyperspeed:";
            } else if (this.isFinished) {
              this.searchTitle = "View your bitcoin transaction history:";
            }
          }
        },
        function() {
          // No connection. Now what?
        }
      );
    },
    getTransactions: function(bitcoinAddress, page) {
      const vm = this;
      vm.searching = true;
      return new Promise(function(resolve, reject) {
        vm.$http
          .get("api/transactions/" + bitcoinAddress + "?page=" + page)
          .then(
            function(response) {
              vm.searching = false;
              const content = response.body;
              const transactions = content.data;
              const page = content.meta.page;
              resolve({ data: transactions, page: page });
            },
            function() {
              vm.searching = false;
              reject();
              vm.$notify({
                group: "notification",
                type: "error",
                text: "Couldn't find any transactions for that address"
              });
            }
          );
      });
    }
  },
  data: function() {
    return {
      state: STATE_UNKNOWN,
      searching: false,
      testStartTimestamp: 0,
      testEndTimestamp: 0,
      testNextTimestamp: 0,
      peakTps: 0,
      averageTps: 0,
      currentTps: 0,
      maxProgress: 100,
      progress: 0,
      searchTitle: "",
      poller: null
    };
  },
  beforeMount: function() {
    this.getMetrics();
    this.poller = setInterval(this.getMetrics, 2000);
  },
  beforeDestroy: function() {
    clearInterval(this.poller);
  }
};
</script>
