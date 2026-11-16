# 词幕 Logo

「词幕 Lyricon」的符号图标。本目录的每个结论都由**实测**得出，不靠观感判断。

## 当前方案：开口环

```
<circle cx="120" cy="115" r="70" fill="none" stroke="currentColor"
        stroke-width="38" stroke-dasharray="352 88" stroke-dashoffset="44"
        transform="rotate(-64 120 115)"/>
```

笔宽 38 的等宽圆环，去掉 70° 缺口（周长 440 实 + 88 虚），缺口朝右上。

| 实测项 | 结果 |
|:--|:--|
| 面积占比 | 23.2% |
| 外接盒 | 178×169（240 网格） |
| 连通域 | 1（12/16/20/24/32/48/64px 全部为 1） |
| 12–64px 面积波动 | 21.5% – 23.8% |
| 居中偏差 | ≤1.5px |
| 开运算(20px)损失 | 0.5% |

## 文件

| 文件 | 说明 |
|:--|:--|
| `lyricon-symbol.svg` | **符号本体**。透明底单色，填充继承 `currentColor` |
| `lyricon-appicon.svg` | App 图标。品牌蓝渐变底板 + 白色符号，`rx=54` |
| `showcase.html` / `showcase.png` | 配色、尺寸阶梯、图标形态预览 |
| `candidates.png` | 候选对比（含被淘汰方案的淘汰理由） |

## 用法

```html
<!-- 内联，跟随 CSS 颜色 -->
<svg style="color:#3582FF" width="32" height="32"><use href="lyricon-symbol.svg#lyricon"/></svg>

<!-- 当图片引用：默认黑色，可反色 -->
<img src="lyricon-symbol.svg" width="32" height="32">
<img src="lyricon-symbol.svg" width="32" height="32" style="filter:brightness(0) invert(1)">
```

推荐配色：品牌蓝 `#3582FF`（浅底）/ 白 `#FFFFFF`（深底）。

## 淘汰记录（附实测原因）

| 方案 | 淘汰原因 |
|:--|:--|
| 偏心环 | 偏心量 15.6/240，缩到 96px 仅剩 6px，被视觉抹平成等宽环 —— 第一眼读不出特征 |
| 270° 弧 + 实心核 | 弧核间隙 29px，16px 下只剩 1.9px，糊在一起又各自断裂：连通域 2，面积跌到 17.6% |
| 双弧对扣 | 低分与特征都合格且不撞通用语汇，但两弧交叉读起来像蝴蝶结，形态存疑（留作备选） |
| 音符（多轮） | 撞 Apple Music 一类；在符头上挖刻痕属于两个概念拼接 |
| 斜切圆 / 咬口圆 | "圆缺一块"是饼图、月亮、加载、进度的通用语汇 |

## 已知取舍

开口环在低分辨率下最稳、最干净，但**"缺口的环"本身是加载/进度的通用语汇**，独特性中等。这是本轮未解决的问题，需要用户定夺方向。

## 设计规范

- 画布 240×240。
- 品牌色沿用 App 现有 `ic_launcher_background`（`#3582FF`）。
- 图形落在中心 160×160 的 Android 自适应图标安全区内。
