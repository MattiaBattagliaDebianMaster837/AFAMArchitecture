#!/usr/bin/env python3
"""
Minimal APK assembler that does not need the Android SDK (no aapt2).

It encodes:
  * AndroidManifest.xml  -> Android binary XML (AXML)
  * a tiny resources.arsc with the launcher icon (legacy PNG + adaptive icon)
and writes an *unsigned*, 4-byte aligned APK that is then signed by ApkSign.java.

Usage:
  apkbuild.py --manifest app/AndroidManifest.xml --dex build/classes.dex \
              --package it.pixelquest.game --icons build/icons --out build/unsigned.apk
"""
import argparse
import os
import struct
import xml.etree.ElementTree as ET
import zipfile

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# --- framework attribute ids (from android.R.attr, API 34) -------------------
A = {
    "theme": 16842752, "label": 16842753, "icon": 16842754, "name": 16842755,
    "exported": 16842768, "launchMode": 16842781, "screenOrientation": 16842782,
    "configChanges": 16842783, "drawable": 16843161, "minSdkVersion": 16843276,
    "versionCode": 16843291, "versionName": 16843292, "targetSdkVersion": 16843376,
    "allowBackup": 16843392, "hardwareAccelerated": 16843475, "isGame": 16843764,
    "resizeableActivity": 16844022, "roundIcon": 16844076, "appCategory": 16844101,
}
STYLES = {
    "Theme.Material.NoActionBar.Fullscreen": 16974383,
    "Theme.Black.NoTitleBar.Fullscreen": 16973834,
}
ENUMS = {
    "screenOrientation": {"unspecified": -1, "landscape": 0, "portrait": 1, "user": 2,
                          "sensor": 4, "sensorLandscape": 6, "userLandscape": 11},
    "launchMode": {"standard": 0, "singleTop": 1, "singleTask": 2, "singleInstance": 3},
    "appCategory": {"game": 0},
}
FLAGS = {
    "configChanges": {"mcc": 0x1, "mnc": 0x2, "locale": 0x4, "touchscreen": 0x8,
                      "keyboard": 0x10, "keyboardHidden": 0x20, "navigation": 0x40,
                      "orientation": 0x80, "screenLayout": 0x100, "uiMode": 0x200,
                      "screenSize": 0x400, "smallestScreenSize": 0x800, "density": 0x1000,
                      "layoutDirection": 0x2000, "fontScale": 0x40000000},
}
INT_ATTRS = {"versionCode", "minSdkVersion", "targetSdkVersion"}
BOOL_ATTRS = {"exported", "allowBackup", "hardwareAccelerated", "isGame", "resizeableActivity"}

T_REFERENCE, T_STRING, T_INT_DEC, T_INT_HEX, T_INT_BOOLEAN = 0x01, 0x03, 0x10, 0x11, 0x12


# --- string pool ---------------------------------------------------------------
def string_pool(strings):
    """ResStringPool chunk, UTF-16 encoded."""
    offsets, data = [], b""
    for s in strings:
        offsets.append(len(data))
        enc = s.encode("utf-16-le")
        n = len(s.encode("utf-16-le")) // 2
        if n > 0x7FFF:
            data += struct.pack("<HH", 0x8000 | (n >> 16), n & 0xFFFF)
        else:
            data += struct.pack("<H", n)
        data += enc + b"\x00\x00"
    while len(data) % 4:
        data += b"\x00"
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    size = strings_start + len(data)
    out = struct.pack("<HHIIIIII", 0x0001, header_size, size, len(strings), 0, 0, strings_start, 0)
    out += b"".join(struct.pack("<I", o) for o in offsets)
    return out + data


