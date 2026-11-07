# UniversalMixin

Proof of consept java mod injecor with bitcode manimulation.

Beispiel für Anwendung: [RaumPlanner][https://github.com/HamburgBigJ/Raumplanner-Informatik]

## AI

Ai helpt in the createon of thes project.


## Build and use the agent

```sh
./gradlew build
```

Outputs in `build/libs`:

- `UniversalMixin-1.0-SNAPSHOT.jar`: library classes; requires ASM and ClassGraph at runtime.
- `UniversalMixin-1.0-SNAPSHOT-agent.jar`: standalone agent including runtime dependencies.

The build also produces `UniversalMixin-1.0-SNAPSHOT-example.jar` (executable
application) and `UniversalMixin-1.0-SNAPSHOT-example-mod.jar` (callbacks only).

Launch the example with its separate mod:

```sh
java -javaagent:build/libs/UniversalMixin-1.0-SNAPSHOT-agent.jar=build/libs/UniversalMixin-1.0-SNAPSHOT-example-mod.jar \
  -jar build/libs/UniversalMixin-1.0-SNAPSHOT-example.jar
```

For your own application:

```sh
java -javaagent:universal-mixin-agent.jar=mods/my-mod.jar -jar application.jar
```

```sh
java "-javaagent:universal-mixin-agent.jar=mods/first.jar:mods/second.jar" -jar application.jar
```

```sh
java -jar build/libs/UniversalMixin-1.0-SNAPSHOT-example.jar
```
