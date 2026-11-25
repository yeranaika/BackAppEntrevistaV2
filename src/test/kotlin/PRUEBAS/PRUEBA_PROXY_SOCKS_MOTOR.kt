package PRUEBAS

import CONFIGURACION.ConfiguracionMotorVision
import INTEGRACIONES.ClienteMotorVision
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D49: MOTOR_SOCKS_PROXY (Tailscale en userspace dentro del contenedor de Railway) solo cambia el engine
 * del cliente del motor: vacío = CIO directo (como siempre), socks5:// = engine con proxy SOCKS.
 */
class PruebaProxySocksMotor {

    @Test
    fun `sin MOTOR_SOCKS_PROXY el cliente usa conexion directa`() {
        val config = ConfiguracionMotorVision()
        assertTrue(config.socksProxy.isEmpty())
        ClienteMotorVision(config).use { }
    }

    @Test
    fun `con socks5 valido el cliente se construye con el engine del proxy`() {
        val config = ConfiguracionMotorVision(socksProxy = "socks5://localhost:1055")
        ClienteMotorVision(config).use { }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un proxy que no es socks5 se rechaza en la configuracion`() {
        ConfiguracionMotorVision(socksProxy = "http://localhost:1055")
    }
}
