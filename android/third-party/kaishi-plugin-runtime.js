/* Original CatVod JS adapter. Runs only approved source modules; no bundled provider. */
(function () {
  'use strict';
  var host = globalThis.KaishiPlugin;
  var memory = Object.create(null);
  globalThis.req = globalThis.request = globalThis.http = function (url, options) {
    var value = JSON.parse(host.request(String(url), JSON.stringify(options || {})));
    if (value.error) throw new Error(value.error);
    return value;
  };
  globalThis.local = {
    get: function (key, name) { return memory[key + '\n' + name] || ''; },
    set: function (key, name, value) { memory[key + '\n' + name] = String(value); },
    delete: function (key, name) { delete memory[key + '\n' + name]; }
  };
  globalThis.base64Encode = function (text) { return btoa(unescape(encodeURIComponent(text))); };
  globalThis.base64Decode = function (text) { return decodeURIComponent(escape(atob(text))); };
  globalThis.joinUrl = function (base, target) { return new URL(String(target), String(base)).href; };
  globalThis.md5 = function (text) { return host.hash(String(text), 'MD5'); };
  globalThis.pdfh = function (html, rule) { return host.html(String(html), String(rule), false, ''); };
  globalThis.pdfa = function (html, rule) { return JSON.parse(host.html(String(html), String(rule), true, '')); };
  globalThis.pd = function (html, rule, base) { return host.html(String(html), String(rule), false, String(base || '')); };
  globalThis.__kaishiCall = async function (number, operation, args) {
    try {
      var spider = globalThis.__kaishiSpider;
      if (!spider || typeof spider[operation] !== 'function') throw new Error('Plugin has no ' + operation);
      var value = await spider[operation].apply(spider, args);
      var result = typeof value === 'string' ? value : JSON.stringify(value);
      host.done(number, result, '');
    } catch (error) {
      // Host reports a generic error; provider URLs/tokens do not go into logs.
      host.done(number, '', String(error && error.name || 'PluginError'));
    }
  };
})();
