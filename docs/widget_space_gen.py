#!/usr/bin/env python3
"""Generator skin widget Boomly bertema SPACE (Batch 325).

Menulis ulang SATU paket berkas widget dari satu sumber angka:
  values/widget_theme.xml, drawable/widget_background.xml, widget_panel_off.xml,
  widget_panel_on.xml, widget_status_dot_off.xml, widget_status_dot_on.xml, ic_widget_eq.xml

Pakai:  python3 docs/widget_space_gen.py --res app/src/main/res [--preview /tmp/prev]
--preview (opsional, butuh Pillow + numpy) merender pratinjau PNG pendekatan + hitung kontras teks.
Layout (widget_booster.xml) dan BoosterWidgetProvider.kt TIDAK disentuh: id & nama token yang dipakai
provider (widget_eq_on/off, widget_text_active/secondary) dipertahankan.
Warna mengikuti palet tema Space di app (Theme.kt: SpaceVoid ... SpaceGold).
"""
import argparse, math, os, random

# ----------------------------------------------------------------------------- palet
PAL = {
    'widget_void': '#04050E', 'widget_gem': '#2B3A8A', 'widget_deep': '#070A1C', 'widget_surface': '#0B1030',
    'widget_raised': '#121A42', 'widget_card_light': '#1A2354', 'widget_card_dark': '#0F1537',
    'widget_cyan': '#7DE3FF', 'widget_violet': '#B9A3FF', 'widget_magenta': '#FF7AD9',
    'widget_gold': '#FFD98E', 'widget_star': '#EEF2FF',
    'widget_text_primary': '#EEF2FF', 'widget_text_secondary': '#BAC6F0', 'widget_text_active': '#7DE3FF',
    'widget_glyph_off': '#8A97C6', 'widget_glyph_on': '#7DE3FF',
    'widget_eq_off': '#7482B8', 'widget_eq_on': '#7DE3FF',
}
CYAN, VIOLET, MAGENTA, GOLD, STAR = '7DE3FF', 'B9A3FF', 'FF7AD9', 'FFD98E', 'EEF2FF'
INK = '02030A'

def n(x):
    s = ('%.2f' % x).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s

def argb(rgb, a):
    return '#%02X%s' % (max(0, min(255, round(a * 255))), rgb.upper())

# ----------------------------------------------------------------------------- fill
class Solid:
    def __init__(s, c): s.c = c
    def xml(s): return '<solid android:color="%s" />' % s.c

class Lin:
    def __init__(s, angle, start, end, center=None, pos=0.5):
        s.angle, s.start, s.end, s.center, s.pos = angle, start, end, center, pos
    def xml(s):
        a = 'android:angle="%d" ' % s.angle
        if s.center:
            if abs(s.pos - 0.5) > 1e-6:
                a += ('android:centerY="%s" ' if s.angle in (90, 270) else 'android:centerX="%s" ') % n(s.pos)
            a += 'android:centerColor="%s" ' % s.center
        return '<gradient %sandroid:startColor="%s" android:endColor="%s" />' % (a, s.start, s.end)

class Rad:
    def __init__(s, cx, cy, r, start, end): s.cx, s.cy, s.r, s.start, s.end = cx, cy, r, start, end
    def xml(s):
        return ('<gradient android:type="radial" android:centerX="%s" android:centerY="%s" android:gradientRadius="%d%%" '
                'android:startColor="%s" android:endColor="%s" />') % (n(s.cx), n(s.cy), round(s.r * 100), s.start, s.end)

# ----------------------------------------------------------------------------- drawable shape / vector
class Shape:
    def __init__(s, kind='rect', corners=None, fill=None, stroke=None, size_h=None):
        s.kind, s.corners, s.fill, s.stroke, s.size_h = kind, corners, fill, stroke, size_h
    def xml(s, ind):
        o = ['<shape android:shape="%s">' % ('rectangle' if s.kind == 'rect' else 'oval')]
        if s.kind == 'rect' and s.corners:
            tl, tr, br, bl = s.corners
            o.append('<corners android:topLeftRadius="%s" android:topRightRadius="%s" android:bottomRightRadius="%s" '
                     'android:bottomLeftRadius="%s" />' % tuple(c if isinstance(c, str) else n(c) + 'dp' for c in (tl, tr, br, bl)))
        if s.size_h: o.append('<size android:height="%sdp" />' % n(s.size_h))
        if s.fill: o.append(s.fill.xml())
        if s.stroke: o.append('<stroke android:width="%sdp" android:color="%s" />' % (n(s.stroke[0]), s.stroke[1]))
        o.append('</shape>')
        return ind + ''.join(o)

class Vec:
    """elemen: ('path', geom, fill_hex|token, fillAlpha, stroke_hex, strokeAlpha, strokeWidth) | ('group', dict, [children])"""
    def __init__(s, w, h, vw, vh, els): s.w, s.h, s.vw, s.vh, s.els = w, h, vw, vh, els
    @staticmethod
    def pd(g):
        k = g[0]
        if k == 'poly':
            p = g[1]; return 'M' + ' L'.join('%s,%s' % (n(x), n(y)) for x, y in p) + ('z' if g[2] else '')
        if k == 'circle':
            _, cx, cy, r = g; return 'M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 -%s,0z' % (n(cx - r), n(cy), n(r), n(r), n(2 * r), n(r), n(r), n(2 * r))
        if k == 'ellipse':
            _, cx, cy, rx, ry = g; return 'M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 -%s,0z' % (n(cx - rx), n(cy), n(rx), n(ry), n(2 * rx), n(rx), n(ry), n(2 * rx))
        if k == 'multi':   # banyak lingkaran kecil dalam satu path
            return ''.join('M%s,%sa%s,%s 0 1,0 %s,0a%s,%s 0 1,0 -%s,0z' % (n(cx - r), n(cy), n(r), n(r), n(2 * r), n(r), n(r), n(2 * r)) for cx, cy, r in g[1])
        if k == 'earc':   # busur elips, sudut layar (searah jarum jam positif, y ke bawah), a0 -> a1 searah jarum jam
            _, cx, cy, rx, ry, a0, a1 = g
            x0, y0 = cx + rx * math.cos(math.radians(a0)), cy + ry * math.sin(math.radians(a0))
            x1, y1 = cx + rx * math.cos(math.radians(a1)), cy + ry * math.sin(math.radians(a1))
            return 'M%s,%sA%s,%s 0 %d,1 %s,%s' % (n(x0), n(y0), n(rx), n(ry), 1 if (a1 - a0) % 360 > 180 else 0, n(x1), n(y1))
        if k == 'raw': return g[1]
        raise ValueError(k)
    def _el(s, e, ind):
        if e[0] == 'group':
            d = e[1]; a = ''.join(' android:%s="%s"' % (k, n(v)) for k, v in d.items())
            return [ind + '<group%s>' % a] + sum((s._el(c, ind + '    ') for c in e[2]), []) + [ind + '</group>']
        _, g, fill, fa, stroke, sa, sw = e
        pd = s.pd(g); assert len(pd) < 780, ('pathData terlalu panjang (lint VectorPath)', len(pd))
        a = ''
        if fill: a += ' android:fillColor="%s"' % (('@color/' + fill) if fill in PAL else '#' + fill) + ((' android:fillAlpha="%s"' % n(fa)) if fa < 0.999 else '')
        if stroke: a += ' android:strokeColor="#%s" android:strokeWidth="%s"' % (stroke, n(sw)) + ((' android:strokeAlpha="%s"' % n(sa)) if sa < 0.999 else '')
        return [ind + '<path%s android:pathData="%s" />' % (a, pd)]
    def xml(s, ind, root=False):
        o = [ind + '<vector%s android:width="%sdp" android:height="%sdp" android:viewportWidth="%s" android:viewportHeight="%s">' % (
            ' xmlns:android="http://schemas.android.com/apk/res/android"' if root else '', n(s.w), n(s.h), n(s.vw), n(s.vh))]
        for e in s.els: o += s._el(e, ind + '    ')
        o.append(ind + '</vector>')
        return o

