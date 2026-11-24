# -*- coding: utf-8 -*-
"""离线自测：用与 App 相同的 ZNCC 算法，把每个模板在全部参考截图上跑一遍。

输出矩阵：
  - 对角线（模板自己的源截图）分数应接近 1.0
  - 非同源截图分数应明显更低，否则说明模板太"泛滥"会误点

用法: python tools/selftest_match.py
"""
import os
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
REF = os.path.join(ROOT, "ref")
TDIR = os.path.join(ROOT, "app", "src", "main", "assets", "templates")

SCALE = 2
# 模板 -> 它应该在哪些截图上出现
EXPECT = {
    "stage_play.png": ["a1_stage"],
    "back_arrow.png": ["a1_stage", "a3_banner", "a4_banner_toast", "a7_shop",
                       "a9_allsold", "a12_chapter7", "a13_shop_all", "a14_sell"],
    "result_refresh.png": ["a2_result"],
    "merchant_banner.png": ["a3_banner"],
    "merchant_banner_edge.png": ["a3_banner", "a4_banner_toast"],
    "merchant_fig.png": ["a6_hub2"],
    "campaign_btn.png": ["a5_hub"],
    "shop_dialog_ok.png": ["a8_confirm"],
    "chapter_left.png": ["a10_chapter", "a12_chapter7"],
    "chapter_right.png": ["a10_chapter", "a12_chapter7"],
    "shop_open.png": ["a7_shop", "a8_confirm", "a9_allsold", "a13_shop_all", "a14_sell"],
    "chapter7_node.png": ["a12_chapter7"],
    "sell_dialog_ok.png": ["a14_sell"],
}

SHOTS = ["a1_stage", "a2_result", "a3_banner", "a4_banner_toast", "a5_hub",
         "a6_hub2", "a7_shop", "a8_confirm", "a9_allsold", "a10_chapter",
         "a12_chapter7", "a13_shop_all", "a14_sell"]


def gray(path, scale=SCALE):
    im = Image.open(path).convert("L")
    if scale > 1:
        im = im.resize((im.width // scale, im.height // scale), Image.BILINEAR)
    return np.asarray(im, dtype=np.float64)


def integral(a):
    return np.pad(a.cumsum(0).cumsum(1), ((1, 0), (1, 0)), mode="constant")


def boxsum(integ, h, w):
    return integ[h:, w:] - integ[:-h, w:] - integ[h:, :-w] + integ[:-h, :-w]


def fftconv_valid(img, ker):
    H, W = img.shape
    h, w = ker.shape
    fh, fw = H + h - 1, W + w - 1
    F = np.fft.rfft2(img, (fh, fw))
    K = np.fft.rfft2(ker[::-1, ::-1], (fh, fw))
    out = np.fft.irfft2(F * K, (fh, fw))
    # 全卷积从 (h-1, w-1) 开始才是「模板完全落在图内」的有效区
    return out[h - 1: H, w - 1: W]


def best_score(frame, tpl):
    h, w = tpl.shape
    if h >= frame.shape[0] or w >= frame.shape[1]:
        return -1.0, 0, 0
    n = float(h * w)
    meanT = tpl.mean()
    varT = float(((tpl - meanT) ** 2).sum())
    if varT < 1e-6:
        return -1.0, 0, 0
    corr = fftconv_valid(frame, tpl)
    si = boxsum(integral(frame), h, w)
    si2 = boxsum(integral(frame * frame), h, w)
    num = corr - si * meanT
    den = np.sqrt(np.maximum(si2 - si * si / n, 0.0) * varT)
    with np.errstate(divide="ignore", invalid="ignore"):
        sc = np.where(den > 1e-6, num / den, -1.0)
    sc = np.nan_to_num(sc, nan=-1.0)
    idx = np.unravel_index(int(np.argmax(sc)), sc.shape)
    return float(sc[idx]), int(idx[1]), int(idx[0])


def main():
    frames = {s: gray(os.path.join(REF, s + ".jpg")) for s in SHOTS}
    print("帧尺寸(scale=%d): %s" % (SCALE, frames[SHOTS[0]].shape))
    names = sorted(n for n in os.listdir(TDIR) if n.endswith(".png"))
    hdr = "template".ljust(26) + "".join(s.split("_")[0].rjust(6) for s in SHOTS)
    print(hdr)
    rows = {}
    for name in names:
        tpl = gray(os.path.join(TDIR, name))
        line = name.ljust(26)
        scores = {}
        for s in SHOTS:
            sc, x, y = best_score(frames[s], tpl)
            scores[s] = (sc, x, y)
            line += ("%.2f" % sc).rjust(6)
        rows[name] = scores
        print(line)

    print("\n--- 每行最高分与建议阈值 ---")
    for name in names:
        scores = rows[name]
        bestshot = max(scores, key=lambda k: scores[k][0])
        top = scores[bestshot][0]
        others = [v[0] for k, v in scores.items() if k != bestshot]
        second = max(others) if others else -1
        exp = EXPECT.get(name, [])
        exp_hit = [s for s in exp if scores[s][0] >= 0.80]
        miss = [s for s in exp if s not in exp_hit]
        suggest = round((top + max(second, 0.0)) / 2, 2) if top > 0 else 0
        flag = "OK " if not miss else "MISS"
        print("%s %-26s best=%.3f@%-14s 第二个=%.3f  建议阈值≈%.2f  期望未命中=%s"
              % (flag, name, top, bestshot, second, suggest, miss))


if __name__ == "__main__":
    main()
