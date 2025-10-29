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
     * Update the rounded box parameters and push new buffers to Filament.
     */
    fun updateGeometry(
        width: Float = geometry.width,
        depth: Float = geometry.depth,
        height: Float = geometry.height,
        cornerRadius: Float = geometry.cornerRadius,
        segmentCount: Int = geometry.segmentCount
    ) = setGeometry(
        geometry.update(
            engine,
            width = width,
            depth = depth,
            height = height,
            cornerRadius = cornerRadius,
            segmentCount = segmentCount
        )
    )
}
