<template>
  <footer class="footer has-text-centered">
    <img class="logo" src="@/assets/speach-icon.svg">
    <section class="section">
      <h2 class="title is-size-2 is-family-secondary has-text-white">Join the conversation</h2>
    </section>
    <div class="columns is-centered has-text-weight-semibold has-text-white">
      <div class="column is-2">
        <a
          class="button is-primary is-fullwidth"
          href="https://t.me/radix_dlt"
          target="_blank"
        >Telegram</a>
      </div>
      <div class="column is-2">
        <a
          class="button is-primary is-fullwidth"
          href="https://twitter.com/radixdlt?lang=en"
          target="_blank"
        >Twitter</a>
      </div>
      <div class="column is-2">
        <a
          class="button is-primary is-fullwidth"
          href="https://forum.radixdlt.com/"
          target="_blank"
        >Forum</a>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.footer {
  background-image: url("../assets/pre-footer-bg.svg");
  background-size: cover;
  background-position: center center;
  background-repeat: no-repeat;
  background-color: #525f7f;
}
</style>
