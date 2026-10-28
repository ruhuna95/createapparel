import gzip
import io
import json
import struct
import sys
import zlib
from pathlib import Path


def read_nbt(stream, kind):
    def unpack(pattern):
        return struct.unpack(pattern, stream.read(struct.calcsize(pattern)))

    def string():
        return stream.read(unpack('>H')[0]).decode('utf-8')

    if kind in (1, 2, 3, 4, 5, 6):
        return kind, unpack({1: '>b', 2: '>h', 3: '>i', 4: '>q', 5: '>f', 6: '>d'}[kind])[0]
    if kind == 8:
        return kind, string()
    if kind == 9:
        element, length = unpack('>bi')
        return kind, [read_nbt(stream, element) for _ in range(length)]
    if kind == 10:
        result = {}
        while (child := unpack('>b')[0]) != 0:
            name = string()
            result[name] = read_nbt(stream, child)
        return kind, result
    if kind in (7, 11, 12):
        length = unpack('>i')[0]
        return kind, [unpack({7: '>b', 11: '>i', 12: '>q'}[kind])[0] for _ in range(length)]
    raise ValueError(f'Unknown NBT type: {kind}')


def snbt(node):
    kind, value = node
    if kind == 10:
        return '{' + ','.join(json.dumps(key) + ':' + snbt(child) for key, child in value.items()) + '}'
    if kind == 9:
        return '[' + ','.join(map(snbt, value)) + ']'
    if kind == 8:
        return json.dumps(value, ensure_ascii=False)
    if kind in (7, 11, 12):
        prefix, suffix = {7: ('B', 'b'), 11: ('I', ''), 12: ('L', 'L')}[kind]
        return '[' + prefix + ';' + ','.join(str(entry) + suffix for entry in value) + ']'
    return str(value) + {1: 'b', 2: 's', 3: '', 4: 'L', 5: 'f', 6: 'd'}[kind]


def scan(world):
    for region in sorted((world / 'region').glob('*.mca')):
        with region.open('rb') as stream:
            locations = stream.read(4096)
            for index in range(1024):
                location = int.from_bytes(locations[index * 4:index * 4 + 4], 'big') >> 8
                if not location:
                    continue
                stream.seek(location * 4096)
                length = int.from_bytes(stream.read(4), 'big')
                compression = stream.read(1)[0]
                payload = stream.read(length - 1)
                payload = {1: gzip.decompress, 2: zlib.decompress, 3: lambda data: data}[compression](payload)
                nbt = io.BytesIO(payload)
                kind = nbt.read(1)[0]
                nbt.read(int.from_bytes(nbt.read(2), 'big'))
                chunk = read_nbt(nbt, kind)[1]
                for _, entity in chunk.get('block_entities', (9, []))[1]:
                    if entity.get('id', (8, ''))[1] != 'minecraft:barrel':
                        continue
                    photos = []
                    for _, item in entity.get('Items', (9, []))[1]:
                        if item.get('id', (8, ''))[1] == 'createapparel:advertising_photo':
                            photo = item.get('tag', (10, {}))[1].get('AdvertisingPhoto')
                            if photo:
                                photos.append((item['Slot'][1], photo))
                    if photos:
                        print(json.dumps({'region': region.name, 'position': [entity[key][1] for key in ('x', 'y', 'z')], 'slots': [slot for slot, _ in photos]}, ensure_ascii=False))
                        for slot, photo in photos:
                            print(f'PHOTO {slot}: {snbt(photo)}')


if __name__ == '__main__':
    scan(Path(sys.argv[1]))
