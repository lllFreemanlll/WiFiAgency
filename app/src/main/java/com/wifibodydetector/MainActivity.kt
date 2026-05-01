package com.wifibodydetector

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.slider.Slider
import com.wifibodydetector.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wifiManager: WifiManager
    private lateinit var anomalyDetector: AnomalyDetector
    private var isScanning = false
    private var scanCount = 0

    private val chartEntries = mutableListOf<Entry>()
    private val anomalyEntries = mutableListOf<Entry>()
    private val maxChartEntries = 60

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processScanResults()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        anomalyDetector = AnomalyDetector(thresholdSigma = 1.5)

        setupChart()
        setupListeners()
        requestPermissions()
    }

    private fun setupChart() {
        val chart = binding.signalChart
        chart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            setDrawGridBackground(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#9E9E9E")
                valueFormatter = object : ValueFormatter() {
                    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    override fun getPointLabel(entry: Entry?): String {
                        return ""
                    }
                }
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#1AFFFFFF")
                textColor = Color.parseColor("#9E9E9E")
                setDrawAxisLine(false)
            }

            axisRight.isEnabled = false
            legend.isEnabled = true
            legend.textColor = Color.parseColor("#9E9E9E")
        }
    }

    private fun setupListeners() {
        binding.startButton.setOnClickListener {
            if (isScanning) {
                stopDetection()
            } else {
                startDetection()
            }
        }

        binding.sensitivitySlider.addOnChangeListener { _, value, _ ->
            anomalyDetector.setThreshold(value.toDouble())
            binding.sensitivityLabel.text = "Detection Sensitivity: %.1fσ".format(value)
        }
    }

    private fun startDetection() {
        if (!checkPermissions()) {
            requestPermissions()
            return
        }

        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Please enable WiFi", Toast.LENGTH_LONG).show()
            return
        }

        isScanning = true
        scanCount = 0
        anomalyDetector.reset()
        chartEntries.clear()
        anomalyEntries.clear()

        binding.startButton.text = getString(R.string.stop_scanning)
        binding.startButton.setBackgroundColor(ContextCompat.getColor(this, R.color.accent))

        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        scheduleNextScan()
    }

    private fun stopDetection() {
        isScanning = false
        binding.startButton.text = getString(R.string.start_scanning)
        binding.startButton.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))

        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    private fun scheduleNextScan() {
        if (!isScanning) return

        if (wifiManager.isWifiEnabled) {
            val started = wifiManager.startScan()
            if (!started) {
                // Throttled, retry after delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    scheduleNextScan()
                }, 3000)
                return
            }
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            scheduleNextScan()
        }, 1500)
    }

    private fun processScanResults() {
        if (!isScanning) return

        val results = wifiManager.scanResults
        if (results.isNullOrEmpty()) return

        val strongNetworks = results
            .filter { it.SSID.isNotBlank() }
            .sortedByDescending { it.level }
            .take(5)

        if (strongNetworks.isEmpty()) return

        val avgRSSI = strongNetworks.map { it.level.toDouble() }.average()

        val result = anomalyDetector.addReading(avgRSSI)
        scanCount++

        runOnUiThread {
            updateUI(result, results.size)
            updateChart(result)
        }
    }

    private fun updateUI(result: AnomalyDetector.DetectionResult, networkCount: Int) {
        binding.rssiValue.text = "%.0f dBm".format(result.currentRSSI)
        binding.anomalyValue.text = "%.2f".format(result.anomalyScore)
        binding.networkCount.text = networkCount.toString()

        if (result.isAnomaly) {
            binding.detectionLabel.text = getString(R.string.body_detected)
            binding.detectionLabel.setTextColor(getColor(R.color.red))
            binding.detectionStatus.text = "Status: DETECTED"
        } else {
            binding.detectionLabel.text = getString(R.string.no_detection)
            binding.detectionLabel.setTextColor(getColor(R.color.green))
            binding.detectionStatus.text = "Status: Monitoring"
        }
    }

    private fun updateChart(result: AnomalyDetector.DetectionResult) {
        val entry = Entry(scanCount.toFloat(), result.currentRSSI.toFloat())
        chartEntries.add(entry)

        if (result.isAnomaly) {
            anomalyEntries.add(Entry(scanCount.toFloat(), result.currentRSSI.toFloat()))
        } else {
            anomalyEntries.add(Entry(scanCount.toFloat(), Float.NaN))
        }

        if (chartEntries.size > maxChartEntries) {
            chartEntries.removeAt(0)
            anomalyEntries.removeAt(0)
        }

        val dataSet = LineDataSet(chartEntries, "RSSI").apply {
            color = Color.parseColor("#1A73E8")
            lineWidth = 2f
            setDrawCircles(true)
            circleRadius = 2f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = Color.parseColor("#331A73E8")
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }

        val anomalyDataSet = LineDataSet(anomalyEntries, "Anomaly").apply {
            color = Color.parseColor("#FF1744")
            lineWidth = 3f
            setDrawCircles(true)
            circleRadius = 5f
            circleColor = Color.parseColor("#FF1744")
            setDrawValues(false)
            setDrawFilled(false)
            setDrawCircles(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
            enableDashedLine(10f, 5f, 0f)
        }

        binding.signalChart.data = LineData(dataSet, anomalyDataSet)
        binding.signalChart.invalidate()
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        return permissions.isEmpty()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions required for WiFi scanning", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDetection()
    }
}
