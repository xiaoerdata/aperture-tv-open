export function drawGallery(ctx,w,h,t) {
  ctx.fillStyle='#e9eff0';ctx.fillRect(0,0,w,h);
  ctx.fillStyle='#d0dfe0';ctx.beginPath();ctx.moveTo(0,h*.78);ctx.lineTo(w,h*.69);ctx.lineTo(w,h);ctx.lineTo(0,h);ctx.fill();
  ctx.strokeStyle='#bbcdce';ctx.lineWidth=1;
  for(let i=0;i<15;i++){ctx.beginPath();ctx.moveTo(w*.48,-h*.5);ctx.lineTo(w*(i/7-1),h*1.4);ctx.stroke();}
  ctx.fillStyle='#e9eff0';ctx.fillRect(0,0,w,h*.68);
  ctx.strokeStyle='#c6d5d5';ctx.beginPath();ctx.arc(w*.68,h*.43,h*.43,0,Math.PI*2);ctx.stroke();
  ctx.beginPath();ctx.arc(w*.68,h*.43,h*.46,0,Math.PI*2);ctx.stroke();
  ctx.fillStyle='#ebac79';ctx.beginPath();ctx.arc(w*.22+Math.cos(t*.07)*w*.012,h*.5+Math.sin(t*.07)*h*.025,h*.067,0,Math.PI*2);ctx.fill();
  ctx.strokeStyle='#749994';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(w*.22,h*.57);ctx.lineTo(w*.22,h*.74);ctx.stroke();
  ctx.fillStyle='#baccc9';ctx.beginPath();ctx.ellipse(w*.22,h*.755,w*.06,h*.015,0,0,Math.PI*2);ctx.fill();
}
