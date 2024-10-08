# About plugin

### Plugin dependencies:

- Paper 1.16.5+ (newer versions haven't been tested, but probably work too)
- [AMusic 0.13](https://spigotmc.org/resources/108835)

# Building, testing, deployment

### Manual testing of plugin:

- Clone project
- Run `./gradlew runServer`
- Connect to server by the address `localhost:25565`

### Plugin build:

- Clone project
- Run `./gradlew build`
- Output jar will be `build/libs/ParkourBeat-<version>-all.jar`

### Plugin deployment on your server:

- Make sure if AMusic is installed
- Create world "pb_lobby" using [MultiVerse](https://spigotmc.org/resources/390) or simple copy world directory and name
  it "pb_lobby"
- Create world "pb_default_level" using [MultiVerse](https://spigotmc.org/resources/390) or simple copy world directory
  and name it "pb_default_level"
- Build plugin
- Copy `ParkourBeat-<version>-all.jar` to `plugins` folder
- Restart server to load plugin
- Edit all configs in `plugins/ParkourBeat` directory
- Restart server to apply new configs

# Information about collaboration

### Discord-server
https://discord.gg/eMbPn7nAUD
