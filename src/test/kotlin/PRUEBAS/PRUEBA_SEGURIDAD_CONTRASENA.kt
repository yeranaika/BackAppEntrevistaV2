package PRUEBAS

import at.favre.lib.crypto.bcrypt.BCrypt
import UTILIDADES.generarHashContrasena
import UTILIDADES.verificarContrasena
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PruebaSeguridadContrasena {

    @Test
    fun `hash nuevo usa argon2id y verifica la misma contrasena`() {
        val hash = generarHashContrasena("Clave-segura-1")
        assertTrue(hash.startsWith("\$argon2id"))
        assertTrue(verificarContrasena("Clave-segura-1", hash))
        assertFalse(verificarContrasena("otra-clave", hash))
    }

    @Test
    fun `hash bcrypt antiguo sigue verificando`() {
        val hashAntiguo = BCrypt.withDefaults().hashToString(4, "Clave-antigua-1".toCharArray())
        assertTrue(verificarContrasena("Clave-antigua-1", hashAntiguo))
    }

    @Test
    fun `contrasena guardada en texto plano se rechaza`() {
        assertFalse(verificarContrasena("texto-plano", "texto-plano"))
    }

    @Test
    fun `los espacios forman parte de la contrasena`() {
        val hash = generarHashContrasena(" con espacios ")
        assertTrue(verificarContrasena(" con espacios ", hash))
        assertFalse(verificarContrasena("con espacios", hash))
    }
}
