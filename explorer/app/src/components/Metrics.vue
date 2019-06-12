<template>
  <div class="container">
    <div class="columns is-centered">
      <div class="column is-half">
        <progress
          class="progress is-primary"
          v-bind:value="progress"
          v-bind:max="max"
        >{{ percent.toFixed(0) }}</progress>
      </div>
    </div>
    <div class="columns is-centered">
      <div class="column is-one-fifth">
        <div class="is-size-5">Throughput</div>
        <h1 class="title is-1 has-text-primary">
          {{ tps.toFixed(0) }}
          <span class="is-size-7">tps</span>
        </h1>
      </div>
      <div class="column is-1"></div>
      <div class="column is-one-fifth">
        <div class="is-size-5">Age</div>
        <h1 class="title is-1 has-text-primary">
          {{ age.toFixed(0) }}
          <span class="is-size-7">ms</span>
        </h1>
      </div>
      <div class="column is-1"></div>
      <div class="column is-one-fifth">
        <div class="is-size-5">Progress</div>
        <h1 class="title is-1 has-text-primary">
          {{ percent.toFixed(0) }}
          <span class="is-size-7">%</span>
        </h1>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  data: function() {
    return {
      max: 2402981 // Should be 410+ million in real test
    };
  },
  props: {
    age: Number,
    tps: Number,
    progress: Number
  },
  computed: {
    percent: function() {
      return (this.progress / this.max) * 100.0;
    }
  }
};
</script>
