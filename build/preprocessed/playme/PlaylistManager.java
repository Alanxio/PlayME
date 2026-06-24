package playme;

import java.util.Random;

/**
 * Gestiona la cola de reproduccion, mezcla aleatoria, repeticion.
 * Carga canciones bajo demanda por paginas del servidor.
 *
 * La lista completa NO se carga en memoria.
 * Solo se almacena una ventana de IDs y una cola de mezcla.
 */
public class PlaylistManager {

    private ServerClient client;
    private Settings settings;

    // Cola actual: solo IDs de canciones
    private int[] songIds;
    private String[] songTitles; // Solo titulos para mostrar en lista
    private int totalSongs;
    private int currentIndex;
    private boolean loaded;

    // Mezcla: orden aleatorio de indices
    private int[] shuffleOrder;
    private int shufflePos;

    // Filtros activos
    public static final int FILTER_ALL = 0;
    public static final int FILTER_ARTIST = 1;
    public static final int FILTER_ALBUM = 2;
    public static final int FILTER_FAVORITES = 3;
    public static final int FILTER_SEARCH = 4;
    private int activeFilter = FILTER_ALL;
    private String filterValue = "";

    // Paginacion de la ventana de datos
    private static final int WINDOW_SIZE = 40;
    private Song[] windowCache;
    private int windowStart;

    private Random random = new Random();

    public PlaylistManager(ServerClient client, Settings settings) {
        this.client = client;
        this.settings = settings;
        this.currentIndex = 0;
        this.totalSongs = 0;
        this.loaded = false;
    }

    /**
     * Carga la lista inicial.
     * Solo carga conteo y primera pagina de IDs.
     */
    public void loadCatalog() {
        new Thread() {
            public void run() {
                loadCatalogInternal();
            }
        }.start();
    }

    private void loadCatalogInternal() {
        totalSongs = client.getTotalSongs();
        if (totalSongs <= 0) {
            loaded = false;
            return;
        }

        // Cargar todos los IDs y titulos en bloques
        songIds = new int[totalSongs];
        songTitles = new String[totalSongs];
        int pageSize = 20;
        int pages = (totalSongs + pageSize - 1) / pageSize;

        for (int p = 0; p < pages; p++) {
            Song[] songs = client.getCatalogPage(p);
            for (int i = 0; i < songs.length; i++) {
                int idx = p * pageSize + i;
                if (idx < totalSongs) {
                    songIds[idx] = songs[i].id;
                    songTitles[idx] = songs[i].title;
                }
            }
        }

        currentIndex = 0;
        loaded = true;

        if (settings.shuffle) {
            generateShuffleOrder();
        }
    }

    /**
     * Carga catalogo filtrado por artista.
     */
    public void loadByArtist(String artist) {
        activeFilter = FILTER_ARTIST;
        filterValue = artist;
        // Re-cargar con filtro
        loadFilteredCatalog();
    }

    /**
     * Carga catalogo filtrado por album.
     */
    public void loadByAlbum(String album) {
        activeFilter = FILTER_ALBUM;
        filterValue = album;
        loadFilteredCatalog();
    }

    /**
     * Carga resultados de busqueda.
     */
    public void loadSearch(String query) {
        activeFilter = FILTER_SEARCH;
        filterValue = query;
        loadFilteredCatalog();
    }

    /**
     * Carga todas las canciones (sin filtro).
     */
    public void loadAll() {
        activeFilter = FILTER_ALL;
        filterValue = "";
        if (loaded && totalSongs > 0) {
            return;
        }
        loadCatalogInternal();
    }

    private void loadFilteredCatalog() {
        // Cargar primera pagina para obtener total
        Song[] firstPage;
        switch (activeFilter) {
            case FILTER_ARTIST:
                firstPage = client.getByArtist(filterValue, 0);
                break;
            case FILTER_ALBUM:
                firstPage = client.getByAlbum(filterValue, 0);
                break;
            case FILTER_SEARCH:
                firstPage = client.search(filterValue, 0);
                break;
            default:
                firstPage = client.getCatalogPage(0);
                break;
        }

        // Para simplificar, cargar hasta que no haya mas
        // (El filtrado reduce el total, asi que es manejable)
        java.util.Vector allSongs = new java.util.Vector();
        int page = 0;
        Song[] batch = firstPage;
        while (batch != null && batch.length > 0) {
            for (int i = 0; i < batch.length; i++) {
                allSongs.addElement(batch[i]);
            }
            if (batch.length < 20) break; // ultima pagina
            page++;
            switch (activeFilter) {
                case FILTER_ARTIST:
                    batch = client.getByArtist(filterValue, page);
                    break;
                case FILTER_ALBUM:
                    batch = client.getByAlbum(filterValue, page);
                    break;
                case FILTER_SEARCH:
                    batch = client.search(filterValue, page);
                    break;
                default:
                    batch = client.getCatalogPage(page);
                    break;
            }
        }

        totalSongs = allSongs.size();
        songIds = new int[totalSongs];
        songTitles = new String[totalSongs];
        for (int i = 0; i < totalSongs; i++) {
            Song s = (Song) allSongs.elementAt(i);
            songIds[i] = s.id;
            songTitles[i] = s.title;
        }

        currentIndex = 0;
        loaded = true;

        if (settings.shuffle) {
            generateShuffleOrder();
        }
    }

