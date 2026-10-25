// Original, provider-free CatVod ES module fixture; this import is part of its approval digest.
import { detailRecord } from './records.js';
let prefix = '';
export default {
  init(ext) { prefix = typeof ext === 'string' ? ext : ext.title; },
  home() { return {class: [{type_id: 'films', type_name: '影片'}], list: [{vod_id: 'one', vod_name: prefix + '测试短片'}]}; },
  homeVod() { return this.home(); },
  category(tid, pg) { return {page: Number(pg), pagecount: 3, list: [{vod_id: tid + '-one', vod_name: prefix + '测试短片'}]}; },
  detail(id) { return {list: [detailRecord(id)]}; },
  search(key, quick, pg) { return {page: Number(pg), pagecount: 3, list: [{vod_id: 'search-one', vod_name: key}]}; },
  async play(flag, id) { return {parse: 0, url: 'https://media.example.test/' + encodeURIComponent(id) + '.mp4', header: {'Referer': 'https://media.example.test/'}}; }
};
