package playme;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;
import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreFullException;

/**
 * Servicio de reproduccion de audio.
 * Gestiona el ciclo de vida del Player MMAPI:
 * stop -> close -> gc -> create -> prefetch -> start
 */
public class MusicService implements PlayerListener {

    public static final int STATE_IDLE = 0;
    public static final int STATE_BUFFERING = 1;
    public static final int STATE_PLAYING = 2;
    public static final int STATE_PAUSED = 3;
    public static final int STATE_ERROR = 4;

    private Player player;
    private VolumeControl volumeCtrl;
    private InputStream currentStream;
    private int state = STATE_IDLE;
    private long duration; // microsegundos
    private MusicServiceListener listener;
    private ServerClient client;
    private int currentSongId = -1;
    private int volume = 80;

    // Rate control para velocidad x2
    private boolean doubleSpeed = false;

    public interface MusicServiceListener {
        void onPlaybackStarted();
        void onPlaybackStopped();
        void onPlaybackBuffering();
        void onPlaybackError(String error);
        void onPlaybackComplete();
    }

    public MusicService(ServerClient client) {
        this.client = client;
    }

    public void setListener(MusicServiceListener listener) {
        this.listener = listener;
    }

    /**
     * Reproduce una cancion por ID.
     * Primero limpia el player anterior.
     * @param mimeType tipo MIME del audio (audio/mpeg, audio/amr, etc.)
     */
    public void playSong(final int songId, final int quality, final String mimeType) {
        // Ejecutar en thread separado para no bloquear UI
        new Thread() {
            public void run() {
                playSongInternal(songId, quality, mimeType);
            }
        }.start();
    }

    private void playSongInternal(int songId, int quality, String mimeType) {
        // 1. Detener reproduccion actual
        stopAndCleanup();

        // 2. Preparar reproduccion
        currentSongId = songId;
        state = STATE_BUFFERING;
        if (listener != null) listener.onPlaybackBuffering();

        try {
            // 3. Descarga completa a RecordStore (RMS) distribuido y reproduce desde ahi
            String contentType = (mimeType != null && mimeType.length() > 0) ? mimeType : "audio/mpeg";
            player = createPlayerFromStorage(songId, quality, contentType);

            player.addPlayerListener(this);
            player.realize();
            player.prefetch();

            // 4. Configurar volumen
            try {
                volumeCtrl = (VolumeControl) player.getControl("VolumeControl");
                if (volumeCtrl != null) {
                    volumeCtrl.setLevel(volume);
                }
            } catch (Exception e) {
                System.out.println("[MusicService] volume error: " + formatError(e));
            }

            // 5. Obtener duracion
            duration = player.getDuration();

            // 6. Iniciar reproduccion
            player.start();
            state = STATE_PLAYING;

            if (listener != null) listener.onPlaybackStarted();

        } catch (Exception e) {
            state = STATE_ERROR;
            String errorDetail = formatError(e);
            System.out.println("[MusicService] playSongInternal error: " + errorDetail);
            stopAndCleanup();
            if (listener != null) listener.onPlaybackError(errorDetail);
        }
    }



