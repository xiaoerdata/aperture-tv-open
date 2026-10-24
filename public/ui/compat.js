/* Runs before module scripts on an old WebView. Keep ES5 syntax. */
(function(){var script=document.createElement('script');var modern=('noModule' in script)&&window.fetch&&window.EventSource&&window.CSS&&CSS.supports('display','grid');var version=/Chrom(?:e|ium)\/([0-9]+)/.exec(navigator.userAgent);if(!modern||(version&&Number(version[1])<89))location.replace('/tv-lite'+location.search);})();
