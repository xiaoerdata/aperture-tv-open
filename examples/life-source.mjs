import {fileURLToPath} from 'node:url';
export function demoSource(mode='normal'){
  return {id:'local-demo',catalogId:'ha-community',label:'本地协议示例 · 非真实设备',transport:'stdio',command:process.execPath,args:[fileURLToPath(new URL('./life-fixture.mjs',import.meta.url))],readOnlyReviewed:true,readTools:['read_daily'],queries:[{id:'today',label:'读取示例家庭',tool:'read_daily',arguments:{mode},tiles:[
    {id:'temperature',title:'客厅温度',valuePath:'temperature',unit:'°C',observedAtPath:'observedAt',category:'home',ttlSeconds:900},
    {id:'weather',title:'出门提醒',valuePath:'weather',unit:'',observedAtPath:'observedAt',category:'outside',ttlSeconds:900},
    {id:'dinner',title:'今晚菜单',valuePath:'dinner',unit:'',observedAtPath:'observedAt',category:'food',ttlSeconds:900}
  ]}]};
}
