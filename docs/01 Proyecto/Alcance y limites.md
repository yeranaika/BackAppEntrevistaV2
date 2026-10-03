---
tipo: proyecto
tags: [alcance, limites, restricciones]
fuente: DOCUMENTOS_INVESTIGACION/final.docx (4. Límites y restricciones)
actualizado: 2026-10-03
---

# Alcance y límites

## Dentro del alcance (backend)
- Cuentas: registro con correo, login con correo o Google, recuperación de contraseña por código, perfil y objetivo de carrera.
- Onboarding: área, cargo meta, nivel de experiencia.
- Banco de preguntas (alternativas, abiertas, video) administrado por admins, con generación asistida por IA.
- **Nivelación**, **práctica** y **simulación de entrevista**, con corrección automática.
- **Reporte de feedback** de la entrevista (freemium o IA para premium) y **progreso por skill**.
- Mercado laboral: cargos, skills requeridas por cargo y tendencias desde APIs de empleo.
- Consentimientos legales (EULA, términos, privacidad), recordatorios, suscripción freemium/premium (códigos y Google Play).
- Sincronización de intentos hechos sin conexión.

## Fuera del alcance (límites L01–L10 del proyecto)
| Código | Límite | Consecuencia para el backend |
|---|---|---|
| L01 | No reemplaza a un entrevistador humano | El feedback es formativo; no "aprueba" ni "reprueba" candidatos |
| L02 | No incluye contratación laboral | No hay postulación a ofertas ni contacto con empresas |
| L03 | No es evaluación psicológica clínica | Las habilidades blandas se evalúan como comunicación/estructura (STAR), sin diagnósticos |
| L04 | No se entrena un modelo de IA propio | Se usan APIs (OpenAI / Anthropic) detrás de una interfaz intercambiable |
| L05 | No cubre todos los rubros | Foco inicial en tecnología; el catálogo de cargos crece por datos, no por código |
| L06 | Offline limitado | Sin conexión solo se repasa lo guardado; el backend sincroniza intentos offline |
| L07 | Sin publicación comercial definitiva | Prototipo; sin operación masiva |
| L08 | Sin panel institucional completo | Hay roles `user`/`admin`; no hay paneles por institución |
| L09 | Soporte básico | Sin mesa de ayuda 24/7 |
| L10 | Pagos reales no obligatorios | Premium por códigos y Google Play (con modo simulado) |

## Restricciones
Tiempo (5 meses), presupuesto ($3.000.000 CLP: cuidar consumo de tokens de IA), recursos técnicos (3 personas),
dependencia de terceros (IA, APIs de empleo, Google), conectividad, **Ley 19.628** de protección de datos personales,
seguridad informática, escalabilidad inicial modesta, calidad del feedback de IA, compatibilidad de dispositivos.

## Reglas para no salirse del alcance
- No agregar funcionalidades "de versión comercial" sin acordarlo (el informe lo llama *scope creep*).
- Toda llamada a un servicio con costo (LLM, APIs de empleo) debe ser **opt-in o restringida** (premium, admin) y medible.
- Cualquier cambio de alcance se registra en [[Decisiones]].

Relacionado: [[Vision y objetivos]] · [[Requisitos y criterios de aceptacion]] · [[Pendientes y deuda tecnica]]
