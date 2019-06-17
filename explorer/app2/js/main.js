function Chart(canvasElement, config, model) {
    var chartEngine = new ChartEngine(canvasElement, config, model);
    $(window).resize(function () {
        chartEngine.onResize();
    });

    return chartEngine;
};

(function (factory) {
    if (typeof define === 'function' && define.amd) {
        // AMD. Register as an anonymous module.
        define(['jquery'], factory);
    } else if (typeof module === 'object' && module.exports) {
        // Node/CommonJS
        module.exports = function (root, jQuery) {
            if (jQuery === undefined) {
                // require('jQuery') returns a factory that requires window to
                // build a jQuery instance, we normalize how we use modules
                // that require this pattern but the window provided is a noop
                // if it's defined (how jquery works)
                if (typeof window !== 'undefined') {
                    jQuery = require('jquery');
                }
                else {
                    jQuery = require('jquery')(root);
                }
            }
            factory(jQuery);
            return jQuery;
        };
    } else {
        // Browser globals
        factory(jQuery);
    }
}(function ($) {
    $.fn.chart = function (config, number, percentage) { return new Chart(this, config, number, percentage); };
}));

function ChartEngine(canvasElement, config, number, percentage) {
    var chartEngine = this;
    this.config = config;
    this.number = number;
    this.percentage = percentage;
    this.canvas = document.getElementById(canvasElement[0].id);
    this.inDrawing = false;

    this.config.cx = getPerfectPixel(config.width / 2);
    this.config.cy = getPerfectPixel(config.height / 2);
    this.ctx = this.canvas.getContext("2d");
    this.ctx.scale(2,2);

    var gradient = this.ctx.createLinearGradient(0, 0, config.width, 0);
    //  var gradient = this.ctx.createLinearGradient(50, 50,100, 0);
 //   gradient.addColorStop(0, "#565BB9");
    gradient.addColorStop(1, "rgba(4,201, 136, 1.0)");
    gradient.addColorStop(0.5, "rgba(55,155, 200, 1.0)");
    gradient.addColorStop(0, "rgba(84,125, 234, 1.0)");
   // gradient.addColorStop(1, "#24B47E");

    this.config.outerColor = gradient,

        config.NumDraws = 0;
    config.NumCalls = 0;
    this.onResize();
}


ChartEngine.prototype.onResize = function (params) {
    var rect = this.canvas.getBoundingClientRect();
}

ChartEngine.prototype.updateModel = function (number, percentage) {
    this.number = number;
    this.percentage = percentage;
    this.draw();
}

ChartEngine.prototype.draw = function () {
    this.config.NumCalls++;

    if (this.inDrawing) return;

    if (this.skipDrawing) return;

    var that = this

    this.inDrawing = true;
    setTimeout(
        function () {
            that.inDrawing = false;
        }, 1000 / 60
    );

    var canvas = this.canvas;
    var ctx = this.ctx;
    var config = this.config;
    config.NumDraws++;

    canvas.width = this.config.width;
    canvas.height = this.config.height;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    drawInnerCircle(ctx, this.config);
    drawOuterCirlce(ctx, this.config, this.percentage);
    drawZero(ctx, this.config, this.percentage);
    drawDrawPercentage(ctx, this.config, this.percentage);
    // drawValue(ctx, this.config, this.number);
    updateValues(ctx, this.config, this.number);

}

function updateValues(ctx, config, number) {
    config.parent.children('.graph-counter').text(FormatIntegerValue(number))
    config.parent.children('.graph-comparison').text(Math.floor(number/20000) + "x Visa")
}

