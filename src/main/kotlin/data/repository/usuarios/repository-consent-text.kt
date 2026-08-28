package data.repository.usuarios

import data.models.usuarios.CreateConsentTextReq
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object ConsentTextTable : Table("consentimiento_texto") {
    val version = varchar("version", 20)
    val titulo = text("titulo")
    val cuerpo = text("cuerpo")
    val fechaPublicacion = datetime("fecha_publicacion")
    val vigente = bool("vigente")

    override val primaryKey = PrimaryKey(version)
}

data class ConsentTextRow(
    val version: String,
    val title: String,
    val body: String
)

class ConsentTextRepository {

    private suspend fun <T> tx(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction { block() }

    suspend fun createOrUpdate(req: CreateConsentTextReq): ConsentTextRow = tx {
        // marcar versiones anteriores como no vigentes
        ConsentTextTable.update({ ConsentTextTable.vigente eq true }) {
            it[ConsentTextTable.vigente] = false
        }

        // insertar esta versión como vigente (si no existe)
        ConsentTextTable.insertIgnore {
            it[ConsentTextTable.version] = req.version
            it[ConsentTextTable.titulo] = req.title
            it[ConsentTextTable.cuerpo] = req.body
            it[ConsentTextTable.vigente] = true
        }

        ConsentTextRow(
            version = req.version,
            title = req.title,
            body = req.body
        )
    }

    suspend fun getCurrent(): ConsentTextRow? = tx {
        ConsentTextTable
            .selectAll()                                // ✅ en vez de select { ... }
            .where { ConsentTextTable.vigente eq true } // ✅ nuevo DSL
            .orderBy(ConsentTextTable.fechaPublicacion, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.let { row ->
                ConsentTextRow(
                    version = row[ConsentTextTable.version],
                    title = row[ConsentTextTable.titulo],
                    body = row[ConsentTextTable.cuerpo]
                )
            }
    }
}
