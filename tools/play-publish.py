"""Upload an already signed AAB to the internal Play track using a short-lived CI token."""
import argparse
import json
import os
from pathlib import Path
import urllib.error
import urllib.request

PACKAGE = 'om.mgtrener.mgym'
BASE = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' + PACKAGE


def track_payload(version_code, version_name, status='draft', existing_releases=()):
    if not isinstance(version_code, int) or version_code < 1:
        raise ValueError('Invalid versionCode')
    if status not in ('draft', 'completed'):
        raise ValueError('Only draft or internal completed releases are supported')
    candidate = {'name': version_name, 'versionCodes': [str(version_code)], 'status': status}
    # A draft must not remove the completed release installed by current testers.
    releases = [dict(release) for release in existing_releases] if status == 'draft' else []
    return {'track': 'internal', 'releases': releases + [candidate]}


def publish(bundle, version_code, version_name, status, request):
    edit = request('POST', BASE + '/edits', {})['id']
    url = BASE + '/edits/' + edit
    try:
        tracks = request('GET', url + '/tracks').get('tracks', [])
        codes = [int(code) for track in tracks for release in track.get('releases', [])
                 for code in release.get('versionCodes', [])]
        if codes and version_code <= max(codes):
            raise ValueError('Increase versionCode before uploading a new Play release')
        existing = next((track.get('releases', []) for track in tracks
                         if track.get('track') == 'internal'), [])
        if any(release.get('status') == 'draft' for release in existing):
            raise ValueError('Review or discard the existing internal draft in Play Console before uploading another release')
        uploaded = request('POST', 'https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/'
                           + PACKAGE + '/edits/' + edit + '/bundles?uploadType=media', bundle.read_bytes())
        if int(uploaded['versionCode']) != version_code:
            raise ValueError('Uploaded bundle versionCode does not match the expected source version')
        request('PUT', url + '/tracks/internal', track_payload(version_code, version_name, status, existing))
        request('POST', url + ':validate', {})
        request('POST', url + ':commit?changesNotSentForReview=true', {})
    except Exception:
        # Abandon an incomplete edit. Never silently retry or submit for review.
        try:
            request('DELETE', url)
        except Exception:
            pass
        raise
    return edit


def api_request(token):
    def request(method, url, data=None):
        binary = isinstance(data, bytes)
        payload = data if binary else json.dumps(data).encode() if data is not None else None
        req = urllib.request.Request(url, data=payload, method=method, headers={
            'Authorization': 'Bearer ' + token,
            'Content-Type': 'application/octet-stream' if binary else 'application/json'})
        try:
            with urllib.request.urlopen(req, timeout=300) as response:
                raw = response.read()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'Google Play API returned HTTP {error.code}. Check Console setup, permissions and versionCode.') from None
    return request


def check_access(request):
    edit = request('POST', BASE + '/edits', {})['id']
    url = BASE + '/edits/' + edit
    try:
        request('GET', url + '/tracks')
    finally:
        request('DELETE', url)


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--bundle', type=Path)
    mode.add_argument('--check-access', action='store_true')
    parser.add_argument('--version-code', type=int)
    parser.add_argument('--version-name')
    parser.add_argument('--status', choices=['draft', 'completed'], default='draft')
    args = parser.parse_args()
    if not args.check_access and (not args.version_code or not args.version_name):
        parser.error('Bundle upload requires version-code and version-name')
    token = os.environ.get('PLAY_ACCESS_TOKEN')
    if not token:
        parser.error('Missing short-lived PLAY_ACCESS_TOKEN; do not put tokens in command arguments')
    request = api_request(token)
    if args.check_access:
        check_access(request)
        print('Google Play access verified. Temporary edit deleted; no release changed.')
        return

    publish(args.bundle, args.version_code, args.version_name, args.status, request)
    print('Bundle uploaded to the internal track. No production release or review submission was made.')


if __name__ == '__main__':
    main()
