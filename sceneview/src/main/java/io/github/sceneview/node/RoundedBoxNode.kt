package io.github.sceneview.node

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.geometries.RoundedBox

/**
 * Renderable node for a [RoundedBox] geometry.
 */
class RoundedBoxNode private constructor(
    engine: Engine,
    override val geometry: RoundedBox,
    materialInstances: List<MaterialInstance?>,
    primitivesOffsets: List<IntRange> = geometry.primitivesOffsets,
    builderApply: RenderableManager.Builder.() -> Unit = {}
) : GeometryNode(
    engine = engine,
    geometry = geometry,
    materialInstances = materialInstances,
    primitivesOffsets = primitivesOffsets,
    builderApply = builderApply
) {

    constructor(
        engine: Engine,
        geometry: RoundedBox,
        materialInstance: MaterialInstance? = null,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = geometry,
        materialInstances = listOf(materialInstance),
        primitivesOffsets = listOf(0..geometry.primitivesOffsets.last().last),
        builderApply = builderApply
    )

    constructor(
        engine: Engine,
        width: Float = RoundedBox.DEFAULT_WIDTH,
        depth: Float = RoundedBox.DEFAULT_DEPTH,
        height: Float = RoundedBox.DEFAULT_HEIGHT,
        cornerRadius: Float = RoundedBox.DEFAULT_CORNER_RADIUS,
        segmentCount: Int = RoundedBox.DEFAULT_SEGMENT_COUNT,
        materialInstances: List<MaterialInstance?>,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = RoundedBox.Builder()
            .width(width)
            .depth(depth)
            .height(height)
            .cornerRadius(cornerRadius)
            .segmentCount(segmentCount)
            .build(engine),
        materialInstances = materialInstances,
        builderApply = builderApply
    )

    constructor(
        engine: Engine,
        width: Float = RoundedBox.DEFAULT_WIDTH,
        depth: Float = RoundedBox.DEFAULT_DEPTH,
        height: Float = RoundedBox.DEFAULT_HEIGHT,
        cornerRadius: Float = RoundedBox.DEFAULT_CORNER_RADIUS,
        segmentCount: Int = RoundedBox.DEFAULT_SEGMENT_COUNT,
        materialInstance: MaterialInstance? = null,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = RoundedBox.Builder()
            .width(width)
            .depth(depth)
            .height(height)
            .cornerRadius(cornerRadius)
            .segmentCount(segmentCount)
            .build(engine),
        materialInstance = materialInstance,
        builderApply = builderApply
    )

    /**
     * Updates the rounded-box geometry and pushes the new buffers to Filament.
     *
     * This method supports two paths:
     *
     * 1) **Fast path (no topology change)** – When only size parameters change
     *    (`width`, `depth`, `height`, `cornerRadius`) and `segmentCount` stays the same,
     *    we call `geometry.update(...)` which updates vertex/index data in place and
     *    preserves the current primitive layout and material bindings.
     *
     * 2) **Topology change (segmentCount changed)** – Changing `segmentCount` alters the
     *    primitive layout (triangle count / offsets). After `geometry.update(...)`, we must
     *    call `setGeometry(geometry)` so Filament rebuilds the renderable. This drops any
     *    prior material bindings, so we snapshot and re-bind materials (here we reuse the
     *    first currently-bound `MaterialInstance`, spreading it across all primitives).
     *
     * This design avoids “disappearing” meshes during animation when the primitive layout
     * changes, while keeping size-only updates fast and stable.
     *
     * @param width         New box width. Defaults to current `geometry.width`.
     * @param depth         New box depth. Defaults to current `geometry.depth`.
     * @param height        New box height. Defaults to current `geometry.height`.
     * @param cornerRadius  Corner radius in world units. Clamped by the geometry builder.
     *                      Defaults to current `geometry.cornerRadius`.
     * @param segmentCount  Arc tessellation per corner. Increasing changes topology and will
     *                      trigger `setGeometry(geometry)` with material re-binding.
     *
     * @see io.github.sceneview.geometries.RoundedBox.update
     * @see com.google.android.filament.RenderableManager.setGeometry
     */
    fun updateGeometry(
        width: Float = geometry.width,
        depth: Float = geometry.depth,
        height: Float = geometry.height,
        cornerRadius: Float = geometry.cornerRadius,
        segmentCount: Int = geometry.segmentCount
    ) {
        val topologyChanges = (segmentCount != geometry.segmentCount)

        // Always update the CPU data / GPU buffers first
        geometry.update(
            engine = engine,
            width = width,
            depth = depth,
            height = height,
            cornerRadius = cornerRadius,
            segmentCount = segmentCount
        )

        if (!topologyChanges) {
            // No primitive-count change -> keep existing renderable/materials
            return
        }

        // Topology changed: Filament primitive count will change after setGeometry(...).
        // Preserve current material(s) and re-bind them across *all* primitives.
        // If you used a single MI originally, bind it to every new primitive.

        // Prefer the currently bound first MI (if any)
        val currMI = runCatching { materialInstance }.getOrNull()

        // Update the renderable primitive layout
        setGeometry(geometry)

        // If you had a single material, spread it across all primitives
        if (currMI != null) {
            setMaterialInstances(currMI) // binds to [0 until primitiveCount]
        } else {
            // Fallback: if you had distinct per-primitive MIs and you keep track of them,
            // reapply that list here. Otherwise at least ensure something is bound:
            // materialInstances = List(primitiveCount) { someDefaultMI }
        }
    }
}
