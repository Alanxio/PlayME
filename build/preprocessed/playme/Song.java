package playme;

/**
 * Modelo ligero de cancion.
 * Solo almacena datos primitivos y Strings para minimizar RAM.
 */
public class Song {

    public int id;
    public String title;
    public String artist;
    public String album;
    public int duration; // segundos
    public String coverFile;
    public String audioFile;
    public String mimeType;
    public boolean favorite;

    public Song() {
    }

    public Song(int id, String title, String artist, String album,
                int duration, String coverFile, String audioFile) {
        this(id, title, artist, album, duration, coverFile, audioFile, "audio/mpeg");
    }

    public Song(int id, String title, String artist, String album,
                int duration, String coverFile, String audioFile, String mimeType) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.coverFile = coverFile;
        this.audioFile = audioFile;
        this.mimeType = mimeType == null ? "audio/mpeg" : mimeType;
        this.favorite = false;
    }

    /**
     * Parsea un bloque JSON simple de una cancion.
     * Parser manual ultra-ligero, sin libreria JSON.
     */
    public static Song fromJSON(String json) {
        Song s = new Song();
        s.id = getIntValue(json, "id");
        s.title = getStringValue(json, "title");
        s.artist = getStringValue(json, "artist");
        s.album = getStringValue(json, "album");
        s.duration = getIntValue(json, "duration");
        s.coverFile = getStringValue(json, "cover");
        s.audioFile = getStringValue(json, "url");
        s.mimeType = getStringValue(json, "mime_type");
        if (s.mimeType == null || s.mimeType.length() == 0) {
            s.mimeType = "audio/mpeg";
        }
        return s;
    }

    /** Formato de duracion mm:ss */
    public static String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int m = seconds / 60;
        int s = seconds % 60;
        return (m < 10 ? "0" : "") + m + ":" + (s < 10 ? "0" : "") + s;
    }

    // --- Parser JSON manual ultra-ligero ---

    static String getStringValue(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return "";
        // Buscar el : despues de la key
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0) return "";
        // Buscar la primera comilla del valor
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return "";
        int q2 = json.indexOf('"', q1 + 1);
        if (q2 < 0) return "";
        return json.substring(q1 + 1, q2);
    }

    static int getIntValue(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return 0;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0) return 0;
        // Saltar espacios
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c >= '0' && c <= '9') {
                end++;
            } else {
                break;
            }
        }
        if (end == start) return 0;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Parsea un array JSON de canciones.
     * Retorna array de Song.
     */
    public static Song[] parseSongArray(String jsonArray) {
        // Contar objetos
        int count = 0;
        int idx = 0;
        while ((idx = jsonArray.indexOf('{', idx)) >= 0) {
            count++;
            idx++;
        }
        if (count == 0) return new Song[0];

        Song[] songs = new Song[count];
        int pos = 0;
        int songIdx = 0;
        while (songIdx < count) {
            int objStart = jsonArray.indexOf('{', pos);
            if (objStart < 0) break;
            int objEnd = jsonArray.indexOf('}', objStart);
            if (objEnd < 0) break;
            String obj = jsonArray.substring(objStart, objEnd + 1);
            songs[songIdx] = Song.fromJSON(obj);
            songIdx++;
            pos = objEnd + 1;
        }
        return songs;
    }
}