    /**
     * Descarga el audio completo a RecordStore (RMS) distribuido por chunks
     * de 32 KB usando HTTP Range Requests. Cada chunk se guarda en un
     * RecordStore independiente (audio_chunk_000, audio_chunk_001, ...) siguiendo
     * el patron de MahoMaps/mm-v1. Esto evita los limites de tamano de un
     * unico RecordStore y no requiere permisos de FileConnection.
     * El tamano total se obtiene del header Content-Range del primer chunk.
     * Si el archivo es mayor a MAX_RMS_TOTAL_SIZE, lanza IOException.
     */
    private Player createPlayerFromStorage(int songId, int quality, String contentType) throws Exception {
        final int MAX_RMS_TOTAL_SIZE = 2 * 1024 * 1024; // 2 MB limite total en RMS
        final int CHUNK_SIZE = 32 * 1024;               // 32 KB por chunk (mismo tamano que tiles)
        final int MAX_RETRIES = 3;

        String url = client.getStreamUrl(songId, quality);
        System.out.println("[MusicService] RMS chunks: " + url);

        // 1. Descargar primer chunk con Range para obtener Content-Range/total
        int[] totalSizeHolder = new int[1];
        byte[] firstChunk = downloadFirstChunk(url, contentType, CHUNK_SIZE, MAX_RETRIES, totalSizeHolder);
        int totalSize = totalSizeHolder[0];
        System.out.println("[MusicService] totalSize=" + totalSize + " firstChunk=" + firstChunk.length);

        if (totalSize > MAX_RMS_TOTAL_SIZE) {
            throw new IOException("ARCHIVO_DEMASIADO_GRANDE " + totalSize);
        }
        if (totalSize <= 0) {
            throw new IOException("SIN_CONTENT_LENGTH");
        }

        // 2. Limpiar chunks anteriores
        RecordStoreInputStream.clearAllChunks();
        System.gc();

        // 3. Guardar primer chunk en su propio RecordStore
        RecordStore rs = RecordStore.openRecordStore(RecordStoreInputStream.chunkName(0), true);
        rs.addRecord(firstChunk, 0, firstChunk.length);
        rs.closeRecordStore();
        rs = null;

        int downloaded = firstChunk.length;
        int chunkIndex = 1;
        System.out.println("[MusicService] chunk 0 stored in RMS: " + downloaded + "/" + totalSize);

        try {
            // 4. Descargar y guardar resto de chunks
            while (downloaded < totalSize) {
                int start = downloaded;
                int end = start + CHUNK_SIZE - 1;
                if (end >= totalSize) {
                    end = totalSize - 1;
                }

                byte[] chunk = downloadChunk(url, contentType, start, end, chunkIndex, MAX_RETRIES);
                String rmsName = RecordStoreInputStream.chunkName(chunkIndex);
                rs = RecordStore.openRecordStore(rmsName, true);
                rs.addRecord(chunk, 0, chunk.length);
                rs.closeRecordStore();
                rs = null;

                downloaded += chunk.length;
                chunkIndex++;

                System.out.println("[MusicService] chunk " + (chunkIndex - 1) + " stored in RMS: " + downloaded + "/" + totalSize);
            }

            // 5. Crear player desde RecordStoreInputStream
            System.gc();
            RecordStoreInputStream rsis = new RecordStoreInputStream(totalSize);
            currentStream = rsis;
            Player player = Manager.createPlayer(rsis, contentType);
            player.prefetch();
            System.out.println("[MusicService] Player creado desde RMS, total=" + totalSize);
            return player;

        } catch (RecordStoreFullException e) {
            RecordStoreInputStream.clearAllChunks();
            throw new IOException("RMS_LLENO " + totalSize);
        } catch (RecordStoreException e) {
            RecordStoreInputStream.clearAllChunks();
            throw new IOException("RMS_ERROR " + e.getMessage());
        }
    }