def P(g, fill=None, fa=1.0, stroke=None, sa=1.0, sw=0.0): return ('path', g, fill, fa, stroke, sa, sw)
def G(children, **kw): return ('group', kw, children)

class Item:
    def __init__(s, d, l=0, t=0, r=0, b=0, w=None, h=None, grav=None): s.d, s.l, s.t, s.r, s.b, s.w, s.h, s.grav = d, l, t, r, b, w, h, grav
    def xml(s, ind):
        a = ''.join(' android:%s="%sdp"' % (k, n(v)) for k, v in (('left', s.l), ('top', s.t), ('right', s.r), ('bottom', s.b)) if v)
        if s.w is not None: a += ' android:width="%sdp" android:height="%sdp"' % (n(s.w), n(s.h))
        if s.grav: a += ' android:gravity="%s"' % s.grav
        if isinstance(s.d, Shape): return [ind + '<item%s>' % a, s.d.xml(ind + '    '), ind + '</item>']
        return [ind + '<item%s>' % a] + s.d.xml(ind + '    ') + [ind + '</item>']

def layer_xml(items, header):
    o = ['<?xml version="1.0" encoding="utf-8"?>', header,
         '<layer-list xmlns:android="http://schemas.android.com/apk/res/android">']
    for it in items:
        if isinstance(it, str): o.append('    ' + it)
        else: o += it.xml('    ')
    o.append('</layer-list>')
    return '\n'.join(o) + '\n'

# ----------------------------------------------------------------------------- pembantu geometri / hiasan
BIG, SMALL = '@dimen/widget_corner_radius', '@dimen/widget_corner_radius_small'
PLATE_C = (BIG, SMALL, SMALL, SMALL)          # bahasa "orbit": satu pojok besar kiri-atas (SpaceOrbitCorners)
PANEL_C = (6, 6, 24, 6)                       # kebalikannya: pojok besar kanan-bawah (SpaceOrbitMirrorCorners)
F = 4                                         # ruang bayangan di dalam drawable panel / knob

def ring_shadow(rgb, A, sigma, M, ox, oy, corners, step=0.5):
    """Tumpukan layer alpha-bertingkat (profil Gaussian) di ruang M dp di luar muka. Layer terluar dulu."""
    items, prev, k = [], 0.0, 0
    steps = int(round(M / step))
    for k in range(steps):
        d = M - k * step                       # jarak layer ini dari tepi muka
        c = A * math.exp(-0.5 * (d / sigma) ** 2)
        a = 1 - (1 - c) / (1 - prev); prev = c
        ins = k * step
        l, t, r, b = max(0, ins + ox), max(0, ins + oy), max(0, ins - ox), max(0, ins - oy)
        if a < 0.004: continue
        items.append(Item(Shape('rect', corners, Solid(argb(rgb, a))), l=l, t=t, r=r, b=b))
    return items

def put(ax, dx, ay, dy, w, h, base):
    """Posisi item berukuran `size` dengan pusat dx dari tepi muka (l/r) atau offset dari tengah (c); idem vertikal."""
    kw = dict(w=w, h=h)
    g = []
    if ax == 'l': g.append('left'); kw['l'] = base + dx - w / 2
    elif ax == 'r': g.append('right'); kw['r'] = base + dx - w / 2
    else:
        g.append('center_horizontal'); kw['l'] = base + max(0, 2 * dx); kw['r'] = base + max(0, -2 * dx)
    if ay == 't': g.append('top'); kw['t'] = base + dy - h / 2
    elif ay == 'b': g.append('bottom'); kw['b'] = base + dy - h / 2
    else:
        g.append('center_vertical'); kw['t'] = base + max(0, 2 * dy); kw['b'] = base + max(0, -2 * dy)
    kw['grav'] = '|'.join(g)
    return kw

def dot(ax, dx, ay, dy, size, rgb, a, base):
    return Item(Shape('oval', fill=Solid(argb(rgb, a))), **put(ax, dx, ay, dy, size, size, base))

def sparkle(size, rgb, a, base, ax, dx, ay, dy, halo=0.20):
    c, R, r = 5, 4.7, 0.95
    pts = [(c, c - R), (c + r, c - r), (c + R, c), (c + r, c + r), (c, c + R), (c - r, c + r), (c - R, c), (c - r, c - r)]
    v = Vec(size, size, 10, 10, [P(('circle', c, c, 3.3), rgb, a * halo), P(('poly', pts, True), rgb, a), P(('circle', c, c, 0.95), 'FFFFFF', min(1, a + 0.1))])
    return Item(v, **put(ax, dx, ay, dy, size, size, base))

def planet(base, ax, dx, ay, dy, boost=1.0):
    W, H, cx, cy = 18, 11, 9, 5.5
    rot = dict(rotation=-17, pivotX=cx, pivotY=cy)
    els = [G([P(('ellipse', cx, cy, 7.9, 2.0), None, 1, GOLD, 0.55 * boost, 0.7)], **rot),
           P(('circle', cx, cy, 3.9), '3E3396', 0.98),
           P(('circle', cx - 0.5, cy - 0.7, 3.0), '6A58D8', 0.9),
           P(('circle', cx - 1.3, cy - 1.5, 1.6), VIOLET, 0.55),
           G([P(('earc', cx, cy, 7.9, 2.0, 5, 175), None, 1, GOLD, 0.95 * min(1, boost), 0.8)], **rot)]
    return Item(Vec(W, H, W, H, els), **put(ax, dx, ay, dy, W, H, base))

def comet(base, ax, dx, ay, dy, boost=1.0):
    W, H = 30, 10
    segs = [(27, 21.5, 1.4, 1.0, .60), (21.5, 16, 1.0, .75, .40), (16, 10.5, .75, .5, .25), (10.5, 5.5, .5, .3, .13), (5.5, 1, .3, .1, .06)]
    els = []
    for x0, x1, h0, h1, a in segs:
        els.append(P(('poly', [(x0, 5 - h0), (x1, 5 - h1), (x1, 5 + h1), (x0, 5 + h0)], True), STAR, min(1, a * boost)))
    els += [P(('circle', 27, 5, 2.8), CYAN, 0.16 * boost), P(('circle', 27, 5, 1.2), STAR, 0.95)]
    return Item(Vec(W, H, W, H, [G(els, rotation=12, pivotX=15, pivotY=5)]), **put(ax, dx, ay, dy, W, H, base))

def starfield(on):
    rnd = random.Random(325); out = []
    colors = [(STAR, .85), (STAR, .6), (CYAN, .7), (VIOLET, .7), (STAR, .45), (GOLD, .8)]
    def add(ax, ay, xs, dys, vis):
        for x in xs:
            dy = rnd.choice(dys); size = rnd.choice([1.0, 1.2, 1.4, 1.7]); rgb, a = rnd.choice(colors)
            out.append((ax, x + rnd.uniform(-2, 2), ay, dy + rnd.uniform(-1, 1), size, rgb, a * vis))
    add('l', 't', [10, 23, 37, 53, 71, 90], [4.5, 7, 9.5], 1)
    add('c', 't', [-34, -12, 10, 33], [4, 6.5, 9], 1)
    add('r', 't', [14, 29, 45, 64, 84], [4.5, 7.5, 10], 1)
    add('l', 'b', [11, 26, 43, 63, 85], [4.5, 7, 9.5], 1)
    add('c', 'b', [-38, -15, 8, 30], [4.2, 7, 9.5], 1)
    add('r', 'b', [27, 44, 63, 85], [4.5, 7.5, 10], 1)
    add('l', 'm', [5.5, 9], [-9, 0, 8], .5)
    add('r', 'm', [6, 9.5], [-8, 3, 9], .5)
    k = 1.3 if on else 1.0
    return [(ax, dx, ay, dy, s, rgb, min(1, a * k)) for ax, dx, ay, dy, s, rgb, a in out]

