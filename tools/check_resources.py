#!/usr/bin/env python3
"""Check bilingual Android resources and positional format arguments (stdlib only)."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FORMAT = re.compile(r'%(?!%)(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z])')


def read(folder):
    resources = {}
    for path in sorted(folder.glob('*.xml')):
        for node in ET.parse(path).getroot():
            if node.tag not in ('string', 'plurals', 'string-array') or node.get('translatable') == 'false':
                continue
            name = node.attrib['name']
            if name in resources:
                raise ValueError(f'{path}: duplicate resource {name}')
            items = [(node.tag, ''.join(node.itertext()))] if node.tag == 'string' else [
                (child.get('quantity', str(index)), ''.join(child.itertext()))
                for index, child in enumerate(node)
            ]
            resources[name] = (node.tag, dict(items))
    return resources


def main():
    base = ROOT / 'app/src/main/res'
    english, spanish = read(base / 'values'), read(base / 'values-es')
    if english.keys() != spanish.keys():
        raise ValueError(f'Missing Spanish: {sorted(english.keys() - spanish.keys())}; '
                         f'extra Spanish: {sorted(spanish.keys() - english.keys())}')
    for name, (kind, items) in english.items():
        other_kind, translated = spanish[name]
        if kind != other_kind or items.keys() != translated.keys():
            raise ValueError(f'{name}: resource type, plural quantities or array size differ')
        for item, text in items.items():
            if sorted(FORMAT.findall(text.replace("%%", ""))) != sorted(FORMAT.findall(translated[item].replace("%%", ""))):
                raise ValueError(f'{name}[{item}]: format arguments differ')
    print(f'Verified {len(english)} English/Spanish resources and format arguments')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, ET.ParseError, KeyError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
