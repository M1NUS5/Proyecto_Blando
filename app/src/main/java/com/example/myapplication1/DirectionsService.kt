package com.example.myapplication1

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object DirectionsService {

    private val client = OkHttpClient()

    suspend fun obtenerRuta(
        origen: LatLng,
        destino: LatLng,
        apiKey: String
    ): List<LatLng> = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=${origen.latitude},${origen.longitude}" +
                    "&destination=${destino.latitude},${destino.longitude}" +
                    "&mode=walking" +
                    "&key=$apiKey"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val status = json.getString("status")

            if (status != "OK") return@withContext emptyList()

            val polyline = json
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("overview_polyline")
                .getString("points")

            decodificarPolyline(polyline)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodificarPolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift = 0; result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            poly.add(LatLng(lat / 1e5, lng / 1e5))
        }
        return poly
    }
}