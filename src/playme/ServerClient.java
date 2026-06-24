package playme;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

/**
 * Cliente HTTP para comunicacion con el MediaServer.
 * Maneja paginacion del catalogo para no cargar todo en RAM.
 */
public class ServerClient {

    private String baseUrl;
    private static final int PAGE_SIZE = 10;
    private static final int TIMEOUT = 30000;
    private static final int MAX_RETRIES = 3;
    private static final int COVER_RETRIES = 1;

    public ServerClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setBaseUrl(String url) {
        this.baseUrl = url;
    }

    /**
     * Obtiene una pagina del catalogo.
     * @param page numero de pagina (0-based)
     * @return array de Song
     */
    public Song[] getCatalogPage(int page) {
        String url = baseUrl + "/catalog?page=" + page + "&size=" + PAGE_SIZE;
        String json = httpGet(url);
        if (json == null || json.length() == 0) return new Song[0];
        // Extraer el array "songs" del JSON
        String songsArray = extractArray(json, "songs");
        if (songsArray == null) return new Song[0];
        return Song.parseSongArray(songsArray);
    }

    /**
     * Obtiene canciones filtradas por artista.
     */
    public Song[] getByArtist(String artist, int page) {
        String url = baseUrl + "/catalog?artist=" + urlEncode(artist)
                   + "&page=" + page + "&size=" + PAGE_SIZE;
        String json = httpGet(url);
        if (json == null) return new Song[0];
        String songsArray = extractArray(json, "songs");
        if (songsArray == null) return new Song[0];
        return Song.parseSongArray(songsArray);
    }

    /**
     * Obtiene canciones filtradas por album.
     */
    public Song[] getByAlbum(String album, int page) {
        String url = baseUrl + "/catalog?album=" + urlEncode(album)
                   + "&page=" + page + "&size=" + PAGE_SIZE;
        String json = httpGet(url);
        if (json == null) return new Song[0];
        String songsArray = extractArray(json, "songs");
        if (songsArray == null) return new Song[0];
        return Song.parseSongArray(songsArray);
    }

    /**
     * Busqueda por texto.
     */
    public Song[] search(String query, int page) {
        String url = baseUrl + "/catalog?search=" + urlEncode(query)
                   + "&page=" + page + "&size=" + PAGE_SIZE;
        String json = httpGet(url);
        if (json == null) return new Song[0];
        String songsArray = extractArray(json, "songs");
        if (songsArray == null) return new Song[0];
        return Song.parseSongArray(songsArray);
    }

    /**
     * Obtiene el total de canciones.
     */
    public int getTotalSongs() {
        String json = httpGet(baseUrl + "/catalog/count");
        if (json == null) return 0;
        return Song.getIntValue(json, "total");
    }

    /**
     * Obtiene lista de artistas como array de Strings.
     */
    public String[] getArtists() {
        String json = httpGet(baseUrl + "/catalog/artists");
        if (json == null) return new String[0];
        return parseStringArray(json);
    }

    /**
     * Obtiene lista de albumes.
     */
    public String[] getAlbums() {
        String json = httpGet(baseUrl + "/catalog/albums");
        if (json == null) return new String[0];
        return parseStringArray(json);
    }

    /**
     * Obtiene info de una cancion por ID.
     */
    public Song getSongInfo(int songId) {
        String json = httpGet(baseUrl + "/song/" + songId);
        if (json == null) return null;
        return Song.fromJSON(json);
    }

    /**
     * Devuelve la URL directa del stream de audio.
     */
    public String getStreamUrl(int songId, int quality) {
        return baseUrl + "/stream/" + songId + "?quality=" + quality;
    }

