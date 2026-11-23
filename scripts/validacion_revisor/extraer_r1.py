"""Extrae R1 (historial ya evaluado) en modo SOLO LECTURA desde DBentrevista.

Consulta app.sesion_pregunta_respuesta (y app.reporte_entrevista para el modo de
evaluacion y estado de generacion) sin realizar ninguna escritura en la BD, y
genera scripts/validacion_revisor/R1_historial.csv con las columnas:
    pregunta_id,respuesta_usuario,score_o_veredicto_previo,modo
"""

from __future__ import annotations

import csv
import json
import os
import re
from pathlib import Path
from urllib.parse import urlparse

import psycopg2

ROOT_DIR = Path(__file__).resolve().parents[2]
OUT_DIR = Path(__file__).resolve().parent
R1_CSV = OUT_DIR / "R1_historial.csv"

SQL_R1 = """
SELECT
    COALESCE(spr.pregunta_id::text, '') AS pregunta_id,
    spr.transcripcion_audio AS respuesta_usuario,
    COALESCE(
        spr.puntaje_respuesta::text,
        spr.feedback_ia_tecnico->>'puntaje',
        spr.feedback_ia_blando->>'puntaje',
        ''
    ) AS score_o_veredicto_previo,
    COALESCE(
        spr.feedback_ia_tecnico->>'modo',
        spr.feedback_ia_blando->>'modo',
        r.modo_evaluacion,
        ''
    ) AS modo
FROM app.sesion_pregunta_respuesta spr
LEFT JOIN app.reporte_entrevista r
       ON r.sesion_id = spr.sesion_id
WHERE COALESCE(spr.tipo_pregunta, '') <> 'opcion_multiple'
  AND spr.transcripcion_audio IS NOT NULL
  AND btrim(spr.transcripcion_audio) <> ''
  AND (
      spr.puntaje_respuesta IS NOT NULL
      OR spr.feedback_ia_tecnico IS NOT NULL
      OR spr.feedback_ia_blando IS NOT NULL
  )
ORDER BY spr.fecha_respuesta NULLS LAST, spr.respuesta_id;
"""

SQL_DIAGNOSTICO = """
SELECT
    (SELECT count(*) FROM app.sesion_entrevista) AS total_sesiones,
    (SELECT count(*) FROM app.sesion_pregunta_respuesta) AS total_spr,
    (SELECT count(*) FROM app.sesion_pregunta_respuesta
      WHERE puntaje_respuesta IS NOT NULL
         OR feedback_ia_tecnico IS NOT NULL
         OR feedback_ia_blando IS NOT NULL) AS spr_con_evaluacion,
    (SELECT count(*) FROM app.sesion_pregunta_respuesta
      WHERE COALESCE(tipo_pregunta, '') <> 'opcion_multiple'
        AND transcripcion_audio IS NOT NULL
        AND btrim(transcripcion_audio) <> ''
        AND (puntaje_respuesta IS NOT NULL
             OR feedback_ia_tecnico IS NOT NULL
             OR feedback_ia_blando IS NOT NULL)) AS spr_abiertas_evaluadas,
    (SELECT count(*) FROM app.reporte_entrevista) AS total_reportes,
    (SELECT count(*) FROM app.respuesta_practica) AS total_respuestas_practica,
    (SELECT count(*) FROM app.pregunta) AS total_preguntas,
    (SELECT count(*) FROM app.pregunta WHERE respuesta_ideal IS NOT NULL AND btrim(respuesta_ideal) <> '') AS preguntas_con_ideal;
"""


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


def parsear_conexion() -> dict[str, str | int]:
    env_file = cargar_env(ROOT_DIR / ".env")
    db_url = os.environ.get("DB_URL") or env_file.get("DB_URL", "jdbc:postgresql://localhost:5432/DBentrevista")
    db_user = os.environ.get("DB_USER") or env_file.get("DB_USER", "root")
    db_pass = os.environ.get("DB_PASS") or env_file.get("DB_PASS", "")

    limpia = re.sub(r"^jdbc:", "", db_url)
    parsed = urlparse(limpia)
    return {
        "host": parsed.hostname or "localhost",
        "port": parsed.port or 5432,
        "dbname": (parsed.path or "/DBentrevista").lstrip("/"),
        "user": db_user,
        "password": db_pass,
    }


def main() -> None:
    cfg = parsear_conexion()
    conn = psycopg2.connect(
        host=cfg["host"],
        port=cfg["port"],
        dbname=cfg["dbname"],
        user=cfg["user"],
        password=cfg["password"],
    )
    try:
        conn.set_session(readonly=True, autocommit=True)
        with conn.cursor() as cur:
            cur.execute(SQL_DIAGNOSTICO)
            cols = [d.name for d in cur.description]
            diag = dict(zip(cols, cur.fetchone()))

            cur.execute(SQL_R1)
            filas = cur.fetchall()
    finally:
        conn.close()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with R1_CSV.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, quoting=csv.QUOTE_MINIMAL)
        writer.writerow(["pregunta_id", "respuesta_usuario", "score_o_veredicto_previo", "modo"])
        for fila in filas:
            writer.writerow(fila)

    print("DIAGNOSTICO_BD:", json.dumps(diag, ensure_ascii=False))
    print(f"R1_ESCRITO: {R1_CSV}")
    print(f"R1_FILAS_DATOS: {len(filas)}")


if __name__ == "__main__":
    main()
