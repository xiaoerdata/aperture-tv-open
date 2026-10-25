"""CatVod Python calling convention and common HTTP helpers.

Trusted plugins run with the application's permissions. This is not a sandbox.
The host never supplies NAS, household or Hub credentials to this module.
"""
import base64
import json
import re
import requests
from urllib.parse import urljoin, quote, unquote, urlencode


class Spider:
    def __init__(self):
        self._cache = {}
        self._session = requests.Session()

    def init(self, extend=""):
        pass

    def getName(self):
        return self.__class__.__name__

    def fetch(self, url, params=None, cookies=None, headers=None, timeout=10, **kwargs):
        return self._session.get(url, params=params, cookies=cookies, headers=headers,
                                 timeout=min(float(timeout), 15), **kwargs)

    def post(self, url, params=None, data=None, headers=None, timeout=10, **kwargs):
        return self._session.post(url, params=params, data=data, headers=headers,
                                  timeout=min(float(timeout), 15), **kwargs)

    def html(self, content):
        from lxml import etree
        return etree.HTML(content)

    def cleanText(self, text):
        return re.sub(r"<[^>]+>", "", str(text)).strip()

    def regStr(self, text, pattern, group=1):
        found = re.search(pattern, text)
        return found.group(group) if found else ""

    def getCache(self, key):
        return self._cache.get(key)

    def setCache(self, key, value):
        self._cache[key] = value

    def delCache(self, key):
        self._cache.pop(key, None)

    def log(self, message):
        pass

    def destroy(self):
        self._session.close()

    def homeContent(self, filter=False):
        return {"class": [], "list": []}

    def homeVideoContent(self):
        return {"list": []}

    def categoryContent(self, tid, pg, filter=False, extend=None):
        return {"list": [], "page": int(pg), "pagecount": int(pg)}

    def detailContent(self, ids):
        return {"list": []}

    def searchContent(self, key, quick=False, pg="1"):
        return {"list": []}

    def playerContent(self, flag, id, vipFlags):
        return {"parse": 0, "url": id}
