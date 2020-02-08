package com.example.simple_arcompass

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.animation.ModelAnimator
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.abs

const val MY_PERMISSIONS_REQUEST_CAMERA = 3
const val CHANNEL_ID = "somexxx"

class MainActivity : AppCompatActivity(), SensorEventListener {

    var arFragment: ArFragment? = null
    var pointerRenderable: ModelRenderable? = null
    private lateinit var sensorManager: SensorManager

    //Size is multiples of 3 as data is received for (X, Y, Z) values
    private var magnetoData = FloatArray(3)
    private val acceleroData = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val anglesOrientation = FloatArray(3)
    private var currentAngle: Double? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val buttonAdd = btn_add_pointer
        val buttonAngle = btn_angle
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        createNotificationChannel()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                MY_PERMISSIONS_REQUEST_CAMERA
            )
        }

        buttonAdd.setOnClickListener {
            placeModel()
        }

        buttonAngle.setOnClickListener {
            currentAngle = Math.toDegrees(anglesOrientation[0].toDouble())
            val x = currentAngle
            Snackbar.make(
                coordinator,
                "Azimuth is $x",
                Snackbar.LENGTH_SHORT
            ).show()
            Log.d("x", x.toString())
        }

        arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as ArFragment

        val modelUri = Uri.parse("3.sfb")

        ModelRenderable.builder().setSource(this, modelUri)
            .build()
            .thenAccept { renderable ->
                pointerRenderable = renderable
            }


    }

    override fun onResume() {
        super.onResume()

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { mField ->
            sensorManager.registerListener(
                this,
                mField,
                SensorManager.SENSOR_DELAY_NORMAL,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    private fun placeModel() {
        val frame = arFragment?.arSceneView?.arFrame

        val point = getViewCenter()

        val hits: MutableList<HitResult>
        if (currentAngle == null) {
            Snackbar.make(
                coordinator,
                "No direction saved",
                Snackbar.LENGTH_SHORT
            ).show()
            val notif = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle("Compass").setContentText("Placed new compass pointer").setPriority(NotificationCompat.PRIORITY_HIGH).build()
            with(NotificationManagerCompat.from(this)) {
                notify(0, notif)
            }

        } else if (frame != null && pointerRenderable != null) {
            hits = frame.hitTest(point.x.toFloat(), point.y.toFloat())
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable is Plane) {
                    val anchor = hit.createAnchor()
                    val anchorNode = AnchorNode(anchor)
                    anchorNode.setParent(arFragment?.arSceneView?.scene)
                    val node = TransformableNode(arFragment?.transformationSystem)
                    node.scaleController.minScale = 0.2f
                    node.scaleController.maxScale = 0.3f
                    node.localPosition = Vector3(0.0f, 0.7f, 0.0f)
                    var a = (currentAngle)
                    if (a != null) {
                        Log.d("x is ======", a.toString())
                        if(a < 0) {
                            val b = abs(a)
                            node.localRotation = Quaternion.eulerAngles(Vector3(-90.0f, b.toFloat(), 0.0f))
                        }
                        else {
                            node.localRotation = Quaternion.eulerAngles(Vector3(-90.0f, -a.toFloat(), 0.0f))
                        }

                    }

                    node.setParent(anchorNode)
                    node.renderable = pointerRenderable
                    node.select()

                    break
                }
            }
            currentAngle = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "channel name"
            val descriptionText = "channel description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getViewCenter(): android.graphics.Point {
        val view = findViewById<View>(android.R.id.content)
        return android.graphics.Point(view.width / 2, view.height / 2)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("SENSOR", "accuracy changed")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            event.values.copyInto(acceleroData, 0, 0, acceleroData.size)
            //Log.d("event values accel", event.values.toString())
            //Log.d("acceldata", acceleroData.toString())
        } else {
            event.values.copyInto(magnetoData, 0, 0, magnetoData.size)
            //Log.d("event values magne", event.values[0].toString())
            //Log.d("magnedata", magnetoData[0].toString())
        }
        updateOrientation()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    fun updateOrientation() {
        SensorManager.getRotationMatrix(rotationMatrix, null, acceleroData, magnetoData)
        SensorManager.getOrientation(rotationMatrix, anglesOrientation)
        //Log.d("rotation matrix", rotationMatrix.contentToString())
        //Log.d("azimuth", Math.toDegrees(anglesOrientation[0].toDouble()).toString())
        //Log.d("pitch", anglesOrientation[1].toString())
        //Log.d("roll", anglesOrientation[2].toString())
    }
}

