
const STATE_UNKNOWN = "UNKNOWN";
const STATE_STARTED = "STARTED";
const STATE_FINISHED = "FINISHED";
const STATE_TERMINATED = "TERMINATED";

var state = '';
var page = 0;

/**
 * Requests TPS metrics from the server.
 *
 * @returns A Promise which, when resolved, provides an
 * object with a "speed" integer property and a "progress"
 * integer property.
 */
function getMetrics() {
  return new Promise(function(resolve, reject) {
    $.getJSON("https://test.radixdlt.com/api/metrics")
      .done(function(json) {
        const p = json.data.progress;
        const total = json.meta.progressMax;
        const percentage = Math.min(100, p / total * 100).toFixed();
        resolve({
            speed: json.data.spotTps,
            peak: json.data.peakTps,
            average: json.data.averageTps,
            progress: percentage,
            state: json.meta.testState,
            start: json.meta.testStart,
            stop: json.meta.testStop
          })
      })
      .fail(function() {
        reject();
      });
  });
}

/**
 * Tries to request the next page of transactions for a
 * given Bitcoin address. Returns a promise
 *
 * @param {String} bitcoinAddress The address to get
 * transactions for.
 *
 * @returns A Promise, which when resolved, provides an
 * object with a "data" array of transactions.
 */
function getMoreTransactions(bitcoinAddress) {
  return getTransactions(bitcoinAddress, page + 1);
}

/**
 * Tries to request the +10th page of transactions for a
 * given Bitcoin address. Returns a promise
 *
 * @param {String} bitcoinAddress The address to get
 * transactions for.
 *
 * @returns A Promise, which when resolved, provides an
 * object with a "data" array of transactions.
 */
function getMuchMoreTransactions(bitcoinAddress) {
  return getTransactions(bitcoinAddress, page + 10);
}

/**
 * Tries to request the previous page of transactions for
 * a given Bitcoin address. Returns a promise
 *
 * @param {String} bitcoinAddress The address to get
 * transactions for.
 *
 * @returns A Promise, which when resolved, provides an
 * object with a "data" array of transactions.
 */
function getPreviousTransactions(bitcoinAddress) {
  return getTransactions(bitcoinAddress, page - 1);
}

/**
 * Tries to request the given page of transactions for a
 * given Bitcoin address. Returns a promise
 *
 * @param {String} bitcoinAddress The address to get
 * transactions for.
 *
 * @returns A Promise, which when resolved, provides an
 * object with a "data" array of transactions.
 */
function getTransactions(bitcoinAddress, page) {
  return new Promise(function(resolve, reject) {
    $.getJSON("https://test.radixdlt.com/api/transactions/" + bitcoinAddress + "?page=" + page)
      .done(function(json) {
        if (json.data.length === 0){
          reject();
        }
        page = json.meta.page;
        resolve({ data: json.data });
      })
      .fail(function() {
        reject();
      });
  });
}

function buildTransactionRowItems(transactions) {
  const items = [];
  $.each(transactions, function(index, transaction) {
    var amount = transaction.amount;
    var amountClass = amount > 0 ? 'text-green' : 'text-red';
    var amountString = amount > 0 ? ('+ ' + amount + ' BTC') : ('- ' + amount + ' BTC');
    var dateString = niceDate(transaction.bitcoinBlockTimestamp);

    var amountCell = '<td class="' + amountClass + '">' + amountString + '</td>';
    var dateCell = '<td class="text-date">' + dateString + '</td>';
    items.push('<tr>' + amountCell + dateCell + '</tr>');
  });
  return items;
}

function buildTransactionErrorRowItem() {
  const msg = 'Couldn\'t find any transactions for that address right now. Try again later.'
  return '<tr><td class="text-red" colspan="2">' + msg + '</td><tr>'
}

function niceDate(timestamp) {
  var date = new Date(timestamp);
  var yyyy = date.getFullYear();
  var dd = date.getDate();
  var mm = (date.getMonth() + 1);
  var HH = date.getHours()
  var MM = date.getMinutes()
  var SS = date.getSeconds();

  if (dd < 10)
    dd = "0" + dd;

  if (mm < 10)
    mm = "0" + mm;

  if (HH < 10)
    HH = "0" + HH;

  if (MM < 10)
    MM = "0" + MM;

  if (SS < 10)
    SS = "0" + SS;

  return yyyy + '-' + mm + '-' + dd + ' ' + HH + ':' + MM + ':' + SS;
}

function beautifulDateTime(timestamp) {
  const date = new Date(timestamp);
  const options = {
    month: "long",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "UTC",
    timeZoneName: "short"
  };
  const dateTimeFormat = new Intl.DateTimeFormat("default", options).format;
  return dateTimeFormat(date);
}

function beautifulDate(timestamp) {
  const date = new Date(timestamp);
  const options = {
    month: "long",
    day: "numeric"
  };
  const dateTimeFormat = new Intl.DateTimeFormat("default", options).format;
  return dateTimeFormat(date);
}

function beautifulTime(timestamp) {
  const date = new Date(timestamp);
  const options = {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "UTC",
    timeZoneName: "short"
  };
  const dateTimeFormat = new Intl.DateTimeFormat("default", options).format;
  return dateTimeFormat(date);
}

function beautifulNumber(number, decimals) {
  return number
    .toFixed(decimals)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, "\xa0"); // non-breaking space
}
