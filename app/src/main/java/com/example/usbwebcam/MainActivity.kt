package com.example.usbwebcam

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.usbwebcam.databinding.ActivityMainBinding
import com.example.usbwebcam.databinding.ItemCameraBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val ACTION_USB_PERMISSION = "com.example.usbwebcam.USB_PERMISSION"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: UsbCameraManager
    private lateinit var cameraAdapter: CameraAdapter
    private lateinit var gridLayoutManager: CameraGridLayoutManager

    // Track which camera is pending permission approval
    private var pendingPermissionCamera: UsbCameraManager.CameraInfo? = null

    // Map from deviceId → the binding that holds its SurfaceView
    private val surfaceBindings = mutableMapOf<Int, ItemCameraBinding>()

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.d(TAG, "USB permission result: granted=$granted, device=${device?.deviceName}")

                if (granted && device != null) {
                    // Re-open the camera that was waiting for permission
                    val cam = pendingPermissionCamera
                    if (cam != null && cam.usbDevice.deviceId == device.deviceId) {
                        val surfaceBinding = surfaceBindings[cam.deviceId]
                        if (surfaceBinding != null) {
                            openCameraOnSurface(cam, surfaceBinding)
                        }
                    }
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.permission_denied),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                pendingPermissionCamera = null
            }
        }
    }

    private val usbDetachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                device?.let {
                    Log.d(TAG, "USB device detached: ${it.deviceName}")
                    cameraManager.closeCamera(it.deviceId)
                    surfaceBindings.remove(it.deviceId)
                    refreshCameraList()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraManager = UsbCameraManager(this)
        setupRecyclerView()
        setupButtons()
        registerReceivers()
        observeCameras()

        // Initial scan
        refreshCameraList()
    }

    private fun setupRecyclerView() {
        gridLayoutManager = CameraGridLayoutManager(this, 0)

        cameraAdapter = CameraAdapter(
            cameras = emptyList(),
            onCameraReady = { cameraInfo, itemBinding ->
                surfaceBindings[cameraInfo.deviceId] = itemBinding
                openCameraOnSurface(cameraInfo, itemBinding)
            },
            onRetry = { cameraInfo ->
                val binding = surfaceBindings[cameraInfo.deviceId]
                if (binding != null) {
                    openCameraOnSurface(cameraInfo, binding)
                }
            }
        )

        binding.cameraGrid.apply {
            layoutManager = gridLayoutManager
            adapter = cameraAdapter
            // Prevent RecyclerView from recycling camera views to avoid SurfaceView issues
            recycledViewPool.setMaxRecycledViews(0, 0)
        }
    }

    private fun setupButtons() {
        binding.btnRefresh.setOnClickListener {
            binding.statusText.text = getString(R.string.scanning)
            surfaceBindings.clear()
            cameraManager.closeAll()
            refreshCameraList()
        }

        binding.btnLayout.setOnClickListener {
            // Toggle between grid and stacked layout
            val current = gridLayoutManager.spanCount
            gridLayoutManager.spanCount = if (current == 1) {
                CameraGridLayoutManager.spanForCount(cameraAdapter.itemCount)
            } else 1
            binding.btnLayout.text = if (gridLayoutManager.spanCount == 1) "Stack" else "Grid"
            cameraAdapter.notifyDataSetChanged()
        }
    }

    private fun registerReceivers() {
        val permFilter = IntentFilter(ACTION_USB_PERMISSION)
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        val attachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, permFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(usbDetachReceiver, detachFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, permFilter)
            registerReceiver(usbDetachReceiver, detachFilter)
        }
    }

    private fun observeCameras() {
        lifecycleScope.launch {
            cameraManager.cameras.collectLatest { cameras ->
                runOnUiThread {
                    val hasCameras = cameras.isNotEmpty()
                    binding.emptyState.visibility = if (hasCameras) View.GONE else View.VISIBLE
                    binding.cameraGrid.visibility = if (hasCameras) View.VISIBLE else View.GONE

                    val connectedCount = cameras.count { it.isConnected }
                    binding.statusText.text = if (hasCameras) {
                        "${cameras.size} camera(s) found · $connectedCount streaming"
                    } else {
                        getString(R.string.no_cameras)
                    }

                    gridLayoutManager.updateCameraCount(cameras.size)
                    cameraAdapter.updateCameras(cameras)
                }
            }
        }
    }

    private fun refreshCameraList() {
        val cameras = cameraManager.scanDevices()
        Log.d(TAG, "Found ${cameras.size} cameras")
    }

    private fun openCameraOnSurface(
        cameraInfo: UsbCameraManager.CameraInfo,
        itemBinding: ItemCameraBinding
    ) {
        cameraManager.openCamera(
            cameraInfo = cameraInfo,
            surfaceView = itemBinding.cameraPreview,
            onPermissionNeeded = { device ->
                requestUsbPermission(device, cameraInfo)
            }
        )
    }

    private fun requestUsbPermission(device: UsbDevice, cameraInfo: UsbCameraManager.CameraInfo) {
        pendingPermissionCamera = cameraInfo
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else 0
        val permissionIntent = PendingIntent.getBroadcast(
            this, device.deviceId,
            Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(device, permissionIntent)
        Log.d(TAG, "Requested permission for ${device.deviceName}")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraManager.closeAll()
        try {
            unregisterReceiver(usbPermissionReceiver)
            unregisterReceiver(usbDetachReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Receiver already unregistered", e)
        }
    }
}
