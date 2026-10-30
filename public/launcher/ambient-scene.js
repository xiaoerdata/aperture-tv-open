// Original procedural artwork: no astronomical observations, video or downloaded textures.
(function(){'use strict';
 window.KaishiSky=function(canvas){
  var ctx=canvas.getContext('2d'),layer=document.createElement('canvas'),dust=layer.getContext('2d'),w=1280,h=720,raf=0,last=0,phase=0,running=false,mode='galaxy',points=[],seed=71031,frames=0;
  function random(){seed=(seed*1664525+1013904223)>>>0;return seed/4294967296;}
  function build(){canvas.width=layer.width=w;canvas.height=layer.height=h;dust.clearRect(0,0,w,h);seed=71031;points=[];
   var haze=dust.createRadialGradient(w*.69,h*.48,0,w*.69,h*.48,w*.48);haze.addColorStop(0,'#132337');haze.addColorStop(.45,'#0b1423');haze.addColorStop(1,'#020409');dust.fillStyle=haze;dust.fillRect(0,0,w,h);
   for(var i=0;i<1700;i++){var radius=Math.pow(random(),.7)*550,arm=i%3,angle=radius*.014+arm*Math.PI*2/3+(random()-.5)*.85,x=w*.69+Math.cos(angle)*radius,y=h*.48+Math.sin(angle)*radius*.40;dust.fillStyle='rgba(135,163,193,'+(.035+random()*.20)+')';dust.beginPath();dust.arc(x,y,.35+random()*.9,0,Math.PI*2);dust.fill();}
   for(i=0;i<240;i++){points.push({x:random()*w,y:random()*h,r:.35+random()*1.1,s:random(),a:random()*6.28});}
  }
  function draw(){ctx.fillStyle='#020409';ctx.fillRect(0,0,w,h);
   if(mode==='galaxy'){ctx.save();ctx.translate(w*.69,h*.48);ctx.rotate(Math.sin(phase*.05)*.018);ctx.translate(-w*.69,-h*.48);ctx.drawImage(layer,0,0);ctx.restore();}
   else{var g=ctx.createRadialGradient(w*.68,h*.60,0,w*.68,h*.60,w*.6);g.addColorStop(0,'#0b1b26');g.addColorStop(1,'#020409');ctx.fillStyle=g;ctx.fillRect(0,0,w,h);}
   for(var i=0;i<points.length;i++){var p=points[i],x,y;
    if(mode==='galaxy'){x=(p.x+phase*(1+p.s)*.8)%w;y=p.y+Math.sin(phase*.14+p.a)*4;}
    else{x=(p.x+phase*(3+p.s*5))%w;y=p.y+Math.sin(x/w*5+phase*.12+p.a)*30;}
    ctx.fillStyle='rgba('+(p.s>.6?'178,189,193':'98,146,169')+','+(.14+p.s*.28)+')';ctx.beginPath();ctx.ellipse(x,y,mode==='flow'?p.r*5:p.r,p.r,mode==='flow'?-.2:0,0,6.283);ctx.fill();
   }frames++;
  }
  function tick(now){if(!running)return;raf=requestAnimationFrame(tick);if(now-last<66)return;phase+=Math.min(100,now-last||66)/1000;last=now;draw();}
  build();draw();return {start:function(value,animate){mode=value==='flow'?'flow':'galaxy';this.stop();draw();if(animate){running=true;last=performance.now();raf=requestAnimationFrame(tick);}},stop:function(){running=false;if(raf)cancelAnimationFrame(raf);raf=0;},state:function(){return {running:running,frames:frames,particles:240,width:w,height:h,mode:mode};}};
 };
}());
