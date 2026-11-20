# Interview Desk

App de escritorio (Java 17 + JavaFX + SQLite) para organizar entrevistas y contar horas.

## Requisitos
- JDK 17 o superior
- Maven

## Ejecutar
    mvn javafx:run

La base de datos queda en `~/InterviewDesk/desk.db` (carpeta de usuario).

## Carpetas esperadas
- Entrevistas: `Documentos/Interviewer Specialist/<Empresa - Persona>/` con un PDF (CV),
  un .txt con "meeting" en el nombre (enlace) y otro .txt (job description).
- Videos: `Videos/<Empresa>/<Empresa> <n>.mp4`

Las rutas se cambian en la pestaña Ajustes.

## Regla de horas
Se cuenta el tiempo exacto de cada reunión, sin redondeos, a una tarifa fija por hora ($15).
La tarifa y la regla se cambian solo en `src/main/java/desk/model/Billing.java`.
