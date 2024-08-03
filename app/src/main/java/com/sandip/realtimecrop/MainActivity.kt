package com.sandip.realtimecrop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.sandip.realtimecrop.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            startCamera()
        }

        findViewById<ImageView>(R.id.captureImgView).setOnClickListener {
            takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {

            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.createSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder().build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//            val cameraSelector = CameraSelector.Builder()
//                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
//                .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the
        // modifiable image capture use case
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)

                    val msg = "Photo captured!"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                    Log.d(TAG, msg)

                    // Get rectangle coordinates
                    val drawView = findViewById<DrawView>(R.id.drawView)
                    val rect = drawView.getRectangle()

                    // Correct the image orientation
                    val correctedBitmap = correctImageOrientation(image, bitmap)

                    val croppedBitmap : Bitmap
                    if (rect.top != 0 && rect.left != 0 && rect.width() != 0 && rect.height() != 0) {
                        // Crop the bitmap based on the rectangle
                        val adjustedRect = adjustRect(rect, correctedBitmap.width, correctedBitmap.height)
                        croppedBitmap = Bitmap.createBitmap(
                            correctedBitmap,
                            adjustedRect.left,
                            adjustedRect.top,
                            adjustedRect.width(),
                            adjustedRect.height()
                        )
                    } else {
                        // If no rectangle is selected, use the original bitmap
                        croppedBitmap = correctedBitmap
                    }

                    // Set the bitmap in BitmapHolder
                    BitmapHolder.bitmap = croppedBitmap

                    // Start the next activity and pass the URI
                    val intent = Intent(this@MainActivity, DisplayResultActivity::class.java)
                    startActivity(intent)

                    image.close()
                }
            })
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun adjustRect(rect: Rect, bitmapWidth: Int, bitmapHeight: Int): Rect {
        val scaleX = bitmapWidth.toFloat() / binding.previewView.width
        val scaleY = bitmapHeight.toFloat() / binding.previewView.height

        val adjustedLeft = (rect.left * scaleX).toInt()
        val adjustedTop = (rect.top * scaleY).toInt()
        val adjustedRight = (rect.right * scaleX).toInt()
        val adjustedBottom = (rect.bottom * scaleY).toInt()

        return Rect(adjustedLeft, adjustedTop, adjustedRight, adjustedBottom)
    }

    private fun correctImageOrientation(image: ImageProxy, bitmap: Bitmap): Bitmap {
        val rotationDegrees = image.imageInfo.rotationDegrees
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }


    companion object {
        private const val TAG = "RealTimeCrop"
        private const val REQUEST_CODE_PERMISSIONS = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}