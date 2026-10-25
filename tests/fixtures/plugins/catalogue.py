# Original, provider-free Python Spider fixture.
from base.spider import Spider as BaseSpider


class Spider(BaseSpider):
    def init(self, extend=''):
        self.prefix = extend

    def homeContent(self, filter=False):
        return {'class': [{'type_id': 'films', 'type_name': '影片'}], 'list': [{'vod_id': 'one', 'vod_name': self.prefix + '测试短片'}]}

    def categoryContent(self, tid, pg, filter=False, extend=None):
        return {'page': int(pg), 'pagecount': 3, 'list': [{'vod_id': tid + '-one', 'vod_name': '测试短片'}]}

    def detailContent(self, ids):
        return {'list': [{'vod_id': ids[0], 'vod_name': '测试短片', 'vod_play_from': '测试线路', 'vod_play_url': '第一集$opaque-episode-1#第二集$opaque-episode-2'}]}

    def searchContent(self, key, quick=False, pg='1'):
        return {'page': int(pg), 'pagecount': 3, 'list': [{'vod_id': 'search-one', 'vod_name': key}]}

    def playerContent(self, flag, id, vipFlags):
        return {'parse': 0, 'url': 'https://media.example.test/' + id + '.mp4', 'header': {'Referer': 'https://media.example.test/'}}
