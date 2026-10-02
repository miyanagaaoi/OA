/**
 * oa-web · 水印常量与夹紧（REQ-USER-004 / AC-44）
 * ----------------------------------------------------------------------------
 * 来源：
 *   · `DESIGN.md` › Components › Feedback & Overlays › watermark（墨色 5%–8%、-24°、间距 240×160）
 *   · `doc/prd-0.1.md` 6.8 REQ-USER-004 / `doc/test-cases.md` AC-44（内容 = 姓名 + 工号）
 *   · 后端实现：`oa-server` 的 `WatermarkPolicy`
 *     （`MIN_OPACITY_PERCENT=5` / `MAX_OPACITY_PERCENT=8` / `ROTATION_DEGREES=-24`）
 *
 * 为什么把常量单独放一个模块：水印值有两个来源——后端 `GET /api/v1/portal/watermark/profile`
 * （以服务端为准）与本地演示/用户手动调整；**两个来源都必须收敛到同一区间**，
 * 若各自写死一份常量，迟早出现「服务端 0.05、本地 0.06」的不一致。
 * 这里只放**纯函数与常量**，不做任何 IO。
 */

/** 透明度下限 5%（DESIGN.md 规定区间的下沿）。 */
export const WATERMARK_OPACITY_MIN = 0.05

/** 透明度上限 8%（DESIGN.md 规定区间的上沿）。 */
export const WATERMARK_OPACITY_MAX = 0.08

/** 透明度缺省值（取区间中值 6%）。 */
export const WATERMARK_OPACITY_DEFAULT = 0.06

/** 旋转角度（DESIGN.md：-24°，水印为斜排）。 */
export const WATERMARK_ROTATE = -24

/** 横向间距（px，DESIGN.md：240）。 */
export const WATERMARK_GAP_X = 240

/** 纵向间距（px，DESIGN.md：160）。 */
export const WATERMARK_GAP_Y = 160

/**
 * 夹紧水印透明度到 5%–8%。
 *
 * 后端也做同一件事（`WatermarkPolicy#clampOpacity`），前端这一层是**兜底**：
 * 手工调整、旧缓存、演示数据、以及将来可能的新字段都不会画出越界水印。
 * 非有限数（NaN/Infinity）按默认值 6% 处理。
 */
export function clampWatermarkOpacity(value: number): number {
  if (!Number.isFinite(value)) return WATERMARK_OPACITY_DEFAULT
  return Math.min(WATERMARK_OPACITY_MAX, Math.max(WATERMARK_OPACITY_MIN, value))
}