    /**
     * Descarga el primer chunk (bytes 0-CHUNK_SIZE-1) con Range.
     * Devuelve los bytes descargados y deja el tamano total en totalSizeHolder[0].
     * Si el servidor ignora Range y devuelve 200 OK, usa todo el cuerpo como fallback.
     */
    private byte[] downloadFirstChunk(String url, String contentType, int chunkSize, int maxRetries, int[] totalSizeHolder) throws IOException {
        IOException lastError = null;
        totalSizeHolder[0] = 0;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpConnection conn = null;
            InputStream is = null;
            ByteArrayOutputStream baos = null;

            try {
                conn = (HttpConnection) Connector.open(url, Connector.READ, true);
                conn.setRequestMethod(HttpConnection.GET);
                if (contentType != null && contentType.length() > 0) {
                    conn.setRequestProperty("Accept", contentType);
                }
                conn.setRequestProperty("Range", "bytes=0-" + (chunkSize - 1));

                int rc = conn.getResponseCode();
                System.out.println("[MusicService] firstChunk HTTP " + rc);

                // Fallback: servidor ignoro Range y envio todo el archivo
                if (rc == HttpConnection.HTTP_OK) {
                    int len = (int) conn.getLength();
                    if (len <= 0) len = 50 * 1024;
                    baos = new ByteArrayOutputStream(len);
                    is = conn.openInputStream();
                    byte[] buffer = new byte[2048];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    byte[] result = baos.toByteArray();
                    totalSizeHolder[0] = result.length;
                    System.out.println("[MusicService] firstChunk fallback 200, size=" + result.length);
                    return result;
                }

                if (rc != 206) {
                    throw new IOException("FIRST_CHUNK_HTTP_" + rc);
                }

                // Parsear Content-Range: bytes 0-65535/total
                String contentRange = conn.getHeaderField("Content-Range");
                int totalSize = 0;
                if (contentRange != null) {
                    int slashIndex = contentRange.indexOf('/');
                    if (slashIndex != -1 && slashIndex + 1 < contentRange.length()) {
                        String totalStr = contentRange.substring(slashIndex + 1).trim();
                        try {
                            totalSize = Integer.parseInt(totalStr);
                        } catch (NumberFormatException nfe) {
                            System.out.println("[MusicService] Content-Range parse error: " + contentRange);
                        }
                    }
                }
                if (totalSize <= 0) {
                    throw new IOException("NO_CONTENT_RANGE");
                }
                totalSizeHolder[0] = totalSize;

                int expected = chunkSize;
                if (expected > totalSize) expected = totalSize;
                int len = (int) conn.getLength();
                if (len <= 0 || len > expected) {
                    len = expected;
                }

                baos = new ByteArrayOutputStream(len);
                is = conn.openInputStream();
                byte[] buffer = new byte[2048];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }

                byte[] result = baos.toByteArray();
                System.out.println("[MusicService] firstChunk 206 got " + result.length + " total=" + totalSize);
                return result;

            } catch (IOException e) {
                lastError = e;
                System.out.println("[MusicService] firstChunk attempt " + attempt + " failed: " + formatError(e));
            } finally {
                try { if (is != null) is.close(); } catch (Exception ex) {}
                try { if (conn != null) conn.close(); } catch (Exception ex) {}
            }
        }

        throw lastError;
    }

    /**
     * Descarga un rango de bytes usando Range: bytes=start-end.
     * Reintentando hasta maxRetries veces ante errores de red.
     */
    private byte[] downloadChunk(String url, String contentType, int start, int end, int chunkIndex, int maxRetries) throws IOException {
        IOException lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            HttpConnection conn = null;
            InputStream is = null;
            ByteArrayOutputStream baos = null;

            try {
                conn = (HttpConnection) Connector.open(url, Connector.READ, true);
                conn.setRequestMethod(HttpConnection.GET);
                if (contentType != null && contentType.length() > 0) {
                    conn.setRequestProperty("Accept", contentType);
                }
                conn.setRequestProperty("Range", "bytes=" + start + "-" + end);

                int rc = conn.getResponseCode();
                if (rc != HttpConnection.HTTP_OK && rc != 206) {
                    throw new IOException("CHUNK_" + chunkIndex + "_HTTP_" + rc);
                }

                int expected = end - start + 1;
                int len = (int) conn.getLength();
                if (len <= 0 || len > expected) {
                    len = expected;
                }

                baos = new ByteArrayOutputStream(len);
                is = conn.openInputStream();
                byte[] buffer = new byte[2048];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }

                byte[] result = baos.toByteArray();
                System.out.println("[MusicService] chunk " + chunkIndex + " attempt " + attempt + " got " + result.length + " bytes");
                return result;

            } catch (IOException e) {
                lastError = e;
                System.out.println("[MusicService] chunk " + chunkIndex + " attempt " + attempt + " failed: " + formatError(e));
            } finally {
                try { if (is != null) is.close(); } catch (Exception ex) {}
                try { if (conn != null) conn.close(); } catch (Exception ex) {}
            }
        }

        throw lastError;
    }

    private String formatError(Exception e) {
        String cls = e.getClass().getName();
        String msg = e.getMessage();
        if (msg != null && msg.length() > 0) {
            return cls + ": " + msg;
        }
        return cls;
    }

    /** Pausa/Reanuda la reproduccion */
    public void togglePause() {
        if (player == null) return;
        try {
            if (state == STATE_PAUSED) {
                player.start();
                state = STATE_PLAYING;
                if (listener != null) listener.onPlaybackStarted();
            } else if (state == STATE_PLAYING) {
                player.stop();
                state = STATE_PAUSED;
                if (listener != null) listener.onPlaybackStopped();
            }
        } catch (Exception e) {}
    }

    /** Detiene y limpia todo */
    public void stopAndCleanup() {
        state = STATE_IDLE;
        doubleSpeed = false;

        if (player != null) {
            try {
                if (player.getState() == Player.STARTED) {
                    player.stop();
                }
            } catch (Exception e) {}
            try {
                player.close();
            } catch (Exception e) {}
            player = null;
        }

        volumeCtrl = null;

        if (currentStream != null) {
            try {
                currentStream.close();
            } catch (Exception e) {}
            currentStream = null;
        }

        // Limpiar chunks de RMS residuales
        RecordStoreInputStream.clearAllChunks();

        // Forzar limpieza de memoria
        System.gc();
    }

    /** Avanza o retrocede N segundos */
    public void seek(int deltaSec) {
        if (player == null) return;
        try {
            long current = player.getMediaTime();
            long target = current + ((long) deltaSec * 1000000L);
            if (target < 0) target = 0;
            if (duration > 0 && target > duration) target = duration;
            player.setMediaTime(target);
        } catch (Exception e) {
            // seek no soportado en algunos dispositivos
        }
    }

    /** Obtiene posicion actual en segundos */
    public int getCurrentPosition() {
        if (player == null) return 0;
        try {
            return (int) (player.getMediaTime() / 1000000L);
        } catch (Exception e) {
            return 0;
        }
    }

    /** Obtiene duracion en segundos */
    public int getDurationSeconds() {
        if (duration <= 0) return 0;
        return (int) (duration / 1000000L);
    }

    /** Establece volumen (0-100) */
    public void setVolume(int vol) {
        this.volume = vol;
        if (volumeCtrl != null) {
            try {
                volumeCtrl.setLevel(vol);
            } catch (Exception e) {}
        }
    }

    public int getVolume() {
        return volume;
    }

    /** Intenta poner velocidad x2 */
    public void toggleDoubleSpeed() {
        if (player == null) return;
        try {
            javax.microedition.media.control.RateControl rc =
                (javax.microedition.media.control.RateControl)
                player.getControl("RateControl");
            if (rc != null) {
                if (doubleSpeed) {
                    rc.setRate(100000); // 100% = velocidad normal
                    doubleSpeed = false;
                } else {
                    rc.setRate(200000); // 200% = velocidad doble
                    doubleSpeed = true;
                }
            }
        } catch (Exception e) {
            // RateControl no soportado
        }
    }

    public boolean isPlaying() { return state == STATE_PLAYING; }
    public boolean isPaused() { return state == STATE_PAUSED; }
    public boolean isBuffering() { return state == STATE_BUFFERING; }
    public int getState() { return state; }
    public boolean isDoubleSpeed() { return doubleSpeed; }
    public int getCurrentSongId() { return currentSongId; }

    // --- PlayerListener ---

    public void playerUpdate(Player p, String event, Object data) {
        if (event.equals(PlayerListener.END_OF_MEDIA)) {
            state = STATE_IDLE;
            if (listener != null) listener.onPlaybackComplete();
        } else if (event.equals(PlayerListener.ERROR)) {
            state = STATE_ERROR;
            String detail = (data != null && data instanceof String) ? (String) data : "unknown";
            System.out.println("[MusicService] playerUpdate ERROR: " + detail);
            if (listener != null) listener.onPlaybackError("PlayerError: " + detail);
        }
    }
}