# ----------------------------------------------------------------------------- pelat (statis)
def build_plate():
    it = []
    it += ring_shadow('7A5CFF', 0.30, 2.6, 6, 0, 0, PLATE_C)                    # aura ungu-nebula
    it += ring_shadow(INK, 0.60, 1.9, 6, 1.4, 2.2, PLATE_C)                     # bayangan jatuh gelap (kanan-bawah)
    it.append(Item(Shape('rect', PLATE_C, Lin(315, argb(CYAN, .95), argb(MAGENTA, .85), center=argb(VIOLET, .90))), 6, 6, 6, 6))   # tepi gradasi cyan-ungu-magenta
    it.append(Item(Shape('rect', PLATE_C, Lin(315, '@color/widget_card_dark', '@color/widget_void', center='@color/widget_deep')), 7, 7, 7, 7))
    for cx, cy, r, rgb, a in ((0.05, 0.10, 1.5, CYAN, .30), (0.93, 0.85, 1.6, '7A5CFF', .36), (0.50, 0.00, 1.0, MAGENTA, .16)):
        it.append(Item(Shape('rect', PLATE_C, Rad(cx, cy, r, argb(rgb, a), argb(rgb, 0))), 7, 7, 7, 7))
    base = 0
    specks = [('l', 34, 't', 9.6, 1.3, STAR, .8), ('l', 71, 't', 9.9, 1.0, CYAN, .7), ('l', 118, 't', 9.5, 1.2, STAR, .55), ('c', 20, 't', 9.7, 1.1, GOLD, .85),
              ('r', 25, 't', 9.6, 1.3, STAR, .75), ('r', 62, 't', 9.9, 1.0, VIOLET, .8), ('r', 104, 't', 9.5, 1.2, STAR, .5),
              ('l', 52, 'b', 9.6, 1.2, STAR, .6), ('l', 97, 'b', 9.9, 1.0, VIOLET, .75), ('c', -26, 'b', 9.6, 1.3, CYAN, .7),
              ('c', 31, 'b', 9.8, 1.0, STAR, .55), ('r', 46, 'b', 9.6, 1.2, GOLD, .8), ('r', 88, 'b', 9.9, 1.0, STAR, .6)]
    for ax, dx, ay, dy, s, rgb, a in specks: it.append(dot(ax, dx, ay, dy, s, rgb, a, base))
    hdr = ('<!-- Batch 325: skin SPACE (rombak total dari Neumorphism burgundy). Pelat = bahasa "orbit" (pojok besar kiri-atas, selaras SpaceOrbitCorners) dgn tepi gradasi\n'
           '     cyan-ungu-magenta 1dp, bidang void gradasi + 3 kabut nebula (radial) + 13 bintang kecil di cincin 5dp yang terlihat di antara pelat dan knob/panel.\n'
           '     Aura ungu + bayangan gelap = tumpukan layer alpha-bertingkat (profil Gaussian) di ruang inset 6dp (sama seperti B217). Layer-list murni, 0 bitmap.\n'
           '     Dihasilkan docs/widget_space_gen.py; ubah angka lewat skrip. -->')
    return layer_xml(it, hdr)

# ----------------------------------------------------------------------------- panel teks
def build_panel(on):
    it = []; k = 1.0
    if on:
        it += ring_shadow(CYAN, 0.34, 1.7, F, 0, 0, PANEL_C)                  # pendar cyan di sekeliling sumur (LED menyala)
        it += ring_shadow(INK, 0.45, 1.5, F, 0.4, 0.9, PANEL_C)
        face = Lin(315, '@color/widget_void', '#FF10204A', center='@color/widget_surface')
        blobs = ((1.0, 1.05, 1.0, CYAN, .30), (0.08, 0.10, 1.4, '7A5CFF', .30), (0.50, 1.10, 0.9, MAGENTA, .14))
        k = 1.35
    else:
        it += ring_shadow(INK, 0.60, 1.5, F, 0.6, 1.3, PANEL_C)
        face = Lin(315, '@color/widget_card_light', '@color/widget_deep', center='@color/widget_surface')
        blobs = ((0.08, 0.05, 1.3, CYAN, .22), (0.93, 0.78, 1.5, '7A5CFF', .30), (0.55, 1.00, 0.9, MAGENTA, .14))
    it.append(Item(Shape('rect', PANEL_C, face), F, F, F, F))
    for cx, cy, r, rgb, a in blobs:
        it.append(Item(Shape('rect', PANEL_C, Rad(cx, cy, r, argb(rgb, a), argb(rgb, 0))), F, F, F, F))
    it.append(Item(Shape('rect', PANEL_C, Lin(315, argb('E6DCFF', 0), argb('E6DCFF', 0), center=argb('E6DCFF', .11 if not on else .08))), F, F, F, F))   # pita galaksi diagonal
    oa = 1.4 if on else 1.0
    it.append(Item(Shape('oval', stroke=(0.5, argb(VIOLET, .20 * oa))), F + 8, F + 4, F + 12, F + 4))                 # orbit besar
    it.append(Item(Shape('oval', stroke=(0.5, argb(CYAN, .15 * oa))), F + 58, F + 9, F + 5, F + 1.5))                  # orbit kecil (kanan)
    for ax, dx, ay, dy, s, rgb, a in starfield(on): it.append(dot(ax, dx, ay, dy, s, rgb, a, F))
    it.append(sparkle(9, CYAN, min(1, .85 * k), F, 'l', 30, 't', 7.5))
    it.append(sparkle(7, GOLD, min(1, .9 * k), F, 'r', 24, 't', 8.5))
    it.append(sparkle(6, VIOLET, min(1, .8 * k), F, 'c', 16, 'b', 7.5))
    it.append(comet(F, 'c', -22, 't', 5.5, k))
    it.append(planet(F, 'r', 62, 'b', 6.4, k))
    it.append(Item(Shape('rect', PANEL_C, Lin(270, argb('FFFFFF', .07), argb('FFFFFF', 0), center=argb('FFFFFF', 0), pos=.42)), F, F, F, F))   # kilau kaca
    if on:
        it.append(Item(Shape('rect', PANEL_C, Lin(270, argb(INK, .55), argb(INK, 0), center=argb(INK, 0), pos=.30)), F, F, F, F))             # bayangan dalam atas (cekung)
        it.append(Item(Shape('rect', fill=Lin(0, argb(CYAN, 0), argb(CYAN, 0), center=argb(CYAN, .95)), size_h=1), l=F + 14, r=F + 14, b=F + 4.5, grav='bottom'))   # garis energi
    it.append(Item(Shape('rect', PANEL_C, stroke=(0.6, argb(CYAN, .70 if on else .32))), F, F, F, F))                  # bezel luar
    it.append(Item(Shape('rect', (5, 5, 23, 5), stroke=(0.4, argb(VIOLET, .30 if on else .14))), F + 1.3, F + 1.3, F + 1.3, F + 1.3))   # bezel dalam
    st = 'AKTIF = cekung: bayangan dalam atas, pendar cyan, bintang lebih terang, garis energi di bawah' if on else 'NONAKTIF = timbul: bayangan jatuh gelap, bintang redup'
    hdr = ('<!-- Batch 325: panel teks Space, %s. Bentuk "orbit" KEBALIKAN pelat (pojok besar 24dp kanan-bawah). Layer: bayangan/pendar, muka gradasi, 3 kabut nebula,\n'
           '     pita galaksi, 2 orbit elips, ±45 bintang (jalur atas/bawah terang, tengah samar supaya teks tetap terbaca), 3 percikan 4-sudut, komet, planet bercincin, kilau kaca, bezel ganda.\n'
           '     Hiasan di-anchor ke tepi (gravity) bukan diregangkan: tak distorsi saat widget diubah ukurannya. Dihasilkan docs/widget_space_gen.py. -->' % st)
    return layer_xml(it, hdr)

