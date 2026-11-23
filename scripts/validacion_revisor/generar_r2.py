"""Genera R2 (golden cases humanos) a partir de BasedeDatos.EntrevistaApp.Seeds.sql.

Lee las 14 preguntas con respuesta_ideal de src/DB/BasedeDatos.EntrevistaApp.Seeds.sql,
consulta en modo SOLO LECTURA los pregunta_id actuales en DBentrevista.app.pregunta
para cruzarlos por enunciado, verifica coherencia 100 % y escribe:
    scripts/validacion_revisor/R2_golden.csv

Casos por pregunta (6 por pregunta -> 84 filas de datos):
  1. IDENTICA       : respuesta_ideal literal de Seeds.sql
  2. CASI_IDENTICA  : parafrasis fiel con sinonimos menores y reordenamiento
  3. PARCIAL        : cubre ~50 % de los conceptos clave y omite la otra mitad
  4. ALEATORIA      : respuesta_ideal literal de otra pregunta distinta del banco
  5. OFF_TOPIC      : parrafo coherente en espanol sin relacion con la pregunta
  6. EVASIVA_CORTA  : respuesta breve evasiva o de desconocimiento real de candidato
"""

from __future__ import annotations

import csv
import json
import os
import re
from collections import Counter
from pathlib import Path
from urllib.parse import urlparse

import psycopg2

ROOT_DIR = Path(__file__).resolve().parents[2]
SEEDS_SQL = ROOT_DIR / "src" / "DB" / "BasedeDatos.EntrevistaApp.Seeds.sql"
OUT_DIR = Path(__file__).resolve().parent
R2_CSV = OUT_DIR / "R2_golden.csv"


def cargar_env(env_path: Path) -> dict[str, str]:
    valores: dict[str, str] = {}
    if not env_path.exists():
        return valores
    for linea in env_path.read_text(encoding="utf-8").splitlines():
        linea = linea.strip()
        if not linea or linea.startswith("#") or "=" not in linea:
            continue
        k, v = linea.split("=", 1)
        valores[k.strip()] = v.strip().strip('"').strip("'")
    return valores


def consultar_preguntas_bd() -> dict[str, dict[str, str]]:
    """Lee app.pregunta en modo SOLO LECTURA indexado por enunciado."""
    env_file = cargar_env(ROOT_DIR / ".env")
    db_url = os.environ.get("DB_URL") or env_file.get("DB_URL", "jdbc:postgresql://localhost:5432/DBentrevista")
    db_user = os.environ.get("DB_USER") or env_file.get("DB_USER", "root")
    db_pass = os.environ.get("DB_PASS") or env_file.get("DB_PASS", "")
    parsed = urlparse(re.sub(r"^jdbc:", "", db_url))

    conn = psycopg2.connect(
        host=parsed.hostname or "localhost",
        port=parsed.port or 5432,
        dbname=(parsed.path or "/DBentrevista").lstrip("/"),
        user=db_user,
        password=db_pass,
    )
    try:
        conn.set_session(readonly=True, autocommit=True)
        with conn.cursor() as cur:
            cur.execute(
                """
                SELECT
                    p.pregunta_id::text,
                    COALESCE(s.nombre, '') AS skill,
                    COALESCE(c.nombre, '') AS cargo,
                    p.tipo_pregunta,
                    p.categoria_habilidad,
                    p.nivel_dificultad,
                    p.enunciado,
                    p.respuesta_ideal,
                    p.rubrica_evaluacion::text
                FROM app.pregunta p
                LEFT JOIN app.skill s ON s.skill_id = p.skill_id
                LEFT JOIN app.cargo c ON c.cargo_id = p.cargo_id
                ORDER BY p.fecha_creacion, p.pregunta_id;
                """
            )
            filas = cur.fetchall()
    finally:
        conn.close()

    resultado: dict[str, dict[str, str]] = {}
    for row in filas:
        resultado[row[6]] = {
            "pregunta_id": row[0],
            "skill": row[1],
            "cargo": row[2],
            "tipo_pregunta": row[3],
            "categoria_habilidad": row[4],
            "nivel_dificultad": row[5],
            "enunciado": row[6],
            "respuesta_ideal": row[7] or "",
            "rubrica_evaluacion": row[8] or "",
        }
    return resultado


