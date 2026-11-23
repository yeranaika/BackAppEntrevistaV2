package MODELOS

import org.jetbrains.exposed.sql.Table

/** Una variable del modelo de visión en un instante de la simulación (migrations/025: prediccion_simulacion). */
object TablaPrediccionSimulacion : Table("prediccion_simulacion") {
    val id = uuid("id")
    val sesionId = uuid("sesion_id")
    /** Milisegundos desde el inicio de la simulación. */
    val ts = long("ts")
    val variable = varchar("variable", 60)
    val valor = double("valor")
    val nivel = short("nivel")

    override val primaryKey = PrimaryKey(id)
}
