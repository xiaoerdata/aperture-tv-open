// Original procedural scene. Ambient artwork, not weather or astronomical data.
export function drawDaylight(ctx,w,h,t) {
  const bg=ctx.createLinearGradient(0,0,w,h);bg.addColorStop(0,'#f1f5ed');bg.addColorStop(.65,'#e4eee5');bg.addColorStop(1,'#c5d9c6');ctx.fillStyle=bg;ctx.fillRect(0,0,w,h);
  const sunX=w*.76,sunY=h*.27,r=Math.min(w*.15,h*.25);
  ctx.fillStyle='#f0c39a';ctx.beginPath();ctx.arc(sunX,sunY,r,0,Math.PI*2);ctx.fill();
  ctx.strokeStyle='#b8caba';ctx.lineWidth=1;ctx.beginPath();ctx.arc(w*.76,h*.37,h*.48,Math.PI*.9,Math.PI*2.05);ctx.stroke();
  for(let layer=0;layer<60;layer++){
    const p=layer/59;ctx.beginPath();
    for(let i=0;i<=100;i++){
      const x=w*i/100;
      const wave=Math.sin(i/100*5.7 + t*.12)*h*.027 + Math.cos(i/100*3.8 + .8)*h*.045;
      const y=h*.7+p*h*.43+wave*(.4+p*.6)-Math.sin(i/100*Math.PI)*h*.09;
      i===0?ctx.moveTo(x,y):ctx.lineTo(x,y);
    }
    ctx.strokeStyle=`rgba(69,112,86,${.07+p*.2})`;ctx.lineWidth=1;ctx.stroke();
  }
  ctx.fillStyle='#d7e3cc';ctx.beginPath();ctx.moveTo(0,h*.88);ctx.bezierCurveTo(w*.2,h*.65,w*.4,h*.98,w*.58,h);ctx.lineTo(0,h);ctx.fill();
  for(let i=0;i<8;i++){
    const x=w*(.42+i*.025),y=h*(.51+Math.sin(i)*.015)+Math.sin(t*.5+i)*2;
    ctx.strokeStyle='#6c8971';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(x-4,y);ctx.quadraticCurveTo(x-2,y-4,x,y);ctx.quadraticCurveTo(x+2,y-4,x+4,y);ctx.stroke();
  }
}
