<template>
  <div class="container">
    <!-- Titles and subtitles -->
    <h1 class="title is-2 is-spaced is-family-secondary has-text-centered">
      10 years of Bitcoin replayed
      on Radix
    </h1>
    <div class="columns is-centered">
      <div class="column is-three-quarters">
        <h2 class="subtitle is-6 has-text-centered">
          (with full signature, transaction validation
          and consensus)
        </h2>
        <h2 class="subtitle is-6 has-text-centered">
          Bitcoin was our first crypto love. We could think of no better transaction
          history to truly test the scalability of Radix with. Here is our ode to Bitcoin.
        </h2>
      </div>
    </div>

    <!-- Progress bar -->
    <section class="section">
      <div class="columns is-centered">
        <div class="column is-two-thirds">
          <progress class="progress is-primary is-large" max="100" v-bind:value="progressPercent">
            {{
            progressPercent
            }}
          </progress>
        </div>
      </div>
    </section>

    <!-- Throughput -->
    <div class="columns is-centered">
      <div class="column is-narrow">
        <h2 class="subtitle is-4 has-text-left">Validating Bitcoin transactions at:</h2>
        <h1 class="title has-text-centered has-text-primary" style="font-size:10rem">
          {{
          beautifulNumber(throughput)
          }}
        </h1>
        <p class="subtitle is-4 has-text-right has-text-primary">transactions per second</p>
      </div>
    </div>

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
    throughput: Number,
    progress: Number,
    maxProgress: Number
  },
  computed: {
    progressPercent: function() {
      return (this.progress / this.maxProgress) * 100.0;
    }
  }
};
</script>
