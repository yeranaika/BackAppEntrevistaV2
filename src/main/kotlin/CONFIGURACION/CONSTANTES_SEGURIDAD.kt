package CONFIGURACION

/** Vida del access token JWT: corta, porque no se puede revocar antes de expirar. */
const val TTL_TOKEN_ACCESO_SEGUNDOS = 15 * 60

/** Vida del refresh token opaco; se rota en cada uso. */
const val DIAS_VIGENCIA_REFRESH_TOKEN = 15L

const val LARGO_MINIMO_CONTRASENA = 8

// Tope para no gastar CPU de Argon2 con entradas gigantes.
const val LARGO_MAXIMO_CONTRASENA = 128

const val MINUTOS_VIGENCIA_CODIGO_RECUPERACION = 15L

/** Tras estos intentos fallidos el código se invalida: 6 dígitos no resisten fuerza bruta sin límite. */
const val INTENTOS_MAXIMOS_CODIGO_RECUPERACION = 5

/** Logins fallidos permitidos por correo antes de bloquear temporalmente (incluso con la contraseña correcta). */
const val INTENTOS_MAXIMOS_LOGIN = 5
const val MINUTOS_BLOQUEO_LOGIN = 15L
