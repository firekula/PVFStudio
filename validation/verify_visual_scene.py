#!/usr/bin/env python3
"""Independent native scene export verification; imports no Studio implementation."""
from pathlib import Path
import argparse
import hashlib
import json
import math
import re
import struct
from verify_pvf import Archive, verify, require


def source_tokens(text):
    pattern = re.compile(r'\s+|//[^\r\n]*|\#[^\r\n]*|/\*.*?\*/|`(?:\\.|[^`\\])*`|"(?:\\.|[^"\\])*"|\[[^\]\r\n]*\]|[^\s\[]+', re.S)
    result = []
    for match in pattern.finditer(text):
        raw = match.group()
        if raw.isspace() or raw.startswith(('//', '#', '/*')):
            continue
        if raw.startswith('['):
            result.append(('tag', raw))
        elif raw.startswith(('`', '"')):
            value = raw[1:-1]
            escapes = {'r':'\r','n':'\n','t':'\t','0':'\0','\\':'\\','`':'`'}
            value = re.sub(r'\\(.)', lambda m: escapes[m[1]], value) if raw.startswith('`') else value
            result.append(('str', value))
        elif re.fullmatch(r'[-+]?\d+', raw):
            result.append(('int', int(raw)))
        elif raw.endswith('f'):
            try:
                result.append(('float', struct.pack('<f', float(raw[:-1]))))
            except ValueError:
                result.append(('opaque', raw))
        else:
            result.append(('opaque', raw))
    return result


def binary_tokens(script, table, charset):
    count, = struct.unpack_from('<I', table)
    offsets = struct.unpack_from(f'<{count + 1}I', table, 4)
    def string(index):
        require(0 <= index < count, 'invalid independent string index')
        return table[4+offsets[index]:4+offsets[index+1]].decode(charset)
    require(script[:2] == b'\xb0\xd0' and (len(script)-2)%5 == 0, 'invalid B0 script')
    result = []
    for at in range(2, len(script), 5):
        kind, value = struct.unpack_from('<Bi', script, at)
        if kind in (2, 3):
            result.append(('int', value))
        elif kind == 4 and math.isfinite(struct.unpack('<f', script[at+1:at+5])[0]):
            result.append(('float', script[at+1:at+5]))
        elif kind == 5:
            raw=string(value)
            result.append(('tag',raw) if re.fullmatch(r'\[[^\]\r\n]*\]',raw) else ('opaque',f'@5({value})'))
        elif kind == 7:
            result.append(('str', string(value)))
        else:
            result.append(('opaque', f'@{kind}({value})'))
    return result


def check(original, directory):
    manifest=json.loads((directory/'manifest.json').read_text(encoding='utf-8'))
    source=Archive.read(original); exported=Archive.read(directory/'edited.pvf')
    require(hashlib.sha256(source.data).hexdigest()==manifest['inputSha256'], 'original archive SHA changed')
    expected={c['path']:(directory/c['payload']).read_bytes() for c in manifest['changes']}
    report=verify(source,exported,expected)
    entries={e.path:e for e in exported.entries}
    charset={'windows-31j':'cp932','x-windows-949':'cp949'}.get(manifest['charset'].lower(),manifest['charset'])
    decoded=[]
    for change in manifest['changes']:
        if not change['source']:
            continue
        raw=exported.plaintext(entries[change['path']]); text=(directory/change['source']).read_bytes().decode('utf-8')
        if raw.startswith(b'\xb0\xd0'):
            table=exported.plaintext(entries['stringtable.bin'])
            require(binary_tokens(raw,table,charset)==source_tokens(text), f'independent B0 semantic mismatch: {change["path"]}')
        else:
            require(raw.decode(charset)==text, f'independent text mismatch: {change["path"]}')
        decoded.append(change['path'])
    report['independentSceneReadback']=decoded
    report['hellEdited']=manifest['hellEdited']
    (directory/'independent-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f'VISUAL_SCENE_INDEPENDENT_OK {original}: {len(decoded)} scenes; original SHA unchanged; all untouched payloads checked')
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('original',type=Path);parser.add_argument('directory',type=Path);args=parser.parse_args();check(args.original,args.directory)
