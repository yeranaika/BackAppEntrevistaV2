package data.repository.usuarios

import data.models.usuarios.CreateConsentTextReq
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import java.io.File

object ConsentTextTable : Table("consentimiento_texto") {
    val version = varchar("version", 20)
    val titulo = text("titulo")
    val cuerpo = text("cuerpo")
    val fechaPublicacion = datetime("fecha_publicacion").clientDefault { LocalDateTime.now() }
    val vigente = bool("vigente").default(true)

    override val primaryKey = PrimaryKey(version)
}

data class ConsentTextRow(
    val version: String,
    val title: String,
    val body: String,
    val vigente: Boolean = true,
    val fechaPublicacion: String? = null
)

class ConsentTextRepository(private val db: Database? = null) {

    private suspend fun <T> tx(block: suspend Transaction.() -> T): T =
        if (db != null) newSuspendedTransaction(db = db) { block() }
        else newSuspendedTransaction { block() }

    suspend fun createOrUpdate(req: CreateConsentTextReq): ConsentTextRow = tx {
        // marcar versiones anteriores como no vigentes
        ConsentTextTable.update({ ConsentTextTable.vigente eq true }) {
            it[vigente] = false
        }

        // Si ya existe la versión, actualizarla; si no, insertarla como vigente
        val updated = ConsentTextTable.update({ ConsentTextTable.version eq req.version }) {
            it[titulo] = req.title
            it[cuerpo] = req.body
            it[vigente] = true
            it[fechaPublicacion] = LocalDateTime.now()
        }

        if (updated == 0) {
            ConsentTextTable.insert {
                it[version] = req.version
                it[titulo] = req.title
                it[cuerpo] = req.body
                it[vigente] = true
                it[fechaPublicacion] = LocalDateTime.now()
            }
        }

        ConsentTextRow(
            version = req.version,
            title = req.title,
            body = req.body,
            vigente = true
        )
    }

    suspend fun getCurrent(): ConsentTextRow = tx {
        val row = ConsentTextTable
            .selectAll()
            .where { ConsentTextTable.vigente eq true }
            .orderBy(ConsentTextTable.fechaPublicacion, SortOrder.DESC)
            .limit(1)
            .firstOrNull()

        if (row != null) {
            ConsentTextRow(
                version = row[ConsentTextTable.version],
                title = row[ConsentTextTable.titulo],
                body = row[ConsentTextTable.cuerpo],
                vigente = row[ConsentTextTable.vigente],
                fechaPublicacion = row[ConsentTextTable.fechaPublicacion].toString()
            )
        } else {
            // Si la base de datos no tiene un registro previo, inicializar desde docs/legal/EULA.md
            val eulaFile = File("docs/legal/EULA.md")
            val defaultBody = if (eulaFile.exists()) eulaFile.readText() else "Acuerdo de Licencia de Usuario Final (EULA) y Términos de Servicio."
            val defaultTitle = "Acuerdo de Licencia de Usuario Final (EULA)"
            val defaultVersion = "1.0.0"

            ConsentTextTable.insertIgnore {
                it[version] = defaultVersion
                it[titulo] = defaultTitle
                it[cuerpo] = defaultBody
                it[vigente] = true
                it[fechaPublicacion] = LocalDateTime.now()
            }

            ConsentTextRow(
                version = defaultVersion,
                title = defaultTitle,
                body = defaultBody,
                vigente = true
            )
        }
    }

    suspend fun getAllVersions(): List<ConsentTextRow> = tx {
        ConsentTextTable
            .selectAll()
            .orderBy(ConsentTextTable.fechaPublicacion, SortOrder.DESC)
            .map { row ->
                ConsentTextRow(
                    version = row[ConsentTextTable.version],
                    title = row[ConsentTextTable.titulo],
                    body = row[ConsentTextTable.cuerpo],
                    vigente = row[ConsentTextTable.vigente],
                    fechaPublicacion = row[ConsentTextTable.fechaPublicacion].toString()
                )
            }
    }
}
