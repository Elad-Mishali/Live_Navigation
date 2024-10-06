package com.example.myfirstapplication

import java.util.Stack
import kotlin.math.*


data class Node(
    val id : Long,
    val lat : Double,
    val lon : Double
)

data class Way(
    val id : Long,
    val nodes : List<Long>
)

data class OverPassNode(
    val elements : List<Node>
)

data class OverPassWay(
    val elements: List<Way>
)

class Vertex(newId: Long, newLat: Double, newLon: Double){
    companion object{ private var currSerial : Int = 0 }
    val id: Long = newId
    val lat: Double = newLat
    val lon: Double = newLon
    val serial: Int = currSerial++

    fun getNorm(other: Vertex ): Double{
        return ((other.lat - lat).pow(2.0) + (other.lon - lon).pow(2)).pow(0.5)
    }
    fun getDistance(other : Vertex) : Double{
        //Should later change to Haverstine's formula
        return getDistance(other.lat, other.lon)
    }
    fun getDistance(lati: Double, loni : Double): Double{
        //Convert latitude and longitude from degrees to radians
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val lat2 = Math.toRadians(lati)
        val lon2 = Math.toRadians(loni)

        // Haversine formula
        val dlat = lat2 - lat1
        val dlon = lon2 - lon1
        val a = sin(dlat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2)
        val c = 2 * atan2((a).pow(0.5), (1 - a).pow(0.5))

        // Radius of Earth in meters
        val r = 6371000

        // Calculate the distance
        val distance = r * c

        return distance
    }
    fun getVector(target: Vertex): Pair<Double, Double>{
        return Pair(target.lat - lat, target.lon - lon)
    }
}

class Graph(nodes: List<Node>, ways : List<Way>){
    private var vertices : MutableList<Vertex> = mutableListOf()
    private var g : MutableList<MutableList<Pair<Int, Double>>> = mutableListOf()
    private val map : MutableMap<Long, Int> = mutableMapOf()
    init{
        for(i in nodes.indices ){
            vertices.add(Vertex(nodes[i].id, nodes[i].lat, nodes[i].lon))
            map[vertices[i].id] = vertices[i].serial
            g.add(mutableListOf())
        }
        for(i in ways.indices){
            for(j in 0 until ways[i].nodes.size-1){
                val curr = ways[i].nodes[j]
                val next = ways[i].nodes[j+1]
                val s1 = map[curr]
                val s2 = map[next]
                if(s1 != null && s2 != null){
                   g[s1].add(Pair(s2, vertices[s1].getDistance(vertices[s2])))
                   g[s2].add(Pair(s1, vertices[s1].getDistance(vertices[s2])))
                }
            }
        }
    }

    fun getClosestVertex(lat : Double, lon : Double) : Int{
        var minIdx = 0
        var minDist = Double.MAX_VALUE
        for(i in vertices.indices){
            if(minDist > vertices[i].getDistance(lat, lon)){
                minDist = vertices[i].getDistance(lat, lon)
                minIdx = i
            }
        }
        return minIdx
    }

    private fun Dijkstra(start : Int) : MutableList<Vertex?>{
        //Init data structures for algorithm
        val heap : MutableSet<Pair<Double, Int>> = mutableSetOf()
        val dist : MutableList<Double> = MutableList(vertices.size) {Double.MAX_VALUE}
        val prev : MutableList<Vertex?> = MutableList(vertices.size) {null}
        //Init starting point

        dist[start] = 0.0
        heap.add(Pair(0.0,start))

        while(heap.isNotEmpty()){
            //find min and remove it
            val curr = heap.minByOrNull { it.first }
            heap.remove(curr)
            //Go through all neighbors and update data structures
            if (curr != null) {
                val src = curr.second
                for(i in g[src].indices){
                    //if there is a shorter path to some neighboring node update it
                    val ng = g[src][i]
                    if(dist[ng.first] > dist[src] + ng.second){
                        prev[ng.first] = vertices[src]
                        if(heap.contains(Pair(dist[ng.first], ng.first))){
                            heap.remove(Pair(dist[ng.first], ng.first))
                        }
                        dist[ng.first] = dist[src] + ng.second
                        heap.add(Pair(dist[ng.first], ng.first))
                    }
                }
            }
        }
        return prev
    }

    fun getShortestPath(start: Int, end : Int) : List<Vertex>{
        val prev = Dijkstra(start)
        val s : Stack<Vertex> = Stack()
        var curr = end
        s.push(vertices[curr])
        while(prev[curr] != null){
            s.push(prev[curr])
            curr = prev[curr]?.serial ?: throw Exception()
        }
        val path : MutableList<Vertex?> = MutableList(s.size) {null}
        for(i in path.indices){
            path[i] = s.peek()
            s.pop()
        }
        return path.filterNotNull()

    }
}