# ----------------------------------------------------------------------------- knob permata (vector 56dp)
def octagon(cx, cy, hw, cut):
    return [(cx - hw + cut, cy - hw), (cx + hw - cut, cy - hw), (cx + hw, cy - hw + cut), (cx + hw, cy + hw - cut),
            (cx + hw - cut, cy + hw), (cx - hw + cut, cy + hw), (cx - hw, cy + hw - cut), (cx - hw, cy - hw + cut)]

NORMALS = [(0, -1), (.7071, -.7071), (1, 0), (.7071, .7071), (0, 1), (-.7071, .7071), (-1, 0), (-.7071, -.7071)]
def lit(i): return (-NORMALS[i][0] - NORMALS[i][1]) / 1.4142

def build_knob(on):
    cx = cy = 28.0; hw, cut, bw = 22.0, 14.0, 2.8
    cut_in = cut - bw * (2 - 2 ** .5)
    outer, inner = octagon(cx, cy, hw, cut), octagon(cx, cy, hw - bw, cut_in)
    els = []
    def tri(i, rgb, a): els.append(P(('poly', [(cx, cy), inner[i], inner[(i + 1) % 8]], True), rgb, a))
    if on:
        for j, r in enumerate((27.9, 26.9, 25.9, 24.9, 23.9, 22.9)): els.append(P(('circle', cx, cy, r), CYAN, 0.03 + 0.004 * j))
        els.append(P(('poly', outer, True), 'widget_deep', 1))
        for i in range(8):                                   # bevel terbalik = cekung
            L = -lit(i); a = (0.50 * L) if L > 0 else (0.55 * -L)
            if abs(L) > 0.05: els.append(P(('poly', [outer[i], outer[(i + 1) % 8], inner[(i + 1) % 8], inner[i]], True), CYAN if L > 0 else INK, min(.9, a)))
        els.append(P(('poly', inner, True), '05081A', 1))
        for j in range(20):                                  # inti permata: oktagon bersarang, alpha menumpuk ke tengah (pendar halus)
            ins = j * 0.8; o = octagon(cx, cy, hw - bw - ins, max(1, cut_in - ins * (2 - 2 ** .5)))
            els.append(P(('poly', o, True), CYAN, 0.027))
        for i in range(8):
            L = -lit(i)
            if abs(L) > 0.05: tri(i, STAR if L > 0 else INK, 0.05 * L if L > 0 else 0.20 * -L)
    else:
        els.append(P(('poly', octagon(cx + 1.2, cy + 2.0, hw + 1.5, cut + 1.2), INK, .24), None))
        els.append(P(('poly', octagon(cx + 1.4, cy + 2.4, hw + 3.0, cut + 2.4), INK, .12), None))
        els.append(P(('poly', outer, True), 'widget_gem', 1))
        for i in range(8):
            L = lit(i); a = (0.55 * L) if L > 0 else (0.55 * -L)
            if abs(L) > 0.05: els.append(P(('poly', [outer[i], outer[(i + 1) % 8], inner[(i + 1) % 8], inner[i]], True), (CYAN if L > .7 else STAR) if L > 0 else INK, min(.9, a)))
        els.append(P(('poly', inner, True), 'widget_card_light', 1))
        for i in range(8):
            L = lit(i)
            if abs(L) > 0.05: tri(i, STAR if L > 0 else INK, 0.075 * L if L > 0 else 0.17 * -L)
    # cincin orbit + tanda ukur + satelit
    R = 26.4
    if on:
        for w, a in ((4.2, .06), (2.8, .10), (1.6, .30), (0.8, .95)): els.append(P(('circle', cx, cy, R), None, 1, CYAN, a, w))
    else:
        els.append(P(('circle', cx, cy, R), None, 1, CYAN, .30, 0.7))
    sat_ang = -40
    ticks = [(cx + R * math.cos(math.radians(a)), cy + R * math.sin(math.radians(a)), 0.62 if on else 0.5)
             for a in range(0, 360, 15) if abs(((a - sat_ang + 180) % 360) - 180) > 9 and a % 90 != 0]
    cards = [(cx + R * math.cos(math.radians(a)), cy + R * math.sin(math.radians(a)), 1.0 if on else 0.8) for a in range(0, 360, 90)]
    half = len(ticks) // 2
    els.append(P(('multi', ticks[:half]), CYAN, 1.0 if on else 0.45))
    els.append(P(('multi', ticks[half:]), CYAN, 1.0 if on else 0.45))
    els.append(P(('multi', cards), GOLD if on else 'widget_glyph_off', 1.0 if on else 0.8))
    sx, sy = cx + R * math.cos(math.radians(sat_ang)), cy + R * math.sin(math.radians(sat_ang))
    if on:
        els.append(P(('circle', sx, sy, 5.0), GOLD, .08)); els.append(P(('circle', sx, sy, 3.6), GOLD, .18))
    els.append(P(('circle', sx, sy, 2.1), GOLD if on else 'widget_glyph_off', 1 if on else .9))
    els.append(P(('circle', sx - .5, sy - .5, .8), 'FFFFFF', .9 if on else .5))
    if on:
        for j, r in enumerate((13, 11.5, 10, 8.5, 7, 5.5)): els.append(P(('circle', cx, cy, r), CYAN, 0.045 + 0.01 * j))
    glyph = 'M13,3h-2v10h2V3zm4.83,2.17l-1.42,1.42C17.99,7.86 19,9.81 19,12c0,3.87 -3.13,7 -7,7s-7,-3.13 -7,-7c0,-2.19 1.01,-4.14 2.58,-5.42L6.17,5.17C4.23,6.82 3,9.26 3,12c0,4.97 4.03,9 9,9s9,-4.03 9,-9c0,-2.74 -1.23,-5.18 -3.17,-6.83z'
    els.append(G([P(('raw', glyph), 'widget_glyph_on' if on else 'widget_glyph_off', 1)], translateX=cx - 11, translateY=cy - 11, scaleX=22 / 24, scaleY=22 / 24))
    return els

def build_knob_xml(on):
    v = Vec(56, 56, 56, 56, build_knob(on))
    st = ('AKTIF = cekung (bevel terbalik, inti permata menyala cyan, cincin orbit + 24 tik + satelit emas bercahaya, ikon daya cyan berpendar)' if on else
          'NONAKTIF = timbul (permata 8 sisi ber-bevel terang kiri-atas, 8 faset, cincin orbit redup + tik + satelit, ikon daya redup)')
    hdr = ('<!-- Batch 325: knob daya Space, %s.\n'
           '     Permata = oktagon potong-sudut (selaras CutCornerShape knob app). SATU vector 56dp (viewport 56): ImageView (fitCenter) menskala seluruh knob seragam di widget pendek.\n'
           '     Tanpa layer-list/bitmap/gradien-vector (hanya fill+alpha: aman API 24+). Bevel/faset dihitung dari arah cahaya kiri-atas oleh docs/widget_space_gen.py. -->' % st)
    return '<?xml version="1.0" encoding="utf-8"?>\n' + hdr + '\n' + '\n'.join(v.xml('', root=True)) + '\n'

