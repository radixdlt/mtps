
const STATE_UNKNOWN = "UNKNOWN";
const STATE_STARTED = "STARTED";
const STATE_FINISHED = "FINISHED";
const STATE_TERMINATED = "TERMINATED";

// Access demo mode by adding parameters e.g. '?demo&state=FINISHED'
const DEMO_MODE = getParameterByName('demo') !== null ? true : false;
const DEMO_STATE = getParameterByName('state') !== null ? getParameterByName('state') : STATE_STARTED;

const MOCK_TRANSACTIONS = [
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
];

const MOCK_METRICS = function() {
  return {
    spot: Math.floor(Math.random() * 1000000),
    peak: Math.floor(Math.random() * 1000000),
    average: Math.floor(Math.random() * 1000000),
    progress: Math.floor(Math.random() * 100),
    latest: 1561378584000, // 2019-06-24 12:16:24 UTC
    state: DEMO_STATE,
    start: 1550867544,
    stop: 1560867544,
    next: 1560867544 + (24 * 60 * 60 * 1000)
  };
}

/**
 * Requests TPS metrics from the server.
 *
 * @returns A Promise which, when resolved, provides an
 * object with a "speed" integer property and a "progress"
 * integer property.
 */
function getMetrics() {
  return new Promise(function(resolve, reject) {
    if(DEMO_MODE) {
      resolve(MOCK_METRICS());
      return;
    }

    $.getJSON("/api/metrics")
      .done(function(json) {
        const p = json.data.progress;
        const total = json.meta.progressMax;
        const percentage = Math.min(100, p / total * 100).toFixed();
        resolve({
            spot: json.data.spotTps,
            peak: json.data.peakTps,
            average: json.data.averageTps,
            progress: percentage,
            latest: json.meta.progressLatest,
            state: json.meta.testState,
            start: json.meta.testStart,
            stop: json.meta.testStop,
            next: json.meta.testNext
          })
      })
      .fail(function() {
        reject();
      });
  });
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
    if(DEMO_MODE) {
      resolve({ data: MOCK_TRANSACTIONS, page: page });
      return;
    }

    $.getJSON("/api/transactions/" + bitcoinAddress + "?page=" + page)
      .done(function(json) {
        if (json.data.length === 0){
          reject();
        } else {
          resolve({ data: json.data, page: json.meta.page });
        }
      })
      .fail(function() {
        reject();
      });
  });
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
