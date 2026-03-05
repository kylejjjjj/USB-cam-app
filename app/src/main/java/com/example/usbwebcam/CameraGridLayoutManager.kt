package com.example.usbwebcam

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager

/**
 * Auto-calculates the optimal grid span count based on camera count.
 *
 * Layout logic:
 *  1 camera  → 1 column (full screen)
 *  2 cameras → 2 columns (side by side)
 *  3-4 cameras → 2 columns, 2 rows
 *  5-6 cameras → 3 columns
 *  7+ cameras  → 3 columns (scrollable)
 */
class CameraGridLayoutManager(
    context: Context,
    private var cameraCount: Int
) : GridLayoutManager(context, spanForCount(cameraCount)) {

    companion object {
        fun spanForCount(count: Int): Int = when {
            count <= 1 -> 1
            count <= 2 -> 2
            count <= 6 -> 2
            else -> 3
        }
    }

    fun updateCameraCount(count: Int) {
        cameraCount = count
        spanCount = spanForCount(count)
    }
}
