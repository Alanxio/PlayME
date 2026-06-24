package playme;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

/**
 * MIDlet principal de PlayME.
 * Orquesta todas las pantallas y servicios.
 *
 * Flujo:
 * 1. startApp() -> carga settings -> crea servicios -> muestra PlayerCanvas
 * 2. PlayerCanvas es la pantalla principal
 * 3. LibraryCanvas para navegar la biblioteca
 * 4. SettingsCanvas para ajustes
 *
 * La navegacion entre pantallas se hace via Display.setCurrent()
 */
public class MusicPlayerMIDlet extends MIDlet implements MusicService.MusicServiceListener {

    private Display display;
    private Settings settings;
    private ServerClient client;
    private MusicService musicService;
    private PlaylistManager playlist;

    private PlayerCanvas playerCanvas;
    private LibraryCanvas libraryCanvas;
    private SettingsCanvas settingsCanvas;

    private boolean initialized = false;

    public void startApp() throws MIDletStateChangeException {
        if (!initialized) {
            initialize();
            initialized = true;
        }

        display.setCurrent(libraryCanvas);
    }

    private void initialize() {
        display = Display.getDisplay(this);

        // 1. Cargar ajustes
        settings = new Settings();

        // 2. Crear cliente HTTP
        client = new ServerClient(settings.serverUrl);

        // 3. Crear servicio de musica
        musicService = new MusicService(client);
        musicService.setListener(this);
        musicService.setVolume(settings.volume);

        // 4. Crear playlist manager
        playlist = new PlaylistManager(client, settings);

        // 5. Crear pantallas
        playerCanvas = new PlayerCanvas(this, musicService, playlist, settings, client);
        libraryCanvas = new LibraryCanvas(this, playlist, client, settings);
        libraryCanvas.resetToMenu();
        settingsCanvas = new SettingsCanvas(this, settings);

        // 6. No precargar el catalogo al inicio para ahorrar datos y memoria.
        // El usuario lo cargara explicitamente desde Biblioteca > Todas.
        playerCanvas.setLoading("Ve a Biblioteca > Todas");
    }

    public void pauseApp() {
        // Pausar reproduccion al minimizar
        if (musicService != null && musicService.isPlaying()) {
            musicService.togglePause();
        }
    }

    public void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        // Limpiar todo
        if (playerCanvas != null) {
            playerCanvas.stop();
        }
        if (musicService != null) {
            musicService.stopAndCleanup();
        }
        if (settings != null) {
            settings.volume = musicService != null ? musicService.getVolume() : 80;
            settings.save();
        }
        System.gc();
    }

    // --- Navegacion entre pantallas ---

    /** Muestra el reproductor */
    public void showPlayer() {
        display.setCurrent(playerCanvas);
    }

    /** Muestra la biblioteca */
    public void showLibrary() {
        libraryCanvas.applyTheme();
        display.setCurrent(libraryCanvas);
    }

    /** Muestra ajustes */
    public void showSettings() {
        settingsCanvas.applyTheme();
        display.setCurrent(settingsCanvas);
    }

    /**
     * Reproduce la cancion seleccionada en la biblioteca.
     * Llamado desde LibraryCanvas cuando el usuario elige una cancion.
     */
    public void playSelectedSong() {
        Song song = playlist.getCurrentSong();
        if (song != null) {
            playerCanvas.updateCurrentSong(song);
            musicService.playSong(song.id, settings.quality, song.mimeType);
            display.setCurrent(playerCanvas);
        }
    }

    /** Notificacion de cambio de tema */
    public void onThemeChanged() {
        playerCanvas.applyTheme();
        libraryCanvas.applyTheme();
        settingsCanvas.applyTheme();
    }

    // --- MusicServiceListener ---

    public void onPlaybackStarted() {
        playerCanvas.clearLoading();
        playerCanvas.repaint();
    }

    public void onPlaybackStopped() {
        playerCanvas.repaint();
    }

    public void onPlaybackBuffering() {
        playerCanvas.setLoading("Cargando audio...");
    }

    public void onPlaybackError(String error) {
        // Mostrar error concreto de Java
        playerCanvas.setError("Error de red", error);
        // Auto-limpiar despues de 10 segundos para poder leer el error completo
        new Thread() {
            public void run() {
                try { Thread.sleep(10000); } catch (Exception e) {}
                Song current = playlist.getCurrentSong();
                if (current != null) {
                    playerCanvas.updateCurrentSong(current);
                }
            }
        }.start();
    }

    public void onPlaybackComplete() {
        // Cancion terminada -> siguiente segun ajustes
        playerCanvas.onSongComplete();
    }
}
