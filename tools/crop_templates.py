#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
模板裁剪小工具：把游戏截图里的一小块剪成模板 PNG。

用法:
    python crop_templates.py 截图.png
    python crop_templates.py 截图.png -o ../app/src/main/assets/templates

操作:
    左键拖拽 = 框选区域，松手后弹窗输入文件名（如 btn_battle_again.png），回车保存
    同一张图上可以连续框多次，一次性把需要的模板都剪出来
    Esc = 退出

依赖: Pillow（pip install pillow），tkinter（Python 自带）
"""
import argparse
import os
import sys

try:
    from PIL import Image, ImageTk
except ImportError:
    sys.exit("缺少 Pillow，请先执行: pip install pillow")

import tkinter as tk
from tkinter import simpledialog, messagebox


class Cropper:
    def __init__(self, root, path, outdir):
        self.root = root
        self.outdir = outdir
        os.makedirs(outdir, exist_ok=True)
        self.img = Image.open(path).convert("RGB")
        self.scale = min(1.0, 1000.0 / max(self.img.width, self.img.height))
        self.disp = self.img.resize(
            (int(self.img.width * self.scale), int(self.img.height * self.scale)),
            Image.LANCZOS)
        self.tkimg = ImageTk.PhotoImage(self.disp)
        self.canvas = tk.Canvas(root, width=self.disp.width, height=self.disp.height,
                                cursor="crosshair")
        self.canvas.pack()
        self.canvas.create_image(0, 0, anchor="nw", image=self.tkimg)
        self.start = None
        self.rect = None
        self.canvas.bind("<ButtonPress-1>", self.on_press)
        self.canvas.bind("<B1-Motion>", self.on_drag)
        self.canvas.bind("<ButtonRelease-1>", self.on_release)
        root.bind("<Escape>", lambda e: root.destroy())

    def on_press(self, e):
        self.start = (e.x, e.y)
        if self.rect:
            self.canvas.delete(self.rect)
        self.rect = self.canvas.create_rectangle(e.x, e.y, e.x, e.y, outline="#ff4081", width=2)

    def on_drag(self, e):
        if self.start and self.rect:
            self.canvas.coords(self.rect, self.start[0], self.start[1], e.x, e.y)

    def on_release(self, e):
        if not self.start:
            return
        x1, y1 = self.start
        x2, y2 = e.x, e.y
        self.start = None
        x1, x2 = sorted((x1, x2))
        y1, y2 = sorted((y1, y2))
        if x2 - x1 < 6 or y2 - y1 < 6:
            return
        # 换算回原图坐标
        ox1, oy1 = int(x1 / self.scale), int(y1 / self.scale)
        ox2, oy2 = int(x2 / self.scale), int(y2 / self.scale)
        name = simpledialog.askstring("保存模板", "文件名（例如 btn_battle_again.png）：",
                                      initialvalue="template.png", parent=self.root)
        if not name:
            return
        if not name.lower().endswith(".png"):
            name += ".png"
        crop = self.img.crop((ox1, oy1, ox2, oy2))
        out = os.path.join(self.outdir, name)
        crop.save(out)
        print(f"saved {out}  ({crop.width}x{crop.height})  原始区域=({ox1},{oy1})-({ox2},{oy2})")
        messagebox.showinfo("已保存", f"{out}\n裁剪尺寸 {crop.width}x{crop.height}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("-o", "--outdir", default="templates")
    args = ap.parse_args()
    root = tk.Tk()
    root.title("裁剪模板 · 拖拽框选，Esc 退出")
    Cropper(root, args.image, args.outdir)
    root.mainloop()


if __name__ == "__main__":
    main()
