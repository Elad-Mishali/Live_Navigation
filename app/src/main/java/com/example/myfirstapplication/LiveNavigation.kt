package com.example.myfirstapplication

import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.myfirstapplication.databinding.ActivityLiveNavigationBinding
import com.google.gson.Gson
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.InputStreamReader
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.math.*

//32.568828,34.956769,32.564838,34.945474
//32.564838,34.945474,32.565067, 34.947001
//32.568828,34.956769,32.568934, 34.957771
//32.568828,34.956769,32.570674, 34.955796
//32.568828,34.956769,32.570685, 34.955977
const val success = 0
const val turnedTooMuch = 1
const val walkedTooFar = 2

class LiveNavigation : ComponentActivity(), SensorEventListener {
    private lateinit var binding: ActivityLiveNavigationBinding
    //Data for path computation
    private lateinit var nodes : List<Node>
    private lateinit var ways : List<Way>
    private lateinit var graph: Graph
    //Data for sensor use
    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    //Data for live Navigation
    private lateinit var path : List<Vertex>
    private var currDist : Double = 0.0
    private var currAngle : Double = 0.0
    private var currTimeGyro : Long = -1
    private var prevTimeGyro : Long = -1
    private lateinit var distances : List<Double>
    private lateinit var angles : List<Double>
    private lateinit var liveLocation : Marker
    private var i = 0
    private var b = 0.0
    private var m = 0.0


    @SuppressLint("SetTextI18n", "UseCompatLoadingForDrawables")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Get coordinates that were taken from the user in previous activity
        val intent = intent
        val coordinates: String = intent.getStringExtra("Coordinates")!!
        val stringCoordinates: List<String> = coordinates.split(",")
        //Turn into Doubles
        val startLatitude = stringCoordinates[0].toDouble()
        val startLongitude = stringCoordinates[1].toDouble()
        val endLatitude = stringCoordinates[2].toDouble()
        val endLongitude = stringCoordinates[3].toDouble()
        //Create bbox
        val extra = 0.002
        val minLat = (minOf(startLatitude, endLatitude) - extra).toString()
        val maxLat = (maxOf(startLatitude, endLatitude) + extra).toString()
        val minLon = (minOf(startLongitude, endLongitude) - extra).toString()
        val maxLon = (maxOf(startLongitude, endLongitude) + extra).toString()
        val bbox = """$minLat,$minLon,$maxLat,$maxLon"""
        //Create threads to retrieve data from the web
        val tNode = fetchNodeData(bbox)
        val tWay = fetchWayData(bbox)
        //Run threads and wait for them to finish
        tNode.start()
        tNode.join()
        tWay.start()
        tWay.join()

        //Create graph
        graph = Graph(nodes, ways)
        //Get shortest path with Dijkstra
        val start = graph.getClosestVertex(startLatitude, startLongitude)
        val end = graph.getClosestVertex(endLatitude, endLongitude)
        path = graph.getShortestPath(start, end)
        //get distances and angles of the path
        val p = getDistancesAndAngles(path)
        distances = p.first
        val tempList : MutableList<Double> = p.second
        tempList.add(0.0)
        angles = tempList

        //Display map
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(20.0)
        val startPoint = GeoPoint(startLatitude,startLongitude)
        binding.map.controller.setCenter(startPoint)
        //Draw the path and add start and end markers
        drawPath(path)
        //Add live location marker
        liveLocation = Marker(binding.map)
        liveLocation.position = GeoPoint(startLatitude, startLongitude)
        liveLocation.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        liveLocation.icon = getDrawable(R.drawable.baseline_location_pin_24)

        binding.map.overlays.add(liveLocation)
        //initialize sensor manager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // Initialize sensor
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        // Check if sensor are available
        if (gyroscope == null) {
            binding.InstructionPannel.text = "Gyroscope not available!"
        }

