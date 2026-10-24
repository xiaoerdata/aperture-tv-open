export function drawLandscape(ctx,w,h,t,board) {
  ctx.fillStyle='#e9f1ee';ctx.fillRect(0,0,w,h);
  const metrics=board.entries.filter(e=>e.type==='metric').slice(0,3);
  const colors=[[62,126,113],[84,133,144],[159,112,72]];
  metrics.forEach((metric,index)=>{
    const ratio=Math.min(1,metric.value/metric.target),x=w*(.205+index*.295),base=h*.66;
    const height=h*(.06+ratio*.29),rx=w*.112,ry=h*.038;
    const [r,g,b]=colors[index];
    ctx.fillStyle='rgba(35,75,63,.07)';ctx.beginPath();ctx.ellipse(x+8,base+10,rx*1.1,ry*1.4,0,0,Math.PI*2);ctx.fill();
    for(let n=0;n<38;n++){
      const p=n/37, y=base-height*p, radius=rx*(1-p*.63),shift=Math.sin(p*4+t*.3+index)*w*.004*p;
      ctx.fillStyle=`rgb(${r+Math.round(p*28)},${g+Math.round(p*38)},${b+Math.round(p*28)})`;
      ctx.beginPath();ctx.ellipse(x+shift,y,radius,ry*(1-p*.3),0,0,Math.PI*2);ctx.fill();
      ctx.strokeStyle='rgba(235,248,239,.28)';ctx.lineWidth=.7;ctx.stroke();
    }
    ctx.strokeStyle='rgba(66,104,89,.4)';ctx.setLineDash([2,5]);ctx.beginPath();ctx.moveTo(x,base-height-ry);ctx.lineTo(x,base-height-ry-h*.06);ctx.stroke();ctx.setLineDash([]);
  });
}