# ----------------------------------------------------------------------------- ikon equalizer (vector 25x24, ditint provider)
def build_eq_xml():
    els = []; bot = 22.5
    tops = [14.5, 10.0, 4.8, 12.0, 7.6]; alphas = [.65, .85, 1.0, .75, .95]
    for i, (top, a) in enumerate(zip(tops, alphas)):
        x = 1.5 + 5.5 * i
        els.append(P(('multi', [(x, y, 0.42) for y in [bot - 0.4 - 2.6 * j for j in range(7) if bot - 0.4 - 2.6 * j > 2.4]]), 'FFFFFF', 0.30))   # jalur titik-titik (trek)
        els.append(P(('poly', [(x - 1.5, bot - 1.5), (x - 1.5, top + 1.5), (x + 1.5, top + 1.5), (x + 1.5, bot - 1.5)], True), 'FFFFFF', a))
        els.append(P(('circle', x, bot - 1.5, 1.5), 'FFFFFF', a)); els.append(P(('circle', x, top + 1.5, 1.5), 'FFFFFF', a))
        R, r = 2.5, 0.55
        pts = [(x, top - R + .4), (x + r, top - r + .4), (x + R, top + .4), (x + r, top + r + .4), (x, top + R + .4), (x - r, top + r + .4), (x - R, top + .4), (x - r, top - r + .4)]
        els.append(P(('poly', pts, True), 'FFFFFF', 1.0))
    v = Vec(25, 24, 25, 24, els)
    hdr = ('<!-- Batch 325: indikator equalizer DEKORATIF Space (bukan level audio; tak ada data dibaca). 5 batang kapsul + percikan bintang 4-sudut di ujung + trek titik-titik\n'
           '     (rasi) di belakang. Putih ber-alpha; BoosterWidgetProvider mewarnainya lewat setColorFilter (SRC_ATOP, alpha dipertahankan): biru-ungu redup saat Nonaktif, cyan saat Aktif.\n'
           '     Viewport/ukuran 25x24 SAMA dgn B214 (layout tak terpengaruh). -->')
    return '<?xml version="1.0" encoding="utf-8"?>\n' + hdr + '\n' + '\n'.join(v.xml('', root=True)) + '\n'

# ----------------------------------------------------------------------------- token warna (values/widget_theme.xml)
TOKEN_NOTES = {
    'widget_gem': 'Tepi permata knob Nonaktif (indigo terang, di atasnya bevel kiri-atas).',
    'widget_void': 'Void / kedalaman ruang (SpaceVoid): dasar sumur Aktif, ujung gradasi pelat.',
    'widget_deep': 'SpaceBackground: dasar knob Aktif, tengah gradasi pelat.',
    'widget_surface': 'SpaceSurface: tengah gradasi panel.',
    'widget_raised': 'SpaceSurfaceRaised: muka timbul knob.',
    'widget_card_light': 'SpaceCardLight: sisi terang kartu/panel Nonaktif, dasar knob Nonaktif.',
    'widget_card_dark': 'SpaceCardDark: awal gradasi muka pelat.',
    'widget_cyan': 'Ion cyan (SpaceAccent): utama; pendar Aktif.',
    'widget_violet': 'Ungu nebula (SpaceSecondary).',
    'widget_magenta': 'Magenta nebula: ujung tepi pelat + kabut.',
    'widget_gold': 'Emas bintang (SpaceGold): satelit/bintang.',
    'widget_star': 'Putih bintang.',
    'widget_text_primary': 'Judul: #EEF2FF di atas panel (kontras terukur di pratinjau).',
    'widget_text_secondary': 'Status Nonaktif.',
    'widget_text_active': 'Status Aktif (cyan).',
    'widget_glyph_off': 'Ikon daya + satelit knob Nonaktif.',
    'widget_glyph_on': 'Ikon daya knob Aktif.',
    'widget_eq_off': 'Bar equalizer dekoratif Nonaktif (setColorFilter di provider).',
    'widget_eq_on': 'Bar equalizer dekoratif Aktif (setColorFilter di provider).',
}
ALWAYS = ['widget_text_primary', 'widget_text_secondary', 'widget_text_active', 'widget_eq_on', 'widget_eq_off']

def build_values(used):
    names = [k for k in PAL if k in used or k in ALWAYS]
    rows = ['    <color name="%s">%s</color>  <!-- %s -->' % (k, PAL[k], TOKEN_NOTES[k]) for k in names]
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Batch 325 (rombak total dari Neumorphism burgundy B212-B228): token widget beranda bertema SPACE, satu sumber untuk layout + drawable widget.\n'
            '     Palet = tema Space di app (Theme.kt: SpaceVoid/Background/Surface/CardLight/CardDark/Accent/Secondary/Gold + magenta nebula). Overlay ber-alpha di drawable\n'
            '     (bayangan, kabut nebula, kilau, bintang) memakai RGB turunan palet ini dgn alpha bertingkat hasil skrip (docs/widget_space_gen.py), jadi tidak bisa @color tunggal.\n'
            '     Nama token yang dibaca BoosterWidgetProvider/layout DIPERTAHANKAN (widget_text_*, widget_eq_on/off); token Neumorphism lama (plate/raised/well/rim/midnight) dihapus. -->\n'
            '<resources>\n' + '\n'.join(rows) + '\n\n'
            '    <!-- INSET. Ruang bayangan/aura pelat 6dp (di widget_background.xml) + padding root 8dp + ruang bayangan knob/panel 4dp (di dalam drawable masing-masing) =>\n'
            '         muka knob/panel berjarak 8+4=12dp dari tepi widget = 6dp dari tepi pelat (cincin 5dp pelat + bintang-bintangnya terlihat di sana). -->\n'
            '    <dimen name="widget_padding">8dp</dimen>\n'
            '    <dimen name="widget_knob_max_size">56dp</dimen>\n'
            '    <!-- Padding dalam panel = ruang bayangan 4dp + napas teks 12dp. -->\n'
            '    <dimen name="widget_panel_padding">16dp</dimen>\n'
            '    <!-- Radius pelat (pojok besar kiri-atas). Android 12+ mengganti dgn radius sistem peluncur (values-v31). -->\n'
            '    <dimen name="widget_corner_radius">22dp</dimen>\n'
            '    <!-- Batch 325: pojok kecil bahasa "orbit" Space = 6dp (SpaceOrbitCorners 44/6/6/6): dipakai 3 pojok lain pelat. Panel teks kebalikannya (24dp/6dp literal di widget_panel_*.xml). -->\n'
            '    <dimen name="widget_corner_radius_small">6dp</dimen>\n'
            '</resources>\n')

def generate(res):
    import re, xml.dom.minidom as md
    files = {
        'drawable/widget_background.xml': build_plate(),
        'drawable/widget_panel_off.xml': build_panel(False),
        'drawable/widget_panel_on.xml': build_panel(True),
        'drawable/widget_status_dot_off.xml': build_knob_xml(False),
        'drawable/widget_status_dot_on.xml': build_knob_xml(True),
        'drawable/ic_widget_eq.xml': build_eq_xml(),
    }
    used = set(re.findall(r'@color/(widget_\w+)', ''.join(files.values())))
    files['values/widget_theme.xml'] = build_values(used)
    for rel, txt in files.items():
        md.parseString(txt.encode('utf-8'))      # XML harus well-formed
        p = os.path.join(res, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, 'w', encoding='utf-8', newline='\n').write(txt)
    return files


