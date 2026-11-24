package CONFIGURACION

import CONTROLADORES.controladorAdminUsuario
import CONTROLADORES.controladorConsentimiento
import CONTROLADORES.controladorCostosIa
import CONTROLADORES.controladorContrasena
import CONTROLADORES.controladorEntrevista
import CONTROLADORES.controladorFeedback
import CONTROLADORES.controladorLogin
import CONTROLADORES.controladorMercado
import CONTROLADORES.controladorNivelacion
import CONTROLADORES.controladorOnboarding
import CONTROLADORES.controladorPractica
import CONTROLADORES.controladorPregunta
import CONTROLADORES.controladorLlamada
import CONTROLADORES.controladorRevisionLocal
import CONTROLADORES.controladorPruebaPractica
import CONTROLADORES.controladorPruebaVideoSiHabilitado
import CONTROLADORES.controladorRecordatorio
import CONTROLADORES.controladorSalud
import CONTROLADORES.controladorSuscripcion
import CONTROLADORES.controladorUsuario
import CONTROLADORES.controladorVision
import io.ktor.server.application.Application
import io.ktor.server.routing.routing

/** Monta todas las rutas con las dependencias ya construidas en el contenedor. */
fun Application.configurarRutas(d: ContenedorDependencias, disponibilidad: EstadoFreemiumReadiness, pruebasVideo: Boolean = false) {
    routing {
        controladorSalud(disponibilidad, d.clienteMotorVision)

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
        controladorPregunta(d.servicioPregunta, d.servicioGeneracionPregunta, d.servicioCoberturaPreguntas, d.servicioFeedbackGenerico)
        controladorCostosIa(d.servicioCostosIa)

        // Mercado laboral: cargos, skills y tendencias
        controladorMercado(d.servicioMercado, d.servicioTendencias)

        // Pruebas: entrevista, práctica y nivelación (API /api/v1 y contrato de la app Android)
        controladorEntrevista(d.servicioEntrevista)
        controladorVision(d.clienteMotorVision, d.servicioSimulacion)
        controladorLlamada(d.servicioLlamada)
        controladorFeedback(d.servicioReporte)
        controladorPractica(d.servicioPractica, d.servicioPruebasApp, d.evaluadorRespuesta)
        controladorRevisionLocal(d.servicioRevisionLocal)
        controladorNivelacion(d.servicioNivelacion, d.servicioTestNivelacion)
        controladorPruebaPractica(d.servicioPruebasApp)
        // Calibración del modelo de visión: solo con HABILITAR_PRUEBAS_VIDEO=true, nunca en Railway (D39).
        controladorPruebaVideoSiHabilitado(pruebasVideo)
    }
}
