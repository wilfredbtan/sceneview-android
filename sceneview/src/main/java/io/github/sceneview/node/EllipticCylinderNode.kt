package io.github.sceneview.node

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import io.github.sceneview.geometries.EllipticCylinder
import io.github.sceneview.math.Position

/**
 * Renderable node for an [EllipticCylinder] geometry.
 */
class EllipticCylinderNode private constructor(
    engine: Engine,
    override val geometry: EllipticCylinder,
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
        geometry: EllipticCylinder,
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
        width: Float = EllipticCylinder.DEFAULT_WIDTH,
        depth: Float = EllipticCylinder.DEFAULT_DEPTH,
        height: Float = EllipticCylinder.DEFAULT_HEIGHT,
        center: Position = EllipticCylinder.DEFAULT_CENTER,
        sideCount: Int = EllipticCylinder.DEFAULT_SIDE_COUNT,
        materialInstances: List<MaterialInstance?>,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = EllipticCylinder.Builder()
            .width(width)
            .depth(depth)
            .height(height)
            .center(center)
            .sideCount(sideCount)
            .build(engine),
        materialInstances = materialInstances,
        builderApply = builderApply
    )

    constructor(
        engine: Engine,
        width: Float = EllipticCylinder.DEFAULT_WIDTH,
        depth: Float = EllipticCylinder.DEFAULT_DEPTH,
        height: Float = EllipticCylinder.DEFAULT_HEIGHT,
        center: Position = EllipticCylinder.DEFAULT_CENTER,
        sideCount: Int = EllipticCylinder.DEFAULT_SIDE_COUNT,
        materialInstance: MaterialInstance? = null,
        builderApply: RenderableManager.Builder.() -> Unit = {}
    ) : this(
        engine = engine,
        geometry = EllipticCylinder.Builder()
            .width(width)
            .depth(depth)
            .height(height)
            .center(center)
            .sideCount(sideCount)
            .build(engine),
        materialInstance = materialInstance,
        builderApply = builderApply
    )


    /**
     * Update the cylinder parameters and push new buffers to Filament.
     *
     * - If only size/center change, we just `geometry.update(...)` (no topology change) — fast path.
     * - If `sideCount` changes (topology change), we call `setGeometry(geometry)` and
     *   re-bind materials across the new primitive layout so the mesh doesn't "disappear".
     */
    fun updateGeometry(
        width: Float = geometry.width,
        depth: Float = geometry.depth,
        height: Float = geometry.height,
        center: Position = geometry.center,
        sideCount: Int = geometry.sideCount
    ) {
        val topologyChanges = (sideCount != geometry.sideCount)

        // Update CPU data / GPU buffers (preserves primitives if topology unchanged)
        geometry.update(
            engine = engine,
            width = width,
            depth = depth,
            height = height,
            center = center,
            sideCount = sideCount
        )

        if (!topologyChanges) {
            // No primitive-count change -> the currently bound materials remain valid
            return
        }

        // --- Topology changed: primitives will be recreated by setGeometry(geometry).
        // Snapshot currently bound material instances (if any) to reapply afterward.
        val rm = engine.renderableManager
        val instBefore = rm.getInstance(entity)
        val oldPrimCount = if (instBefore != 0) rm.getPrimitiveCount(instBefore) else 0
        val oldMIs: List<MaterialInstance> =
            if (oldPrimCount > 0) List(oldPrimCount) { i ->
                rm.getMaterialInstanceAt(
                    instBefore,
                    i
                )
            }
            else emptyList()

        // Apply the new geometry layout
        setGeometry(geometry)

        // Re-bind materials to all new primitives so nothing renders invisible.
        val instAfter = rm.getInstance(entity)
        val newPrimCount = if (instAfter != 0) rm.getPrimitiveCount(instAfter) else 0
        if (newPrimCount > 0 && oldMIs.isNotEmpty()) {
            when {
                // If you had a single material, spread it across all primitives.
                oldMIs.size == 1 -> setMaterialInstances(oldMIs[0])

                // If counts match, re-apply one-for-one.
                oldMIs.size == newPrimCount -> {
                    oldMIs.forEachIndexed { i, mi -> setMaterialInstanceAt(i, mi) }
                }

                // Fallback: spread the first MI across all primitives.
                else -> setMaterialInstances(oldMIs[0])
            }
        }
    }
}
