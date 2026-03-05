package com.example.usbwebcam

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.widget.AspectRatioSurfaceView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages multiple simultaneous USB UVC webcam connections.
 * Uses the AndroidUSBCamera library (libausbc) by jiangdongguo.
 */
class UsbCameraManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbCameraManager"

        // Default preview resolution — many webcams support 640x480
        const val DEFAULT_WIDTH = 1280
        const val DEFAULT_HEIGHT = 720
    }

    data class CameraInfo(
        val usbDevice: UsbDevice,
        val deviceId: Int,
        val label: String,
        var isConnected: Boolean = false,
        var resolution: String = "--",
        var camera: CameraUVC? = null
    )

    private val _cameras = MutableStateFlow<List<CameraInfo>>(emptyList())
    val cameras: StateFlow<List<CameraInfo>> = _cameras

    private var multiCameraClient: MultiCameraClient? = null
    private val activeCameras = mutableMapOf<Int, CameraUVC>()

    private val cameraStateCallback = object : ICameraStateCallBack {
        override fun onCameraState(
            self: MultiCameraClient.ICamera,
            code: ICameraStateCallBack.State,
            msg: String?
        ) {
            val deviceId = (self as? CameraUVC)?.let {
                activeCameras.entries.find { e -> e.value == it }?.key
            } ?: -1

            Log.d(TAG, "Camera[$deviceId] state: $code, msg: $msg")

            when (code) {
                ICameraStateCallBack.State.OPENED -> {
                    updateCameraState(deviceId, connected = true)
                }
                ICameraStateCallBack.State.CLOSED -> {
                    updateCameraState(deviceId, connected = false)
                }
                ICameraStateCallBack.State.ERROR -> {
                    Log.e(TAG, "Camera[$deviceId] error: $msg")
                    updateCameraState(deviceId, connected = false)
                }
            }
        }
    }

    private fun updateCameraState(deviceId: Int, connected: Boolean) {
        val current = _cameras.value.toMutableList()
        val idx = current.indexOfFirst { it.deviceId == deviceId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(isConnected = connected)
            _cameras.value = current
        }
    }

    /**
     * Scan for connected USB UVC devices and build camera list.
     */
    fun scanDevices(): List<CameraInfo> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList

        val found = mutableListOf<CameraInfo>()
        var index = 1

        for ((_, device) in deviceList) {
            if (isUvcDevice(device)) {
                Log.d(TAG, "Found UVC device: ${device.deviceName} " +
                        "vid=${device.vendorId} pid=${device.productId}")
                found.add(
                    CameraInfo(
                        usbDevice = device,
                        deviceId = device.deviceId,
                        label = "Camera $index — ${device.productName ?: "USB Camera"}"
                    )
                )
                index++
            }
        }

        _cameras.value = found
        return found
    }

    /**
     * Open a specific camera on the given SurfaceView.
     */
    fun openCamera(
        cameraInfo: CameraInfo,
        surfaceView: AspectRatioSurfaceView,
        onPermissionNeeded: (UsbDevice) -> Unit
    ) {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (!usbManager.hasPermission(cameraInfo.usbDevice)) {
            onPermissionNeeded(cameraInfo.usbDevice)
            return
        }

        try {
            val camera = CameraUVC(context, cameraInfo.usbDevice)
            activeCameras[cameraInfo.deviceId] = camera

            val request = CameraRequest.Builder()
                .setFrontCamera(false)
                .setPreviewWidth(DEFAULT_WIDTH)
                .setPreviewHeight(DEFAULT_HEIGHT)
                .create()

            camera.openCamera(surfaceView, cameraStateCallback, request)

            // Update resolution info
            val current = _cameras.value.toMutableList()
            val idx = current.indexOfFirst { it.deviceId == cameraInfo.deviceId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(
                    camera = camera,
                    resolution = "${DEFAULT_WIDTH}x${DEFAULT_HEIGHT}"
                )
                _cameras.value = current
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera ${cameraInfo.deviceId}", e)
        }
    }

    /**
     * Close a specific camera.
     */
    fun closeCamera(deviceId: Int) {
        activeCameras[deviceId]?.closeCamera()
        activeCameras.remove(deviceId)
    }

    /**
     * Close all open cameras.
     */
    fun closeAll() {
        activeCameras.values.forEach { it.closeCamera() }
        activeCameras.clear()
        _cameras.value = emptyList()
    }

    /**
     * Determine if a USB device is a UVC (Video Class) camera.
     * UVC devices have class 0x0E (video) or are composite devices
     * that contain a video interface.
     */
    private fun isUvcDevice(device: UsbDevice): Boolean {
        // Direct video class device
        if (device.deviceClass == 0x0E) return true

        // Check interfaces for video class
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 0x0E) return true
        }

        // Composite devices (class 0xEF) that may contain UVC
        // We include them and let the library handle it
        if (device.deviceClass == 0xEF) {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == 0x0E) return true
            }
        }

        return false
    }
}
