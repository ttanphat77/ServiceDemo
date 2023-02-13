package com.example.servicedemo

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.util.*


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val backgroundBtn = findViewById(R.id.button_background) as Button
        backgroundBtn.setOnClickListener{
            val backgroundServiceIntent = Intent(this, BackgroundService::class.java)
            startService(backgroundServiceIntent)
        }
        val foregroundBtn = findViewById(R.id.button_foreground) as Button
        foregroundBtn.setOnClickListener{
            val foregroundServiceIntent = Intent(this, ForegroundService::class.java)
            startService(foregroundServiceIntent)
        }
    }

}

class ForegroundService : Service() {
    private val notificationId = 101
    private val channelId = "channelId"
    private val channelName = "channelName"

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "stop") {
            stopForeground(true)
            unregisterReceiver(mBroadcastReceiver)
            stopSelf()
        } else {
            val notification = getNotification()
            val filter = IntentFilter()
            filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            registerReceiver(mBroadcastReceiver, filter)
            startForeground(notificationId, notification)
        }

        return START_STICKY
    }

    private fun getNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            0
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Foreground Service")
            .setContentText("Foreground Service is running in the background.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private val mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {
                val pm = context.getSystemService(POWER_SERVICE) as PowerManager
                if (pm.isDeviceIdleMode()) {
                    Log.d("ForegroundService", "Device is idle")
                    // Do your work here
                }
            }
        }
    }

}

class BackgroundService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAdvertising()
        // Do your work here
        return START_STICKY
    }


    private var mBluetoothGattServer: BluetoothGattServer? = null
    private var mAdvertiseCallback: AdvertiseCallback? = null
    private var mBluetoothLeAdvertiser: BluetoothLeAdvertiser? = null
    private var mBluetoothAdapter : BluetoothAdapter? = null
    private var notiChar : BluetoothGattCharacteristic? = null
    private var statusCharUUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
    private var statusCharUUID2 = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    private var statusServiceUUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
//    private val bytes = listOf(0xa1, 0x2e, 0x38, 0xd4, 0x89, 0xc3)
//        .map { it.toByte() }
//        .toByteArray()

    override fun onCreate() {

    }

    private val mGattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // A device has connected to the peripheral
                Log.d("test", "Device connected: $device")
                startActivity(Intent(this@BackgroundService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                })

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // A device has disconnected from the peripheral
                Log.d("test", "Device disconnected: $device")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            super.onServiceAdded(status, service)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("test", "Service added: $service")
            } else {
                Log.d("test", "Service not added: $service")
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            Log.d("test", "onCharacteristicReadRequest: $offset")
            val value = characteristic?.value
            if (offset != 0) {
                mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                return
            }
            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)

        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            super.onCharacteristicWriteRequest(
                device,
                requestId,
                characteristic,
                preparedWrite,
                responseNeeded,
                offset,
                value
            )
            Log.d("test", "Received: ${Arrays.toString(value)}")
            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            notiChar?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            notiChar?.value = value
            mBluetoothGattServer?.notifyCharacteristicChanged(device, notiChar,  true)

            Log.d("test", "Send back!")
        }
    }

    private fun createBluetoothGattServer() {

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        mBluetoothGattServer = bluetoothManager.openGattServer(this, mGattServerCallback)
        val writeCharacteristic = BluetoothGattCharacteristic(statusCharUUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE ,
            BluetoothGattCharacteristic.PERMISSION_WRITE)
        notiChar = BluetoothGattCharacteristic(statusCharUUID2,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY ,
            BluetoothGattCharacteristic.PERMISSION_READ)
        val service = BluetoothGattService(statusServiceUUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service?.addCharacteristic(writeCharacteristic)
        service?.addCharacteristic(notiChar)
        mBluetoothGattServer?.removeService(service)
        mBluetoothGattServer?.addService(service)
    }


    fun startAdvertising() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {

            createBluetoothGattServer()
            mBluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

            mBluetoothLeAdvertiser = mBluetoothAdapter?.bluetoothLeAdvertiser

            mAdvertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    Log.d("test",  "LE Advertise Started.")
                }

                override fun onStartFailure(errorCode: Int) {
                    Log.d("test", "LE Advertise Failed: $errorCode")
                }
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()
            mBluetoothAdapter?.setName("abcd")
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(statusServiceUUID))
                .build()

            mBluetoothLeAdvertiser?.startAdvertising(settings, data, mAdvertiseCallback)
        }
    }
}