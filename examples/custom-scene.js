// Original extension example. Register this explicitly; uploaded JSON cannot execute it.
export function drawGarden(ctx,width,height,seconds,board) {
  ctx.fillStyle='#edf4ed';ctx.fillRect(0,0,width,height);
  const items=board.entries.filter(item=>item.type==='metric').slice(0,12);
  items.forEach((item,index)=>{
    const growth=Math.max(0,Math.min(1,item.value/item.target));
    const x=width*(index+1)/(items.length+1),base=height*.8,top=base-height*.45*growth;
    ctx.strokeStyle='#3d7855';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(x,base);ctx.quadraticCurveTo(x+Math.sin(seconds*.3+index)*8,top,x,top);ctx.stroke();
    ctx.fillStyle='#83a979';ctx.beginPath();ctx.ellipse(x+12,top+14,17,8,-.5,0,Math.PI*2);ctx.fill();
  });
}
