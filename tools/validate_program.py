# -*- coding: utf-8 -*-
"""校验 program.json：标签是否都存在、模板文件是否齐全、有没有死指令。

用法: python tools/validate_program.py
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PROG = os.path.join(ROOT, "app", "src", "main", "assets", "program.json")
TDIR = os.path.join(ROOT, "app", "src", "main", "assets", "templates")

OPS = {
    "label", "goto", "if", "ifnot", "tap", "wait", "tapxy",
    "swipe", "sleep", "back", "set", "add", "iflt", "ifgt",
    "log", "stop",
}


def target(x):
    """goto 指令既支持 {"op":"goto","goto":"x"} 也支持 {"op":"goto","label":"x"}。"""
    g = x.get("goto")
    if not g and x.get("op") == "goto":
        g = x.get("label")
    return g


def main():
    with open(PROG, encoding="utf-8") as f:
        p = json.load(f)
    ins = p["program"]
    labels = {}
    for i, x in enumerate(ins):
        if x.get("op") == "label":
            labels.setdefault(x["name"], i)

    errors = []
    for i, x in enumerate(ins):
        op = x.get("op")
        if op not in OPS:
            errors.append("#%d 未知指令 %s" % (i, op))
        g = target(x)
        if g and g not in labels:
            errors.append("#%d %s 跳到不存在的标签 %s" % (i, op, g))
        if op in ("iflt", "ifgt") and not x.get("var"):
            errors.append("#%d %s 缺少 var" % (i, op))
        if op in ("if", "ifnot", "tap", "wait") and not x.get("t"):
            errors.append("#%d %s 缺少 t" % (i, op))
        for t in x.get("t", []):
            if not os.path.exists(os.path.join(TDIR, t)):
                errors.append("#%d 模板不存在 %s" % (i, t))
        if op in ("swipe", "tapxy"):
            for k in ("x", "y"):
                if not (0.0 <= float(x.get(k, -1)) <= 1.0):
                    errors.append("#%d %s 坐标 %s 越界" % (i, op, k))

    reachable = set()
    stack = [0]
    while stack:
        i = stack.pop()
        while 0 <= i < len(ins) and i not in reachable:
            reachable.add(i)
            x = ins[i]
            g = target(x)
            if g and g in labels:
                stack.append(labels[g])
            if x.get("op") in ("goto", "stop"):
                break
            i += 1

    used_labels = {ins[i].get("name") for i in reachable if ins[i].get("op") == "label"}
    dead = sorted(set(labels) - used_labels)
    used_tpl = {t for x in ins for t in x.get("t", [])}
    unused_tpl = sorted(set(os.listdir(TDIR)) - {"README.txt"} - used_tpl)

    print("指令 %d 条 / 标签 %d 个 / 可达 %d 条" % (len(ins), len(labels), len(reachable)))
    if dead:
        print("!! 不可达标签: %s" % (dead,))
    if unused_tpl:
        print("!! 未被引用的模板: %s" % (unused_tpl,))
    if errors:
        print("XX 发现问题:")
        for e in errors:
            print("   ", e)
        sys.exit(1)
    if not dead:
        print("OK program.json 校验通过")


if __name__ == "__main__":
    main()
