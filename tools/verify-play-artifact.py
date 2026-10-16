"""Inspect the exact AAB and bundletool-dumped manifest, without network access."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile

ANDROID = '{http://schemas.android.com/apk/res/android}'


def native_alignment(data):
    if data[:4] != b'\x7fELF':
        raise ValueError('Invalid native library')
    endian = '<' if data[5] == 1 else '>'
    wide = data[4] == 2
    offset = struct.unpack_from(endian + ('Q' if wide else 'I'), data, 32 if wide else 28)[0]
    entry_size, count = struct.unpack_from(endian + 'HH', data, 54 if wide else 42)
    alignments = []
    for i in range(count):
        entry = offset + i * entry_size
        if struct.unpack_from(endian + 'I', data, entry)[0] == 1:
            alignments.append(struct.unpack_from(endian + ('Q' if wide else 'I'), data, entry + (48 if wide else 28))[0])
    if not alignments or min(alignments) < 16384:
        raise ValueError('Native library lacks 16 KB load-segment alignment')
    return min(alignments)


def verify(bundle, manifest):
    root = ET.parse(manifest).getroot()
    if root.get('package') != 'om.mgtrener.mgym':
        raise ValueError('Wrong applicationId')
    app = root.find('application')
    if app.get(ANDROID + 'debuggable', 'false') != 'false' or app.get(ANDROID + 'usesCleartextTraffic') != 'false' or app.get(ANDROID + 'allowBackup') != 'false':
        raise ValueError('Release/debug/network/backup flags are incorrect')
    permissions = [p.get(ANDROID + 'name') for p in root.findall('uses-permission')]
    allowed = {'android.permission.INTERNET', 'android.permission.VIBRATE', 'om.mgtrener.mgym.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    if set(permissions) - allowed:
        raise ValueError('Unexpected permission: ' + ', '.join(set(permissions) - allowed))
    if any('UpdateCheckJob' in e.get(ANDROID + 'name', '') for e in app):
        raise ValueError('GitHub update job is present in the Play manifest')
    with zipfile.ZipFile(bundle) as archive:
        names = archive.namelist()
        signed = [name for name in names if name.startswith('META-INF/') and name.upper().endswith(('.RSA', '.EC', '.DSA'))]
        if not signed:
            raise ValueError('Unsigned bundle; do not upload to Google Play')
        # Every payload entry must have a matching JAR manifest digest, not merely a certificate file.
        raw = archive.read('META-INF/MANIFEST.MF').decode().replace('\r\n', '\n').replace('\n ', '')
        entries = {}
        for section in raw.split('\n\n'):
            values = dict(line.split(': ', 1) for line in section.splitlines() if ': ' in line)
            if 'Name' in values:
                entries[values['Name']] = values
        for name in names:
            if name.endswith('/') or name.startswith('META-INF/'):
                continue
            digest = entries.get(name, {}).get('SHA-256-Digest')
            if not digest or base64.b64decode(digest) != hashlib.sha256(archive.read(name)).digest():
                raise ValueError('Unsigned or altered bundle payload: ' + name)
        libraries = {name: native_alignment(archive.read(name)) for name in names if name.endswith('.so')}
        if 'base/assets/bench150-plan.json' in names:
            raise ValueError('Removed original program has reappeared')
    return {'package': root.get('package'), 'version': root.get(ANDROID + 'versionName'),
            'versionCode': int(root.get(ANDROID + 'versionCode')), 'permissions': permissions,
            'nativeAlignment': libraries, 'sha256': hashlib.sha256(bundle.read_bytes()).hexdigest()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--manifest', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.bundle, args.manifest), ensure_ascii=False, indent=2))
