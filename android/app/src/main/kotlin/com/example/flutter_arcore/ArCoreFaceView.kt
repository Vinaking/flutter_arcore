package com.example.flutter_arcore

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import com.example.flutter_arcore.utils.ArCoreUtils
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import java.io.File

class ArCoreFaceView(private val activity: Activity, context: Context, id: Int):
    PlatformView, MethodChannel.MethodCallHandler {

    lateinit var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks

    private var arSceneView: ArSceneView? = null
    private val RC_PERMISSIONS = 0x123
    private var installRequested: Boolean = false

    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var faceSceneUpdateListener: Scene.OnUpdateListener

    init {
//        methodChannel.setMethodCallHandler(this)
        if (ArCoreUtils.checkIsSupportedDeviceOrFinish(activity)) {
            arSceneView = ArSceneView(context)
            ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            setupLifeCycle(context)
        }

        val scene = arSceneView?.scene

        faceSceneUpdateListener = Scene.OnUpdateListener { frameTime ->
            run {
                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)

                faceList?.let {
                    for (face in faceList) {
                        Log.d("ArCoreFaceView", "face: ${face.meshVertices}")
                        if (!faceNodeMap.containsKey(face)) {
                            val faceNode = AugmentedFaceNode(face)
                            faceNode.setParent(scene)

                            ModelRenderable.builder()
                                .setSource(activity, R.raw.fox_face)
                                .build()
                                .thenAccept { modelRenderable ->
                                    faceNode.faceRegionsRenderable = modelRenderable
                                    modelRenderable.isShadowCaster = false
                                    modelRenderable.isShadowReceiver = false
                                }

                            //give the face a little blush
                            Texture.builder()
                                .setSource(activity, R.drawable.blush_texture_1)
                                .build()
                                .thenAccept { texture ->
                                    faceNode.faceMeshTexture = texture
                                }
                            faceNodeMap[face] = faceNode
                        }
                    }
                }

            }
        }

        arSceneView?.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        arSceneView?.scene?.addOnUpdateListener(faceSceneUpdateListener)

//        loadMesh()
    }

    override fun getView(): View {
        return arSceneView as View
    }

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
//                onDestroy()
            }
        }

        activity.application
            .registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks)
    }

}