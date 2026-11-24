# -*- coding: utf-8 -*-
"""从 ref/ 里的参考截图重新生成全部模板（模板是按「预览坐标」裁的，脚本自动换算到原图）。

用法:
    python tools/make_templates.py

游戏更新、界面位移后，改下面的坐标表重跑即可。
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
REF = os.path.join(ROOT, "ref")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "templates")
# 参考截图宽度是 2510，我给坐标时用的是 1904 宽的预览图，这里换算
K = 2510.0 / 1904.0

# (输出文件名, 参考截图, 预览坐标 x1,y1,x2,y2, 说明)
TEMPLATES = [
    ("stage_play.png", "a1_stage", (1420, 640, 1615, 825), "图一 右下角大箭头(开始战斗)"),
    ("back_arrow.png", "a1_stage", (160, 35, 265, 125), "左上角返回箭头"),
    ("result_refresh.png", "a2_result", (1405, 190, 1590, 360), "图二 右上角循环图标"),
    ("merchant_banner.png", "a3_banner", (470, 345, 1130, 500), "图三 商人到访横幅"),
    ("merchant_banner_edge.png", "a4_banner_toast", (1035, 355, 1125, 500), "图四 横幅右端(被遮挡时也能认)"),
    ("merchant_fig.png", "a6_hub2", (1440, 275, 1570, 382), "图六 主城里的商人小人"),
    ("campaign_btn.png", "a5_hub", (752, 678, 914, 724), "图五 战役按钮"),
    ("shop_dialog_ok.png", "a8_confirm", (800, 612, 1000, 706), "图八 确定按钮"),
    ("chapter_left.png", "a10_chapter", (314, 440, 382, 528), "图十/图一 左翻页箭头(只裁三角形本身)"),
    ("chapter_right.png", "a10_chapter", (1510, 440, 1588, 528), "图十/图一 右翻页箭头(只裁三角形本身)"),
    ("shop_open.png", "a7_shop", (390, 100, 600, 270), "图七 商店标题头像(判断是否在商店里)"),
    ("chapter7_node.png", "a12_chapter7", (1292, 548, 1452, 748), "第七章地图右下角关卡节点"),
    ("sell_dialog_ok.png", "a14_sell", (840, 570, 960, 635), "「卖给商人」弹窗的 好的 按钮"),
]


def main():
    os.makedirs(ASSETS, exist_ok=True)
    crops = []
    for name, src, box, desc in TEMPLATES:
        img = Image.open(os.path.join(REF, src + ".jpg")).convert("RGB")
        b = tuple(int(v * K) for v in box)
        c = img.crop(b)
        c.save(os.path.join(ASSETS, name))
        print("%-26s %4dx%-4d  %s" % (name, c.width, c.height, desc))
        crops.append((name, c))

    # 拼一张对照图，方便肉眼检查每个模板内容对不对
    cols, pad, label_h = 3, 14, 20
    cell_w = max(c.width for _, c in crops) + pad * 2
    cell_h = max(c.height for _, c in crops) + pad * 2 + label_h
    rows = (len(crops) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell_w, rows * cell_h), (245, 245, 248))
    d = ImageDraw.Draw(sheet)
    for i, (name, c) in enumerate(crops):
        cx, cy = (i % cols) * cell_w, (i // cols) * cell_h
        d.text((cx + pad, cy + 5), "%02d %s" % (i, name), fill=(190, 0, 80))
        sheet.paste(c, (cx + pad, cy + label_h + pad // 2))
    out = os.path.join(ROOT, "ref", "_templates_sheet.png")
    sheet.save(out)
    print("\n对照图: %s" % out)


if __name__ == "__main__":
    main()
