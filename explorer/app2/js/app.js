const SEVEN_DAYS = 604800000;
const TWO_DAYS = 172800000;
const ONE_HOUR = 3600000;

const charts = [];
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

var ticker;
var currentCompetitor;
var currentTps;

$(function() {
  nextTestTicker = null;
  durationTicker = null;
  currentCompetitor = 0;
  currentTps = 0;

  setupCharts();
  updateCharts(currentTps, 0);
  setupTransactions();
  showPage(STATE_UNKNOWN);

  setInterval(function() {
      getMetrics().then(function(result) {
        const newState = result.state;
        showPage(newState);
        updateSummary(result.peak, result.average, result.start, result.stop);

        if (newState != state) {
          ticker = null;
        }

        switch (newState) {
          case STATE_UNKNOWN:
            currentTps = 0;
            const future = 1560429000000; // 13 June 13:30 BST
            const now = new Date().getTime();
            const offset = future - now;
            startTicker(0, now, offset);
            break;
          case STATE_TERMINATED: // intentional fallthrough
          case STATE_FINISHED:
            $('#top-buttons').show();
            currentTps = result.peak;
            const f = 1560960000000; // 19 June 17:00 BST
            const n = new Date().getTime();
            const o = f - n;
            startTicker(3, n, o);
            updateCharts(currentTps, 100);
            break;
          case STATE_STARTED:
            currentTps = result.speed;
            startTicker(1, result.start, ONE_HOUR);
            updateCharts(currentTps, result.progress);
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

function setupCharts() {
  charts.push(
    initializeChart('canvas-1'),
    initializeChart('canvas-2'),
    initializeChart('canvas-3')
  );
}

function initializeChart(id) {
  return $('#' + id).chart({
      width: 512,
      height: 512,
      radius: 228,
      textSize: '80px',
      textColor: '#5B76F1',
      innerlineHeight: 8,
      outerLineHeight: 54,
      glowLineHeight: 36,
      parent: $('#' + id).parent(),
  }, 10, 1500);
}

function startTicker(idSuffix, timestamp, offset) {
  if (ticker) {
    return;
  }

  const now = new Date().getTime();
  const diff = timestamp > 0 ? (now - timestamp + offset) / 1000 : 0; // seconds
  ticker = $('#counter-' + idSuffix)
      .FlipClock(diff, {
          clockFace: 'HourlyCounter',
          countdown: true
        });
}

function setupTransactions() {
  setupWatchTextbox(1);
  setupWatchButton(1);
  setupWatchTextbox(2);
  setupWatchButton(2);
}

function setupWatchTextbox(idSuffix) {
  $('#txt-watch-' + idSuffix).keyup(function() {
    var address = $('#txt-watch-' + idSuffix).val();
    var isValid = WAValidator.validate(address, "bitcoin", "prod");
    if (isValid) {
      $('#btn-watch-' + idSuffix).removeAttr('disabled');
    } else {
      $('#btn-watch-' + idSuffix).attr('disabled', 'disabled');
    }
    $('#lst-watch-' + idSuffix + ' tbody').empty();
  });
}

function setupWatchButton(idSuffix) {
  $('#btn-watch-' + idSuffix).click(function() {
    var address = $('#txt-watch-' + idSuffix).val();
    getTransactions(address, 1)
        .then(function(result) {
          const rows = buildTransactionRowItems(result.data);
          $('#lst-watch-' + idSuffix + ' tbody').empty();
          $('#lst-watch-' + idSuffix + ' tbody').append(rows.join('\n'));
        }, function(error) {
          const message= buildTransactionErrorRowItem();
          $('#lst-watch-' + idSuffix + ' tbody').empty();
          $('#lst-watch-' + idSuffix + ' tbody').append(message);
        });
  });
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

function updateCharts(tps, progress) {
  const p = Math.min(100, progress);
  charts.forEach(function(chart) {
    chart.updateModel(tps, p);
  });
}

function updateSummary(peak, average, start, stop) {
  $('#tps-peak-2').text(beautifulNumber(peak));
  $('#tps-avg-2').text(beautifulNumber(average));

  //const dataSetAgeDate = new Date().getTime() - TWO_DAYS;
  const dataSetAgeDate = 1555502400000 // April 17, 12:00:00 UTC
  $('#age-date-2').text(beautifulDate(dataSetAgeDate));

  if (stop > 0) {
    const nextTestDate = stop + SEVEN_DAYS;
    $('#test-date-2').text('Next test will be: ' + beautifulDate(nextTestDate));
    $('#test-date-3').text('On ' + beautifulDate(stop) + ' we replayed history');
    $('#next-date-2').text(beautifulDate(nextTestDate));
    $('#next-time-2').text(beautifulTime(nextTestDate));
    $('#next-date-3').text(beautifulDateTime(nextTestDate));

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
