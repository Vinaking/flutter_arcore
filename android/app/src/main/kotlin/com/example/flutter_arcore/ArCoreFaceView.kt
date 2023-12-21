package com.example.flutter_arcore

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.example.flutter_arcore.utils.ArCoreUtils
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.SceneView
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.FloatBuffer


class ArCoreFaceView(private val activity: Activity, context: Context, id: Int):
    PlatformView, MethodChannel.MethodCallHandler {

    lateinit var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks

    private lateinit var frameView: FrameLayout
    private var arSceneView: ArSceneView? = null
    private lateinit var faceMeshView: FaceMeshView
    private lateinit var objectView: ObjectDetectView

    private val RC_PERMISSIONS = 0x123
    private var installRequested: Boolean = false

    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var faceSceneUpdateListener: Scene.OnUpdateListener

    private lateinit var tfObjectDetetector: ObjectDetector
    private var count = 0

    private var chinPoints = arrayOf(58, 172, 136, 150, 149, 176, 148, 152, 377, 400, 378, 379, 365, 397)
    private var cheeckPoints = arrayOf(132, 93, 234, 127, 162)

    // 150, 149, 176, 148, 152, 377, 400, 378
    // 132, 58, 172

    init {
//        methodChannel.setMethodCallHandler(this)
        if (ArCoreUtils.checkIsSupportedDeviceOrFinish(activity)) {
            frameView = FrameLayout(context)
            arSceneView = ArSceneView(context)
            faceMeshView = FaceMeshView(context)
            objectView = ObjectDetectView(context)
            frameView.addView(arSceneView)
            frameView.addView(faceMeshView)
            frameView.addView(objectView)

            ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            setupLifeCycle(context)
            setupDetector(context)
        }

        val scene = arSceneView?.scene

        faceSceneUpdateListener = Scene.OnUpdateListener { _ ->
            run {
                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)

                faceList?.let {
                    for (face in faceList) {
                        Log.d("ArCoreFaceView", "face: ${face.meshVertices}")
                        setTextureView(scene, face)
                        setFaceMeshView(scene, face)
                    }
                }

                val faceIterator = faceNodeMap.entries.iterator()
                while (faceIterator.hasNext()) {
                    val entry = faceIterator.next()
                    val face = entry.key
                    if (face.trackingState == TrackingState.STOPPED) {
                        val faceNode = entry.value
                        faceNode.setParent(null)
                        faceNode.children.clear()
                        faceIterator.remove()
                    }
                }

                arSceneView?.let {
                    Handler().post{
                        detector(it)
                    }
                }
            }
        }

        arSceneView?.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        arSceneView?.scene?.addOnUpdateListener(faceSceneUpdateListener)
//        loadMesh()
    }

    override fun getView(): View {
        return frameView as View
    }

    // =================================== LIFECYCLE =====================================
    override fun dispose() {}

    private fun onResume() {
        if (arSceneView == null) {
            return
        }

        if (arSceneView?.session == null) {

            // request camera permission if not already requested
            if (!ArCoreUtils.hasCameraPermission(activity)) {
                ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            }

            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session = ArCoreUtils.createArSession(activity, installRequested, true)
                if (session == null) {
                    installRequested = false
                    return
                } else {
                    val config = Config(session)
                    config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    session.configure(config)
                    arSceneView?.setupSession(session)
                }
            } catch (e: UnavailableException) {
                ArCoreUtils.handleSessionException(activity, e)
            }
        }

        try {
            arSceneView?.resume()
        } catch (ex: CameraNotAvailableException) {
            ArCoreUtils.displayError(activity, "Unable to get camera", ex)
            activity.finish()
            return
        }
    }

    private fun onPause() {
        if (arSceneView != null) {
            arSceneView?.pause()
        }
    }

    private fun onDestroy() {
        arSceneView?.scene?.removeOnUpdateListener(faceSceneUpdateListener)
        if (arSceneView != null) {
            arSceneView?.destroy()
            arSceneView = null
        }
    }

    fun removeNode(node: Node) {
        arSceneView?.scene?.removeChild(node)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        TODO("Not yet implemented")
    }

    private fun setupLifeCycle(context: Context) {
        activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.d("ArCoreFaceView", "onActivityCreated")
            }

            override fun onActivityStarted(activity: Activity) {
                Log.d("ArCoreFaceView", "onActivityStarted")
            }

            override fun onActivityResumed(activity: Activity) {
                onResume()
                Log.d("ArCoreFaceView", "onActivityResumed")
            }

            override fun onActivityPaused(activity: Activity) {
                onPause()
                Log.d("ArCoreFaceView", "onActivityPaused")
            }

            override fun onActivityStopped(activity: Activity) {
                onPause()
                Log.d("ArCoreFaceView", "onActivityStopped")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                onDestroy()
            }
        }

        activity.application
            .registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks)
    }

    // =================================== TEXTTURE =====================================

    private fun setTextureView(scene: Scene?, face: AugmentedFace) {
        if (!faceNodeMap.containsKey(face)) {
            val faceNode = AugmentedFaceNode(face)
            faceNode.setParent(scene)

//            ModelRenderable.builder()
//                .setSource(activity, R.raw.fox_face)
//                .build()
//                .thenAccept { modelRenderable ->
//                    faceNode.faceRegionsRenderable = modelRenderable
//                    modelRenderable.isShadowCaster = false
//                    modelRenderable.isShadowReceiver = false
//                }

            //give the face a little blush
            Texture.builder()
                .setSource(activity, R.drawable.blush_texture)
                .build()
                .thenAccept { texture ->
                    faceNode.faceMeshTexture = texture
                }
            faceNodeMap[face] = faceNode
        }
    }

    // =================================== FACEMESHVIEW =====================================

    private fun setFaceMeshView(scene: Scene?, face: AugmentedFace) {
        if (count%10 != 0) return
        val buffer: FloatBuffer = face.meshVertices
        val points = ArrayList<Vector3>()
        val pointsBoundRight = ArrayList<Vector3>()
        val pointsBoundBottom = ArrayList<Vector3>()
        val chinCheeckPoints = ArrayList<Int>()
        chinCheeckPoints.addAll(chinPoints)
        chinCheeckPoints.addAll(cheeckPoints)
        for (i in 0..467) {
            val vector3 = Vector3(
                buffer[i * 3],
                buffer[i * 3 + 1], buffer[i * 3 + 2]
            )
            val node = Node()
            node.localPosition = vector3
            node.setParent(faceNodeMap[face])
            val worldPosition = node.worldPosition
            val point = scene?.camera?.worldToScreenPoint(worldPosition)

            point?.let {
                points.add(point)
//                if (i == 132) {
//                    val pointChin = Vector3(point.x + 50, point.y, point.z)
//                    pointsBoundRight.add(pointChin)
//                }
//
//                if (i == 172) {
//                    val pointChin = Vector3(point.x + 50, point.y, point.z)
//                    pointsBoundRight.add(pointChin)
//                }
//
//                if (i == 136) {
//                    val pointChin = Vector3(point.x, point.y + 200, point.z)
//                    pointsBoundBottom.add(pointChin)
//                }
//
//                if (i == 378) {
//                    val pointChin = Vector3(point.x, point.y + 50, point.z)
//                    pointsBoundBottom.add(pointChin)
//                }
            }
        }

        faceMeshView.setPointsRight(pointsBoundRight)
        faceMeshView.setPointsBottom(pointsBoundBottom)
        faceMeshView.setPoints(points)
    }

    // =================================== DETECT =====================================

    private fun setupDetector(context: Context) {
        val tfoptions = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(2)
                .setScoreThreshold(0.3f)
                .build()
        tfObjectDetetector = ObjectDetector.createFromFileAndOptions(
                context,
                "biosensor_model_v1.1.tflite",
                tfoptions
            )
    }

    private fun detector(sceneView: ArSceneView) {
        count++
        if (count%10 != 0) return
        val frame: Frame = sceneView.arFrame ?: return
        // Copy the camera stream to a bitmap
        try {
            val frameImage = frame.acquireCameraImage()
            frameImage.use { image ->
                if (image.format != ImageFormat.YUV_420_888) {
                    return
                }
                val byteArray = imageToByte(image) ?: return
                val scaledBitmap = scaleBitmap(sceneView, byteArray)
                runObjectDetection(scaledBitmap)
            }
            frameImage.close()
        } catch (e: Exception) {
            Log.e("MainActivity", "Exception copying image", e)
        }
    }

    private fun runObjectDetection(bitmap: Bitmap) {
        val image = TensorImage.fromBitmap(bitmap)

        val results = tfObjectDetetector.detect(image)

        results.map {
            // Get the top-1 category and craft the display text
            val category = it.categories.first()
            val text = "${category.label}, ${category.score.times(100).toInt()}%"
            Log.d("SaladLog", "text $text")

        }
        objectView.setData(results, "text", image.width, image.height)

    }

    private fun scaleBitmap(sceneView: ArSceneView, byteArray: ByteArray): Bitmap {
        val bitmap = byteArray.toBitmap()

        val matrix = Matrix()
        matrix.postRotate(270F)
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)

        val rotatedBitmap = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )

        val w = sceneView.width
        val h = sceneView.height
        val scaledHeight = rotatedBitmap.height * w / rotatedBitmap.width
        val scaledWidth = rotatedBitmap.width * h / rotatedBitmap.height

        return if (scaledHeight < h) Bitmap.createScaledBitmap(rotatedBitmap, w, scaledHeight, true)
            else Bitmap.createScaledBitmap(bitmap, scaledWidth, h, true)

    }

    private fun imageToByte(image: Image): ByteArray? {
        var byteArray: ByteArray? = null
        byteArray = nV21toJPEG(yUV420toNV21(image), image.width, image.height, 100)
        return byteArray
    }

    private fun nV21toJPEG(nv21: ByteArray, width: Int, height: Int, quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), quality, out)
        return out.toByteArray()
    }

    private fun yUV420toNV21(image: Image): ByteArray {
        val nv21: ByteArray
        // Get the three planes.
        val yBuffer: ByteBuffer = image.planes[0].buffer
        val uBuffer: ByteBuffer = image.planes[1].buffer
        val vBuffer: ByteBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        nv21 = ByteArray(ySize + uSize + vSize)

        //U and V are swapped
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        return nv21
    }

    private fun ByteArray.toBitmap(): Bitmap {
        return BitmapFactory.decodeByteArray(this, 0, this.size)
    }
}