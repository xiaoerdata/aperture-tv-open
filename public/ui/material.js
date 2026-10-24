const root=document.documentElement;
let solid=false;try{solid=localStorage.getItem('aperture-material')==='solid';}catch{}
const host=document.querySelector('.toolbar,.guide-nav nav,.studio-header,.cinema-header');
function apply(){root.dataset.material=solid?'solid':'glass';if(button){button.textContent=solid?'恢复透光':'减少透明';button.setAttribute('aria-pressed',String(solid));}}
if(document.querySelector('.toolbar')){const lite=document.createElement('a');lite.href='/tv-lite'+location.search;lite.textContent='轻量模式';lite.title='旧内核或运行较慢的电视可使用';host.append(lite);const diagnostics=document.createElement('a');diagnostics.href='/diagnostics'+(new URLSearchParams(location.search).get('native')==='1'?'?native=1':'');diagnostics.textContent='电视检查';host.append(diagnostics);}
let button=null;if(host){button=document.createElement('button');button.type='button';button.className='material-toggle';button.dataset.mobileControl='action';button.title='减少透明效果，适合旧电视和需要更清晰对比度时';button.onclick=()=>{solid=!solid;try{localStorage.setItem('aperture-material',solid?'solid':'glass');}catch{}apply();};host.append(button);}apply();