# ----------------------------------------------------------------------------- PRATINJAU (opsional: butuh numpy + Pillow)
import re, glob, xml.etree.ElementTree as ET
try:
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
except ImportError:      # pembuatan XML tetap jalan tanpa keduanya
    np = None
import numpy as np
from PIL import Image, ImageDraw, ImageFont

A = '{http://schemas.android.com/apk/res/android}'
S = 6                                   # px per dp
DIMEN = {'widget_corner_radius': 22.0, 'widget_corner_radius_small': 6.0}

def dp(v, default=0.0):
    if v is None: return default
    m = re.match(r'^(-?[\d.]+)dp$', v)
    if m: return float(m.group(1))
    m = re.match(r'^@dimen/(\w+)$', v)
    if m: return DIMEN[m.group(1)]
    return default

def parse_color(c, pal):
    if c.startswith('@color/'): c = pal[c[7:]]
    h = c.lstrip('#')
    if len(h) == 6: h = 'FF' + h
    a, r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
    return np.array([r, g, b], np.float32) / 255, a / 255

class Canvas:
    def __init__(s, W, H):
        s.W, s.H = W, H; s.px = np.zeros((int(round(H * S)), int(round(W * S)), 4), np.float32)
    def sl(s, x0, y0, x1, y1):
        xs0, ys0 = max(0, int(math.floor(x0 * S))), max(0, int(math.floor(y0 * S)))
        xs1, ys1 = min(s.px.shape[1], int(math.ceil(x1 * S))), min(s.px.shape[0], int(math.ceil(y1 * S)))
        if xs1 <= xs0 or ys1 <= ys0: return None
        yy, xx = np.mgrid[ys0:ys1, xs0:xs1]
        return (slice(ys0, ys1), slice(xs0, xs1)), (xx + .5) / S, (yy + .5) / S
    def over(s, sl, mask, rgb, alpha=1.0):
        a = (mask * alpha).astype(np.float32); d = s.px[sl]
        rgb = np.asarray(rgb, np.float32)
        if rgb.ndim == 1: rgb = np.broadcast_to(rgb, d.shape[:2] + (3,))
        d[..., :3] = rgb * a[..., None] + d[..., :3] * (1 - a)[..., None]
        d[..., 3] = a + d[..., 3] * (1 - a)

def aa(d): return np.clip(0.5 - d * S, 0, 1)

def rrect_d(X, Y, r, radii):
    x0, y0, x1, y1 = r; cx, cy = (x0 + x1) / 2, (y0 + y1) / 2; hx, hy = (x1 - x0) / 2, (y1 - y0) / 2
    tl, tr, br, bl = radii
    rad = np.where(X < cx, np.where(Y < cy, tl, bl), np.where(Y < cy, tr, br))
    rad = np.minimum(rad, min(hx, hy))
    qx, qy = np.abs(X - cx) - (hx - rad), np.abs(Y - cy) - (hy - rad)
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - rad

def ell_d(X, Y, r):
    x0, y0, x1, y1 = r; cx, cy = (x0 + x1) / 2, (y0 + y1) / 2; rx, ry = (x1 - x0) / 2, (y1 - y0) / 2
    return (np.hypot((X - cx) / rx, (Y - cy) / ry) - 1) * min(rx, ry)

def grad_field(g, X, Y, r, pal):
    typ = g.get(A + 'type', 'linear'); c0, a0 = parse_color(g.get(A + 'startColor'), pal); c1, a1 = parse_color(g.get(A + 'endColor'), pal)
    cc = g.get(A + 'centerColor'); cxp, cyp = float(g.get(A + 'centerX', .5)), float(g.get(A + 'centerY', .5))
    x0, y0, x1, y1 = r; w, h = x1 - x0, y1 - y0
    if typ == 'radial':
        rad = float(g.get(A + 'gradientRadius').rstrip('%')) / 100 * min(w, h)
        t = np.hypot(X - (x0 + w * cxp), Y - (y0 + h * cyp)) / rad
    else:
        ang = int(g.get(A + 'angle', 0)) % 360
        pts = {0: (x0, y0 + h / 2, x1, y0 + h / 2), 45: (x0, y1, x1, y0), 90: (x0 + w / 2, y1, x0 + w / 2, y0), 135: (x1, y1, x0, y0),
               180: (x1, y0 + h / 2, x0, y0 + h / 2), 225: (x1, y0, x0, y1), 270: (x0 + w / 2, y0, x0 + w / 2, y1), 315: (x0, y0, x1, y1)}[ang]
        px, py, qx, qy = pts; dx, dy = qx - px, qy - py
        t = ((X - px) * dx + (Y - py) * dy) / (dx * dx + dy * dy)
    t = np.clip(t, 0, 1)
    if cc:
        c2, a2 = parse_color(cc, pal); pos = cxp if cxp != .5 else cyp
        pos = pos if abs(pos - .5) > 1e-6 else .5
        cols = [c0, c2, c1]; als = [a0, a2, a1]; st = [0, pos, 1]
    else:
        cols = [c0, c1]; als = [a0, a1]; st = [0, 1]
    rgb = np.zeros(X.shape + (3,), np.float32); al = np.zeros(X.shape, np.float32)
    for i in range(len(st) - 1):
        m = (t >= st[i]) & (t <= st[i + 1]); u = ((t - st[i]) / max(1e-6, st[i + 1] - st[i]))[m]
        for ch in range(3): rgb[..., ch][m] = cols[i][ch] * (1 - u) + cols[i + 1][ch] * u
        al[m] = als[i] * (1 - u) + als[i + 1] * u
    return rgb, al

def place(rect, w, h, grav):
    x0, y0, x1, y1 = rect; g = (grav or '').split('|')
    if w is None or w < 0: ox0, ox1 = x0, x1
    elif 'center_horizontal' in g or 'center' in g: ox0 = (x0 + x1) / 2 - w / 2; ox1 = ox0 + w
    elif 'right' in g or 'end' in g: ox1 = x1; ox0 = x1 - w
    else: ox0 = x0; ox1 = x0 + w
    if h is None or h < 0: oy0, oy1 = y0, y1
    elif 'center_vertical' in g or 'center' in g: oy0 = (y0 + y1) / 2 - h / 2; oy1 = oy0 + h
    elif 'bottom' in g: oy1 = y1; oy0 = y1 - h
    else: oy0 = y0; oy1 = y0 + h
    return ox0, oy0, ox1, oy1

# --- mini parser path SVG -> polyline
def arc_pts(x1, y1, rx, ry, phi, fa, fs, x2, y2, n=48):
    if rx == 0 or ry == 0: return [(x2, y2)]
    cp, sp = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2; x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1: rx *= math.sqrt(lam); ry *= math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2; den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    co = (-1 if fa == fs else 1) * math.sqrt(max(0, num / den)); cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx, cy = cp * cxp - sp * cyp + (x1 + x2) / 2, sp * cxp + cp * cyp + (y1 + y2) / 2
    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy); return a
    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry); dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0: dt -= 2 * math.pi
    if fs and dt < 0: dt += 2 * math.pi
    out = []
    for i in range(1, n + 1):
        t = t1 + dt * i / n; out.append((cp * rx * math.cos(t) - sp * ry * math.sin(t) + cx, sp * rx * math.cos(t) + cp * ry * math.sin(t) + cy))
    return out

