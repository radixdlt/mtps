
const STATE_UNKNOWN = "UNKNOWN";
const STATE_STARTED = "STARTED";
const STATE_FINISHED = "FINISHED";
const STATE_TERMINATED = "TERMINATED";
const MONTH_LONG = [ "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December" ];

var state = '';
var page = 0;

// Access demo mode by adding parameters e.g. '?demo&state=FINISHED'
const demoMode = getParameterByName('demo') !== null ? true : false;
const demoState = getParameterByName('state') !== null ? getParameterByName('state') : STATE_STARTED;

/**
 * Requests TPS metrics from the server.
 *
 * @returns A Promise which, when resolved, provides an
 * object with a "speed" integer property and a "progress"
 * integer property.
 */
function getMetrics() {
  return new Promise(function(resolve, reject) {
    if(demoMode) {
      resolve({
        speed: Math.floor(Math.random() * 1000000),
        peak: Math.floor(Math.random() * 1000000),
        average: Math.floor(Math.random() * 1000000),
        progress: Math.floor(Math.random() * 100),
        state: demoState,
        start: 1550867544,
        stop: 1560867544
      })
      return
    }

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
    if(demoMode) {
      resolve({
        data: [
          {
            "bitcoinTransactionId": "hjidf2f68e38b980a6c4cec21e71851b0d8a5847d85208331a27321a9967bbd6",
            "bitcoinBlockTimestamp": 1234567890,
            "amount": 123.457
          },
          {
            "bitcoinTransactionId": "2f2442f68e38b980a6c4cec21e71851b0d8a5847d85208331a27321a9967bbd6",
            "bitcoinBlockTimestamp": 7462738942,
            "amount": 909.10
          },
          {
            "bitcoinTransactionId": "6ce5f3ff98d4d39e1ea408ec8128a17f6c426549bf8476738e30c418950b4911",
            "bitcoinBlockTimestamp": 23849302039,
            "amount": -10
          },
          ]
        })
    }


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
  var d = date.getDate();
  var m = (date.getMonth() + 1);
  var H = date.getHours()
  var M = date.getMinutes()
  var S = date.getSeconds();
  return yyyy + '-' + pad(m) + '-' + pad(d) + ' ' + pad(H) + ':' + pad(M) + ':' + pad(S);
}

function beautifulDateTime(timestamp) {
  const date = new Date(timestamp);
  const month = MONTH_LONG[date.getUTCMonth()];
  const d = date.getUTCDate();
  const h = date.getUTCHours();
  const m = date.getUTCMinutes();
  return month + ' ' + d + ', ' + pad(h) + ':' + pad(m) + ' UTC'
}

function beautifulDate(timestamp) {
  const date = new Date(timestamp);
  const month = MONTH_LONG[date.getMonth()];
  const d = date.getDate();
  return month + ' ' + pad(d);
}

function beautifulTime(timestamp) {
  const date = new Date(timestamp);
  const h = date.getUTCHours();
  const m = date.getUTCMinutes();
  return pad(h) + ':' + pad(m) + ' UTC'
}

function pad(n) {
  return n < 10 ? '0' + n : n;
}

function beautifulNumber(number, decimals) {
  return number
    .toFixed(decimals)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, "\xa0"); // non-breaking space
}



function getParameterByName(name, url) {
  if (!url) url = window.location.href;
  name = name.replace(/[\[\]]/g, '\\$&');
  var regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)'),
      results = regex.exec(url);
  if (!results) return null;
  if (!results[2]) return '';
  return decodeURIComponent(results[2].replace(/\+/g, ' '));
}