package com.example.myapplication1

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds

/** Punto del recorrido donde se cumple un kilometro completo. */
data class MarcaKilometro(
    val kilometro: Int,
    val posicion: LatLng
)

/**
 * Utilidades para dibujar el recorrido de un entrenamiento sobre el mapa.
 */
object RecorridoMapaUtils {

    private const val RADIO_TIERRA_METROS = 6_371_000.0

    /**
     * Distancia entre dos coordenadas usando la formula del semiverseno.
     *
     * Se calcula aqui y no con utilidades de Android para que la funcion sea
     * comprobable sin depender del dispositivo.
     */
    fun metrosEntre(a: LatLng, b: LatLng): Double {
        val latA = Math.toRadians(a.latitude)
        val latB = Math.toRadians(b.latitude)
        val difLat = Math.toRadians(b.latitude - a.latitude)
        val difLng = Math.toRadians(b.longitude - a.longitude)

        val h = Math.sin(difLat / 2) * Math.sin(difLat / 2) +
                Math.cos(latA) * Math.cos(latB) *
                Math.sin(difLng / 2) * Math.sin(difLng / 2)

        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Longitud total del recorrido, en kilometros. */
    fun distanciaTotalKm(puntos: List<LatLng>): Double {
        if (puntos.size < 2) return 0.0

        var metros = 0.0
        for (i in 1 until puntos.size) {
            metros += metrosEntre(puntos[i - 1], puntos[i])
        }

        return metros / 1000.0
    }

    /**
     * Ubica sobre el trazo el punto exacto donde se cumple cada kilometro.
     *
     * Al recorrer la ruta acumulando distancia, cuando un tramo cruza la marca
     * se interpola la posicion dentro de ese tramo en lugar de usar el extremo,
     * para que el marcador caiga sobre la linea y no adelante o atras de ella.
     */
    fun marcasDeKilometro(puntos: List<LatLng>): List<MarcaKilometro> {
        if (puntos.size < 2) return emptyList()

        val marcas = mutableListOf<MarcaKilometro>()
        var acumulado = 0.0
        var siguienteKm = 1

        for (i in 1 until puntos.size) {
            val inicio = puntos[i - 1]
            val fin = puntos[i]
            val tramo = metrosEntre(inicio, fin)

            if (tramo <= 0.0) continue

            while (acumulado + tramo >= siguienteKm * 1000.0) {
                val faltante = siguienteKm * 1000.0 - acumulado
                val proporcion = (faltante / tramo).coerceIn(0.0, 1.0)

                marcas.add(
                    MarcaKilometro(
                        kilometro = siguienteKm,
                        posicion = LatLng(
                            inicio.latitude + (fin.latitude - inicio.latitude) * proporcion,
                            inicio.longitude + (fin.longitude - inicio.longitude) * proporcion
                        )
                    )
                )

                siguienteKm += 1
            }

            acumulado += tramo
        }

        return marcas
    }

    /**
     * Rectangulo que contiene todo el recorrido, para encuadrar la camara.
     *
     * Devuelve null cuando no hay puntos suficientes; en ese caso conviene dejar
     * la camara donde este en lugar de moverla a una posicion sin sentido.
     */
    fun limitesDelRecorrido(puntos: List<LatLng>): LatLngBounds? {
        if (puntos.isEmpty()) return null

        val constructor = LatLngBounds.builder()
        puntos.forEach { constructor.include(it) }

        return constructor.build()
    }
}
