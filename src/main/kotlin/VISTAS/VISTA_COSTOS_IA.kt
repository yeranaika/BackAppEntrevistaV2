package VISTAS

import ESQUEMAS.RespuestaCostoIaDia
import ESQUEMAS.RespuestaCostoIaModelo
import ESQUEMAS.RespuestaCostosIa
import ESQUEMAS.RespuestaGastoIa
import ESQUEMAS.RespuestaTopeGeneracionIa
import SERVICIOS.EstadoTopeGeneracionIa
import SERVICIOS.GastoIa
import SERVICIOS.ResumenCostosIa
import java.math.BigDecimal
import java.math.RoundingMode

/** USD con 6 decimales (la precisión de costo_usd en la BD). */
private fun BigDecimal.usd(): Double = setScale(6, RoundingMode.HALF_UP).toDouble()

fun GastoIa.aRespuesta() = RespuestaGastoIa(
    usos = usos,
    fallidas = fallidas,
    tokensEntrada = tokensEntrada,
    tokensSalida = tokensSalida,
    costoUsd = costoUsd.usd(),
    costoEstimadoUsd = costoEstimadoUsd.usd()
)

fun ResumenCostosIa.aRespuesta() = RespuestaCostosIa(
    desde = desde.toString(),
    hasta = hasta.toString(),
    total = total.aRespuesta(),
    reportes = reportes.aRespuesta(),
    generacionPreguntas = generacionPreguntas.aRespuesta(),
    porDia = porDia.map { RespuestaCostoIaDia(it.fecha.toString(), it.reportes.aRespuesta(), it.generacionPreguntas.aRespuesta(), (it.reportes + it.generacionPreguntas).costoUsd.usd()) },
    porModelo = porModelo.map { (modelo, gasto) -> RespuestaCostoIaModelo(modelo, gasto.aRespuesta()) },
    topeGeneracionPreguntas = topeGeneracionPreguntas.aRespuesta()
)

/** Los montos gastado y restante se redondean para mostrar; el tope va tal cual se configuró. */
fun EstadoTopeGeneracionIa.aRespuesta() = RespuestaTopeGeneracionIa(
    mes = mes.toString(),
    gastadoUsd = gastadoUsd.usd(),
    topeUsd = topeUsd.toDouble(),
    restanteUsd = restanteUsd.usd(),
    fechaReposicion = fechaReposicion?.toString(),
    estaDesactivado = estaDesactivado,
    estaAgotado = estaAgotado
)