    /**
     * Genera orden aleatorio sin repeticiones (Fisher-Yates).
     */
    private void generateShuffleOrder() {
        if (totalSongs <= 0) return;
        shuffleOrder = new int[totalSongs];
        for (int i = 0; i < totalSongs; i++) {
            shuffleOrder[i] = i;
        }
        // Fisher-Yates shuffle
        for (int i = totalSongs - 1; i > 0; i--) {
            int j = Math.abs(random.nextInt()) % (i + 1);
            int temp = shuffleOrder[i];
            shuffleOrder[i] = shuffleOrder[j];
            shuffleOrder[j] = temp;
        }
        shufflePos = 0;
    }

    /**
     * Obtiene el indice real de la cancion actual
     * (considerando mezcla si esta activa).
     */
    private int getEffectiveIndex() {
        if (settings.shuffle && shuffleOrder != null) {
            return shuffleOrder[shufflePos % totalSongs];
        }
        return currentIndex;
    }

    /** Obtiene la cancion actual */
    public Song getCurrentSong() {
        if (!loaded || totalSongs == 0) return null;
        int idx = getEffectiveIndex();
        return client.getSongInfo(songIds[idx]);
    }

    /** Obtiene el ID de la cancion actual */
    public int getCurrentSongId() {
        if (!loaded || totalSongs == 0) return -1;
        int idx = getEffectiveIndex();
        return songIds[idx];
    }

    /** Siguiente cancion */
    public boolean next() {
        if (!loaded || totalSongs == 0) return false;

        if (settings.shuffle) {
            shufflePos++;
            if (shufflePos >= totalSongs) {
                if (settings.repeat == Settings.REPEAT_ALL) {
                    generateShuffleOrder(); // Nueva mezcla
                    return true;
                }
                shufflePos = totalSongs - 1;
                return false; // fin de lista
            }
            return true;
        }

        if (settings.repeat == Settings.REPEAT_ONE) {
            return true; // repetir misma
        }

        currentIndex++;
        if (currentIndex >= totalSongs) {
            if (settings.repeat == Settings.REPEAT_ALL) {
                currentIndex = 0;
                return true;
            }
            currentIndex = totalSongs - 1;
            return false; // fin de lista
        }
        return true;
    }

    /** Cancion anterior */
    public boolean previous() {
        if (!loaded || totalSongs == 0) return false;

        if (settings.shuffle) {
            shufflePos--;
            if (shufflePos < 0) {
                shufflePos = 0;
                return false;
            }
            return true;
        }

        currentIndex--;
        if (currentIndex < 0) {
            if (settings.repeat == Settings.REPEAT_ALL) {
                currentIndex = totalSongs - 1;
                return true;
            }
            currentIndex = 0;
            return false;
        }
        return true;
    }

    /** Selecciona cancion por indice en la lista visible */
    public void selectByIndex(int index) {
        if (index >= 0 && index < totalSongs) {
            currentIndex = index;
            if (settings.shuffle) {
                // Buscar en shuffle order
                for (int i = 0; i < totalSongs; i++) {
                    if (shuffleOrder[i] == index) {
                        shufflePos = i;
                        break;
                    }
                }
            }
        }
    }

    /** Obtiene titulo por indice */
    public String getTitleAt(int index) {
        if (index >= 0 && index < totalSongs && songTitles != null) {
            return songTitles[index];
        }
        return "";
    }

    /** Obtiene ID por indice */
    public int getIdAt(int index) {
        if (index >= 0 && index < totalSongs && songIds != null) {
            return songIds[index];
        }
        return -1;
    }

    /** Busca el indice de la primera cancion que empieza con la letra dada */
    public int findByLetter(char letter) {
        char upper = Character.toUpperCase(letter);
        for (int i = 0; i < totalSongs; i++) {
            if (songTitles[i] != null && songTitles[i].length() > 0) {
                if (Character.toUpperCase(songTitles[i].charAt(0)) >= upper) {
                    return i;
                }
            }
        }
        return totalSongs - 1; // ultima si no hay
    }

    // Getters
    public int getTotalSongs() { return totalSongs; }
    public int getCurrentIndex() {
        return settings.shuffle ? getEffectiveIndex() : currentIndex;
    }
    public int getDisplayIndex() {
        return settings.shuffle ? shufflePos : currentIndex;
    }
    public boolean isLoaded() { return loaded; }
    public int getActiveFilter() { return activeFilter; }
    public String getFilterValue() { return filterValue; }
}