# --- AXML encoder ----------------------------------------------------------------
class AxmlEncoder:
    def __init__(self, res_ids):
        self.res_ids = res_ids  # "@type/name" -> id for app resources

    def encode(self, xml_text):
        root = ET.fromstring(xml_text)
        elements = []  # flattened (event, elem)

        # collect strings: attribute names with resource ids first (resource map)
        attr_names = set()
        other = []

        def walk(e):
            for k in e.attrib:
                ns, local = self._split(k)
                if ns == ANDROID_NS:
                    attr_names.add(local)
            for c in e:
                walk(c)
        walk(root)
        mapped = sorted(attr_names, key=lambda n: A[n])
        self.strings = list(mapped)
        self.index = {s: i for i, s in enumerate(self.strings)}
        self.resmap = [A[n] for n in mapped]
        # mapped attribute names live at the front; everything else is appended
        # as a *separate* entry (the same text can't share a mapped slot).
        self.other_index = {}

        body = b""
        line = [1]
        body += self._ns_start()
        body += self._element(root, line)
        body += self._ns_end()

        pool = string_pool(self.strings)
        resmap = struct.pack("<HHI", 0x0180, 8, 8 + 4 * len(self.resmap)) + b"".join(
            struct.pack("<I", r) for r in self.resmap)
        content = pool + resmap + body
        return struct.pack("<HHI", 0x0003, 8, 8 + len(content)) + content

    def _s(self, s):
        if s not in self.other_index:
            self.other_index[s] = len(self.strings)
            self.strings.append(s)
        return self.other_index[s]

    @staticmethod
    def _split(k):
        if k.startswith("{"):
            ns, local = k[1:].split("}")
            return ns, local
        return None, k

    def _ns_start(self):
        return struct.pack("<HHIIIII", 0x0100, 16, 24, 1, 0xFFFFFFFF, self._s("android"), self._s(ANDROID_NS))

    def _ns_end(self):
        return struct.pack("<HHIIIII", 0x0101, 16, 24, 1, 0xFFFFFFFF, self._s("android"), self._s(ANDROID_NS))

    def _value(self, local, raw):
        """Return (rawValueIdx, dataType, data)."""
        if raw.startswith("@"):
            if raw.startswith("@android:style/"):
                return 0xFFFFFFFF, T_REFERENCE, STYLES[raw[len("@android:style/"):]]
            return 0xFFFFFFFF, T_REFERENCE, self.res_ids[raw]
        if local in ENUMS:
            return 0xFFFFFFFF, T_INT_DEC, ENUMS[local][raw] & 0xFFFFFFFF
        if local in FLAGS:
            v = 0
            for f in raw.split("|"):
                v |= FLAGS[local][f.strip()]
            return 0xFFFFFFFF, T_INT_HEX, v
        if local in INT_ATTRS:
            return 0xFFFFFFFF, T_INT_DEC, int(raw)
        if local in BOOL_ATTRS:
            return 0xFFFFFFFF, T_INT_BOOLEAN, 0xFFFFFFFF if raw == "true" else 0
        idx = self._s(raw)
        return idx, T_STRING, idx

    def _element(self, e, line):
        ns, name = self._split(e.tag)
        attrs = []
        for k, v in e.attrib.items():
            ans, local = self._split(k)
            if ans == ANDROID_NS:
                name_idx = self.index[local]
                ns_idx = self._s(ANDROID_NS)
                sort_key = (0, A[local])
            else:
                name_idx = self._s(local)
                ns_idx = 0xFFFFFFFF
                sort_key = (1, local)
            raw_idx, dtype, data = self._value(local, v)
            attrs.append((sort_key, struct.pack("<IIIHBBI", ns_idx, name_idx, raw_idx, 8, 0, dtype, data)))
        attrs.sort(key=lambda a: a[0])
        name_idx = self._s(name)
        ln = line[0]
        line[0] += 1
        ext = struct.pack("<IIHHHHHH", 0xFFFFFFFF, name_idx, 20, 20, len(attrs), 0, 0, 0)
        body = ext + b"".join(a[1] for a in attrs)
        out = struct.pack("<HHIII", 0x0102, 16, 16 + len(body), ln, 0xFFFFFFFF) + body
        for c in e:
            out += self._element(c, line)
        out += struct.pack("<HHIIIII", 0x0103, 16, 24, line[0], 0xFFFFFFFF, 0xFFFFFFFF, name_idx)
        line[0] += 1
        return out


# --- resources.arsc --------------------------------------------------------------
def res_config(density=0, sdk=0):
    cfg = bytearray(64)
    struct.pack_into("<I", cfg, 0, 64)
    struct.pack_into("<H", cfg, 14, density)
    struct.pack_into("<H", cfg, 24, sdk)
    return bytes(cfg)


