package security

import de.mkammerer.argon2.Argon2Factory
import at.favre.lib.crypto.bcrypt.BCrypt

// Parámetros Argon2id recomendados por OWASP (19 MiB de memoria, 2 iteraciones, 1 hilo).
private const val ARGON2_ITERACIONES = 2
private const val ARGON2_MEMORIA_KIB = 19_456
private const val ARGON2_PARALELISMO = 1

private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

/** Único algoritmo para guardar contraseñas nuevas: Argon2id. */
fun hashPassword(plain: String): String =
    argon2.hash(ARGON2_ITERACIONES, ARGON2_MEMORIA_KIB, ARGON2_PARALELISMO, plain.toCharArray())

private fun isArgon2Hash(hash: String): Boolean = hash.startsWith("\$argon2")

private fun isBcryptHash(hash: String): Boolean =
    hash.startsWith("\$2a$") || hash.startsWith("\$2b$") || hash.startsWith("\$2y$")

/**
 * Verifica contra Argon2id, o BCrypt para hashes guardados antes de unificar el algoritmo.
 * Cualquier otro formato se rechaza: nunca se compara texto plano.
 */
fun verifyPassword(plain: String, hash: String): Boolean = when {
    isArgon2Hash(hash) -> argon2.verify(hash, plain.toCharArray())
    isBcryptHash(hash) -> BCrypt.verifyer().verify(plain.toCharArray(), hash.toCharArray()).verified
    else -> false
}
