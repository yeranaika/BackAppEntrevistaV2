package PRUEBAS

import at.favre.lib.crypto.bcrypt.BCrypt
import security.hashPassword
import security.verifyPassword
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PruebaSeguridadContrasena {

    @Test
    fun `hash nuevo usa argon2id y verifica la misma contrasena`() {
        val hash = hashPassword("Clave-segura-1")
        assertTrue(hash.startsWith("\$argon2id"))
        assertTrue(verifyPassword("Clave-segura-1", hash))
        assertFalse(verifyPassword("otra-clave", hash))
    }

    @Test
    fun `hash bcrypt antiguo sigue verificando`() {
        val hashAntiguo = BCrypt.withDefaults().hashToString(4, "Clave-antigua-1".toCharArray())
        assertTrue(verifyPassword("Clave-antigua-1", hashAntiguo))
    }

    @Test
    fun `contrasena guardada en texto plano se rechaza`() {
        assertFalse(verifyPassword("texto-plano", "texto-plano"))
    }

    @Test
    fun `los espacios forman parte de la contrasena`() {
        val hash = hashPassword(" con espacios ")
        assertTrue(verifyPassword(" con espacios ", hash))
        assertFalse(verifyPassword("con espacios", hash))
    }
}
