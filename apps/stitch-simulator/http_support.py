"""Small local-only HTTP helpers; no package dependencies or external service calls."""
import json
import os
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 4 * 1024 * 1024
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


OPENER = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())


class RemoteError(Exception):
    def __init__(self, status):
        self.status = status
        super().__init__(f"Dependency returned HTTP {status}")


def request(url, method='GET', data=None, headers=None, form=False):
    import urllib.parse
    body = None if data is None else (urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode())
    values = dict(headers or {})
    if body is not None:
        values['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    try:
        with OPENER.open(urllib.request.Request(url, data=body, method=method, headers=values), timeout=20) as result:
            payload = result.read(MAX_BODY + 1)
            if len(payload) > MAX_BODY:
                raise ValueError('Dependency response too large')
            if 'application/json' in result.headers.get('Content-Type', ''):
                return json.loads(payload) if payload else {}
            return {'bytes': len(payload), 'text': payload.decode('utf-8', errors='replace')}
    except urllib.error.HTTPError as error:
        raise RemoteError(error.code) from None


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Do not log credentials, content or query strings.

    def body(self):
        if self.headers.get('Transfer-Encoding'):
            raise ValueError('Chunked requests unsupported')
        length = int(self.headers.get('Content-Length', '0'))
        if not 0 <= length <= MAX_BODY:
            raise ValueError('Body too large')
        value = json.loads(self.rfile.read(length) or b'{}')
        if not isinstance(value, dict):
            raise ValueError('JSON object required')
        return value

    def send(self, value, status=200, content_type='application/json'):
        payload = json.dumps(value, ensure_ascii=False).encode() if content_type == 'application/json' else value.encode()
        self.send_response(status)
        self.send_header('Content-Type', content_type + '; charset=utf-8')
        self.send_header('Content-Length', str(len(payload)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('X-Frame-Options', 'DENY')
        self.end_headers()
        self.wfile.write(payload)

    def authorized(self, token):
        import hmac
        if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + token):
            self.send({'error': 'Authentication required'}, 401)
            return False
        return True

    def dispatch(self):
        try:
            self.handle_request()
        except RemoteError as error:
            self.send({'error': str(error), 'dependencyStatus': error.status,
                       'hint': 'Check local API, Keycloak realm and simulation configuration.'}, 502)
        except (ValueError, KeyError, TypeError):
            self.send({'error': 'Invalid simulation request'}, 400)
        except Exception:
            self.send({'error': 'Local dependency unavailable; check container logs/configuration.'}, 503)

    do_GET = dispatch
    do_POST = dispatch


def serve(handler, port):
    ThreadingHTTPServer(('0.0.0.0', int(os.getenv('PORT', str(port)))), handler).serve_forever()
