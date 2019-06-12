<template>
  <div class="container">
    <!-- Titles and subtitles -->
    <h1 class="title is-2 is-spaced is-family-secondary has-text-centered">
      On {{ previousTestDate }} we replayed
      history
    </h1>
    <h2 class="subtitle is-6 has-text-centered">(all Bitcoin transactions to date)</h2>

    <div class="columns is-centered">
      <div class="column is-narrow">
        <!-- Throughput -->
        <section class="section">
          <h2 class="subtitle is-4 has-text-left">
            During the last test we
            <strong>peaked</strong> at
          </h2>
          <h1 class="title has-text-centered has-text-primary" style="font-size:10rem">
            {{
            beautifulNumber(peakTransactions)
            }}
          </h1>
          <h2 class="subtitle is-4 has-text-right has-text-primary">transactions per second</h2>
        </section>
      </div>
    </div>

    <!-- Summary -->
    <section class="section">
      <div class="columns is-centered">
        <div class="column is-three-quarters">
          <nav class="level">
            <div class="level-left">
              <div class="level-item">
                <div class="has-text-left">
                  <p>
                    and we
                    <strong>averaged</strong> at
                  </p>
                  <p class="subtitle is-4 has-text-primary">
                    {{
                    beautifulNumber(averageTransactions)
                    }}
                  </p>
                  <p>transactions per second</p>
                </div>
              </div>
            </div>
            <div class="level-right">
              <div class="level-item">
                <div class="has-text-right">
                  <p>Next test run will start at</p>
                  <p class="subtitle is-4 has-text-primary">12 June, 14:30 (GMT+1) Time in London, UK</p>
                  <button
                    class="button is-small is-primary is-pulled-right"
                    style="height:28px; padding: 0 4px"
                  >Add to calendar</button>
                </div>
              </div>
            </div>
          </nav>
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
export default {
  props: {
    prevTestTimestamp: Number,
    nextTestTimestamp: Number,
    peakTransactions: Number,
    averageTransactions: Number
  },
  computed: {
    previousTestDate: function() {
      const date = new Date(this.prevTestTimestamp);
      const options = {
        month: "long",
        day: "numeric"
      };
      const dateTimeFormat = new Intl.DateTimeFormat("default", options).format;
      return dateTimeFormat(date);
    },
    nextTestDate: function() {
      const date = new Date(this.nextTestTimestamp);
      const options = {
        month: "long",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
        timeZoneName: "short"
      };
      const dateTimeFormat = new Intl.DateTimeFormat("default", options).format;
      return dateTimeFormat(date);
    }
  },
  methods: {
    beautifulNumber: function(number, decimalPlaces) {
      return number
        .toFixed(decimalPlaces)
        .toString()
        .replace(/\B(?=(\d{3})+(?!\d))/g, "\xa0"); // non-breaking space
    }
  }
};
</script>
