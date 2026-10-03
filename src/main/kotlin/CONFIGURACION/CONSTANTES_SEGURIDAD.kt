package CONFIGURACION

/** Vida del access token JWT: corta, porque no se puede revocar antes de expirar. */
const val TTL_TOKEN_ACCESO_SEGUNDOS = 15 * 60

/** Vida del refresh token opaco; se rota en cada uso. */
const val DIAS_VIGENCIA_REFRESH_TOKEN = 15L

const val LARGO_MINIMO_CONTRASENA = 8
