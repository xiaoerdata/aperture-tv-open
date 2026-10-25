// Original deterministic rule for validating the upstream drpy2 engine, no provider/network content.
var rule = {
  title: '开视协议测试', host: 'https://media.example.test', url: '/fyclass/fypage',
  class_name: '电影', class_url: 'films', searchUrl: '/search?wd=**',
  一级: "js:setResult([{title:'规则测试短片',url:'one'}]);",
  二级: "js:VOD={vod_id:'one',vod_name:'规则测试短片',vod_play_from:'测试线路',vod_play_url:'第一集$https://media.example.test/one.mp4'};",
  搜索: "js:setResult([{title:'规则搜索',url:'one'}]);",
  play_parse: false, lazy: ''
};