        //Live Navigation is Active!
        currDist = 0.0
        currAngle = 0.0
        m = (path[0].lon - path[1].lon) / (path[0].lat - path[1].lat)
        b = path[0].lon - m * path[0].lat
        i = 0
    }

    private fun drawPath(vertices: List<Vertex>) {
        //Add two markers - start and end
        val start = Marker(binding.map)
        val end = Marker(binding.map)
        start.position = GeoPoint(vertices[0].lat, vertices[0].lon)
        end.position = GeoPoint(vertices[vertices.size-1].lat, vertices[vertices.size-1].lon)
        start.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        end.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        start.setTextIcon("start")
        end.setTextIcon("end")
        binding.map.overlays.add(start)
        binding.map.overlays.add(end)
        //Draw the path
        val path = Polyline()
        val geoPoints = vertices.map{ vertex ->
            GeoPoint(vertex.lat, vertex.lon)
        }
        path.setPoints(geoPoints)
        binding.map.overlays.add(path)
    }

    @SuppressLint("SetTextI18n")
    private fun fetchNodeData(bbox: String): Thread
    {
        return Thread {
            val query = """
            [out:json];
            (
                way($bbox);
                - way["building"]($bbox);
            )->.a;
            (
                way.a;
                node(w);
            )->.b;
            (
              node.b;
            );
            out body;
            """.trimIndent()
            val url = URL("https://overpass-api.de/api/interpreter?data=$query")
            val connection  = url.openConnection() as HttpsURLConnection

            if(connection.responseCode == 200)
            {
                val inputSystem = connection.inputStream
                val inputStreamReader = InputStreamReader(inputSystem, "UTF-8")
                val request = Gson().fromJson(inputStreamReader, OverPassNode::class.java)
              //  updateUI(request)
                nodes = request.elements
                inputStreamReader.close()
                inputSystem.close()
            }
            else
            {
                binding.InstructionPannel.text = "connection failed"
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchWayData(bbox: String): Thread
    {
        return Thread {
            val query = """
            [out:json];
            (
                way($bbox);
                - way["building"]($bbox);
            );
            out body;
            """.trimIndent()
            val url = URL("https://overpass-api.de/api/interpreter?data=$query")
            val connection  = url.openConnection() as HttpsURLConnection

            if(connection.responseCode == 200)
            {
                val inputSystem = connection.inputStream
                val inputStreamReader = InputStreamReader(inputSystem, "UTF-8")
                val request = Gson().fromJson(inputStreamReader, OverPassWay::class.java)
                ways = request.elements
                inputStreamReader.close()
                inputSystem.close()
            }
            else
            {
                binding.InstructionPannel.text = "connection failed"
            }
        }
    }

    private fun getDistancesAndAngles(path: List<Vertex>) : Pair<MutableList<Double>, MutableList<Double>>{
        val dist : MutableList<Double> = mutableListOf()
        val angles : MutableList<Double> = mutableListOf()
        dist.add(path[0].getDistance(path[1]))
        for(i in 1 until path.size-1){
            dist.add(path[i].getDistance(path[i+1]))
            val vec1 = path[i-1].getVector(path[i])
            val vec2 = path[i].getVector(path[i+1])
            val dotProduct : Double = vec1.first * vec2.first + vec1.second * vec2.second
            val crossProduct : Double = vec1.first * vec2.second - vec1.second * vec2.first
            //Check if changing to Haversine's formula
            angles.add((-1 * atan2(crossProduct, dotProduct)))
        }
        return Pair(dist, angles)
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        // Register sensor listeners if sensors are available
        gyroscope?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        // Unregister the sensor listeners to conserve battery
        sensorManager.unregisterListener(this)
    }

    private fun updateMarkerPosition() {
        // Ensure progress is between 0 and 1
        val progress = currDist / path[i].getDistance(path[i+1])
        // Calculate new latitude and longitude based on progress
        val newLat = path[i].lat + progress * (path[i+1].lat - path[i].lat)
        val newLon = path[i].lon + progress * (path[i+1].lon - path[i].lon)
        try {
            liveLocation.position = GeoPoint(newLat, newLon)
            binding.map.invalidate()
        } catch (e: Exception) {
            e.printStackTrace() // Log the crash reason
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onSensorChanged(event: SensorEvent?) {
        if(currTimeGyro.toInt() == -1){
            //Take time
            prevTimeGyro = System.currentTimeMillis()
        }
        if (event != null) {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    currTimeGyro = System.currentTimeMillis()
                    val timeDiff = currTimeGyro - prevTimeGyro
                    //Get rate of rotation around z-axis
                    val z = event.values[2]
                    //Update currAngle
                    currAngle += timeDiff * z / 1000
                    currAngle %= 2 * PI
                    currDist += timeDiff * 1.3 / 1000
                    //Update PrevTimeAcc for next event
                    prevTimeGyro = currTimeGyro
                    binding.InstructionPannel.text = " timeDiff = $timeDiff \n" +
                            " currAngle =${round(currAngle)}, needed angle = ${round(angles[i])} \n" +
                            " currDist = ${round(currDist)}, needed dist = ${round(distances[i])} \n" +
                            " i = $i out of ${path.size}\n"
                    if (i < distances.size) {
                        if(currDist <= distances[i]*3/4){ // If didn't yet reached turn
                            if(abs(currAngle) >= 0.5){ //But user turned significantly
                                endNavigation(turnedTooMuch)
                            }
                            else{ // User is on the truck - compute is current location and display on map
                                updateMarkerPosition()
                            }
                        }
                        else if(abs(currDist-distances[i]) <= distances[i]/4){ //User reached turn
                            if(abs(angles[i] - currAngle) <= 0.5){ //User turned
                                i++
                                m = (path[i].lon - path[i+1].lon) / (path[i].lat - path[i+1].lat)
                                b = path[i].lon - m * path[i].lat
                                currDist = 0.0
                                currAngle = 0.0
                            }
                            else{
                                updateMarkerPosition()
                            }
                        }
                        else{ // User missed turn
                            endNavigation(walkedTooFar)
                        }
                    }
                    else{
                        endNavigation(success)
                    }
                }
            }
        }
    }

    private fun round(d : Double): Double{
        return (d * 100).roundToInt() / 100.0
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) { //Ignore - If times allow I will implement
    }

    @SuppressLint("SetTextI18n")
    private fun endNavigation(code: Int) {
        when (code) {
            success -> {
                binding.finish.text = "You have reached Your target"
            }
            turnedTooMuch -> {
                binding.finish.text = "You have turned too much and lost the track!"
            }
            walkedTooFar -> {
                binding.finish.text = "You have walked too much and lost the track!"
            }
            else -> {
                binding.finish.text = "Something is fishy!"
            }
        }
    }

}
