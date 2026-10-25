(function () {
  'use strict';
  var script = document.currentScript;
  if (!script) return;
  var base = script.src.slice(0, script.src.lastIndexOf('/') + 1);
  var parents = /(?:\?|&)edition=parents(?:&|$)/.test(location.search);
  var source = base + (parents ? 'kaishi-parents-v2.webp' : 'kaishi-icon-v2.webp');
  var images = document.querySelectorAll('[data-kaishi-brand]');
  for (var i = 0; i < images.length; i++) images[i].src = source;
  var favicon = document.querySelector('[data-kaishi-favicon]');
  if (favicon) favicon.href = source;
}());
