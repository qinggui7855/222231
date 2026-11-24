# -*- coding: utf-8 -*-
"""program.json 的离线流程模拟：用假的游戏状态机验证脚本逻辑会不会卡死/走错。

用法: python tools/simulate_program.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PROG = os.path.join(ROOT, "app", "src", "main", "assets", "program.json")

MAX_STEPS = 6000


class Game:
    """假的游戏界面状态机。"""

    def __init__(self):
        self.state = "STAGE"
        self.chapter = 21
        self.pan = 0            # 0=图五主城左, 1=图六主城右
        self.banner = False
        self.result_count = 0
        self.battle_ticks = 0
        self.cards = [True, True, True]
        self.pending_card = None
        self.sell_dialog = False
        self.buys = 0
        self.trips = 0
        self.battles = 0

    def visible(self, tpl):
        s = self.state
        if s == "STAGE":
            return tpl in ("stage_play", "back_arrow")
        if s == "BATTLE":
            return False
        if s == "RESULT":
            v = tpl == "result_refresh"
            if self.banner:
                v = v or tpl in ("merchant_banner", "merchant_banner_edge")
            return v
        if s == "HUB":
            return tpl == "campaign_btn"
        if s == "HUB2":
            return tpl == "merchant_fig"
        if s == "SHOP":
            return tpl in ("shop_open", "back_arrow") or (
                tpl == "sell_dialog_ok" and self.sell_dialog)
        if s == "DIALOG":
            return tpl in ("shop_open", "shop_dialog_ok")
        if s == "CHAPTER":
            if tpl in ("chapter_left", "chapter_right"):
                return True
            if tpl == "chapter7_node":
                return self.chapter == 7
            return False
        return False

    def tick(self):
        if self.state == "BATTLE":
            self.battle_ticks += 1
            if self.battle_ticks >= 2:
                self.state = "RESULT"
                self.battle_ticks = 0
                self.result_count += 1
                # 第 3 次、第 7 次结算时，商人来访（并补货）
                if self.result_count in (3, 7):
                    self.banner = True
                    self.cards = [True, True, True]

    def tap(self, tpl):
        s = self.state
        if tpl == "stage_play" and s == "STAGE":
            self.state = "BATTLE"
            self.battle_ticks = 0
            self.battles += 1
            return "开打"
        if tpl == "result_refresh" and s == "RESULT":
            self.state = "STAGE"
            self.banner = False
            return "回图一"
        if tpl == "back_arrow":
            if s == "STAGE":
                self.state = "HUB"
                self.trips += 1
                return "回主城"
            if s in ("SHOP", "DIALOG"):
                self.state = "HUB2"
                return "出商店"
        if tpl == "merchant_fig" and s == "HUB2":
            self.state = "SHOP"
            self.sell_dialog = True      # 进店必弹「是否卖给商人」
            return "进商店"
        if tpl == "sell_dialog_ok" and s == "SHOP" and self.sell_dialog:
            self.sell_dialog = False
            return "关掉卖东西弹窗"
        if tpl == "campaign_btn" and s == "HUB":
            self.state = "CHAPTER"
            return "开战役"
        if tpl == "chapter_left" and s == "CHAPTER":
            if self.chapter > 1:
                self.chapter -= 1
            return "翻页左"
        if tpl == "chapter_right" and s == "CHAPTER":
            self.chapter += 1
            return "翻页右"
        if tpl == "chapter7_node" and s == "CHAPTER" and self.chapter == 7:
            self.state = "STAGE"
            return "进关卡"
        if tpl == "shop_dialog_ok" and s == "DIALOG":
            i = self.pending_card
            if i is not None and self.cards[i]:
                self.cards[i] = False
                self.buys += 1
            self.pending_card = None
            self.state = "SHOP"
            return "确认购买"
        return None

    def tap_ratio(self, x, y):
        if self.state == "SHOP":
            if self.sell_dialog:            # 弹窗是模态的，点到也没用
                return "被弹窗挡住"
            for i, cx in enumerate((0.301, 0.500, 0.732)):
                if abs(x - cx) < 0.05 and abs(y - 0.551) < 0.08:
                    if self.cards[i]:
                        self.pending_card = i
                        self.state = "DIALOG"
                        return "点商品%d" % (i + 1)
                    return "商品%d已售罄" % (i + 1)
        return None

    def swipe(self, x1, x2):
        if self.state in ("HUB", "HUB2"):
            if x2 > x1:      # 向右滑
                self.pan = min(1, self.pan + 1)
                self.state = "HUB2" if self.pan >= 1 else "HUB"
            else:            # 向左滑
                self.pan = max(0, self.pan - 1)
                self.state = "HUB2" if self.pan >= 1 else "HUB"
            return "滑动→" + self.state
        return None


def main():
    with open(PROG, encoding="utf-8") as f:
        p = json.load(f)
    ins = p["program"]
    labels = {}
    for i, x in enumerate(ins):
        if x.get("op") == "label":
            labels.setdefault(x["name"], i)

    def tgt(x):
        g = x.get("goto")
        if not g and x.get("op") == "goto":
            g = x.get("label")
        return g

    g = Game()
    vars_ = dict(p.get("vars", {}))
    pc = 0
    steps = 0
    trace = []

    while 0 <= pc < len(ins):
        steps += 1
        if steps > MAX_STEPS:
            print("!! 超过 %d 步仍未结束，疑似死循环" % MAX_STEPS)
            break
        x = ins[pc]
        op = x["op"]
        nxt = pc + 1

        if op == "label":
            pass
        elif op == "goto":
            nxt = labels[tgt(x)]
        elif op == "log":
            trace.append(g.state + " | " + x["msg"])
        elif op == "stop":
            trace.append("STOP: " + x.get("msg", ""))
            break
        elif op == "sleep":
            g.tick()
        elif op == "set":
            vars_[x["var"]] = x["val"]
        elif op == "add":
            vars_[x["var"]] = vars_.get(x["var"], 0) + x["val"]
        elif op == "iflt":
            if vars_.get(x["var"], 0) < x["val"]:
                nxt = labels[tgt(x)]
        elif op == "ifgt":
            if vars_.get(x["var"], 0) > x["val"]:
                nxt = labels[tgt(x)]
        elif op == "swipe":
            g.swipe(x["x"], x["x2"])
            g.tick()
        elif op == "tapxy":
            g.tap_ratio(x["x"], x["y"])
            g.tick()
        elif op in ("if", "ifnot", "wait", "tap"):
            t = x["t"]
            hit = None
            budget = max(1, int(x.get("timeoutMs", 0) / 130))
            for _ in range(budget):
                g.tick()
                for name in t:
                    base = name[:-4] if name.endswith(".png") else name
                    if g.visible(base):
                        hit = name
                        break
                if hit:
                    break
            if op == "if":
                if hit:
                    nxt = labels[tgt(x)]
            elif op == "ifnot":
                if not hit:
                    nxt = labels[tgt(x)]
            elif op == "wait":
                if not hit:
                    nxt = labels[tgt(x)]
            elif op == "tap":
                if hit:
                    r = g.tap(hit[:-4] if hit.endswith(".png") else hit)
                    trace.append("  tap %-22s -> %s" % (hit, r))
                else:
                    nxt = labels[tgt(x)]
        else:
            print("未知指令", op)
        pc = nxt
        if g.buys >= 6:      # 完成两次商人采购即视为验证成功
            trace.append("== 已完成 2 轮商人采购，模拟结束 ==")
            break

    print("\n".join(trace[-30:]))
    print("\n结果: 步骤=%d 战斗=%d 商人行程=%d 购买=%d 当前第%d章 剩余商品=%s"
          % (steps, g.battles, g.trips, g.buys, g.chapter, g.cards))
    ok = g.trips >= 2 and g.buys >= 6 and steps < MAX_STEPS
    print("OK 流程跑通（含 2 次商人采购 + 翻到第七章并回图一）" if ok else "?? 流程可能有问题")


if __name__ == "__main__":
    main()