def extraer_preguntas_de_seeds(sql_path: Path) -> list[dict[str, str]]:
    """Extrae en orden las sentencias INSERT INTO pregunta de Seeds.sql."""
    texto = sql_path.read_text(encoding="utf-8")
    bloques = re.findall(
        r"INSERT INTO pregunta\s*\([^)]+\)\s*SELECT.*?AND p\.nivel_dificultad = v\.nivel\);",
        texto,
        flags=re.DOTALL,
    )
    preguntas: list[dict[str, str]] = []

    for idx, bloque in enumerate(bloques, start=1):
        m_meta = re.search(
            r"'(opcion_multiple|abierta_texto|simulacion_video)',\s*'(tecnica|blanda)',\s*v\.nivel,\s*v\.enunciado,\s*(.*?),\s*'(\{.*?\})'",
            bloque,
            flags=re.DOTALL,
        )
        m_val = re.search(
            r"\(VALUES\s*\('(junior|semisenior|senior)',\s*'((?:''|[^'])*)'\)\)\s*AS v\(nivel, enunciado\)",
            bloque,
            flags=re.DOTALL,
        )
        m_skill = re.search(r"WHERE\s+s\.nombre\s*=\s*'((?:''|[^'])*)'", bloque)
        m_cargo = re.search(r"c\.nombre\s*=\s*'((?:''|[^'])*)'", bloque)

        if not (m_meta and m_val and m_skill):
            raise ValueError(f"No se pudo parsear el bloque de pregunta #{idx} en {sql_path}")

        tipo_pregunta, categoria, ideal_raw, rubrica_raw = m_meta.groups()
        nivel, enunciado_raw = m_val.groups()

        # Concatenar literales SQL adyacentes separados por saltos de linea
        partes_ideal = re.findall(r"'((?:''|[^'])*)'", ideal_raw)
        respuesta_ideal = "".join(p.replace("''", "'") for p in partes_ideal)
        enunciado = enunciado_raw.replace("''", "'")
        rubrica = rubrica_raw.replace("''", "'")

        preguntas.append(
            {
                "seed_num": idx,
                "skill": m_skill.group(1).replace("''", "'"),
                "cargo": m_cargo.group(1).replace("''", "'") if m_cargo else "",
                "tipo_pregunta": tipo_pregunta,
                "categoria_habilidad": categoria,
                "nivel_dificultad": nivel,
                "enunciado": enunciado,
                "respuesta_ideal": respuesta_ideal,
                "rubrica_evaluacion": rubrica,
            }
        )
    return preguntas


