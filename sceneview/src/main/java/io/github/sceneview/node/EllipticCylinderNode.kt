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
     */
    fun updateGeometry(
        width: Float = geometry.width,
        depth: Float = geometry.depth,
        height: Float = geometry.height,
        center: Position = geometry.center,
        sideCount: Int = geometry.sideCount
    ) = setGeometry(
        geometry.update(
            engine,
            width = width,
            depth = depth,
            height = height,
            center = center,
            sideCount = sideCount
        )
    )
}
