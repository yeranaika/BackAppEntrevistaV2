package security

import de.mkammerer.argon2.Argon2Factory
import at.favre.lib.crypto.bcrypt.BCrypt

private val argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

fun hashPassword(plain: String): String {
    val pwd = plain.trim()
    return argon2.hash(2, 19_456, 1, pwd.toCharArray())
}

private fun isArgon2Hash(hash: String?): Boolean {
    if (hash.isNullOrBlank()) return false
    return hash.startsWith("\$argon2")
}

private fun isBcryptHash(hash: String?): Boolean {
    if (hash.isNullOrBlank()) return false
    return hash.startsWith("\$2a$") || hash.startsWith("\$2b$") || hash.startsWith("\$2y$")
}

fun verifyPassword(plain: String, hash: String): Boolean {
    val pwd = plain.trim()

    return when {
        isArgon2Hash(hash) ->
            argon2.verify(hash, pwd.toCharArray())

        isBcryptHash(hash) ->
            BCrypt.verifyer()
                .verify(pwd.toCharArray(), hash.toCharArray())
                .verified

        else ->
            pwd == hash // fallback si algún día se guardó en texto plano
    }
}
    