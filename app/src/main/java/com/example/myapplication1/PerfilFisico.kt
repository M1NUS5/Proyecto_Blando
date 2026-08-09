package com.example.myapplication1

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Datos corporales del usuario y el calculo de sus metas nutricionales.
 *
 * Las cifras de este archivo se obtienen con formulas, no con el modelo de IA.
 * Es una decision deliberada: una formula entrega siempre el mismo resultado
 * para los mismos datos y no puede equivocarse al recordar una cifra, mientras
 * que un modelo de lenguaje si. La IA se reserva para el consejo escrito, que
 * es donde su criterio aporta algo que una formula no puede dar.
 */

enum class Sexo(val etiqueta: String) {
    HOMBRE("Hombre"),
    MUJER("Mujer");

    companion object {
        fun desde(valor: String?): Sexo =
            values().firstOrNull { it.name == valor } ?: HOMBRE
    }
}

/**
 * Multiplicador que convierte el metabolismo basal en gasto energetico diario.
 * Son los factores de actividad usados junto con la ecuacion de Mifflin-St Jeor.
 */
enum class NivelActividad(
    val etiqueta: String,
    val descripcion: String,
    val factor: Double
) {
    SEDENTARIO("Sedentario", "Poco o nada de ejercicio", 1.2),
    LIGERO("Ligero", "Ejercicio 1 a 3 días por semana", 1.375),
    MODERADO("Moderado", "Ejercicio 3 a 5 días por semana", 1.55),
    ACTIVO("Activo", "Ejercicio 6 a 7 días por semana", 1.725),
    MUY_ACTIVO("Muy activo", "Entrenamiento intenso o trabajo físico", 1.9);

    companion object {
        fun desde(valor: String?): NivelActividad =
            values().firstOrNull { it.name == valor } ?: MODERADO
    }
}

/**
 * Ajuste sobre el gasto diario. Los porcentajes son moderados a proposito: un
 * deficit o superavit agresivo es dificil de sostener y no es responsable
 * sugerirlo desde una aplicacion que no conoce el historial medico de nadie.
 */
enum class ObjetivoPeso(
    val etiqueta: String,
    val descripcion: String,
    val ajuste: Double,
    /** Gramos de proteina por kilo de peso recomendados para este objetivo. */
    val proteinaPorKilo: Double
) {
    BAJAR("Bajar de peso", "Déficit moderado del 20%", -0.20, 2.0),
    MANTENER("Mantener mi peso", "Igual a tu gasto diario", 0.0, 1.6),
    SUBIR("Ganar masa muscular", "Superávit moderado del 15%", 0.15, 1.8);

    companion object {
        fun desde(valor: String?): ObjetivoPeso =
            values().firstOrNull { it.name == valor } ?: MANTENER
    }
}

data class PerfilFisico(
    val edad: Int = 0,
    val estaturaCm: Int = 0,
    val pesoKg: Double = 0.0,
    val sexo: Sexo = Sexo.HOMBRE,
    val nivelActividad: NivelActividad = NivelActividad.MODERADO,
    val objetivo: ObjetivoPeso = ObjetivoPeso.MANTENER
) {
    /**
     * Los rangos no son caprichosos: fuera de ellos la ecuacion de Mifflin-St
     * Jeor deja de ser valida, asi que mas vale no calcular nada que entregar
     * una meta sin sentido.
     */
    val estaCompleto: Boolean
        get() = edad in RANGO_EDAD &&
                estaturaCm in RANGO_ESTATURA &&
                pesoKg >= PESO_MINIMO &&
                pesoKg <= PESO_MAXIMO

    companion object {
        val RANGO_EDAD = 12..100
        val RANGO_ESTATURA = 120..230
        const val PESO_MINIMO = 30.0
        const val PESO_MAXIMO = 250.0
    }
}

data class MetasNutricionales(
    /** Gasto energetico en reposo absoluto. */
    val metabolismoBasal: Int,
    /** Gasto total considerando la actividad fisica declarada. */
    val gastoDiario: Int,
    /** Meta de calorias ya ajustada al objetivo del usuario. */
    val caloriasMeta: Int,
    val proteinaMeta: Int,
    val carbosMeta: Int,
    val grasasMeta: Int,
    val imc: Double,
    val categoriaImc: String
)

/**
 * Calcula las metas diarias con la ecuacion de Mifflin-St Jeor, que es la que
 * mejor se aproxima al gasto real en poblacion general.
 *
 * Devuelve null cuando los datos estan incompletos, para que la interfaz pida
 * llenarlos en vez de mostrar cifras inventadas.
 */
fun PerfilFisico.calcularMetas(): MetasNutricionales? {
    if (!estaCompleto) return null

    // Mifflin-St Jeor: la constante final es lo unico que cambia entre sexos.
    val base = (10 * pesoKg) + (6.25 * estaturaCm) - (5.0 * edad)
    val basal = if (sexo == Sexo.HOMBRE) base + 5 else base - 161

    val gasto = basal * nivelActividad.factor
    val meta = gasto * (1 + objetivo.ajuste)

    // La proteina se fija por peso corporal y las grasas como porcentaje de la
    // energia; los carbohidratos ocupan lo que sobra. Es el reparto habitual en
    // nutricion deportiva porque protege la masa muscular en cualquier objetivo.
    val proteina = pesoKg * objetivo.proteinaPorKilo
    val grasas = (meta * PORCENTAJE_GRASAS) / KCAL_POR_GRAMO_GRASA
    val caloriasRestantes = meta - (proteina * KCAL_POR_GRAMO_PROTEINA) - (grasas * KCAL_POR_GRAMO_GRASA)
    val carbos = (caloriasRestantes / KCAL_POR_GRAMO_CARBO).coerceAtLeast(0.0)

    val estaturaMetros = estaturaCm / 100.0
    val imc = pesoKg / estaturaMetros.pow(2)

    return MetasNutricionales(
        metabolismoBasal = basal.roundToInt(),
        gastoDiario = gasto.roundToInt(),
        caloriasMeta = meta.roundToInt(),
        proteinaMeta = proteina.roundToInt(),
        carbosMeta = carbos.roundToInt(),
        grasasMeta = grasas.roundToInt(),
        imc = imc,
        categoriaImc = categoriaDeImc(imc)
    )
}

private const val PORCENTAJE_GRASAS = 0.27
private const val KCAL_POR_GRAMO_PROTEINA = 4.0
private const val KCAL_POR_GRAMO_CARBO = 4.0
private const val KCAL_POR_GRAMO_GRASA = 9.0

/** Cortes de la Organizacion Mundial de la Salud para poblacion adulta. */
fun categoriaDeImc(imc: Double): String = when {
    imc < 18.5 -> "Bajo peso"
    imc < 25.0 -> "Peso normal"
    imc < 30.0 -> "Sobrepeso"
    else -> "Obesidad"
}

/**
 * Peso que una porcion representa dentro de la meta diaria, expresado en
 * porcentaje. Se usa para las barras de la tarjeta de recomendacion.
 */
fun porcentajeDeMeta(valor: Int, meta: Int): Int {
    if (meta <= 0) return 0
    return ((valor.toDouble() / meta) * 100).roundToInt()
}