    /**
     * Abre un InputStream del stream de audio.
     * NO cierra la conexion - el caller debe cerrarla.
     * Lanza IOException si falla, con el motivo real del error.
     * Incluye reintentos y acepta respuestas 206 Partial Content.
     */
    public InputStream openAudioStream(int songId, int quality) throws IOException {
        String url = getStreamUrl(songId, quality);
        String lastError = "unknown";

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println("[ServerClient] STREAM " + url + " (intento " + attempt + "/" + MAX_RETRIES + ")");
            try {
                return openAudioStreamOnce(url);
            } catch (IOException e) {
                lastError = e.getMessage();
                System.out.println("[ServerClient] stream attempt " + attempt + " failed: " + lastError);
                if (attempt < MAX_RETRIES) {
                    sleep(attempt * 1000);
                }
            }
        }
        throw new IOException("STREAM_FAILED after " + MAX_RETRIES + " retries: " + lastError);
    }

    private InputStream openAudioStreamOnce(String url) throws IOException {
        HttpConnection conn = null;
        try {
            conn = (HttpConnection) Connector.open(url, Connector.READ, true);
            conn.setRequestMethod(HttpConnection.GET);
            // Pedir al servidor que acepte range requests
            conn.setRequestProperty("Accept", "audio/mpeg");

            int rc = conn.getResponseCode();
            System.out.println("[ServerClient] stream response code " + rc);
            if (rc == HttpConnection.HTTP_OK || rc == 206) {
                return conn.openInputStream();
            }
            conn.close();
            throw new IOException("HTTP_" + rc);
        } catch (IOException e) {
            if (conn != null) {
                try { conn.close(); } catch (IOException ie) {}
            }
            throw e;
        } catch (Exception e) {
            if (conn != null) {
                try { conn.close(); } catch (IOException ie) {}
            }
            throw new IOException(e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Descarga la portada como bytes.
     * Retorna null si falla.
     */
    public byte[] downloadCover(String coverFile) {
        return downloadCover(coverFile, 0);
    }

    /**
     * Descarga la portada como bytes, opcionalmente redimensionada por el servidor.
     * @param coverFile nombre del archivo de cover
     * @param size tamaño maximo (ej. 48). 0 para tamaño original.
     * @return bytes de la imagen o null
     */
    public byte[] downloadCover(String coverFile, int size) {
        if (coverFile == null || coverFile.length() == 0) return null;
        String url = baseUrl + "/cover/" + urlEncode(coverFile);
        if (size > 0) {
            url += "?size=" + size;
        }
        return httpGetBytes(url);
    }

    // --- Metodos HTTP internos ---

    private String httpGet(String url) {
        return httpGetWithRetries(url, true);
    }

    private byte[] httpGetBytes(String url) {
        for (int attempt = 1; attempt <= COVER_RETRIES; attempt++) {
            System.out.println("[ServerClient] GET bytes " + url + " (intento " + attempt + "/" + COVER_RETRIES + ")");
            byte[] result = httpGetBytesOnce(url);
            if (result != null) return result;
            if (attempt < COVER_RETRIES) {
                sleep(attempt * 1000);
            }
        }
        return null;
    }

    private String httpGetWithRetries(String url, boolean logJson) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println("[ServerClient] GET " + url + " (intento " + attempt + "/" + MAX_RETRIES + ")");
            String result = httpGetOnce(url, logJson);
            if (result != null) return result;
            if (attempt < MAX_RETRIES) {
                sleep(attempt * 1000);
            }
        }
        System.out.println("[ServerClient] Fallo definitivo para " + url);
        return null;
    }

    private String httpGetOnce(String url, boolean logJson) {
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = (HttpConnection) Connector.open(url, Connector.READ, true);
            conn.setRequestMethod(HttpConnection.GET);
            conn.setRequestProperty("Accept", "application/json");

            int rc = conn.getResponseCode();
            System.out.println("[ServerClient] response code " + rc);
            if (rc != HttpConnection.HTTP_OK) return null;

            is = conn.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int len;
            while ((len = is.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            String response = new String(baos.toByteArray(), "UTF-8");
            if (logJson) {
                System.out.println("[ServerClient] response body " + response.length() + " bytes");
            }
            return response;
        } catch (Exception e) {
            System.out.println("[ServerClient] exception " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (IOException e) {}
            try { if (conn != null) conn.close(); } catch (IOException e) {}
        }
    }

    private byte[] httpGetBytesOnce(String url) {
        HttpConnection conn = null;
        InputStream is = null;
        try {
            conn = (HttpConnection) Connector.open(url, Connector.READ, true);
            conn.setRequestMethod(HttpConnection.GET);

            int rc = conn.getResponseCode();
            if (rc != HttpConnection.HTTP_OK) return null;

            is = conn.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            int len;
            while ((len = is.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            System.out.println("[ServerClient] bytes exception " + e.getClass().getName() + ": " + e.getMessage());
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (IOException e) {}
            try { if (conn != null) conn.close(); } catch (IOException e) {}
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {}
    }

    /**
     * Extrae un array JSON por nombre de campo.
     * Busca "field":[ ... ]
     */
    private String extractArray(String json, String field) {
        String search = "\"" + field + "\"";
        int ki = json.indexOf(search);
        if (ki < 0) return null;
        int bracket = json.indexOf('[', ki);
        if (bracket < 0) return null;
        // Encontrar el ] correspondiente
        int depth = 1;
        int pos = bracket + 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            pos++;
        }
        return json.substring(bracket, pos);
    }

    /**
     * Parsea un array JSON simple de strings: ["a","b","c"]
     */
    private String[] parseStringArray(String json) {
        int bracket = json.indexOf('[');
        if (bracket < 0) return new String[0];
        // Contar strings
        int count = 0;
        int idx = bracket;
        while ((idx = json.indexOf('"', idx + 1)) >= 0) {
            count++;
            idx = json.indexOf('"', idx + 1);
            if (idx < 0) break;
        }
        if (count == 0) return new String[0];

        String[] result = new String[count];
        int pos = bracket;
        for (int i = 0; i < count; i++) {
            int q1 = json.indexOf('"', pos + 1);
            if (q1 < 0) break;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            result[i] = json.substring(q1 + 1, q2);
            pos = q2;
        }
        return result;
    }

    /**
     * URL-encode basico para J2ME (sin java.net.URLEncoder).
     */
    static String urlEncode(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append("%20");
            } else {
                // Encode byte a byte
                String hex = Integer.toHexString(c).toUpperCase();
                if (hex.length() == 1) hex = "0" + hex;
                sb.append('%');
                sb.append(hex);
            }
        }
        return sb.toString();
    }
}
