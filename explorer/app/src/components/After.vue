<template>
  <div class="container">
    <!-- Titles and subtitles -->
    <h1 class="title is-2 is-spaced is-family-secondary has-text-centered">
      Test completed in {{ testDurationMinutes }} minutes and {{ testDurationSeconds }}
      seconds
    </h1>
    <div class="columns is-centered">
      <div class="column is-three-quarters">
        <h2 class="subtitle is-6 has-text-centered">
          That concludes the last 10 years of Bitcoin transaction history
          until now - replayed across the Radix ledger with full signature
          and transaction validation. The results of this test will be
          available to search on this page for the next 10 minutes.
        </h2>
      </div>
    </div>

    <!-- Stats -->
    <section class="section">
      <div class="columns is-centered is-8">
        <div class="column is-one-quarter">
          <p class="has-text-centered">
            We
            <strong>peaked</strong> at
            <br>
            <strong class="is-size-5 has-text-primary">{{ beautifulNumber(peakTransactions) }}</strong>
            transactions per second.
          </p>
        </div>
        <div class="column is-one-quarter">
          <p class="has-text-centered">
            We
            <strong>averaged</strong> at
            <br>
            <span class="is-size-6 has-text-primary">{{ beautifulNumber(averageTransactions) }}</span>
            transactions per second.
          </p>
        </div>
        <div class="column is-one-quarter">
          <p class="has-text-centered">
            <strong>Next test</strong> will start on
            <br>
            <span class="is-size-6 has-text-primary">13 June, 13:30 (GMT+1) Time in London, UK</span>
          </p>
        </div>
      </div>
    </section>

    <!-- How we did it buttons -->
    <section class="section">
      <div class="columns is-centered">
        <div class="column is-one-quarter">
          <a class="button is-primary is-fullwidth">How we did it</a>
        </div>
        <div class="column is-one-quarter">
          <a class="button is-primary is-fullwidth">Run our test</a>
        </div>
        <div class="column is-one-quarter">
          <a class="button is-primary is-fullwidth">Get our token</a>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import utils from "@/mixins/utils";
export default {
  mixins: [utils],
  props: {
    nextTestTimestamp: Number,
    testStartTimestamp: Number,
    testStopTimestamp: Number,
    peakTransactions: Number,
    averageTransactions: Number
  },
  computed: {
    testDurationMinutes: function() {
      const duration =
        (this.testStopTimestamp - this.testStartTimestamp) / 1000;
      return Math.round(duration / 60);
    },
    testDurationSeconds: function() {
      const duration =
        (this.testStopTimestamp - this.testStartTimestamp) / 1000;
      return Math.round(duration % 60);
    }
  }
};
</script>
