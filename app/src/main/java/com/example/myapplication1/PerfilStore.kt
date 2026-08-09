package com.example.myapplication1

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado compartido del perfil del usuario: su nombre visible y su fotografia.
 *
 * Estos datos viven en las preferencias del dispositivo, pero leerlos desde
 * cada pantalla por separado provoca que se queden desactualizados: la pantalla
 * de inicio conserva en memoria el valor con el que se creo, de modo que al
 * cambiar la fotografia desde el perfil su encabezado seguia mostrando el icono
 * anterior hasta reiniciar la aplicacion.
 *
 * Publicarlos aqui como flujo observable hace que cualquier pantalla que los
 * muestre se actualice en el momento en que cambian, sin depender de que se
 * vuelva a crear.
 */
object PerfilStore {

    private val _fotoUri = MutableStateFlow<String?>(null)

    /** Ruta de la fotografia de perfil, o `null` si el usuario no ha elegido una. */
    val fotoUri: StateFlow<String?> = _fotoUri

    private val _nombre = MutableStateFlow("Usuario")

    /** Nombre que el usuario decidio mostrar. */
    val nombre: StateFlow<String> = _nombre

    private val _fisico = MutableStateFlow(PerfilFisico())

    /**
     * Estatura, peso, edad y objetivo del usuario. Se publica como flujo por el
     * mismo motivo que el nombre: la pantalla de comida calcula recomendaciones
     * con estos datos y debe reflejar los cambios en cuanto se guardan, sin
     * esperar a que la pantalla se vuelva a crear.
     */
    val fisico: StateFlow<PerfilFisico> = _fisico

    /** Lee los valores guardados. Conviene llamarlo al arrancar la aplicacion. */
    fun cargar(context: Context) {
        val sesion = SessionManager(context)

        _fotoUri.value = sesion.getPhotoUri()
        _nombre.value = sesion.getDisplayName()
            ?: sesion.getName()
            ?: "Usuario"
        _fisico.value = sesion.getPerfilFisico()
    }

    fun actualizarFisico(context: Context, perfil: PerfilFisico) {
        SessionManager(context).savePerfilFisico(perfil)
        _fisico.value = perfil
    }

    fun actualizarFoto(context: Context, uri: String) {
        SessionManager(context).savePhotoUri(uri)
        _fotoUri.value = uri
    }

    fun actualizarNombre(context: Context, nombre: String) {
        val limpio = nombre.trim().ifBlank { return }

        SessionManager(context).saveDisplayName(limpio)
        _nombre.value = limpio
    }

    /** Limpia los datos en memoria al cerrar sesion. */
    fun limpiar() {
        _fotoUri.value = null
        _nombre.value = "Usuario"
        _fisico.value = PerfilFisico()
    }
}
