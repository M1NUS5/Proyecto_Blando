package com.example.myapplication1

import android.location.Location
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Pruebas del calculo de distancia sobre la clase real, no sobre una copia.
 *
 * Se ejecutan como prueba instrumentada porque [Location] es una clase del
 * sistema Android: en una prueba de JVM corriente sus metodos devuelven valores
 * vacios y el resultado no significaria nada.
 *
 * El caso que motivo estas pruebas: una caminata de 326 s por la calle midio
 * 0.16 km cuando el recorrido real rondaba los 0.29 km. Con ese dato el ritmo
 * salia en 34 min/km, por encima del limite de 25 que acepta el analisis, y la
 * pantalla de IA mostraba "No disponible" en todos sus campos.
 */
@RunWith(AndroidJUnit4::class)
class MedidorDistanciaTest {

    private fun ubicacion(
        tiempoMs: Long,
        precisionM: Float,
        velocidadMps: Float? = null
    ): Location = Location("prueba").apply {
        latitude = 0.0
        longitude = 0.0
        time = tiempoMs
        accuracy = precisionM
        if (velocidadMps != null) speed = velocidadMps
    }

    /**
     * El caso real reproducido: caminata lenta con la precision oscilando como
     * ocurre entre edificios. La velocidad Doppler llega bien en todo momento.
     */
    @Test
    fun caminata_con_precision_pobre_conserva_la_distancia() {
        val medidor = MedidorDistancia()

        val duracionS = 326
        val intervaloS = 2
        val velocidad = 0.89f // caminata lenta, coincide con 405 pasos en 326 s

        var semilla = 7
        fun siguienteAzar(): Double {
            semilla = (semilla * 9301 + 49297) % 233280
            return semilla / 233280.0
        }

        var t = 0
        while (t <= duracionS) {
            // Precision entre 8 y 30 m: por encima de 18 el codigo anterior
            // descartaba la lectura completa junto con su intervalo.
            val precision = (8 + siguienteAzar() * 22).toFloat()

            medidor.procesar(
                ubicacion(
                    tiempoMs = t * 1000L,
                    precisionM = precision,
                    velocidadMps = velocidad
                ),
                pasos = 405
            )

            t += intervaloS
        }

        val esperados = velocidad * duracionS // 290 m aproximadamente
        val medidos = medidor.metrosAcumulados
        val errorRelativo = abs(medidos - esperados) / esperados

        assertTrue(
            "Se midieron $medidos m de $esperados m esperados " +
                "(error ${"%.1f".format(errorRelativo * 100)}%). " +
                "Una perdida grande indica que la precision de posicion vuelve " +
                "a descartar lecturas que traian velocidad valida.",
            errorRelativo < 0.05
        )
    }

    /** Sin pasos no hubo movimiento: lo que se mueva es deriva del receptor. */
    @Test
    fun sin_pasos_no_acumula_distancia() {
        val medidor = MedidorDistancia()

        for (t in 0..60 step 2) {
            medidor.procesar(
                ubicacion(tiempoMs = t * 1000L, precisionM = 5f, velocidadMps = 1.2f),
                pasos = 0
            )
        }

        assertEquals(0.0, medidor.metrosAcumulados, 0.001)
    }

    /**
     * Cuando no hay velocidad disponible el calculo cae al metodo por posicion,
     * y ahi la precision si debe seguir descartando lecturas malas.
     */
    @Test
    fun sin_velocidad_la_precision_mala_sigue_descartando() {
        val medidor = MedidorDistancia()

        for (t in 0..60 step 2) {
            medidor.procesar(
                ubicacion(tiempoMs = t * 1000L, precisionM = 45f, velocidadMps = null),
                pasos = 100
            )
        }

        assertEquals(
            "Con precision de 45 m y sin velocidad no hay forma de saber cuanto " +
                "se avanzo, asi que no debe sumarse nada.",
            0.0,
            medidor.metrosAcumulados,
            0.001
        )
    }

    /** Estar detenido no debe acumular distancia aunque lleguen lecturas. */
    @Test
    fun detenido_no_acumula_distancia() {
        val medidor = MedidorDistancia()

        for (t in 0..120 step 2) {
            medidor.procesar(
                ubicacion(tiempoMs = t * 1000L, precisionM = 6f, velocidadMps = 0.05f),
                pasos = 50
            )
        }

        assertEquals(0.0, medidor.metrosAcumulados, 0.001)
    }

    /** Reiniciar debe dejar el medidor como recien creado. */
    @Test
    fun reiniciar_vuelve_a_cero() {
        val medidor = MedidorDistancia()

        for (t in 0..60 step 2) {
            medidor.procesar(
                ubicacion(tiempoMs = t * 1000L, precisionM = 6f, velocidadMps = 1.4f),
                pasos = 90
            )
        }

        assertTrue("Deberia haber acumulado algo antes de reiniciar", medidor.metrosAcumulados > 0.0)

        medidor.reiniciar()

        assertEquals(0.0, medidor.metrosAcumulados, 0.001)
    }
}
