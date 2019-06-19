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

var currentCompetitor;
var currentTps;

$(function() {
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
        updateSummary(result.peak, result.average, result.start, result.stop, result.next);

        switch (newState) {
          case STATE_UNKNOWN:
            currentTps = 0;
            startTicker(0, result.next);
            break;
          case STATE_STARTED:
            currentTps = result.speed;
            startTicker(1, result.start + ONE_HOUR);
            updateCharts(currentTps, result.progress);
            break;
          case STATE_FINISHED:
            $('#top-buttons').show();
            currentTps = result.peak;
            updateCharts(currentTps, 100);
            break;
          case STATE_TERMINATED:
            $('#top-buttons').show();
            currentTps = result.peak;
            startTicker(3, result.next);
            updateCharts(currentTps, 100);
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

function startTicker(idSuffix, future) {
  const now = new Date().getTime();
  const diff = future > now ? Math.round((future - now) / 1000) : 0; // seconds
  $('.counter-' + idSuffix).each(function() {
    $(this).FlipClock(diff, {
      clockFace: 'HourlyCounter',
      countdown: true
    });

  });
}

function setupTransactions() {
  setupWatchTextbox(1);
  setupWatchButton(1);
  setupWatchTextbox(2);
  setupWatchButton(2);
}

function setupWatchTextbox(idSuffix) {
  $('#txt-watch-' + idSuffix).on('input', function() {
    var address = $(this).val();
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
