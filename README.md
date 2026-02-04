# Schematicraft API Clients

Official API client libraries for the [Schematicraft](https://schematicraft.com) In-Game API.

## Available Clients

| Language   | Status     | Directory            |
| ---------- | ---------- | -------------------- |
| Java       | ✅ Stable  | [java/](./java/)     |
| Kotlin     | ✅ Stable  | [kotlin/](./kotlin/) |
| Python     | 🚧 Planned | -                    |
| TypeScript | 🚧 Planned | -                    |

## API Documentation

Full API documentation (Swagger/OpenAPI): https://schematicraft.com/api/ingame/v1/docs

## Getting an API Key

1. Create an account at [schematicraft.com](https://schematicraft.com)
2. Go to your profile → "In-Game API Keys" tab: **https://schematicraft.com/profile#ingame-api-keys**
3. Create a new API key

For in-game mods, direct users to this URL to obtain their API key:

```
https://schematicraft.com/profile#ingame-api-keys
```

## Quick Start

See the README in each language directory for installation and usage instructions.

## API Endpoints

All clients support the following endpoints:

| Method | Endpoint                   | Description                        |
| ------ | -------------------------- | ---------------------------------- |
| GET    | `/me/status`               | Get user info and quotas           |
| GET    | `/me/library`              | Get user's schematics/bundles      |
| GET    | `/search`                  | Search schematics                  |
| POST   | `/search/ai`               | AI-powered natural language search |
| POST   | `/schematics`              | Upload a schematic                 |
| GET    | `/schematics/:id/download` | Download a schematic               |
| POST   | `/downloads/:id/feedback`  | Submit download feedback           |

## Contributing

Contributions are welcome! Please read the contributing guidelines in each language directory.

## License

MIT License - see [LICENSE](./LICENSE) for details.
