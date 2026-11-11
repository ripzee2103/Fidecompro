# Fidecompro — Avance 2

Proyecto de **Programación Cliente-Servidor Concurrente**, continuación del Avance 1 (Fidecompro).

## Requisitos
- JDK 11 o superior (sin bibliotecas externas ni frameworks).
- Puerto TCP 5050 disponible en el equipo del servidor.

## Ejecución desde terminal
Desde la carpeta raíz `Fidecompro_Avance2`:

```bash
javac -encoding UTF-8 -d build src/Protocol.java src/Server.java src/Client.java
java -cp build fidecompro.Server
```

En otra terminal, desde la carpeta raíz:

```bash
java -cp build fidecompro.Client
```

También se admite `java Server 5051` y `java -cp build fidecompro.Client 127.0.0.1 5051`.

## Cuentas iniciales de demostración
- Administrador: `admin` / `Admin123!`
- Cajero: `cajero` / `Caja123!`

**Solo para pruebas académicas:** cambie estas credenciales antes de un uso real. El ejemplo usa SHA-256 sin sal para simplificar el ejercicio; en producción se requiere un derivador de contraseñas (PBKDF2, Argon2, etc.), cifrado TLS y gestión segura de credenciales.

## Funciones implementadas
- Inicio/cierre de sesión, permisos ADMIN/CAJERO.
- Registro y consulta de clientes.
- Registro y consulta de productos con categoría, precio y stock.
- Ajuste de existencias (administrador).
- Carrito y emisión de facturas con IVA de demostración del 13%.
- Consulta de facturas anteriores y exportación a TXT.
- Persistencia local mediante serialización Java en `fidecompro-data.ser`.
- Servidor TCP concurrente con pool de hilos y emisión de facturas sincronizada para evitar sobreventa.

## Correspondencia con historias de usuario del Avance 1
- HU-01: login; HU-02: registro de clientes; HU-04: productos y categorías (categoría como texto); HU-05: existencias (no edición de precio); HU-06: consulta de productos; HU-07: emisión de factura; HU-08: descuento atómico; HU-09: exportación TXT; HU-10: consulta de facturas; HU-12: cierre de sesión.
- HU-03: edición y búsqueda avanzada de clientes **pendiente**.
- HU-11: creación y desactivación de usuarios **pendiente**; sí existen roles predefinidos y validación de permisos.

## Conceptos del Avance 2
- Clases y objetos: entidades `Customer`, `Product`, `Invoice`, `User`.
- Herencia: `StandardLine extends InvoiceLine`.
- Polimorfismo: `InvoiceLine.subtotal()` invocado desde el servicio de facturación.
- Excepciones y colecciones: `BusinessException`, `Map`, `List`, `ConcurrentHashMap`.
- Serialización: persistencia `ObjectOutputStream`.
- GUI: Java Swing (`JFrame`, `JTable`, `JComboBox`, `JOptionPane`).
- Multihilos: `ExecutorService` y `ServerSocket`.

## Nota sobre GitHub
La rúbrica solicita participación mediante Pull Request. **El proyecto no se ha publicado ni se ha creado un Pull Request automáticamente.** Suba estos archivos a su repositorio, cree una rama y abra un PR con cambios significativos. Adjunte el enlace al entregar.

## Limitaciones académicas
Esta es una implementación de avance, no un sistema comercial. No incluye facturación electrónica de Hacienda, seguridad de producción, base de datos SQL ni edición avanzada de entidades. La tasa del 13% es un supuesto del diseño anterior, no una determinación fiscal para cada artículo.
