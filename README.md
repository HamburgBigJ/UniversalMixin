# UniversalMixin

This is a vibe-coded attempt to make a simple mixin library for my mündliche Überprüfung.
The theory behind it is to modify the bytecode of a specific class so that it inserts code at the top or bottom of a method.
An access widener is simply a tool to widen access to private or protected variables so you can get a reference to them.

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