# Casos humanos curados por indice de pregunta en Seeds.sql (1..14).
# ALEATORIA referencia el seed_num de otra pregunta distinta para tomar su respuesta_ideal literal.
CASOS_HUMANOS: dict[int, dict[str, object]] = {
    1: {
        "CASI_IDENTICA": (
            "En Kotlin, var define una referencia mutable cuyo valor puede modificarse posteriormente, "
            "mientras que val crea una referencia inmutable de solo lectura que no admite reasignación tras inicializarse. "
            "La distinción recae únicamente en la referencia y no en la instancia: ambos pueden apuntar a objetos cuyo estado interno sí cambie."
        ),
        "PARCIAL": (
            "val se usa para declarar variables que no se pueden reasignar después de darles un valor inicial, "
            "mientras que var permite cambiar el valor asignado las veces que sea necesario."
        ),
        "ALEATORIA_SEED": 6,
        "OFF_TOPIC": (
            "Para preparar un buen pan de masa madre conviene fermentar la mezcla lentamente en refrigeración durante toda la noche, "
            "mantener una hidratación cercana al setenta por ciento y hornear con vapor en los primeros veinte minutos para lograr una corteza crujiente."
        ),
        "EVASIVA_CORTA": "La verdad no me acuerdo bien, creo que depende del tipo de dato que estés usando.",
    },
    2: {
        "CASI_IDENTICA": (
            "Tanto launch como async son constructores de corrutinas dentro del mismo scope capaces de ejecutarse en paralelo. "
            "Sin embargo, async crea una corrutina que entrega un Deferred<T> para recuperar el valor resultante llamando a .await(), "
            "mientras que launch dispara una tarea fire-and-forget que devuelve un Job sin retornar un valor."
        ),
        "PARCIAL": (
            "launch sirve para lanzar una corrutina que devuelve un Job y no retorna un resultado, "
            "mientras que async devuelve un Deferred para obtener el resultado después."
        ),
        "ALEATORIA_SEED": 9,
        "OFF_TOPIC": (
            "El calendario de lluvias en la zona central suele concentrarse entre mayo y agosto, "
            "por lo que los agricultores planifican la siembra de invierno con semanas de anticipación y revisan periódicamente la humedad del suelo."
        ),
        "EVASIVA_CORTA": "No he trabajado mucho con coroutines, así que no sabría explicar la diferencia.",
    },
    3: {
        "CASI_IDENTICA": (
            "Implementaría un patrón Cache-Aside con Redis como caché de primer nivel y tiempo de expiración (TTL) configurable. "
            "Ante cada solicitud, el servicio busca primero en Redis; en caso de cache miss, consulta PostgreSQL mediante un pool HikariCP, "
            "serializa en JSON el resultado para almacenarlo en Redis y lo retorna. En la capa reactiva utilizaría Kotlin Flow para emitir los datos "
            "y SharedFlow o StateFlow para compartir el estado entre corrutinas concurrentes, contemplando invalidación al editar preguntas bajo CQRS y manejo robusto de errores si Redis falla."
        ),
        "PARCIAL": (
            "Usaría Redis delante de PostgreSQL con un TTL configurado: cuando llega una petición, el servicio revisa primero en Redis "
            "y, si no está en caché, consulta la base de datos y guarda el resultado en Redis para las siguientes solicitudes."
        ),
        "ALEATORIA_SEED": 13,
        "OFF_TOPIC": (
            "En el último campeonato regional de ciclismo de ruta el recorrido superó los ciento cuarenta kilómetros con dos puertos de montaña exigentes, "
            "donde los escaladores marcaron la diferencia en los últimos veinte kilómetros."
        ),
        "EVASIVA_CORTA": "Pondría una caché simple en memoria para que el endpoint responda más rápido.",
    },
    4: {
        "CASI_IDENTICA": (
            "Se utiliza la cláusula HAVING. Mientras WHERE aplica condiciones sobre registros individuales antes de agrupar, "
            "HAVING evalúa los grupos ya formados tras GROUP BY y permite usar funciones de agregación tales como COUNT, SUM o AVG."
        ),
        "PARCIAL": "Se usa HAVING porque permite filtrar los resultados después de hacer un GROUP BY.",
        "ALEATORIA_SEED": 7,
        "OFF_TOPIC": (
            "Al elegir una guitarra acústica para principiantes conviene probar el grosor del mástil, "
            "revisar la altura de las cuerdas respecto al diapasón y verificar que las clavijas mantengan la afinación estable."
        ),
        "EVASIVA_CORTA": "Creo que es WHERE o algo parecido, no estoy seguro.",
    },
    5: {
        "CASI_IDENTICA": (
            "El LEFT OUTER JOIN, conocido habitualmente como LEFT JOIN. Devuelve todos los registros de la tabla situada a la izquierda "
            "y, en los casos donde no existe correspondencia en la tabla derecha, completa los campos de esa segunda tabla con valores NULL."
        ),
        "PARCIAL": "Es el LEFT JOIN, que trae todos los registros de la tabla izquierda de la consulta.",
        "ALEATORIA_SEED": 1,
        "OFF_TOPIC": (
            "Durante la migración otoñal muchas aves costeras recorren miles de kilómetros siguiendo corrientes de viento favorables "
            "y descansan en humedales protegidos donde encuentran alimento abundante."
        ),
        "EVASIVA_CORTA": "No recuerdo cuál JOIN era exactamente, sé que hay varios tipos.",
    },
    6: {
        "CASI_IDENTICA": (
            "En PostgreSQL, el índice por defecto es B-Tree, un árbol balanceado diseñado para comparaciones de igualdad y rangos "
            "(=, <, >, BETWEEN o prefijos LIKE 'foo%') sobre columnas escalares como fechas, UUID o estados, compatible con casi cualquier tipo de dato. "
            "Por otro lado, GIN (Generalized Inverted Index) descompone e indexa cada elemento interno de tipos compuestos como JSONB, arreglos, hstore "
            "o búsqueda de texto completo con tsvector, resolviendo consultas de contención como contexto_evaluacion_ia @> '{\"metodo\":\"STAR\"}' en tiempo O(log n). "
            "Por ello opto por B-Tree para filtros escalares y por GIN para búsquedas dentro de JSONB o arrays."
        ),
        "PARCIAL": (
            "Un índice B-Tree es un árbol balanceado que viene por defecto en PostgreSQL y sirve para búsquedas por igualdad o rangos "
            "en columnas normales como fechas o IDs. El índice GIN en cambio se usa para búsquedas más complejas sobre JSON."
        ),
        "ALEATORIA_SEED": 8,
        "OFF_TOPIC": (
            "La restauración de libros antiguos requiere controlar la humedad relativa de la sala, limpiar el papel con brochas suaves "
            "y utilizar adhesivos reversibles libres de ácido para no deteriorar las hojas con el paso de los años."
        ),
        "EVASIVA_CORTA": "Ambos son índices para acelerar consultas en la base de datos.",
    },
    7: {
        "CASI_IDENTICA": (
            "Corresponde devolver el estado HTTP 201 Created, el cual comunica que la petición se procesó con éxito y dio origen a un nuevo recurso. "
            "Por convención, la respuesta suele incluir la representación del recurso recién creado en el cuerpo o su URI en la cabecera Location."
        ),
        "PARCIAL": "Se debe retornar el código 201 Created porque indica que el recurso se creó correctamente.",
        "ALEATORIA_SEED": 4,
        "OFF_TOPIC": (
            "Para cuidar plantas de interior en departamentos con poca luz natural es recomendable regar solo cuando el sustrato esté seco, "
            "evitar corrientes de aire frío y rotar la maceta cada dos semanas."
        ),
        "EVASIVA_CORTA": "Creo que normalmente se devuelve 200 y ya está.",
    },
    8: {
        "CASI_IDENTICA": (
            "Bajo una ruta versionada /api/v1/ y protegida con autenticación JWT, propondría: POST /sessions para iniciar la simulación "
            "devolviendo 201 con sesion_id, webrtc_room_id y primera_pregunta; GET /sessions/{id}/question para recuperar la pregunta siguiente; "
            "POST /sessions/{id}/answers para registrar la respuesta en texto o audio transcrito; POST /sessions/{id}/finish para cerrar la sesión "
            "y disparar en segundo plano el cálculo del reporte respondiendo 202 Accepted; y GET /sessions/{id}/report devolviendo 200 cuando esté listo "
            "o 202 con estado \"generando\" mientras siga procesándose, añadiendo paginación en listados cuando corresponda."
        ),
        "PARCIAL": (
            "Crearía un POST /sessions para iniciar la entrevista y devolver el identificador de la sesión, "
            "un POST /sessions/{id}/answers para guardar las respuestas del usuario y un GET /sessions/{id}/report para consultar el reporte cuando termine."
        ),
        "ALEATORIA_SEED": 3,
        "OFF_TOPIC": (
            "El museo de historia natural inauguró una exhibición temporal dedicada a fósiles marinos del jurásico, "
            "con visitas guiadas cada hora y talleres didácticos para estudiantes de enseñanza básica."
        ),
        "EVASIVA_CORTA": "Haría un par de endpoints GET y POST con JSON para todo el flujo.",
    },
    9: {
        "CASI_IDENTICA": (
            "Una buena respuesta consiste en una narrativa clara y ordenada de entre sesenta y noventa segundos que abarque cuatro puntos: "
            "una breve presentación profesional, la formación o experiencia pertinente al cargo, una motivación auténtica y concreta hacia la compañía y el puesto, "
            "y el valor específico que puedo aportar al equipo, manteniéndome con tono seguro, contacto visual constante y evitando muletillas."
        ),
        "PARCIAL": (
            "Hacer una presentación profesional breve de unos sesenta segundos comentando mi formación y experiencia más relevante para el puesto, "
            "hablando con tono seguro."
        ),
        "ALEATORIA_SEED": 12,
        "OFF_TOPIC": (
            "Al planificar un viaje en tren por el sur conviene reservar los asientos con anticipación en temporada alta, "
            "llevar ropa impermeable por los cambios bruscos de clima y revisar los horarios de conexión en la estación central."
        ),
        "EVASIVA_CORTA": "Soy una persona responsable y con ganas de trabajar aquí porque necesito experiencia.",
    },
    10: {
        "CASI_IDENTICA": (
            "Estructuraría la respuesta con el método STAR: primero la Situación describiendo el contexto del proyecto y la relación laboral con el colega; "
            "luego la Tarea exponiendo el conflicto concreto, ya sea por criterio técnico, prioridades o comunicación; "
            "después las Acciones específicas emprendidas mediante diálogo directo, mediación, datos objetivos y búsqueda de consenso; "
            "y finalmente el Resultado positivo reflejando el acuerdo logrado, la buena relación conservada y el aprendizaje, "
            "manteniendo siempre empatía, orientación a soluciones y sin descalificar al compañero."
        ),
        "PARCIAL": (
            "Contaría con estructura STAR el contexto del proyecto y el conflicto específico que surgió por diferencias de criterio técnico, "
            "explicando que conversé directamente con el compañero para intentar llegar a un acuerdo."
        ),
        "ALEATORIA_SEED": 14,
        "OFF_TOPIC": (
            "Para mejorar el rendimiento de un huerto urbano en balcones pequeños es útil combinar especies aromáticas con tomates cherry, "
            "usar macetas con buen drenaje y abonar con compost orgánico al inicio de la primavera."
        ),
        "EVASIVA_CORTA": "Casi nunca tengo conflictos con nadie, simplemente dejo que el otro decida para no discutir.",
    },
    11: {
        "CASI_IDENTICA": (
            "Aplicaría el marco STAR partiendo por la Situación de un proyecto crítico bajo presión de plazos o recursos "
            "y la Tarea centrada en definir una decisión técnica compleja de arquitectura, tecnología o deuda técnica. "
            "En las Acciones explicaría el proceso seguido: levantamiento de datos, análisis de ventajas y desventajas, "
            "comunicación transparente con el equipo y asunción plena de responsabilidad. Cerraría con un Resultado medible, "
            "incluyendo éxitos o ajustes necesarios, y una reflexión honesta sobre liderazgo, comunicación, ownership y aprendizaje a partir de los errores."
        ),
        "PARCIAL": (
            "Describiría un proyecto con mucha presión de tiempo donde tuve que tomar una decisión técnica difícil sobre la arquitectura, "
            "evaluando pros y contras y comunicándosela al equipo."
        ),
        "ALEATORIA_SEED": 2,
        "OFF_TOPIC": (
            "El cine documental latinoamericano de la última década ha ganado presencia en festivales internacionales "
            "gracias a coproducciones independientes y fondos públicos que financian investigación y montaje."
        ),
        "EVASIVA_CORTA": "Cuando el equipo no se pone de acuerdo normalmente le pregunto al jefe qué hacer.",
    },
    12: {
        "CASI_IDENTICA": (
            "El ciclo de vida de una Activity en Android representa la secuencia de estados por los que pasa desde su creación hasta su destrucción. "
            "Sus callbacks esenciales son onCreate para inicializar, onStart cuando pasa a ser visible, onResume cuando queda interactuable en primer plano, "
            "onPause al perder el foco, onStop al dejar de verse y onDestroy al destruirse; complementados por onRestart al volver desde detenida "
            "y onSaveInstanceState para respaldar el estado antes de la destrucción."
        ),
        "PARCIAL": (
            "Es la serie de estados por los que pasa un Activity desde que nace hasta que se cierra. "
            "Los métodos principales son onCreate cuando se crea, onResume cuando el usuario la ve y onDestroy cuando termina."
        ),
        "ALEATORIA_SEED": 5,
        "OFF_TOPIC": (
            "Las observaciones astronómicas desde el desierto del norte aprovechan la baja humedad atmosférica "
            "y los cielos despejados durante más de trescientas noches al año para estudiar galaxias lejanas."
        ),
        "EVASIVA_CORTA": "Es cuando se abre y se cierra la pantalla de la aplicación.",
    },
    13: {
        "CASI_IDENTICA": (
            "Dividiría la app en tres capas: Presentation con Jetpack Compose y ViewModel exponiendo StateFlow y SharedFlow; "
            "Domain con interfaces y casos de uso; y Data con implementaciones de repositorios y fuentes de datos que abstraen si provienen de la API, Room o caché. "
            "La simulación se orquestaría con casos de uso dedicados: CameraUseCase sobre CameraX y WebRTC, QuestionUseCase para la API "
            "y MetricaUseCase para el envío de frames. Emplearía Hilt como inyector de dependencias, controlaría el ciclo de vida de la cámara en onStart y onStop, "
            "y ejecutaría el análisis de frames en Dispatchers.Default para no bloquear el hilo de interfaz."
        ),
        "PARCIAL": (
            "Separaría la aplicación en capas de Presentation con ViewModel y Compose, Domain con casos de uso y Data con repositorios. "
            "El ViewModel expondría el estado con StateFlow y usaría Hilt para inyectar las dependencias."
        ),
        "ALEATORIA_SEED": 10,
        "OFF_TOPIC": (
            "El mantenimiento preventivo de una bicicleta de montaña incluye limpiar y lubricar la cadena después de rutas con barro, "
            "revisar el desgaste de las pastillas de freno y comprobar la presión de ambos neumáticos."
        ),
        "EVASIVA_CORTA": "Usaría MVVM separando las vistas de los datos para que el código quede más ordenado.",
    },
    14: {
        "CASI_IDENTICA": (
            "Respondería siguiendo el esquema STAR frente a la Situación de un servicio productivo crítico caído afectando usuarios "
            "y la Tarea de diagnosticar y restablecer la operación cuanto antes. Como Acciones: primero inspecciono los logs de aplicación e infraestructura; "
            "segundo reviso métricas de CPU, memoria, base de datos y red; tercero verifico el último despliegue o cambio reciente; "
            "cuarto aplico un rollback inmediato si existe uno seguro; quinto mantengo informados al equipo y a los stakeholders; "
            "y sexto abordo la causa raíz dejando un postmortem escrito. El Resultado es el servicio recuperado, todos comunicados y el aprendizaje registrado."
        ),
        "PARCIAL": (
            "Primero revisaría los logs del servidor para ver qué error está ocurriendo y chequearía las métricas de CPU, memoria y base de datos. "
            "También miraría si hubo un despliegue reciente para tratar de arreglar el problema rápido."
        ),
        "ALEATORIA_SEED": 11,
        "OFF_TOPIC": (
            "En la cocina japonesa tradicional el caldo dashi se elabora remojando alga kombu en agua fría y llevándola casi a ebullición "
            "antes de añadir copos de bonito seco, logrando un sabor umami delicado en pocos minutos."
        ),
        "EVASIVA_CORTA": "Reiniciaría el servidor para ver si vuelve a funcionar y si no pediría ayuda.",
    },
}

