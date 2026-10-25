# PVF Studio 图标

使用 Codex 内置 image_gen 生成原图；色彩沿用编辑器现有的深灰、薄荷绿。折角档案代表 PVF 配置，门洞代表副本预览，外轮廓隐含 P。外部圆角透明，图标内底板为不透明深灰。

源文件：`pvf-studio-icon.png`。PNG → 多尺寸 ICO、PNG 封装 SVG 均为确定性的尺寸与格式转换，没有重绘图案。执行 `python tools/build-app-icons.py` 可从原图重新生成运行资源和 ICO，需要 Pillow。

## 导出与接入

- 原图：`assets/branding/pvf-studio-icon.png`，1254×1254 RGBA；256 像素预览同目录保存。
- Windows ICO：`tools/windows/launcher/PVFStudio.ico`，包含 16、20、24、32、40、48、64、128、256 像素九种尺寸。
- 平台资源：`pvf-product/src/main/resources/icons/app/`；SVG 内嵌同一图案的 PNG，供平台产品信息和窗口/任务栏加载。
- 启动器和安装器内嵌 ICO；文件树原有的类型图标继续保留。

图标从 0.11.1 起用于正式发行，产品目录为 `pvf-product/build/product/PVFStudio-0.11.1/`，下载入口见 [README](../../README.md#release-下载)。最初的图标接入检查保留在 `build/product-checks/20261025-233010-e835e485/`，包含产品身份、图标解码及完整冒烟；原生 EXE 和安装器也完成 Windows 图标提取检查。

0.11.1 正式产品检查目录为 `build/product-checks/20261025-234206-c1edd44b/`。发行包的原生 EXE 与安装器均为 0.11.1，提取出的 32 像素图标一致，并与 ICO 中的不透明像素逐一核对通过。

## 最终生成提示词

```text
Use case: logo-brand.
Asset type: final Windows desktop application icon for PVF Studio, a professional editor for game resource archives, scripts and dungeon maps.
Create one original, polished, square application icon, not a presentation sheet or mockup. The product's existing UI is dark charcoal with mint-green archive icons. Build a strong, simple central mint/emerald emblem that fuses a folded-corner archive document with an open dungeon doorway; the negative space may subtly suggest the letter P. The emblem should feel like a precision editing tool with a light fantasy-game connection, not a generic folder or game character.
Style: refined flat geometric icon with restrained depth and subtle material gradients, crisp intentional edges, large solid shapes, generous negative space, excellent silhouette. Set the emblem on a deep charcoal rounded-square tile with soft corners. Use mint green and emerald as the main accent, with at most one very small warm amber detail if it improves the design. Keep the result clean and recognizable at 16, 32 and 48 pixels.
Composition: centered, nearly fills the square canvas with about 6% transparent exterior padding. The rounded tile and emblem must be part of the icon itself, and all space outside the rounded tile must be genuinely transparent, including the corners. No external drop shadow extending far from the tile.
Constraints: a single icon only; no text, no wordmark, no captions, no letter strings, no watermark, no official DNF logos, no weapons, no ornate decoration, no tiny linework, no glossy 3D render, no perspective view. Use a large square high-resolution canvas suitable as a 1024px icon master.
```
