# Diokko Player

A premium IPTV player for Amazon Fire TV, designed as a superior alternative to TiVimate.

## Features

- **M3U/M3U8 Playlist Support** - Add any standard M3U playlist
- **Xtream Codes API** - Full support for Xtream Codes providers
- **Live TV** - Watch live channels with EPG support
- **Movies (VOD)** - Browse and watch on-demand movies
- **TV Shows/Series** - Watch series with episode organization
- **Favorites** - Mark your favorite content
- **Watch History** - Resume where you left off
- **Category Filtering** - Browse content by category
- **Multi-Playlist** - Support for multiple providers simultaneously

## Screenshots

The app features a sleek, purple-themed design with:
- Left-side navigation (TV, Movies, Shows, Settings)
- Content cards with focus animations
- Fullscreen video player with overlay controls

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Steps

1. Open the project in Android Studio
2. Sync Gradle files
3. Connect your Fire TV device or use an emulator
4. Run the app

### Build APK
```bash
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build
```bash
./gradlew assembleRelease
```

## Installation on Fire TV

1. Enable Developer Options on Fire TV
   - Settings → My Fire TV → About → Click "Fire TV Stick" 7 times
2. Enable ADB Debugging
   - Settings → My Fire TV → Developer Options → ADB Debugging
3. Install via ADB:
   ```bash
   adb connect <fire-tv-ip>:5555
   adb install app-debug.apk
   ```

## Project Structure

```
app/src/main/java/com/diokko/player/
├── DiokkoApp.kt              # Application class
├── MainActivity.kt           # Main entry point
├── data/
│   ├── models/Models.kt      # Data models & Room entities
│   ├── database/Database.kt  # Room database & DAOs
│   ├── parser/
│   │   ├── M3UParser.kt      # M3U playlist parser
│   │   └── XtreamCodesApi.kt # Xtream Codes API client
│   └── repository/Repository.kt  # Data repositories
├── di/AppModule.kt           # Hilt dependency injection
├── player/
│   ├── VideoPlayerActivity.kt    # ExoPlayer video player
│   └── PlaybackService.kt        # Background playback
├── ui/
│   ├── components/
│   │   ├── SideNavigation.kt     # Side navigation rail
│   │   └── ContentCards.kt       # Channel/Movie/Series cards
│   ├── navigation/Navigation.kt  # Navigation routes
│   ├── screens/
│   │   ├── TvScreen.kt           # Live TV grid
│   │   ├── MoviesScreen.kt       # Movies grid
│   │   ├── ShowsScreen.kt        # Series grid
│   │   ├── SettingsScreen.kt     # Settings menu
│   │   ├── PlaylistsScreen.kt    # Playlist management
│   │   ├── AddPlaylistScreen.kt  # Add new playlist
│   │   └── AboutScreen.kt        # About page
│   ├── theme/Theme.kt            # Material3 theme
│   └── viewmodel/ViewModels.kt   # ViewModels
```

## Technology Stack

- **Kotlin** - Primary language
- **Jetpack Compose for TV** - Modern UI toolkit
- **Material3 TV** - TV-optimized components
- **Room** - Local database
- **Hilt** - Dependency injection
- **ExoPlayer (Media3)** - Video playback
- **Coil** - Image loading
- **OkHttp** - HTTP client
- **Kotlin Coroutines & Flow** - Async operations

## Remote Control Navigation

- **D-pad** - Navigate between items
- **OK/Select** - Select item / Play/Pause
- **Left/Right (in player)** - Seek -/+ 10 seconds
- **Back** - Go back / Hide controls

## Adding Playlists

### M3U Playlist
1. Go to Settings → Playlists
2. Click "Add Playlist"
3. Select "M3U Playlist"
4. Enter a name and the M3U URL
5. Save

### Xtream Codes
1. Go to Settings → Playlists
2. Click "Add Playlist"
3. Select "Xtream Codes"
4. Enter server URL, username, and password
5. Save

## License

This project is for personal use only.

## Credits

Developed with ❤️ for the Fire TV community.
