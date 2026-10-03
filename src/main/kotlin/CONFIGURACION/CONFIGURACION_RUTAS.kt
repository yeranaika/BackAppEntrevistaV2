package CONFIGURACION

import CONTROLADORES.controladorAdminUsuario
import CONTROLADORES.controladorConsentimiento
import CONTROLADORES.controladorContrasena
import CONTROLADORES.controladorEntrevista
import CONTROLADORES.controladorFeedback
import CONTROLADORES.controladorLogin
import CONTROLADORES.controladorMercado
import CONTROLADORES.controladorNivelacion
import CONTROLADORES.controladorOnboarding
import CONTROLADORES.controladorPractica
import CONTROLADORES.controladorPregunta
import CONTROLADORES.controladorPruebaPractica
import CONTROLADORES.controladorRecordatorio
import CONTROLADORES.controladorSalud
import CONTROLADORES.controladorSuscripcion
import CONTROLADORES.controladorUsuario
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/** Monta todas las rutas con las dependencias ya construidas en el contenedor. */
fun Application.configurarRutas(d: ContenedorDependencias) {
    routing {
        controladorSalud()

        // Cuentas y sesión
        controladorLogin(d.servicioLogin)
        controladorUsuario(d.servicioUsuario)
        controladorContrasena(d.servicioContrasena)
        controladorOnboarding(d.servicioOnboarding)
        controladorAdminUsuario(d.servicioAdminUsuario)
        controladorConsentimiento(d.servicioConsentimiento, d.documentosLegales)
        controladorRecordatorio(d.servicioRecordatorio)
        controladorSuscripcion(d.servicioSuscripcion)

        // Banco de preguntas e IA
        controladorPregunta(d.servicioPregunta, d.servicioGeneracionPregunta)

        // Mercado laboral: cargos, skills y tendencias
        controladorMercado(d.servicioMercado, d.servicioTendencias)

        // Pruebas: entrevista, práctica y nivelación (API /api/v1 y contrato de la app Android)
        controladorEntrevista(d.servicioEntrevista)
        controladorFeedback(d.servicioReporte)
        controladorPractica(d.servicioPractica, d.servicioPruebasApp, d.evaluadorRespuesta)
        controladorNivelacion(d.servicioNivelacion, d.servicioTestNivelacion)
        controladorPruebaPractica(d.servicioPruebasApp)
    }
}