function drawValue(ctx, config, number) {
    ctx.beginPath();
    ctx.font = "500 " + config.textSize + " Roboto";
    ctx.fillStyle = config.textColor;
    var n = FormatIntegerValue(number);
    ctx.fillText(n, config.width / 2 - ctx.measureText(n).width / 2, config.height / 2 + 16);

    ctx.beginPath();
    ctx.font = "18px Open Sans";
    ctx.fillStyle = "#515F7F";
    var text = config.text1;
    ctx.fillText(text, config.width / 2 - ctx.measureText(text).width / 2, config.height / 2 - 70);

    ctx.beginPath();
    ctx.font = "bold 18px Open Sans";
    ctx.fillStyle = config.textColor;
    var text = config.text3;
    ctx.fillText(text, config.width / 2 - ctx.measureText(text).width / 2, config.height / 2 + 60);

    ctx.beginPath();
    ctx.font = "bold 18px Open Sans";
    ctx.fillStyle = config.textColor;
    var text = config.text4;
    ctx.fillText(text, config.width / 2 - ctx.measureText(text).width / 2, config.height / 2 + 160);
}

function drawInnerCircle(ctx, config) {
    ctx.beginPath();
    ctx.strokeStyle = config.outerColor;
    ctx.lineWidth = config.innerlineHeight;
    ctx.arc(config.cx, config.cy, config.radius, 0, 2 * Math.PI);
    ctx.stroke();
}

function drawOuterCirlce(ctx, config, percentage) {

    var angle = angleToRadians(percentage * 3.6 - 90);
    ctx.beginPath();
    ctx.strokeStyle = config.outerColor;
    ctx.lineWidth = config.outerLineHeight;
    if (percentage > 1)
        ctx.arc(config.cx, config.cy, config.radius, angleToRadians(270.5), angle);
    ctx.stroke();
}

function drawZero(ctx, config, percentage) {

    ctx.beginPath();
    ctx.fillStyle = config.outerColor;
    var p = pointOnCircle(config.cx, config.cy, config.radius - 0.5, angleToRadians(270.5));
    //  if (percentage > 50)
    ctx.arc(p.x, p.y, config.outerLineHeight / 2, angleToRadians(90), angleToRadians(-270));
    ctx.fill();

}

function drawDrawPercentage(ctx, config, percentage) {

    ctx.beginPath();
    ctx.fillStyle = 'white';
    // var x = 195
    var p = pointOnCircle(config.cx, config.cy, config.radius, angleToRadians(percentage * 3.6 - 90));
    ctx.arc(p.x, p.y, getPerfectPixel(config.outerLineHeight / 2), angleToRadians(90), angleToRadians(-270));
    ctx.fill();

    ctx.beginPath();
    ctx.font = "500 18px Roboto";
    ctx.fillStyle = config.outerColor;
    var text = percentage + '%';
    ctx.fillText(text, p.x - ctx.measureText(text).width / 2 + 2, p.y + 5);

    // glow
    ctx.beginPath();
    ctx.strokeStyle = 'rgba(255,255,255,0.2)';
    ctx.lineWidth = config.glowLineHeight;
    p = pointOnCircle(config.cx, config.cy, config.radius, angleToRadians(percentage * 3.6 - 90));
    ctx.arc(p.x, p.y, config.outerLineHeight / 2, angleToRadians(90), angleToRadians(-270));
    ctx.stroke();

}


function angleToRadians(angle) {
    return (2 * Math.PI * angle / 360.0) % (2 * Math.PI);
}

function pointOnCircle(centerX, centerY, radius, angle) {
    var x = Math.cos(angle) * radius + centerX;
    var y = Math.sin(angle) * radius + centerY;

    return { x: x, y: y };
}

function getPerfectPixel(val) {
    return val + 0.5;
}


function FormatIntegerValue(value) {
    if (value == null) return "";
    var help = value.toString();
    var formattedValue = "";
    var counter = 0;
    var strLength = help.length;
    var negative = value < 0 ? 1 : 0;
    for (var i = help.length; i > 0; i--) {

        formattedValue = help.charAt(i - 1) + formattedValue;
        counter++;
        if (counter % 3 == 0 && i > 0 && counter != strLength - negative) {
            formattedValue = " " + formattedValue;
        }
    }

    return formattedValue;
};
