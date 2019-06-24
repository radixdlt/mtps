const MAX_PAGE_BUTTONS = 4;
const TWO_DAYS = 172800000;
const ONE_HOUR = 3600000;

const competitors = [
  {name: 'Visa', tps: 2000},
  {name: 'Google', tps: 75000},
  {name: 'YouTube', tps: 79000},
  {name: 'Twitter', tps: 85000},
  {name: 'Nasdaq', tps: 250000},
  {name: 'Alipay', tps: 256000},
  {name: 'Whatsapp', tps: 483000},
  {name: 'WeChat', tps: 520000}
];

var forceUpdateTickers;
var currentPageOfTransactions;
var currentCompetitor;
var currentProgress;
var currentTps;
var graphs;

$(function() {
  forceUpdateTickers = true;
  currentPageOfTransactions = 1;
  currentCompetitor = 0;
  currentProgress = 0;
  currentTps = 0;
  graphs = [];

  // Force friendly re-sync of count down tickers every time the window
  // gains focus. This prevents count down digression due to timers
  // being halted under certain circumstances on certain device types
  // (e.g. after screen lock kicks in on some mobile devices).
  $(window).focus(function() {
    forceUpdateTickers = true;
  });

  setupGraphs();
  setupSearch();

  updateGraphs(currentTps, currentProgress);
  showPage(STATE_UNKNOWN);

  setInterval(function() {
      getMetrics().then(function(result) {
        const newState = result.state;
        showPage(newState);
        updateSummary(result.peak, result.average, result.start, result.stop, result.next);

        switch (newState) {
          case STATE_UNKNOWN:
            currentTps = 0;
            updateTickers(0, result.next);
            break;
          case STATE_STARTED:
            currentTps = result.speed;
            updateTickers(1, result.start + ONE_HOUR);
            updateGraphs(currentTps, result.progress);
            break;
          case STATE_FINISHED:
            $('#top-buttons').show();
            currentTps = result.peak;
            updateGraphs(currentTps, 100);
            break;
          case STATE_TERMINATED:
            $('#top-buttons').show();
            currentTps = result.peak;
            updateTickers(3, result.next);
            updateGraphs(currentTps, 100);
            break;
        }

        // Update comparison labels
        if (currentTps === 0) {
          return;
        }

        // Find the next competitor which has less TPS than we do.
        var competitor = competitors[currentCompetitor];
        var startIndex = currentCompetitor;
        while (competitor.tps > currentTps) {
          currentCompetitor = (currentCompetitor + 1) % competitors.length;
          competitor = competitors[currentCompetitor];

          if (currentCompetitor == startIndex && competitor.tps > currentTps) {
            // We have cycled through all competitors and all
            // have higher TPS than we do. Exit in shame.
            $('.graph-header4').empty();
            return;
          }
        }
        // Calculate the fraction by which we are faster
        const fraction = Math.round(currentTps / competitor.tps);
        if (fraction === 0) {
          return;
        }

        // Update the labels.
        const label = fraction + 'x ' + competitor.name;
        $('.graph-header4').text(label);

        currentCompetitor = (currentCompetitor + 1) % competitors.length;
      });
    }, 2000);
  });

function setupGraphs() {
  graphs = $('.graph')
      .map(function() {
        return $(this).chart({
          width: 512,
          height: 512,
          radius: 228,
          textSize: '80px',
          textColor: '#5B76F1',
          innerlineHeight: 8,
          outerLineHeight: 54,
          glowLineHeight: 36,
          parent: $(this).parent(),
        }, 10, 1500)
      })
      .get();
}

function setupSearch() {
  $('.search-textbox').each(function() {
    $(this).on('input', function() {
      $('.search-list tbody').empty();
      validateBitcoinAddress($(this).val())
          .then(function() {
            $('.search-button').each(function() {
              $(this).removeAttr('disabled');
            });
          }, function() {
            $('.search-button').each(function() {
              $(this).attr('disabled', true);
            });
          });
    });
  })
  $('.search-button').each(function() {
    $(this).on('click', function() {
      getPageOfTransactions($(this).prev().val(), 1);
    });
  });
}

function updateGraphs(tps, progress) {
  currentProgress = Math.max(currentProgress, Math.min(100, progress));
  graphs.forEach(function(graph) {
    graph.updateModel(tps, currentProgress);
  });
}

function updateTickers(future) {
  if (forceUpdateTickers) {
    forceUpdateTickers = false;
    const now = new Date().getTime();
    const diffSeconds = future > now ? Math.round((future - now) / 1000) : 0;
    $('.ticker').each(function() {
      $(this).FlipClock(diffSeconds, {
        clockFace: 'HourlyCounter',
        countdown: true
      });
    });
  }
}

function showPage(newTestState) {
  if (newTestState === state) {
    return;
  }

  if (newTestState === STATE_STARTED) {
    state = newTestState;
    $('.page-0').hide();
    $('.page-2').hide();
    $('.page-3').hide();
    $('.page-1').show();
  } else if (newTestState === STATE_FINISHED) {
    state = newTestState;
    $('.page-0').hide();
    $('.page-1').hide();
    $('.page-3').hide();
    $('.page-2').show();
  } else if (newTestState === STATE_TERMINATED) {
    state = newTestState;
    $('.page-0').hide();
    $('.page-1').hide();
    $('.page-2').hide();
    $('.page-3').show();
  } else if (newTestState === STATE_UNKNOWN) {
    state = newTestState;
    $('.page-1').hide();
    $('.page-2').hide();
    $('.page-3').hide();
    $('.page-0').show();
  }
}