def build_arsc(package, resources):
    """
    resources: list of (type, name, [(density, sdk, path_in_apk)]) in id order.
    Returns (arsc_bytes, {"@type/name": id}).
    """
    types = []
    for t, _, _ in resources:
        if t not in types:
            types.append(t)
    keys = []
    values = []
    ids = {}
    per_type = {t: [] for t in types}
    for t, n, cfgs in resources:
        per_type[t].append((n, cfgs))
        if n not in keys:
            keys.append(n)
        for c in cfgs:
            values.append(c[2])
    for ti, t in enumerate(types):
        for ei, (n, _) in enumerate(per_type[t]):
            ids["@%s/%s" % (t, n)] = 0x7F000000 | ((ti + 1) << 16) | ei

    type_pool = string_pool(types)
    key_pool = string_pool(keys)
    chunks = b""
    for ti, t in enumerate(types):
        entries = per_type[t]
        # typeSpec
        spec_flags = []
        configs = []
        for n, cfgs in entries:
            fl = 0
            if any(c[0] for c in cfgs):
                fl |= 0x0100  # CONFIG_DENSITY
            if any(c[1] for c in cfgs):
                fl |= 0x0400  # CONFIG_VERSION
            spec_flags.append(fl)
            for c in cfgs:
                if (c[0], c[1]) not in configs:
                    configs.append((c[0], c[1]))
        chunks += struct.pack("<HHIBBHI", 0x0202, 16, 16 + 4 * len(entries), ti + 1, 0, 0, len(entries))
        chunks += b"".join(struct.pack("<I", f) for f in spec_flags)
        for dens, sdk in configs:
            offsets, data = [], b""
            for n, cfgs in entries:
                match = [c for c in cfgs if c[0] == dens and c[1] == sdk]
                if not match:
                    offsets.append(0xFFFFFFFF)
                    continue
                offsets.append(len(data))
                data += struct.pack("<HHI", 8, 0, keys.index(n))
                data += struct.pack("<HBBI", 8, 0, T_STRING, values.index(match[0][2]))
            header_size = 20 + 64
            entries_start = header_size + 4 * len(entries)
            size = entries_start + len(data)
            chunks += struct.pack("<HHIBBHII", 0x0201, header_size, size, ti + 1, 0, 0, len(entries), entries_start)
            chunks += res_config(dens, sdk)
            chunks += b"".join(struct.pack("<I", o) for o in offsets) + data

    name16 = package.encode("utf-16-le")[:254].ljust(256, b"\x00")
    pkg_header_size = 288
    type_off = pkg_header_size
    key_off = type_off + len(type_pool)
    pkg_body = type_pool + key_pool + chunks
    pkg = struct.pack("<HHII", 0x0200, pkg_header_size, pkg_header_size + len(pkg_body), 0x7F)
    pkg += name16 + struct.pack("<IIIII", type_off, len(types), key_off, len(keys), 0) + pkg_body

    gpool = string_pool(values)
    table = struct.pack("<HHII", 0x0002, 12, 12 + len(gpool) + len(pkg), 1) + gpool + pkg
    return table, ids


# --- aligned zip writer ------------------------------------------------------------
def write_aligned_zip(out_path, entries):
    """entries: list of (name, bytes, store:bool). Stored entries are 4-byte aligned."""
    with open(out_path, "wb") as fp:
        zf = zipfile.ZipFile(fp, "w")
        for name, data, store in entries:
            zi = zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))
            zi.create_system = 0
            zi.external_attr = 0
            if store:
                zi.compress_type = zipfile.ZIP_STORED
                offset = fp.tell()
                data_start = offset + 30 + len(name.encode("utf-8"))
                pad = (4 - data_start % 4) % 4
                zi.extra = b"\x00" * pad
            else:
                zi.compress_type = zipfile.ZIP_DEFLATED
            zf.writestr(zi, data)
        zf.close()


ADAPTIVE_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_bg"/>
    <foreground android:drawable="@drawable/ic_fg"/>
</adaptive-icon>
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--dex", required=True)
    ap.add_argument("--package", required=True)
    ap.add_argument("--icons", required=True, help="dir with ic_bg.png ic_fg.png ic_launcher.png")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    XXXHDPI, ANYDPI = 640, 0xFFFE
    resources = [
        ("drawable", "ic_bg", [(XXXHDPI, 4, "res/drawable-xxxhdpi-v4/ic_bg.png")]),
        ("drawable", "ic_fg", [(XXXHDPI, 4, "res/drawable-xxxhdpi-v4/ic_fg.png")]),
        ("mipmap", "ic_launcher", [(XXXHDPI, 4, "res/mipmap-xxxhdpi-v4/ic_launcher.png"),
                                   (ANYDPI, 26, "res/mipmap-anydpi-v26/ic_launcher.xml")]),
    ]
    arsc, ids = build_arsc(args.package, resources)
    enc = AxmlEncoder(ids)
    manifest = enc.encode(open(args.manifest, encoding="utf-8").read())
    adaptive = AxmlEncoder(ids).encode(ADAPTIVE_XML)

    def rd(p):
        with open(p, "rb") as f:
            return f.read()

    entries = [
        ("AndroidManifest.xml", manifest, False),
        ("classes.dex", rd(args.dex), False),
        ("resources.arsc", arsc, True),
        ("res/drawable-xxxhdpi-v4/ic_bg.png", rd(os.path.join(args.icons, "ic_bg.png")), True),
        ("res/drawable-xxxhdpi-v4/ic_fg.png", rd(os.path.join(args.icons, "ic_fg.png")), True),
        ("res/mipmap-xxxhdpi-v4/ic_launcher.png", rd(os.path.join(args.icons, "ic_launcher.png")), True),
        ("res/mipmap-anydpi-v26/ic_launcher.xml", adaptive, False),
    ]
    write_aligned_zip(args.out, entries)
    print("wrote", args.out, "resources:", {k: hex(v) for k, v in ids.items()})


if __name__ == "__main__":
    main()
