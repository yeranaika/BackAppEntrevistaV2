package data.mapper

import data.tables.usuarios.ConsentimientoTable
import org.jetbrains.exposed.sql.ResultRow
import routes.consent.ConsentRes   // ← importa el DTO desde routes.consent

fun ResultRow.toConsentRes(): ConsentRes =
    ConsentRes(
        id = this[ConsentimientoTable.id].toString(),
        version = this[ConsentimientoTable.version],
        alcances = this[ConsentimientoTable.alcances],
        fechaOtorgado = this[ConsentimientoTable.fechaOtorgado].toString(),
        fechaRevocado = this[ConsentimientoTable.fechaRevocado]?.toString()
    )