ESPEC_ETIQUETAS: list[tuple[str, str, int, int]] = [
    ("IDENTICA", "EXCELENTE", 85, 100),
    ("CASI_IDENTICA", "EXCELENTE", 75, 98),
    ("PARCIAL", "PARCIAL", 35, 65),
    ("ALEATORIA", "INCORRECTA", 0, 25),
    ("OFF_TOPIC", "INCORRECTA", 0, 20),
    ("EVASIVA_CORTA", "INCORRECTA", 0, 15),
]


def main() -> None:
    preguntas = extraer_preguntas_de_seeds(SEEDS_SQL)
    assert len(preguntas) == 14, f"Se esperaban 14 preguntas en Seeds.sql, se encontraron {len(preguntas)}"

    en_bd = consultar_preguntas_bd()
    por_seed = {q["seed_num"]: q for q in preguntas}

    coincidencias_bd = 0
    diferencias_ideal = 0
    for q in preguntas:
        bd = en_bd.get(q["enunciado"])
        if bd:
            coincidencias_bd += 1
            q["pregunta_id"] = bd["pregunta_id"]
            if bd["respuesta_ideal"] != q["respuesta_ideal"]:
                diferencias_ideal += 1
        else:
            q["pregunta_id"] = f"SEED_{q['seed_num']:02d}"

    filas_salida: list[dict[str, object]] = []
    for q in preguntas:
        num = int(q["seed_num"])
        curados = CASOS_HUMANOS[num]
        aleatoria_num = int(curados["ALEATORIA_SEED"])
        assert aleatoria_num != num, f"ALEATORIA de Q{num} no puede apuntar a la misma pregunta"

        textos_por_tipo = {
            "IDENTICA": q["respuesta_ideal"],
            "CASI_IDENTICA": str(curados["CASI_IDENTICA"]),
            "PARCIAL": str(curados["PARCIAL"]),
            "ALEATORIA": por_seed[aleatoria_num]["respuesta_ideal"],
            "OFF_TOPIC": str(curados["OFF_TOPIC"]),
            "EVASIVA_CORTA": str(curados["EVASIVA_CORTA"]),
        }

        for tipo_caso, etiqueta, p_min, p_max in ESPEC_ETIQUETAS:
            respuesta_usuario = textos_por_tipo[tipo_caso]
            assert respuesta_usuario.strip(), f"Texto vacio en Q{num:02d}_{tipo_caso}"
            filas_salida.append(
                {
                    "caso_id": f"Q{num:02d}_{tipo_caso}",
                    "pregunta_seed_num": num,
                    "pregunta_id": q["pregunta_id"],
                    "tipo_pregunta": q["tipo_pregunta"],
                    "categoria_habilidad": q["categoria_habilidad"],
                    "nivel_dificultad": q["nivel_dificultad"],
                    "skill": q["skill"],
                    "enunciado": q["enunciado"],
                    "respuesta_ideal": q["respuesta_ideal"],
                    "rubrica_evaluacion": q["rubrica_evaluacion"],
                    "tipo_caso": tipo_caso,
                    "respuesta_usuario": respuesta_usuario,
                    "etiqueta_esperada": etiqueta,
                    "puntaje_min_esperado": p_min,
                    "puntaje_max_esperado": p_max,
                }
            )

    columnas = [
        "caso_id",
        "pregunta_seed_num",
        "pregunta_id",
        "tipo_pregunta",
        "categoria_habilidad",
        "nivel_dificultad",
        "skill",
        "enunciado",
        "respuesta_ideal",
        "rubrica_evaluacion",
        "tipo_caso",
        "respuesta_usuario",
        "etiqueta_esperada",
        "puntaje_min_esperado",
        "puntaje_max_esperado",
    ]

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with R2_CSV.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columnas, quoting=csv.QUOTE_MINIMAL)
        writer.writeheader()
        writer.writerows(filas_salida)

    resumen = {
        "preguntas_seeds": len(preguntas),
        "coincidencias_bd": coincidencias_bd,
        "diferencias_respuesta_ideal_bd_vs_seeds": diferencias_ideal,
        "total_casos_r2": len(filas_salida),
        "por_tipo_caso": dict(Counter(r["tipo_caso"] for r in filas_salida)),
        "por_etiqueta_esperada": dict(Counter(r["etiqueta_esperada"] for r in filas_salida)),
        "por_tipo_pregunta": dict(Counter(r["tipo_pregunta"] for r in filas_salida)),
    }
    print("RESUMEN_R2:", json.dumps(resumen, ensure_ascii=False))
    print(f"R2_ESCRITO: {R2_CSV}")


if __name__ == "__main__":
    main()
