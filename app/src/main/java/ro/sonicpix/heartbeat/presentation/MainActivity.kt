package ro.sonicpix.heartbeat.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.robsonmartins.androidmidisynth.SynthManager
import jp.kshoji.blemidi.device.MidiInputDevice
import jp.kshoji.blemidi.device.MidiOutputDevice
import jp.kshoji.blemidi.listener.OnMidiDeviceAttachedListener
import jp.kshoji.blemidi.listener.OnMidiDeviceDetachedListener
import jp.kshoji.blemidi.peripheral.BleMidiPeripheralProvider
import ro.sonicpix.heartbeat.R
import ro.sonicpix.heartbeat.presentation.theme.HeartBeatTheme


const val debugTag = "HeartBeatz"

val song: Array<IntArray> = arrayOf(
    intArrayOf(60, 60), // C, C (Twinkle, twinkle)
    intArrayOf(67, 67), // G, G (little star)
    intArrayOf(69, 69), // A, A (How I wonder)
    intArrayOf(67),     // G, (what you are)
    intArrayOf(65, 65), // F, F (Up above the)
    intArrayOf(64, 64), // E, E (world so high)
    intArrayOf(62, 62), // D, D (Like a diamond)
    intArrayOf(60)      // C, (in the sky)
)

var currentSongIndex = 0

val permissions = mapOf(
    Manifest.permission.BLUETOOTH to "Bluetooth",
    Manifest.permission.BLUETOOTH_ADMIN to "Bluetooth Admin",
    Manifest.permission.BLUETOOTH_SCAN to "Bluetooth Scan",
    Manifest.permission.BLUETOOTH_CONNECT to "Bluetooth Connect",
    Manifest.permission.BLUETOOTH_ADVERTISE to "Bluetooth Advertise",
    Manifest.permission.BODY_SENSORS to "Sensors",
    Manifest.permission.ACCESS_COARSE_LOCATION to "Location",
    Manifest.permission.ACCESS_FINE_LOCATION to "Fine Location",
)

class MainActivity : ComponentActivity() {

    companion object {
        init { System.loadLibrary("synth-lib") }
    }

    private lateinit var synthManager: SynthManager
    private val handler = Handler(Looper.getMainLooper())
    private var runnable: Runnable? = null
    private var bluetoothStarted = false

    private lateinit var sensorManager: SensorManager
    private lateinit var heartRateSensorListener: HeartRateSensorListener

    private var heartBeatIntervalMs = 1000L

    private var mainText by mutableStateOf(".")

    private lateinit var bleMidiPeripheralProvider: BleMidiPeripheralProvider
    private var midiOutputDevice: MidiOutputDevice? = null


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(debugTag, "onCreate")
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensorListener = HeartRateSensorListener()
        bleMidiPeripheralProvider = BleMidiPeripheralProvider(this)
        bleMidiPeripheralProvider.setAutoStartDevice(true)
        bleMidiPeripheralProvider.setManufacturer(resources.getString(R.string.app_name))
        bleMidiPeripheralProvider.setDeviceName(resources.getString(R.string.app_name))

        synthManager = SynthManager(this)
        synthManager.loadSF("gm.sf2")
        synthManager.setVolume(0,127)
        synthManager.fluidsynthProgramChange(1, 24)

