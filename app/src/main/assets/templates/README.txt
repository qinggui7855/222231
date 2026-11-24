这些模板是用 tools/make_templates.py 从 ref/ 里的参考截图自动裁出来的，
不要手动改文件名，除非同时改 program.json。

当前模板（都是「2509px 宽」参考分辨率下的像素尺寸，运行时按你的手机分辨率自动缩放）：

  stage_play.png            258x244   图一 右下角大箭头（开始战斗）
  back_arrow.png            139x118   左上角返回箭头（图一/图三/图七/图九都有）
  result_refresh.png        244x224   图二 右上角循环图标
  merchant_banner.png       870x205   图三 商人到访横幅（干净状态）
  merchant_banner_edge.png  119x192   图四 横幅右端（被 toast 挡住时也能认）
  merchant_fig.png          171x141   图六 主城里的商人小人
  campaign_btn.png          213x61    图五 战役按钮
  shop_dialog_ok.png        264x124   图八 确定按钮
  shop_open.png             276x224   图七 商店左上角头像（判断"是否在商店里"）
  chapter_left.png           90x116    章节地图左翻页箭头（只裁三角形，跨章节都能认）
  chapter_right.png         103x116    章节地图右翻页箭头
  chapter7_node.png         211x264    第七章地图右下角的关卡节点（含三颗星）
  sell_dialog_ok.png        158x86     「是否将无用物品卖给商人」弹窗的 好的 按钮

重新生成：python tools/make_templates.py
离线自测：python tools/selftest_match.py   （会把每个模板在所有参考截图上跑一遍）
