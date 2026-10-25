"""Load only a previously approved, hash-checked plugin file supplied by Java."""
import hashlib
import importlib.util
import inspect
import json

_instances = {}


def call(path, digest, candidate_digest, extension, operation, args_json):
    with open(path, 'rb') as source:
        code = source.read(2 * 1024 * 1024 + 1)
    if len(code) > 2 * 1024 * 1024 or hashlib.sha256(code).hexdigest() != digest:
        raise ValueError('Plugin hash changed')
    # The approval identity includes pinned extension/dependency bytes. The entry
    # digest alone cannot distinguish two approved versions at the same ext URL.
    key = candidate_digest
    if key not in _instances:
        name = 'kaishi_plugin_' + digest
        spec = importlib.util.spec_from_file_location(name, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        spider = module.Spider()
        # A few plugins omit super().__init__, but use the conventional base helpers.
        if not hasattr(spider, '_cache'):
            from base.spider import Spider
            Spider.__init__(spider)
        spider.init(extension)
        _instances[key] = spider
    spider = _instances[key]
    args = json.loads(args_json)
    names = {'home': 'homeContent', 'homeVod': 'homeVideoContent', 'category': 'categoryContent',
             'detail': 'detailContent', 'search': 'searchContent', 'play': 'playerContent'}
    if operation not in names:
        raise ValueError('Unknown plugin operation')
    method = getattr(spider, names[operation])
    if operation == 'search' and len(inspect.signature(method).parameters) < 3:
        args = args[:2]
    result = method(*args)
    output = result if isinstance(result, str) else json.dumps(result, ensure_ascii=False)
    if len(output.encode('utf-8')) > 524288:
        raise ValueError('Plugin response too large')
    json.loads(output)
    return output


def clear():
    for spider in list(_instances.values()):
        try:
            spider.destroy()
        except Exception:
            pass
    _instances.clear()
