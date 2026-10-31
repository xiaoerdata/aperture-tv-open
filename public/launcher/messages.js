/* ES5 read-only projection. Native Android owns source secrets, cache, schedule and TTS. */
(function(){'use strict';var active=location.hostname!=='appassets.androidplatform.net',configured=false,list=[],pending=null,timer=0,disposed=false;
 function changed(){window.dispatchEvent(new Event('kaishi-messages-change'));}
 function read(){if(!active||document.hidden||pending||disposed)return;var r=new XMLHttpRequest();pending=r;r.open('GET','/api/messages',true);r.timeout=18000;r.onload=function(){if(pending!==r)return;pending=null;try{if(r.status!==200)return;var data=JSON.parse(r.responseText);if(!Array.isArray(data.messages)||data.messages.length>100)return;configured=data.configured===true;list=data.messages;changed();}catch(e){}};r.onerror=r.ontimeout=function(){if(pending===r)pending=null;};r.send();}
 function notes(){return list.filter(function(m){return !m.expireAt||Date.parse(m.expireAt)>Date.now();}).map(function(m){return {id:m.id,type:'note',title:'留言',body:m.text,time:m.time||''};});}
 function state(value){active=!!value;clearInterval(timer);timer=0;if(!active||document.hidden){if(pending){var r=pending;pending=null;r.abort();}return;}read();timer=setInterval(function(){read();changed();},60000);}
 window.KaishiMessages={notes:notes,configured:function(){return configured;},active:state};window.apertureMessagesRefresh=read;
 document.addEventListener('visibilitychange',function(){state(active);});window.addEventListener('pagehide',function(){disposed=true;state(false);});state(active);
}());