function updateSummary(peak, average, start, stop, next) {
  $('#tps-peak-2').text(beautifulNumber(peak));
  $('#tps-avg-2').text(beautifulNumber(average));

  //const dataSetAgeDate = new Date().getTime() - TWO_DAYS;
  const dataSetAgeDate = 1555502400000 // April 17, 12:00:00 UTC
  $('#age-date-2').text(beautifulDate(dataSetAgeDate));

  if (next) {
    $('.next-datetime').text(beautifulDateTime(next));
    $('.next-date').text(beautifulDate(next));
    $('.next-time').text(beautifulTime(next));
  }

  if (stop > 0) {
    $('.test-date').text(beautifulDate(stop));

    if (start > 0 && start < stop) {
      const duration = Math.floor((stop - start) / 1000);
      const minutes = Math.floor(duration / 60);
      const seconds = duration % 60
      var durationString = '';

      if (minutes > 0) {
        if (minutes > 1) {
          durationString += minutes + ' minutes';
        } else {
          durationString += minutes + ' minute';
        }

        if (seconds > 1) {
          durationString += ' and ' + seconds + ' seconds';
        } else if (seconds > 0) {
          durationString += ' and ' + seconds + ' second';
        }
      } else if (seconds > 0) {
        if (seconds > 1) {
          durationString += seconds + ' seconds';
        } else {
          durationString += seconds + ' second';
        }
      }

      $('#test-duration-2').text('Test Complete in ' + durationString);
    }
  }
}


// BEGIN: Utils
function validateBitcoinAddress(bitcoinAddress) {
  return new Promise(function(resolve, reject) {
    if (WAValidator.validate(bitcoinAddress, "bitcoin", "prod")) {
      resolve();
    } else {
      reject();
    }
  });
}

function getNextPageOfTransactions(bitcoinAddress) {
  return getPageOfTransactions(bitcoinAddress, currentPageOfTransactions + 1);
}

function skipTenPagesOfTransactions(bitcoinAddress) {
  return getPageOfTransactions(bitcoinAddress, currentPageOfTransactions + 10);
}

function getPreviousPageOfTransactions(bitcoinAddress) {
  return getPageOfTransactions(bitcoinAddress, currentPageOfTransactions - 1);
}

function getPageOfTransactions(bitcoinAddress, requestedPage) {
  getTransactions(bitcoinAddress, requestedPage)
      .then(function(result) {
        currentPageOfTransactions = result.page;
        $('.search-list tbody').each(function() {
          $(this).empty().append(buildTransactionList(result.data));
        });
        $('.search-paging').each(function() {
          $(this).empty().append(buildTransactionPagingButtons(bitcoinAddress));
        });
      }, function(error) {
        $('.search-list tbody').each(function() {
          $(this).empty().append(buildTransactionError());
        });
        $('.search-paging').each(function() {
          $(this).empty();
        });
      });
}

function buildTransactionList(transactions) {
  return transactions
      .map(function(transaction) {
        var amount = transaction.amount;
        var amountClass = amount > 0 ? 'text-green' : 'text-red';
        var amountString = amount > 0 ? ('+ ' + amount + ' BTC') : ('- ' + amount + ' BTC');
        var dateString = niceDate(transaction.bitcoinBlockTimestamp);
        var amountCell = '<td class="' + amountClass + '">' + amountString + '</td>';
        var dateCell = '<td class="text-date">' + dateString + '</td>';
        return '<tr>' + amountCell + dateCell + '</tr>';
      })
      .reduce(function(collected, row) {
        return collected + '\n' + row;
      }, '');
}

function buildTransactionError() {
  return '<tr><td class="text-red" colspan="2">' +
      'Couldn\'t find any transactions for that address right now. Try again later.' +
      '</td><tr>';
}

function buildTransactionPagingButtons(bitcoinAddress) {
  const buttons = [];
  var button = 1;
  buttons.push(currentPageOfTransactions > 1 ?
    '<button class="btn btn-success" onclick="getPreviousPageOfTransactions(\'' + bitcoinAddress + '\')">&lt;</button>' :
    '<button class="btn btn-success" disabled>&lt;</button>');
  buttons.push('<span>');
  if (currentPageOfTransactions > MAX_PAGE_BUTTONS) {
    buttons.push('<button class="btn btn-outline-success" disabled>&hellip;</button>');
    button++;
  }
  for(button; button <= MAX_PAGE_BUTTONS && button <= currentPageOfTransactions; button++) {
    var page = Math.max(0, currentPageOfTransactions - MAX_PAGE_BUTTONS) + button;
    buttons.push(page === currentPageOfTransactions ?
      '<button class="btn btn-success" onclick="getPageOfTransactions(\'' + bitcoinAddress + '\', ' + page + ')">' + page + '</button>' :
      '<button class="btn btn-outline-success" onclick="getPageOfTransactions(\'' + bitcoinAddress + '\', ' + page + ')">' + page + '</button>');
  }
  buttons.push('</span>');
  buttons.push('<span>');
  buttons.push('<button class="btn btn-success" onclick="getNextPageOfTransactions(\'' + bitcoinAddress + '\')">&gt;</button>');
  buttons.push('<button class="btn btn-success" onclick="skipTenPagesOfTransactions(\'' + bitcoinAddress + '\')">+10</button>');
  buttons.push('</span>');
  return buttons.join('\n');
}
// END: Utils
