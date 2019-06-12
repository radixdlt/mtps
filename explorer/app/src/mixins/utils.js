export default {
  methods: {
    beautifulNumber: function (number, decimalPlaces) {
      return number
        .toFixed(decimalPlaces)
        .toString()
        .replace(/\B(?=(\d{3})+(?!\d))/g, "\xa0"); // non-breaking space
    },
    beautifulDate: function (timestamp) {
      const date = new Date(timestamp);
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
  }
}
