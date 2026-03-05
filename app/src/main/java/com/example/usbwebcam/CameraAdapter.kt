package com.example.usbwebcam

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.usbwebcam.databinding.ItemCameraBinding

/**
 * RecyclerView adapter displaying each USB camera in a grid cell.
 */
class CameraAdapter(
    private var cameras: List<UsbCameraManager.CameraInfo>,
    private val onCameraReady: (UsbCameraManager.CameraInfo, ItemCameraBinding) -> Unit,
    private val onRetry: (UsbCameraManager.CameraInfo) -> Unit
) : RecyclerView.Adapter<CameraAdapter.CameraViewHolder>() {

    inner class CameraViewHolder(val binding: ItemCameraBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraViewHolder {
        val binding = ItemCameraBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CameraViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CameraViewHolder, position: Int) {
        val camera = cameras[position]
        with(holder.binding) {
            cameraLabel.text = camera.label
            resolutionLabel.text = camera.resolution

            // Status indicator color
            statusIndicator.setBackgroundResource(
                if (camera.isConnected) R.drawable.status_indicator
                else R.drawable.status_indicator_off
            )

            // Show error overlay if not connected after initial setup
            errorOverlay.visibility = View.GONE

            btnRetry.setOnClickListener { onRetry(camera) }

            // Notify host to attach camera to this surface
            onCameraReady(camera, this)
        }
    }

    override fun getItemCount() = cameras.size

    fun updateCameras(newCameras: List<UsbCameraManager.CameraInfo>) {
        cameras = newCameras
        notifyDataSetChanged()
    }

    fun updateCameraState(deviceId: Int, connected: Boolean, resolution: String = "--") {
        val index = cameras.indexOfFirst { it.deviceId == deviceId }
        if (index >= 0) notifyItemChanged(index)
    }
}
