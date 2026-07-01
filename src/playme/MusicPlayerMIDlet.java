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

    // OOM retry state
    private Song pendingSong = null;
    private int oomRetryCount = 0;
    private static final int MAX_OOM_RETRIES = 3;

    public void startApp() throws MIDletStateChangeException {
        // Limpiar chunks residuales de sesiones anteriores (por cierre con boton rojo)
        RecordStoreInputStream.clearAllChunks();

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
        // Pausar reproduccion al minimizar y limpiar chunks para evitar
        // acumulacion si el usuario cierra la app mientras esta pausada
        if (musicService != null) {
            if (musicService.isPlaying()) {
                musicService.togglePause();
            }
            musicService.stopAndCleanup();
        }
    }

    public void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        try {
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
        } finally {
            // Asegurar que los chunks de RMS se borren siempre,
            // incluso si destroyApp se llama de forma inesperada
            RecordStoreInputStream.clearAllChunks();
            System.gc();
        }
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
     * Calcula el bitrate segun perfil y duracion de la cancion.
     */
    public void playSelectedSong() {
        Song song = playlist.getCurrentSong();
        if (song != null) {
            pendingSong = song;
            oomRetryCount = 0;
            playerCanvas.updateCurrentSong(song);
            int bitrate = settings.calculateBitrate(song.duration);
            System.out.println("[MIDlet] playSelectedSong id=" + song.id
                + " dur=" + song.duration + "s profile=" + settings.qualityProfile
                + " bitrate=" + bitrate + "k");
            musicService.playSong(song.id, bitrate, song.mimeType);
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
        // Reproduccion exitosa: resetear estado de OOM
        pendingSong = null;
        oomRetryCount = 0;
        playerCanvas.clearLoading();
        playerCanvas.repaint();
    }

    public void onPlaybackStopped() {
        playerCanvas.repaint();
    }

    public void onPlaybackBuffering() {
        playerCanvas.setLoading("Cargando audio...");
    }

    public void onDownloadProgress(int percent, int downloaded, int total) {
        playerCanvas.setDownloadProgress(percent, downloaded, total);
    }

    public void onPlaybackError(String error) {
        // Detectar OutOfMemoryError para reintento con perfil inferior
        if (error != null && error.indexOf("OutOfMemory") >= 0 && pendingSong != null) {
            oomRetryCount++;
            if (oomRetryCount <= MAX_OOM_RETRIES && settings.downgradeProfile()) {
                // Liberar memoria y reintentar con perfil inferior
                System.gc();
                int newBitrate = settings.calculateBitrate(pendingSong.duration);
                String profileName = Settings.PROFILE_LABELS[settings.qualityProfile];
                System.out.println("[MIDlet] OOM retry #" + oomRetryCount
                    + " -> " + profileName + " " + newBitrate + "k");
                playerCanvas.setLoading("Sin memoria, bajando a " + profileName + "...");
                settings.save();

                // Reintentar con nuevo bitrate en un thread separado
                final Song song = pendingSong;
                final int bitrate = newBitrate;
                new Thread() {
                    public void run() {
                        try { Thread.sleep(1500); } catch (Exception e) {}
                        musicService.playSong(song.id, bitrate, song.mimeType);
                    }
                }.start();
                return;
            }
        }

        // Mostrar error concreto de Java
        playerCanvas.setError("Error de red", error);
        pendingSong = null;
        oomRetryCount = 0;

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