        setContent {
            MainScreen(mainText = mainText)
            if (!areAllPermissionsGranted()) {
                RequestPermissions {
                    this@MainActivity.startBluetoothIfAllPermissionsAreGranted()
                }
            }
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(
                this@MainActivity,
                permission.key
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun stopBluetooth() {
        bluetoothStarted = false
        bleMidiPeripheralProvider.setOnMidiDeviceAttachedListener(null)
        bleMidiPeripheralProvider.setOnMidiDeviceDetachedListener(null)
        bleMidiPeripheralProvider.stopAdvertising()
    }

    private fun startBluetoothIfAllPermissionsAreGranted() {
        if (!bluetoothStarted && areAllPermissionsGranted()) {
            bleMidiPeripheralProvider.startAdvertising()
            val detachedListener = object :
                OnMidiDeviceDetachedListener {
                override fun onMidiInputDeviceDetached(midiInputDevice: MidiInputDevice) {
                }

                override fun onMidiOutputDeviceDetached(midiOutputDevice: MidiOutputDevice) {
                    Log.d(
                        debugTag,
                        "Midi ${midiOutputDevice.deviceName} Output Detached"
                    )
                    this@MainActivity.midiOutputDevice = null
                }
            }
            val attachedListener = object :
                OnMidiDeviceAttachedListener {
                override fun onMidiInputDeviceAttached(midiInputDevice: MidiInputDevice) {
                }

                override fun onMidiOutputDeviceAttached(midiOutputDevice: MidiOutputDevice) {
                    Log.d(
                        debugTag,
                        "Midi ${midiOutputDevice.deviceName} Output Attached"
                    )
                    this@MainActivity.midiOutputDevice = midiOutputDevice
                }
            }

            bleMidiPeripheralProvider.setOnMidiDeviceAttachedListener(attachedListener)
            bleMidiPeripheralProvider.setOnMidiDeviceDetachedListener(detachedListener)

            bluetoothStarted = true

            Log.d(debugTag, "Starting BLE ")
        }
    }

    override fun onDestroy() {
        synthManager.finalize()
        bleMidiPeripheralProvider.terminate()
        sensorManager.unregisterListener(heartRateSensorListener)
        stopInterval()
        super.onDestroy()
    }

    fun sendMidiNote( channel: Int, note: Int, velocity: Int) {
        midiOutputDevice?.sendMidiNoteOn(channel, note, velocity)
        synthManager.fluidsynthNoteOn(channel, note, velocity)
        handler.postDelayed({
            midiOutputDevice?.sendMidiNoteOff(channel, note, velocity)
            synthManager.fluidsynthNoteOff(channel, note)
        }, heartBeatIntervalMs)
    }

    private fun playHeartBeat() {

        var channel = 1
        val notes = song[currentSongIndex]
        currentSongIndex = (currentSongIndex + 1) % song.size

        val velocity = (heartBeatIntervalMs/10).toInt()

        midiOutputDevice?.sendMidiTimingClock()

        for (note in notes) {
            Log.d(debugTag, "Note ${note} ${velocity}   ")
            sendMidiNote(channel, note, velocity)
            channel++;
        }
        Log.d(debugTag, "---")
    }

    private fun startInterval() {
        runnable = Runnable {
            playHeartBeat()
            handler.postDelayed(runnable!!, heartBeatIntervalMs)
        }
        handler.postDelayed(runnable!!, heartBeatIntervalMs)
    }

    private fun stopInterval() {
        runnable?.let { handler.removeCallbacks(it) }
        runnable = null
    }

    private fun updateInterval(newIntervalMillis: Long) {
        heartBeatIntervalMs = newIntervalMillis
        Log.d(debugTag, "updateInterval $heartBeatIntervalMs")
    }

    override fun onPause() {
        Log.d(debugTag, "onPause")
        super.onPause()
        stopBluetooth()
        heartRateSensorListener.unregisterListener()
        sensorManager.unregisterListener(heartRateSensorListener)
        stopInterval()
    }

    override fun onResume() {
        Log.d(debugTag, "onResume")
        super.onResume()
        startBluetoothIfAllPermissionsAreGranted()

        heartRateSensorListener.registerListener(object : HeartBeatListener {
            override fun onHeartRateUpdated(heartRate: Int) {
                if (heartRate == 0) return
                val intervalMs = 60000 / heartRate.toLong()
                updateInterval(intervalMs)
                this@MainActivity.mainText = "$heartRate BPM"
            }
        })

        val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        if (heartRateSensor != null) {
            sensorManager.registerListener(
                heartRateSensorListener,
                heartRateSensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        } else {
            mainText = "Sensor not found"
        }
        startInterval()
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun MainScreen(mainText: String = stringResource(R.string.please_wait)) {


    HeartBeatTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Color.Red,
                fontSize = MaterialTheme.typography.title1.fontSize,
                fontWeight = FontWeight.ExtraBold,
                text = mainText,
            )
        }
    }
}

@Composable
fun RequestPermissions(onPermissionsChange: () -> Unit) {
    permissions.forEach { permission ->
        RequestPermission(
            permission.key,
            permission.value,
            onPermissionsChange
        )
    }
}

@Composable
fun RequestPermission(key: String, name: String, onPermissionsChange: () -> Unit) {
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasPermission = isGranted
        onPermissionsChange()
    }

    LaunchedEffect(key1 = Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            key
        ) == PackageManager.PERMISSION_GRANTED
        onPermissionsChange()
    }

    if (!hasPermission) {
        Button(
            modifier = Modifier.fillMaxSize(),
            onClick = {
                launcher.launch(
                    key
                )
            }) {
            Text("Allow $name")
        }
    }
}