def parse_path(d):
    toks = re.findall(r'[MmLlHhVvAaCcSsZz]|-?\d*\.?\d+(?:e-?\d+)?', d); i = 0; subs = []; cur = []; x = y = sx = sy = 0.0; cmd = None; lc = None
    def num():
        nonlocal i; v = float(toks[i]); i += 1; return v
    while i < len(toks):
        if re.match(r'[A-Za-z]', toks[i]): cmd = toks[i]; i += 1
        c = cmd
        if c in 'Zz':
            if cur: subs.append((cur, True)); cur = []
            x, y = sx, sy; continue
        if c in 'Mm':
            if cur: subs.append((cur, False))
            nx, ny = num(), num(); x, y = (nx, ny) if c == 'M' else (x + nx, y + ny); sx, sy = x, y; cur = [(x, y)]; cmd = 'L' if c == 'M' else 'l'
        elif c in 'Ll': nx, ny = num(), num(); x, y = (nx, ny) if c == 'L' else (x + nx, y + ny); cur.append((x, y))
        elif c in 'Hh': nx = num(); x = nx if c == 'H' else x + nx; cur.append((x, y))
        elif c in 'Vv': ny = num(); y = ny if c == 'V' else y + ny; cur.append((x, y))
        elif c in 'Aa':
            rx, ry, ph, fa, fs, nx, ny = (num() for _ in range(7))
            if c == 'a': nx, ny = x + nx, y + ny
            cur += arc_pts(x, y, rx, ry, ph, int(fa), int(fs), nx, ny); x, y = nx, ny
        elif c in 'Cc':
            v = [num() for _ in range(6)]
            if c == 'c': v = [v[0] + x, v[1] + y, v[2] + x, v[3] + y, v[4] + x, v[5] + y]
            p0 = (x, y)
            for k in range(1, 17):
                t = k / 16; u = 1 - t
                cur.append((u ** 3 * p0[0] + 3 * u * u * t * v[0] + 3 * u * t * t * v[2] + t ** 3 * v[4], u ** 3 * p0[1] + 3 * u * u * t * v[1] + 3 * u * t * t * v[3] + t ** 3 * v[5]))
            x, y = v[4], v[5]
        elif c in 'Ss':
            v = [num() for _ in range(4)]
            if c == 's': v = [v[0] + x, v[1] + y, v[2] + x, v[3] + y]
            p0 = (x, y); c1 = p0
            for k in range(1, 17):
                t = k / 16; u = 1 - t
                cur.append((u ** 3 * p0[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * v[0] + t ** 3 * v[2], u ** 3 * p0[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * v[1] + t ** 3 * v[3]))
            x, y = v[2], v[3]
        else: raise ValueError(c)
    if cur: subs.append((cur, False))
    return subs

def xf_point(p, stack):
    x, y = p
    for g in reversed(stack):      # dalam -> luar
        px, py = g['pivotX'], g['pivotY']; x, y = (x - px) * g['scaleX'], (y - py) * g['scaleY']
        r = math.radians(g['rotation']); x, y = x * math.cos(r) - y * math.sin(r), x * math.sin(r) + y * math.cos(r)
        x, y = x + px + g['translateX'], y + py + g['translateY']
    return x, y

def draw_vector(cv, v, rect, pal):
    vw, vh = float(v.get(A + 'viewportWidth')), float(v.get(A + 'viewportHeight')); x0, y0, x1, y1 = rect
    sx, sy = (x1 - x0) / vw, (y1 - y0) / vh
    def walk(el, stack):
        for ch in el:
            if ch.tag == 'group':
                g = {k: float(ch.get(A + k, d)) for k, d in (('rotation', 0), ('pivotX', 0), ('pivotY', 0), ('scaleX', 1), ('scaleY', 1), ('translateX', 0), ('translateY', 0))}
                walk(ch, stack + [g])
            elif ch.tag == 'path':
                fill, stroke = ch.get(A + 'fillColor'), ch.get(A + 'strokeColor')
                fa, sa = float(ch.get(A + 'fillAlpha', 1)), float(ch.get(A + 'strokeAlpha', 1)); sw = float(ch.get(A + 'strokeWidth', 0))
                for pts, closed in parse_path(ch.get(A + 'pathData')):
                    P = [((x0 + xf_point(p, stack)[0] * sx) * S, (y0 + xf_point(p, stack)[1] * sy) * S) for p in pts]
                    if len(P) < 2: continue
                    bx0, by0 = min(p[0] for p in P) / S - 2, min(p[1] for p in P) / S - 2; bx1, by1 = max(p[0] for p in P) / S + 2, max(p[1] for p in P) / S + 2
                    s = cv.sl(bx0, by0, bx1, by1)
                    if not s: continue
                    sl, X, Y = s; ox, oy = sl[1].start, sl[0].start
                    if fill and (closed or True) and len(P) >= 3:
                        im = Image.new('L', (X.shape[1], X.shape[0]), 0); ImageDraw.Draw(im).polygon([(p[0] - ox, p[1] - oy) for p in P], fill=255)
                        col, al = parse_color(fill, pal); cv.over(sl, np.asarray(im, np.float32) / 255, col, fa * al)
                    if stroke:
                        im = Image.new('L', (X.shape[1], X.shape[0]), 0); dr = ImageDraw.Draw(im)
                        pp = [(p[0] - ox, p[1] - oy) for p in P] + ([(P[0][0] - ox, P[0][1] - oy)] if closed else [])
                        dr.line(pp, fill=255, width=max(1, int(round(sw * sx * S))), joint='curve')
                        col, al = parse_color('#' + stroke.lstrip('#'), pal); cv.over(sl, np.asarray(im, np.float32) / 255, col, sa * al)
    walk(v, [])

def draw_shape(cv, sh, rect, pal):
    x0, y0, x1, y1 = rect; kind = sh.get(A + 'shape', 'rectangle')
    co = sh.find('corners'); radii = (0, 0, 0, 0)
    if co is not None: radii = tuple(dp(co.get(A + k)) for k in ('topLeftRadius', 'topRightRadius', 'bottomRightRadius', 'bottomLeftRadius'))
    st = sh.find('stroke'); sw = dp(st.get(A + 'width')) if st is not None else 0
    s = cv.sl(x0 - 1, y0 - 1, x1 + 1, y1 + 1)
    if not s: return
    sl, X, Y = s; r = (x0 + sw / 2, y0 + sw / 2, x1 - sw / 2, y1 - sw / 2)
    dist = (lambda X, Y, rr: ell_d(X, Y, rr)) if kind == 'oval' else (lambda X, Y, rr: rrect_d(X, Y, rr, radii))
    fl = sh.find('solid'); gr = sh.find('gradient')
    if fl is not None or gr is not None:
        m = aa(dist(X, Y, r))
        if fl is not None: col, al = parse_color(fl.get(A + 'color'), pal); cv.over(sl, m, col, al)
        else: rgb, al = grad_field(gr, X, Y, r, pal); cv.over(sl, m, rgb, al)
    if st is not None:
        ro = (r[0] - sw / 2, r[1] - sw / 2, r[2] + sw / 2, r[3] + sw / 2); ri = (r[0] + sw / 2, r[1] + sw / 2, r[2] - sw / 2, r[3] - sw / 2)
        ring = np.clip(aa(dist(X, Y, ro)) - aa(dist(X, Y, ri)), 0, 1); col, al = parse_color(st.get(A + 'color'), pal); cv.over(sl, ring, col, al)

def draw_drawable(cv, path, rect, pal):
    root = ET.parse(path).getroot()
    if root.tag == 'vector': draw_vector(cv, root, rect, pal); return
    X0, Y0, X1, Y1 = rect
    for it in root.findall('item'):
        l, t, r, b = (dp(it.get(A + k)) for k in ('left', 'top', 'right', 'bottom'))
        w, h = (dp(it.get(A + k), -1) for k in ('width', 'height')); box = (X0 + l, Y0 + t, X1 - r, Y1 - b)
        ch = it.find('shape'); isv = False
        if ch is None: ch = it.find('vector'); isv = True
        if not isv:
            size = ch.find('size')
            if size is not None and size.get(A + 'height'): h = dp(size.get(A + 'height'))
        rc = place(box, None if w < 0 else w, None if h < 0 else h, it.get(A + 'gravity'))
        if isv: draw_vector(cv, ch, rc, pal)
        else: draw_shape(cv, ch, rc, pal)

# --- komposisi widget penuh
def font(sz_px, bold=False):
    for pat in (['/usr/share/fonts/opentype/inter/InterDisplay-Medium.otf', '/usr/share/fonts/opentype/inter/Inter-Medium.otf'] if bold else ['/usr/share/fonts/opentype/inter/Inter-Regular.otf']) + glob.glob('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if os.path.exists(pat): return ImageFont.truetype(pat, int(sz_px))
    return ImageFont.load_default()

def lum(rgb):
    c = np.where(rgb <= 0.03928, rgb / 12.92, ((rgb + 0.055) / 1.055) ** 2.4); return 0.2126 * c[..., 0] + 0.7152 * c[..., 1] + 0.0722 * c[..., 2]

def compose(res, pal, W, H, on, wall):
    cv = Canvas(W, H); cv.px[...] = 0
    # latar wallpaper di belakang (agar aura terlihat)
    bg = Canvas(W, H); yy, xx = np.mgrid[0:cv.px.shape[0], 0:cv.px.shape[1]]
    wl = np.array(wall, np.float32) / 255; bg.px[..., :3] = wl; bg.px[..., 3] = 1
    d = lambda n: os.path.join(res, 'drawable', n)
    draw_drawable(cv, d('widget_background.xml'), (0, 0, W, H), pal)
    k = min(56, H - 16); ky = 8 + (H - 16 - k) / 2
    draw_drawable(cv, d('widget_status_dot_on.xml' if on else 'widget_status_dot_off.xml'), (8, ky, 8 + k, ky + k), pal)
    prect = (8 + k, 8, W - 8, H - 8); pw, ph = prect[2] - prect[0], prect[3] - prect[1]
    draw_drawable(cv, d('widget_panel_on.xml' if on else 'widget_panel_off.xml'), prect, pal)
    # teks
    sec = parse_color(pal['widget_text_active' if on else 'widget_text_secondary'], pal)[0]; pri = parse_color(pal['widget_text_primary'], pal)[0]
    st_txt = 'Aktif' if on else 'Nonaktif'; f13 = font(13 * S); st_w = f13.getlength(st_txt) / S
    eq_h = min(24, ph - 12); eq_w = 25 * eq_h / 24
    avail = pw - 32 - (eq_w + 8 + 8 + st_w) - 8; sz = 18
    while sz > 11 and font(sz * S, True).getlength('Boomly') / S > avail: sz -= 1
    pre = Image.fromarray((np.clip(cv.px[..., :3], 0, 1) * 255).astype(np.uint8))
    px0 = prect[0] + 16; cy = prect[1] + ph / 2
    ft = font(sz * S, True); tb = ft.getbbox('Boomly')
    t_box = (px0, cy - (tb[3] - tb[1]) / 2 / S, px0 + (tb[2] - tb[0]) / S, cy + (tb[3] - tb[1]) / 2 / S)
    ex0 = px0 + avail + 8; eq_rect = (ex0, cy - eq_h / 2, ex0 + eq_w, cy + eq_h / 2)
    sx0 = eq_rect[2] + 8 + 8; sb = f13.getbbox(st_txt); s_box = (sx0, cy - (sb[3] - sb[1]) / 2 / S, sx0 + st_w, cy + (sb[3] - sb[1]) / 2 / S)
    def bgmax(b):
        a = cv.px[int(b[1] * S):int(b[3] * S) + 1, int(b[0] * S):int(b[2] * S) + 1, :3]
        L = lum(a); return float(L.max()), float(L.mean())
    # eq icon (tint SRC_ATOP)
    eqc = parse_color(pal['widget_eq_on' if on else 'widget_eq_off'], pal)[0]
    eqcv = Canvas(W, H); draw_drawable(eqcv, d('ic_widget_eq.xml'), eq_rect, pal)
    a = eqcv.px[..., 3]; sl = (slice(0, cv.px.shape[0]), slice(0, cv.px.shape[1])); cv.over(sl, a, eqc)
    stats = {}
    for name, box, col in (('title', t_box, pri), ('status', s_box, sec)):
        mx, mean = bgmax(box); Lt = float(lum(col)); stats[name] = (Lt + .05) / (mx + .05), (Lt + .05) / (mean + .05)
    img = Image.fromarray((np.clip(cv.px[..., :3], 0, 1) * 255).astype(np.uint8)); al = Image.fromarray((np.clip(cv.px[..., 3], 0, 1) * 255).astype(np.uint8))
    out = Image.new('RGB', img.size, tuple(wall)); out.paste(img, (0, 0), al)
    dr = ImageDraw.Draw(out); sh = (0, 0, 0)
    dr.text((t_box[0] * S + 0, (t_box[1]) * S + 1.5 * S), 'Boomly', font=ft, fill=sh, anchor='lt') if False else None
    dr.text((px0 * S, cy * S), 'Boomly', font=ft, fill=tuple(int(c * 255) for c in pri), anchor='lm')
    dr.text((sx0 * S, cy * S), st_txt, font=f13, fill=tuple(int(c * 255) for c in sec), anchor='lm')
    return out, stats, sz

def run_preview(res, outdir):
    g = globals()
    if np is None: raise SystemExit("--preview butuh numpy + Pillow")
    os.makedirs(outdir, exist_ok=True); pal = g['PAL']
    sizes = [(250, 72), (200, 60), (330, 110)]; wall = (92, 104, 138)
    rows = []; report = []
    for (W, H) in sizes:
        for on in (False, True):
            im, st, sz = compose(res, pal, W, H, on, wall); rows.append((W, H, on, im))
            report.append('%dx%d %-8s title %.1f:1 (rata2 %.1f) status %.1f:1 (rata2 %.1f) font judul %dsp' % (W, H, 'AKTIF' if on else 'NONAKTIF', st['title'][0], st['title'][1], st['status'][0], st['status'][1], sz))
    Wm = max(r[3].width for r in rows) * 2 + 60; Hm = sum(r[3].height for r in rows[::2]) + 40 * (len(sizes) + 1)
    sheet = Image.new('RGB', (Wm, Hm), wall); y = 30
    for i in range(0, len(rows), 2):
        for j in range(2): sheet.paste(rows[i + j][3], (20 + j * (rows[i][3].width + 20), y))
        y += rows[i][3].height + 40
    sheet = sheet.resize((sheet.width // 2, sheet.height // 2), Image.LANCZOS); sheet.save(os.path.join(outdir, 'sheet.png'))
    for W, H, on, im in rows: im.save(os.path.join(outdir, 'w_%dx%d_%s.png' % (W, H, 'on' if on else 'off')))
    open(os.path.join(outdir, 'report.txt'), 'w').write('\n'.join(report) + '\n'); print('\n'.join(report))


if __name__ == '__main__':
    ap = argparse.ArgumentParser(); ap.add_argument('--res', required=True); ap.add_argument('--preview', default=None)
    a = ap.parse_args()
    fs = generate(a.res)
    for k, v in fs.items(): print('%-38s %6d B' % (k, len(v.encode())))
    if a.preview:
        run_preview(a.res, a.preview)
